package app.wichtelein

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Sprachausgabe des Wichtels. Bevorzugt die Wichtelstimme ([ElfVoice]); steht sie nicht zur Verfügung
 * (oder ist „Systemstimme“ gewählt), spricht die Android-Sprachausgabe. Sonst bleibt nur die Sprechblase.
 */
class Speaker(context: Context) {
    private val appContext = context.applicationContext
    private var system: TextToSpeech? = null
    private val playExec = Executors.newSingleThreadExecutor()
    private val generation = AtomicInteger()
    private var track: AudioTrack? = null

    /** Systemstimme: null = noch nicht bereit, true = deutsche Stimme vorhanden, false = nicht verfügbar. */
    var systemAvailable by mutableStateOf<Boolean?>(null)
        private set

    var enabled = true
    /** Einstellung 0,8 bis 2,0; bei der Wichtelstimme wird daraus der Anhebungsfaktor. */
    var pitch = 1.6f
    var rate = 1.0f
    var mood = Mood.AMUSED
    var useElfVoice = true

    init {
        ElfVoice.prepareAsync(appContext)
        system = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = system?.setLanguage(Locale.GERMAN)
                systemAvailable = r != null && r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            } else {
                systemAvailable = false
            }
        }
    }

    /** Welche Stimme gerade benutzt wird, als Text für die Oberfläche. */
    val statusText: String
        get() = when {
            !enabled -> "Sprachausgabe aus"
            useElfVoice && ElfVoice.state == ElfVoice.State.READY -> "Wichtelstimme"
            useElfVoice && ElfVoice.state == ElfVoice.State.PREPARING -> "Wichtelstimme wird vorbereitet …"
            systemAvailable == true -> "Systemstimme"
            else -> "Keine Sprachausgabe verfügbar – nur Sprechblasen"
        }

    fun speak(text: String, moodOverride: Int? = null) {
        if (!enabled) return
        if (useElfVoice) {
            when (ElfVoice.state) {
                ElfVoice.State.READY -> {
                    speakElf(text, moodOverride ?: mood)
                    return
                }
                ElfVoice.State.PREPARING, ElfVoice.State.IDLE -> return // nur Sprechblase, keine fremde Stimme dazwischen
                ElfVoice.State.UNAVAILABLE -> Unit // weiter mit der Systemstimme
            }
        }
        speakSystem(text)
    }

    private fun speakElf(text: String, moodId: Int) {
        val id = generation.incrementAndGet()
        stopPlayback()
        val factor = 1f + (pitch - 1f) * 0.7f
        val r = rate
        playExec.execute {
            val audio = try {
                ElfVoice.synthesize(text, moodId, r, factor)
            } catch (t: Throwable) {
                null
            } ?: return@execute
            if (generation.get() != id) return@execute // inzwischen kam ein neuer Satz
            play(audio)
        }
    }

    @Synchronized
    private fun play(audio: VoiceAudio) {
        stopPlayback()
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(audio.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(audio.samples.size * 4)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(audio.samples, 0, audio.samples.size, AudioTrack.WRITE_BLOCKING)
        track = t
        t.play()
    }

    @Synchronized
    private fun stopPlayback() {
        track?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        track = null
    }

    private fun speakSystem(text: String) {
        val t = system ?: return
        if (systemAvailable != true) return
        t.setPitch(pitch)
        t.setSpeechRate(rate)
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "wichtel")
    }

    fun shutdown() {
        generation.incrementAndGet()
        stopPlayback()
        system?.stop()
        system?.shutdown()
        system = null
        playExec.shutdown()
    }
}
