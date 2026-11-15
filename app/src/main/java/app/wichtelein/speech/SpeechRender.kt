package app.wichtelein.speech

import app.wichtelein.camera.*
import app.wichtelein.anim.*
import app.wichtelein.scene.*
import app.wichtelein.videoexport.*
import app.wichtelein.settings.*
import app.wichtelein.ui.*

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Erzeugt die Sprachclips einer Aufführung als Rohdaten (für Vorschau und Videoexport).
 * Mit der Wichtelstimme direkt; die Systemstimme liefert keine Rohdaten und wird in der Vorschau live gesprochen.
 */
object SpeechRender {
    private val cache = LinkedHashMap<String, VoiceAudio>()

    private fun key(text: String, mood: Int, s: Settings) =
        "${s.voiceId}|$mood|${s.rate}|${s.effect}|$text"

    /** true, wenn [render] mit den aktuellen Einstellungen Rohdaten liefern kann. */
    fun canRender(s: Settings) =
        s.speak && s.elfVoice && ElfVoice.state == ElfVoice.State.READY && ElfVoice.activeId == s.voiceId

    @Synchronized
    private fun cached(k: String): VoiceAudio? = cache[k]

    @Synchronized
    private fun store(k: String, a: VoiceAudio) {
        cache[k] = a
        while (cache.size > 40) cache.remove(cache.keys.first())
    }

    /** Blockiert (Sprachsynthese); nicht auf dem UI-Thread aufrufen. */
    fun render(s: Settings, ev: SpeechEvent): VoiceAudio? {
        if (!canRender(s)) return null
        val mood = ev.mood ?: s.mood
        val k = key(ev.text, mood, s)
        cached(k)?.let { return it }
        val audio = try {
            ElfVoice.synthesize(ev.text, mood, s.rate, 1f + 0.45f * s.effect, s.voiceId)
        } catch (_: Throwable) {
            null
        } ?: return null
        store(k, audio)
        return audio
    }

    /** Ergebnis von [renderAll]: ein Clip je Ereignis (null = keiner) und ob die Stimme einer Figur gefehlt hat. */
    class Result(val clips: List<VoiceAudio?>, val missing: Boolean)

    /**
     * Erzeugt die Clips aller Ereignisse; jede Figur mit ihrer eigenen Stimme (Wichtel männlich, Elfin weiblich). Spricht nur eine Figur,
     * wird nur ihre Stimme geladen; bei zwei Figuren wird die Stimme nacheinander gewechselt. Blockiert.
     */
    fun renderAll(context: Context, s: Settings, events: List<SpeechEvent>, timeoutMs: Long = 120_000, onProgress: (Int) -> Unit = {}): Result =
        // nur eine Erzeugung zugleich (Vorschau und Export dürfen sich nicht gegenseitig die Stimme wegnehmen)
        synchronized(renderLock) { renderAllLocked(context, s, events, timeoutMs, onProgress) }

    private val renderLock = Any()

    private fun renderAllLocked(context: Context, s: Settings, events: List<SpeechEvent>, timeoutMs: Long, onProgress: (Int) -> Unit): Result {
        val out = arrayOfNulls<VoiceAudio>(events.size)
        var missing = false
        var done = 0
        for (actor in events.map { it.actor }.distinct().sorted()) {
            val sa = s.forActor(actor)
            if (!awaitVoice(context, sa, timeoutMs)) {
                missing = true
                continue
            }
            for ((i, ev) in events.withIndex()) if (ev.actor == actor) {
                out[i] = render(sa, ev)
                onProgress(++done)
            }
        }
        return Result(out.toList(), missing)
    }

    /** Wartet (höchstens [timeoutMs]), bis die gewünschte Stimme geladen ist. */
    fun awaitVoice(context: Context, s: Settings, timeoutMs: Long = 120_000): Boolean {
        if (!s.speak || !s.elfVoice) return false
        ElfVoice.prepareAsync(context, s.voiceId)
        val t0 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t0 < timeoutMs) {
            if (canRender(s)) return true
            if (ElfVoice.state == ElfVoice.State.UNAVAILABLE) return false
            Thread.sleep(150)
        }
        return false
    }
}

/** Mischt Sprachclips zu einer Tonspur der Länge [durationMs] (Mono, 16 Bit PCM) und schreibt sie als WAV. */
object SpeechMixer {
    const val SAMPLE_RATE = 44100

    /** Lautstärke der Geräusche gegenüber der Sprache */
    const val FX_VOLUME = 0.55f

