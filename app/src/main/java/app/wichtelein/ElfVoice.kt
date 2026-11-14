package app.wichtelein

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

/** Stimmungen des Modells (Sprecher-Nummern von „thorsten_emotional“). */
object Mood {
    const val AMUSED = 0
    const val NEUTRAL = 4
    const val SLEEPY = 5
    const val SURPRISED = 6
    const val WHISPER = 7

    val choices = listOf(
        "Fröhlich" to AMUSED,
        "Überrascht" to SURPRISED,
        "Normal" to NEUTRAL,
        "Müde" to SLEEPY,
        "Flüsternd" to WHISPER,
    )
}

/**
 * Die Wichtelstimme: ein neuronales deutsches Sprachmodell (Piper „Thorsten emotional“, CC0, läuft komplett offline
 * über sherpa-onnx), dessen Tonhöhe per [pitchShift] angehoben wird. Das Modell liegt in den App-Assets
 * (`voice/`, siehe `tools/fetch-voice.sh`) und wird beim ersten Start einmalig in den App-Speicher entpackt.
 */
object ElfVoice {
    enum class State { IDLE, PREPARING, READY, UNAVAILABLE }

    private const val DATA_VERSION = "1"

    var state by mutableStateOf(State.IDLE)
        private set
    var error: String? = null
        private set

    @Volatile private var tts: OfflineTts? = null
    private val exec = Executors.newSingleThreadExecutor()

    /** Startet die Vorbereitung im Hintergrund (nur einmal). */
    @Synchronized
    fun prepareAsync(context: Context) {
        if (state != State.IDLE) return
        state = State.PREPARING
        val app = context.applicationContext
        exec.execute {
            try {
                tts = load(app)
                state = State.READY
            } catch (t: Throwable) {
                error = t.message ?: t.javaClass.simpleName
                state = State.UNAVAILABLE
            }
        }
    }

    private fun load(context: Context): OfflineTts {
        val am = context.assets
        if (am.list("voice")?.contains("model.onnx") != true) {
            throw IllegalStateException("Stimmen-Modell ist nicht in der App enthalten")
        }
        val dest = File(context.filesDir, "voice-$DATA_VERSION")
        val marker = File(dest, ".ready")
        if (!marker.exists()) {
            dest.deleteRecursively()
            copyAssetDir(am, "voice", dest)
            marker.writeText("ok")
        }
        val vits = OfflineTtsVitsModelConfig(
            model = File(dest, "model.onnx").path,
            tokens = File(dest, "tokens.txt").path,
            dataDir = File(dest, "espeak-ng-data").path,
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

    private fun copyAssetDir(am: AssetManager, src: String, dst: File) {
        val children = am.list(src) ?: emptyArray()
        if (children.isEmpty()) {
            try {
                dst.parentFile?.mkdirs()
                am.open(src).use { input -> dst.outputStream().use { input.copyTo(it) } }
            } catch (_: IOException) {
                // leeres Verzeichnis
                dst.mkdirs()
            }
        } else {
            dst.mkdirs()
            for (name in children) copyAssetDir(am, "$src/$name", File(dst, name))
        }
    }

    /**
     * Erzeugt einen Satz. [pitchFactor] hebt die Stimme an (1 = unverändert), [rate] ist das Sprechtempo
     * (1 = normal). Blockiert, deshalb nicht auf dem UI-Thread aufrufen. Gibt null zurück, wenn die Stimme fehlt.
     */
    @Synchronized
    fun synthesize(text: String, mood: Int, rate: Float, pitchFactor: Float): VoiceAudio? {
        val engine = tts ?: return null
        val sid = mood.coerceIn(0, max(0, engine.numSpeakers() - 1))
        val audio = engine.generate(text, sid, rate.coerceIn(0.5f, 1.6f))
        if (audio.samples.isEmpty()) throw IllegalStateException("Sprachmodell lieferte keine Audiodaten")
        val shifted = pitchShift(audio.samples, pitchFactor)
        return VoiceAudio(normalizePeak(shifted, 0.9f), audio.sampleRate)
    }

    private fun max(a: Int, b: Int) = if (a > b) a else b
}
