package app.wichtelein.speech

import app.wichtelein.camera.*
import app.wichtelein.anim.*
import app.wichtelein.scene.*
import app.wichtelein.videoexport.*
import app.wichtelein.settings.*
import app.wichtelein.ui.*

import android.content.Context
import android.content.res.AssetManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/** Gesprochener Satz als Rohdaten (Mono, Float -1..1). */
class VoiceAudio(val samples: FloatArray, val sampleRate: Int)

/** Stimmungen des Modells „Thorsten emotional“ (Sprecher-Nummern). */
object Mood {
    const val AMUSED = 0
    const val NEUTRAL = 4
    const val SLEEPY = 5
    const val SURPRISED = 6
    const val WHISPER = 7

    val choices = listOf(
        "Normal" to NEUTRAL,
        "Fröhlich" to AMUSED,
        "Überrascht" to SURPRISED,
        "Müde" to SLEEPY,
        "Flüsternd" to WHISPER,
    )
}

/**
 * Die Wichtelstimme: ein neuronales deutsches Sprachmodell (Piper, läuft offline über sherpa-onnx).
 * Eingebaut ist „Thorsten emotional“ (Assets `voice/`, siehe `tools/fetch-voice.sh`); weitere Stimmen lädt
 * [VoiceDownloads]. Die Tonhöhe lässt sich per [pitchShift] leicht anheben (Wichtel-Effekt).
 */
object ElfVoice {
    enum class State { IDLE, PREPARING, READY, UNAVAILABLE }

    private const val DATA_VERSION = "1"

    var state by mutableStateOf(State.IDLE)
        private set
    var error: String? = null
        private set

    /** Welche Stimme gerade geladen ist. */
    var activeId: String? = null
        private set

    @Volatile private var tts: OfflineTts? = null
    private var requested: String? = null
    private val exec = Executors.newSingleThreadExecutor()

    /** Lädt die Stimme [voiceId] im Hintergrund (nichts zu tun, wenn sie schon geladen wird). */
    @Synchronized
    fun prepareAsync(context: Context, voiceId: String = VoiceCatalog.DEFAULT_ID) {
        if (requested == voiceId && (state == State.PREPARING || state == State.READY)) return
        requested = voiceId
        state = State.PREPARING
        val app = context.applicationContext
        exec.execute {
            try {
                // erst freigeben, wenn gerade kein Satz mit dem alten Modell erzeugt wird (sonst Absturz im nativen Code)
                synchronized(this@ElfVoice) {
                    val old = tts
                    tts = null
                    old?.release()
                }
                val loaded = load(app, voiceId)
                synchronized(this@ElfVoice) {
                    tts = loaded
                    activeId = voiceId
                }
                error = null
                if (requested == voiceId) state = State.READY
            } catch (t: Throwable) {
                error = t.message ?: t.javaClass.simpleName
                if (requested == voiceId) state = State.UNAVAILABLE
            }
        }
    }

    private fun load(context: Context, voiceId: String): OfflineTts {
        val base = ensureBundled(context)
        val info = VoiceCatalog.find(voiceId)
        val dir = if (info.bundled) base else VoiceCatalog.dir(context, info.id)
        if (!File(dir, "model.onnx").exists()) throw IllegalStateException("Stimme „${info.name}“ ist nicht installiert")
        val vits = OfflineTtsVitsModelConfig(
            model = File(dir, "model.onnx").path,
            tokens = File(dir, "tokens.txt").path,
            dataDir = File(base, "espeak-ng-data").path,
            noiseScale = 0.667f,
            noiseScaleW = 0.8f,
            lengthScale = 1.0f,
        )
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(vits = vits, numThreads = 2, debug = false, provider = "cpu"),
            maxNumSentences = 1,
            silenceScale = 0.25f,
        )
        return OfflineTts(assetManager = null, config = config)
    }

    /** Entpackt das eingebaute Modell samt espeak-Daten (einmalig) und liefert den Ordner. */
    private fun ensureBundled(context: Context): File {
        val am = context.assets
        val dest = File(context.filesDir, "voice-$DATA_VERSION")
        val marker = File(dest, ".ready")
        if (marker.exists()) return dest
        if (am.list("voice")?.contains("model.onnx") != true) {
            throw IllegalStateException("Stimmen-Modell ist nicht in der App enthalten")
        }
        dest.deleteRecursively()
        copyAssetDir(am, "voice", dest)
        marker.writeText("ok")
        return dest
    }

    private fun copyAssetDir(am: AssetManager, src: String, dst: File) {
        val children = am.list(src) ?: emptyArray()
        if (children.isEmpty()) {
            try {
                dst.parentFile?.mkdirs()
                am.open(src).use { input -> dst.outputStream().use { input.copyTo(it) } }
            } catch (_: IOException) {
                dst.mkdirs() // leeres Verzeichnis
            }
        } else {
            dst.mkdirs()
            for (name in children) copyAssetDir(am, "$src/$name", File(dst, name))
        }
    }

    /**
     * Erzeugt einen Satz. [pitchFactor] hebt die Stimme an (1 = unverändert), [rate] ist das Sprechtempo
     * (1 = normal). Blockiert, deshalb nicht auf dem UI-Thread aufrufen. Gibt null zurück, wenn keine Stimme geladen ist.
     */
    @Synchronized
    fun synthesize(text: String, mood: Int, rate: Float, pitchFactor: Float, voiceId: String? = null): VoiceAudio? {
        val engine = tts ?: return null
        // geladen ist eine andere Stimme als gewünscht (z. B. gerade gewechselt): lieber nichts als die falsche Stimme
        if (voiceId != null && activeId != voiceId) return null
        val sid = mood.coerceIn(0, maxOf(0, engine.numSpeakers() - 1))
        val audio = engine.generate(text, sid, rate.coerceIn(0.5f, 1.6f))
        if (audio.samples.isEmpty()) throw IllegalStateException("Sprachmodell lieferte keine Audiodaten")
        val shifted = pitchShift(audio.samples, pitchFactor)
        return VoiceAudio(normalizePeak(shifted, 0.9f), audio.sampleRate)
    }
}
