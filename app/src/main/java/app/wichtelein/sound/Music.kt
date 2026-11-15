package app.wichtelein.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Kleine Weihnachtsmelodien als Hintergrundmusik (selbst erzeugt, Klang einer Spieluhr). */
enum class MusicTrack(val label: String) {
    SNOWFLAKE("Schneeflocke (Spieluhr)"),
    TANNE("O Tannenbaum"),
    JINGLE("Jingle Bells"),
    NACHT("Stille Nacht"),
}

/** Wahl der Musik in den Einstellungen: 0 = aus, 1..4 = eingebaute Melodie, 5 = eigene Datei (z. B. MP3). */
object MusicChoice {
    const val OFF = 0
    const val CUSTOM = 5

    fun track(choice: Int): MusicTrack? = MusicTrack.values().getOrNull(choice - 1)
}

object MusicSynth {
    const val RATE = 44100
    private val cache = HashMap<MusicTrack, FloatArray>()

    fun render(t: MusicTrack): FloatArray = synchronized(cache) { cache.getOrPut(t) { build(t) } }

    private val names = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)

    /** "G4" → Hz, "F#4" → Hz */
    fun hz(note: String): Float {
        var i = 1
        var semi = names.getValue(note[0])
        if (note[i] == '#') {
            semi += 1
            i++
        }
        val octave = note.substring(i).toInt()
        val midi = 12 * (octave + 1) + semi
        return (440.0 * 2.0.pow((midi - 69) / 12.0)).toFloat()
    }

    /** Melodie als Text: "D4:1 G4:1.5 …" (Ton:Länge in Schlägen). */
    class Tune(val notes: List<Pair<String, Float>>, val beatSec: Float, val drone: List<String>, val arpeggio: List<String> = emptyList()) {
        val beats: Float get() = notes.sumOf { it.second.toDouble() }.toFloat()
    }

    private fun parse(s: String) = s.trim().split(Regex("\\s+")).map { val (n, l) = it.split(':'); n to l.toFloat() }

    private val tunes = mapOf(
        MusicTrack.SNOWFLAKE to Tune(
            parse("E5:1 D5:1 C5:1 D5:1.5 E5:.5 G5:1 E5:2 D5:1 C5:3 A4:1 C5:1 D5:1 E5:1.5 D5:.5 C5:1 D5:2 A4:1 C5:3 " +
                "G5:1.5 E5:.5 D5:1 E5:1.5 C5:.5 A4:1 D5:1 E5:1 G5:1 E5:3 D5:1 C5:1 A4:1 C5:2 D5:1 E5:1 D5:1 C5:1 C5:3"),
            0.42f, listOf("C3", "G3"), listOf("C4", "E4", "G4", "E4"),
        ),
        MusicTrack.TANNE to Tune(
            parse("D4:1 G4:1.5 G4:.5 G4:2 A4:1 B4:1.5 B4:.5 B4:2 A4:1 B4:1 C5:1 D5:1.5 B4:.5 A4:1 G4:1 F#4:1 G4:3"),
            0.5f, listOf("G2", "D3"),
        ),
        MusicTrack.JINGLE to Tune(
            parse("E4:1 E4:1 E4:2 E4:1 E4:1 E4:2 E4:1 G4:1 C4:1.5 D4:.5 E4:4 F4:1 F4:1 F4:1.5 F4:.5 F4:1 E4:1 E4:1 E4:.5 E4:.5 E4:1 D4:1 D4:1 E4:1 D4:2 G4:2"),
            0.3f, listOf("C3", "G3"),
        ),
        MusicTrack.NACHT to Tune(
            parse("G4:1.5 A4:.5 G4:1 E4:3 G4:1.5 A4:.5 G4:1 E4:3 D5:2 D5:1 B4:3 C5:2 C5:1 G4:3 A4:2 A4:1 C5:1.5 B4:.5 A4:1 G4:1.5 A4:.5 G4:1 E4:3 " +
                "A4:2 A4:1 C5:1.5 B4:.5 A4:1 G4:1.5 A4:.5 G4:1 E4:3 D5:2 D5:1 F5:1.5 D5:.5 B4:1 C5:3 E5:3 C5:1 G4:1 E4:1 G4:1.5 F4:.5 D4:1 C4:3"),
            0.42f, listOf("G2", "D3"),
        ),
    )

    private fun bell(out: FloatArray, start: Float, f: Float, lenSec: Float, amp: Float) {
        val s0 = (start * RATE).toInt()
        val len = min((lenSec * RATE).toInt(), out.size - s0)
        if (s0 < 0 || len <= 0) return
        val parts = floatArrayOf(1f, 2f, 3.01f, 4.2f)
        val w = floatArrayOf(1f, 0.35f, 0.12f, 0.05f)
        for (i in 0 until len) {
            val t = i.toFloat() / RATE
            var v = 0f
            for (p in parts.indices) v += w[p] * sin(2.0 * PI * f * parts[p] * t).toFloat() * exp(-(1.6f + 1.4f * p) * t / (lenSec * 0.55f)).toFloat()
            val attack = min(1f, i / 80f)
            val release = min(1f, (len - i).toFloat() / (RATE * 0.04f))
            out[s0 + i] += amp * attack * release * v
        }
    }

    private fun build(t: MusicTrack): FloatArray {
        val tune = tunes.getValue(t)
        val beat = tune.beatSec
        val oneRun = tune.beats * beat
        val gap = 1.2f
        val runs = 2
        val total = (oneRun + gap) * runs + 1.5f
        val out = FloatArray((total * RATE).toInt())
        for (r in 0 until runs) {
            val t0 = r * (oneRun + gap)
            var time = t0
            for ((name, len) in tune.notes) {
                bell(out, time, hz(name), (len * beat * 1.9f).coerceAtLeast(0.6f), 0.28f)
                time += len * beat
            }
            // leise Begleitung: Bordun (zwei tiefe Töne im Wechsel) oder Arpeggio
            var d = 0
            var tt = t0
            val barSec = if (t == MusicTrack.JINGLE) 4 * beat else 3 * beat
            while (tt < t0 + oneRun) {
                bell(out, tt, hz(tune.drone[d % tune.drone.size]), barSec * 1.6f, 0.13f)
                d++
                tt += barSec
            }
            if (tune.arpeggio.isNotEmpty()) {
                var k = 0
                var ta = t0
                while (ta < t0 + oneRun) {
                    bell(out, ta, hz(tune.arpeggio[k % tune.arpeggio.size]), beat * 1.5f, 0.08f)
                    k++
                    ta += beat
                }
            }
        }
        var peak = 0f
        for (v in out) peak = maxOf(peak, kotlin.math.abs(v))
        if (peak > 0.9f) for (i in out.indices) out[i] *= 0.9f / peak
        return out
    }

    fun durationSec(t: MusicTrack): Float = render(t).size.toFloat() / RATE
}