    fun mix(
        events: List<SpeechEvent>,
        clips: List<VoiceAudio?>,
        durationMs: Long,
        sounds: List<app.wichtelein.sound.SoundEvent> = emptyList(),
        music: FloatArray? = null,
        musicVolume: Float = 0.35f,
    ): ShortArray {
        val total = (durationMs * SAMPLE_RATE / 1000L).toInt()
        val acc = FloatArray(total)
        app.wichtelein.sound.SoundMixer.mixInto(acc, sounds, FX_VOLUME)
        if (music != null) app.wichtelein.sound.MusicMixer.mixInto(acc, music, musicVolume)
        for ((i, ev) in events.withIndex()) {
            val clip = clips.getOrNull(i) ?: continue
            val start = (ev.timeMs * SAMPLE_RATE / 1000L).toInt()
            val ratio = clip.sampleRate.toDouble() / SAMPLE_RATE
            val n = (clip.samples.size / ratio).toInt()
            for (j in 0 until n) {
                val idx = start + j
                if (idx >= total) break
                val pos = j * ratio
                val i0 = pos.toInt()
                val a = clip.samples[i0.coerceAtMost(clip.samples.size - 1)]
                val b = clip.samples[(i0 + 1).coerceAtMost(clip.samples.size - 1)]
                acc[idx] += a + (b - a) * (pos - i0).toFloat()
            }
        }
        return ShortArray(total) { (acc[it].coerceIn(-1f, 1f) * 32767f).toInt().toShort() }
    }

    fun toWav(pcm: ShortArray): ByteArray {
        val data = ByteArray(pcm.size * 2)
        for (i in pcm.indices) {
            data[i * 2] = (pcm[i].toInt() and 0xFF).toByte()
            data[i * 2 + 1] = ((pcm[i].toInt() shr 8) and 0xFF).toByte()
        }
        fun le32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
        fun le16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
        val out = java.io.ByteArrayOutputStream()
        out.write("RIFF".toByteArray()); out.write(le32(36 + data.size)); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); out.write(le32(16)); out.write(le16(1)); out.write(le16(1))
        out.write(le32(SAMPLE_RATE)); out.write(le32(SAMPLE_RATE * 2)); out.write(le16(2)); out.write(le16(16))
        out.write("data".toByteArray()); out.write(le32(data.size)); out.write(data)
        return out.toByteArray()
    }
}


/** Liest eine WAV-Datei (16 Bit PCM, Mono oder Stereo) als Mono-Rohdaten. */
fun parseWav(b: ByteArray): VoiceAudio? {
    fun u16(o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    fun u32(o: Int) = u16(o) or (u16(o + 2) shl 16)
    if (b.size < 44 || String(b, 0, 4) != "RIFF" || String(b, 8, 4) != "WAVE") return null
    var pos = 12
    var channels = 1
    var rate = 22050
    var bits = 16
    while (pos + 8 <= b.size) {
        val id = String(b, pos, 4)
        var size = u32(pos + 4)
        val body = pos + 8
        if (id == "fmt ") {
            channels = u16(body + 2)
            rate = u32(body + 4)
            bits = u16(body + 14)
        } else if (id == "data") {
            if (bits != 16 || channels < 1) return null
            if (size < 0 || body + size > b.size) size = b.size - body
            val frames = size / (2 * channels)
            val out = FloatArray(frames)
            for (i in 0 until frames) {
                var sum = 0f
                for (c in 0 until channels) sum += (u16(body + (i * channels + c) * 2).toShort()) / 32768f
                out[i] = sum / channels
            }
            return VoiceAudio(out, rate)
        }
        pos = body + size + (size and 1)
    }
    return null
}

/** Erzeugt Sprachclips mit der Android-Systemstimme (über synthesizeToFile) für den Export. Blockiert. */
object SystemTtsRender {
    fun renderAll(context: Context, s: Settings, events: List<SpeechEvent>): List<VoiceAudio?> {
        val none = events.map { null }
        if (events.isEmpty()) return none
        var ready = false
        val initLatch = CountDownLatch(1)
        val tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            initLatch.countDown()
        }
        try {
            if (!initLatch.await(10, TimeUnit.SECONDS) || !ready) return none
            val lang = tts.setLanguage(Locale.GERMAN)
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) return none
            tts.setPitch(1f + s.effect)
            tts.setSpeechRate(s.rate)
            return events.mapIndexed { i, ev ->
                val file = File(context.cacheDir, "systts-$i.wav")
                val done = CountDownLatch(1)
                var ok = false
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { ok = true; done.countDown() }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { done.countDown() }
                })
                val started = tts.synthesizeToFile(ev.text, null, file, "wichtel-$i")
                val clip = if (started == TextToSpeech.SUCCESS && done.await(20, TimeUnit.SECONDS) && ok) {
                    try { parseWav(file.readBytes()) } catch (_: Exception) { null }
                } else null
                file.delete()
                clip
            }
        } finally {
            tts.shutdown()
        }
    }
}
