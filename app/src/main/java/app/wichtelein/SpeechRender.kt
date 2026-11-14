package app.wichtelein

import android.content.Context

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
            ElfVoice.synthesize(ev.text, mood, s.rate, 1f + 0.45f * s.effect)
        } catch (_: Throwable) {
            null
        } ?: return null
        store(k, audio)
        return audio
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

    fun mix(events: List<SpeechEvent>, clips: List<VoiceAudio?>, durationMs: Long): ShortArray {
        val total = (durationMs * SAMPLE_RATE / 1000L).toInt()
        val acc = FloatArray(total)
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