/** Mischt Musik in den Ton eines Videos: wiederholt sich bei Bedarf und blendet am Ende aus. */
object MusicMixer {
    fun mixInto(acc: FloatArray, music: FloatArray, volume: Float, fadeOutSec: Float = 1.5f) {
        if (music.isEmpty() || acc.isEmpty()) return
        val fade = (fadeOutSec * MusicSynth.RATE).toInt().coerceAtLeast(1)
        for (i in acc.indices) {
            val remaining = acc.size - i
            val f = if (remaining < fade) remaining.toFloat() / fade else 1f
            acc[i] += music[i % music.size] * volume * f
        }
    }
}

/** Liest eine Audiodatei (MP3, AAC, …) als Mono-Rohdaten bei [MusicSynth.RATE] Hz; höchstens [maxSeconds] Sekunden. */
object AudioDecoder {
    fun decode(path: String, maxSeconds: Int): FloatArray? {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(path)
            var track = -1
            for (i in 0 until ex.trackCount) {
                if ((ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                    track = i
                    break
                }
            }
            if (track < 0) return null
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(fmt, null, null, 0)
            codec.start()
            var rate = if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) else MusicSynth.RATE
            var channels = if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
            val mono = ArrayList<Float>(rate * 10)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val limit = maxSeconds.toLong() * rate
            var guard = 0
            while (!outputDone && mono.size < limit && guard++ < 200_000) {
                if (!inputDone) {
                    val ii = codec.dequeueInputBuffer(5000)
                    if (ii >= 0) {
                        val buf = codec.getInputBuffer(ii)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(ii, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 5000)
                when {
                    oi >= 0 -> {
                        val out = codec.getOutputBuffer(oi)!!
                        out.order(ByteOrder.nativeOrder())
                        val sb = out.asShortBuffer()
                        val frames = sb.remaining() / channels.coerceAtLeast(1)
                        for (f in 0 until frames) {
                            var sum = 0f
                            for (c in 0 until channels) sum += sb.get(f * channels + c) / 32768f
                            mono.add(sum / channels)
                        }
                        codec.releaseOutputBuffer(oi, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                    oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val nf = codec.outputFormat
                        if (nf.containsKey(MediaFormat.KEY_SAMPLE_RATE)) rate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        if (nf.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            }
            if (mono.isEmpty()) return null
            return resample(mono.toFloatArray(), rate, MusicSynth.RATE)
        } catch (_: Throwable) {
            return null
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (_: Throwable) {
            }
            ex.release()
        }
    }

    fun resample(src: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to || src.isEmpty()) return src
        val n = (src.size.toLong() * to / from).toInt()
        val out = FloatArray(n)
        val ratio = from.toDouble() / to
        for (i in 0 until n) {
            val p = i * ratio
            val i0 = p.toInt().coerceAtMost(src.size - 1)
            val i1 = (i0 + 1).coerceAtMost(src.size - 1)
            out[i] = src[i0] + (src[i1] - src[i0]) * (p - i0).toFloat()
        }
        return out
    }
}

/** Lädt die gewählte Musik (eingebaute Melodie oder eigene Datei) als Rohdaten; null = keine Musik oder nicht lesbar. */
object MusicLoader {
    fun customFile(context: android.content.Context) = java.io.File(context.filesDir, "music_custom")

    fun load(context: android.content.Context, settings: app.wichtelein.settings.Settings, maxMillis: Long): FloatArray? {
        val choice = settings.music
        MusicChoice.track(choice)?.let { return MusicSynth.render(it) }
        if (choice == MusicChoice.CUSTOM) {
            val f = customFile(context)
            if (!f.exists()) return null
            return AudioDecoder.decode(f.absolutePath, (maxMillis / 1000L + 2L).toInt().coerceIn(5, 600))
        }
        return null
    }
}

/** Spielt Musik als Schleife (Vorschau im Editor). */
class MusicPlayer {
    private var track: AudioTrack? = null

    fun play(pcm: FloatArray, volume: Float) {
        stop()
        if (pcm.isEmpty()) return
        try {
            val shorts = ShortArray(pcm.size) { (pcm[it].coerceIn(-1f, 1f) * 32767f).toInt().toShort() }
            val t = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(MusicSynth.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(shorts.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            t.write(shorts, 0, shorts.size)
            t.setLoopPoints(0, shorts.size, -1)
            t.setVolume(volume.coerceIn(0f, 1f))
            t.play()
            track = t
        } catch (_: Throwable) {
            track = null
        }
    }

    fun setVolume(volume: Float) {
        try {
            track?.setVolume(volume.coerceIn(0f, 1f))
        } catch (_: Throwable) {
        }
    }

    fun stop() {
        try {
            track?.stop()
            track?.release()
        } catch (_: Throwable) {
        }
        track = null
    }
}
