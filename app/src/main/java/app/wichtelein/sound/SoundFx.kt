package app.wichtelein.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Geräusche, die der Wichtel macht. Alle werden selbst erzeugt (keine fremden Klangdateien, keine Lizenzfragen). */
enum class Fx(val label: String) {
    /** Glöckchen an der Mütze: kurzes Klingeln */
    JINGLE("Glöckchen"),
    /** einzelner heller Glockenton */
    BELL("Glocke"),
    /** leises Mützenglöckchen beim Laufen */
    TINKLE("Glöckchen leise"),
    /** kleine Melodie (vier Töne, Weihnachtsstimmung) */
    TUNE("Melodie"),
    /** Tür quietscht auf */
    DOOR_OPEN("Tür auf"),
    /** Tür fällt zu */
    DOOR_CLOSE("Tür zu"),
    /** Klopfen an Holz */
    KNOCK("Klopfen"),
    /** ein Besenstrich */
    SWEEP("Fegen"),
    /** Schritt */
    STEP("Schritt"),
    /** Puff (Erscheinen/Verschwinden) */
    POOF("Puff"),
    /** Funkeln (Zauber) */
    SPARKLE("Funkeln"),
    /** Wurf / Purzelbaum */
    WHOOSH("Wusch"),
    /** Plumps (Hinfallen, Hinsetzen, Geschenk abstellen) */
    THUD("Plumps"),
    /** Knuspern */
    CRUNCH("Knuspern"),
    /** Raschelndes Geschenkpapier mit Glöckchen */
    GIFT("Geschenk"),
}

class SoundEvent(val timeMs: Long, val fx: Fx, val gain: Float = 1f)

/** Erzeugt die Geräusche als Rohdaten (Mono, [SAMPLE_RATE] Hz, Werte -1..1). Gleiche Eingabe ergibt gleiche Klänge. */
object SoundSynth {
    const val SAMPLE_RATE = 44100
    private val cache = HashMap<Fx, FloatArray>()

    fun get(fx: Fx): FloatArray = synchronized(cache) { cache.getOrPut(fx) { normalize(render(fx)) } }

    /** Spitzenpegel auf höchstens 0,8 begrenzen, damit Überlagerungen nicht übersteuern. */
    private fun normalize(a: FloatArray): FloatArray {
        var peak = 0f
        for (v in a) peak = maxOf(peak, kotlin.math.abs(v))
        if (peak > 0.8f) {
            val k = 0.8f / peak
            for (i in a.indices) a[i] *= k
        }
        return a
    }

    private fun n(sec: Float) = (sec * SAMPLE_RATE).toInt()

    /** Glockenton: obere Teiltöne klingen schneller ab, leichte Schwebung macht ihn silbrig. */
    private fun bell(out: FloatArray, startSec: Float, freq: Float, amp: Float, decay: Float = 6f) {
        val s0 = n(startSec)
        val partials = floatArrayOf(1f, 2.76f, 5.4f, 8.9f)
        val weights = floatArrayOf(1f, 0.45f, 0.22f, 0.1f)
        val dec = floatArrayOf(1f, 1.7f, 2.6f, 4f)
        val len = min(out.size - s0, n(1.4f))
        for (i in 0 until len) {
            val t = i.toFloat() / SAMPLE_RATE
            var v = 0f
            for (p in partials.indices) {
                v += weights[p] * sin(2.0 * PI * freq * partials[p] * t).toFloat() * exp(-decay * dec[p] * t)
            }
            val attack = min(1f, i / 40f)
            out[s0 + i] += amp * attack * v
        }
    }

    /** Rauschen mit einfachem Tiefpass (Wert nahe 1 = dumpf) und Hüllkurve. */
    private fun noise(out: FloatArray, startSec: Float, durSec: Float, amp: Float, lowpass: Float, rng: Random, env: (Float) -> Float) {
        val s0 = n(startSec)
        val len = min(out.size - s0, n(durSec))
        var y = 0f
        for (i in 0 until len) {
            val x = rng.nextFloat() * 2f - 1f
            y = lowpass * y + (1f - lowpass) * x
            out[s0 + i] += amp * env(i.toFloat() / len) * y * (1.4f - lowpass * 0.6f) * 2.2f
        }
    }

    /** Gedämpfter Ton (Holz, Plumps). */
    private fun thump(out: FloatArray, startSec: Float, freq: Float, amp: Float, decay: Float) {
        val s0 = n(startSec)
        val len = min(out.size - s0, n(0.5f))
        for (i in 0 until len) {
            val t = i.toFloat() / SAMPLE_RATE
            out[s0 + i] += amp * sin(2.0 * PI * freq * t).toFloat() * exp(-decay * t)
        }
    }

    private fun render(fx: Fx): FloatArray {
        val rng = Random(fx.ordinal * 7919 + 13)
        return when (fx) {
            Fx.JINGLE -> FloatArray(n(1.3f)).also { o ->
                val f = floatArrayOf(2093f, 2637f, 3136f, 2349f, 3520f, 2793f, 3322f, 2489f)
                for (i in 0 until 14) bell(o, i * 0.065f + rng.nextFloat() * 0.02f, f[i % f.size], 0.2f + 0.08f * rng.nextFloat(), 7f)
            }
            Fx.TINKLE -> FloatArray(n(0.5f)).also { o ->
                bell(o, 0f, 3136f, 0.16f, 12f)
                bell(o, 0.07f, 3951f, 0.12f, 14f)
            }
            Fx.BELL -> FloatArray(n(1.5f)).also { o -> bell(o, 0f, 1175f, 0.5f, 3.2f) }
            Fx.TUNE -> FloatArray(n(1.8f)).also { o ->
                // aufsteigende Tonfolge (G-C-E-G) mit Glockenklang
                val f = floatArrayOf(784f, 1047f, 1319f, 1568f)
                for (i in f.indices) bell(o, i * 0.2f, f[i], 0.4f, 4f)
                bell(o, 0.85f, 1319f, 0.3f, 3f)
                bell(o, 0.85f, 1568f, 0.3f, 3f)
            }
            Fx.DOOR_OPEN -> FloatArray(n(1.0f)).also { o ->
                // Quietschen: Ton mit schwankender Höhe plus Raunen
                var ph = 0.0
                val len = n(0.95f)
                for (i in 0 until len) {
                    val u = i.toFloat() / len
                    val f = 380f + 520f * u + 60f * sin(u * 38f)
                    ph += 2.0 * PI * f / SAMPLE_RATE
                    val saw = ((ph / (2 * PI)) % 1.0).toFloat() * 2f - 1f
                    val env = (sin(u * PI).toFloat()).pow(0.7f)
                    o[i] += 0.16f * env * (0.7f * saw + 0.3f * sin(ph).toFloat())
                }
                noise(o, 0f, 0.95f, 0.05f, 0.9f, rng) { sin(it * PI).toFloat() }
                thump(o, 0f, 95f, 0.2f, 14f)
            }
            Fx.DOOR_CLOSE -> FloatArray(n(0.7f)).also { o ->
                thump(o, 0f, 70f, 0.8f, 11f)
                thump(o, 0f, 140f, 0.35f, 18f)
                noise(o, 0f, 0.08f, 0.5f, 0.55f, rng) { exp(-6f * it) }
                bell(o, 0.02f, 2500f, 0.05f, 14f)
            }
            Fx.KNOCK -> FloatArray(n(0.3f)).also { o ->
                thump(o, 0f, 210f, 0.8f, 38f)
                thump(o, 0f, 430f, 0.4f, 55f)
                noise(o, 0f, 0.03f, 0.35f, 0.5f, rng) { exp(-8f * it) }
            }
            Fx.SWEEP -> FloatArray(n(0.5f)).also { o ->
                noise(o, 0f, 0.45f, 0.5f, 0.72f, rng) { u -> (sin(u * PI).toFloat()).pow(1.4f) }
            }
            Fx.STEP -> FloatArray(n(0.12f)).also { o ->
                thump(o, 0f, 120f, 0.5f, 40f)
                noise(o, 0f, 0.05f, 0.25f, 0.7f, rng) { exp(-7f * it) }
            }
            Fx.POOF -> FloatArray(n(0.7f)).also { o ->
                noise(o, 0f, 0.5f, 0.6f, 0.82f, rng) { u -> exp(-4.5f * u) * min(1f, u * 25f) }
                thump(o, 0f, 85f, 0.45f, 12f)
                bell(o, 0.05f, 2637f, 0.12f, 9f)
            }
            Fx.SPARKLE -> FloatArray(n(1.5f)).also { o ->
                val f = floatArrayOf(1568f, 1976f, 2349f, 2637f, 3136f, 3951f, 4699f)
                for (i in f.indices) bell(o, i * 0.075f, f[i], 0.22f, 6f)
            }
            Fx.WHOOSH -> FloatArray(n(0.6f)).also { o ->
                noise(o, 0f, 0.55f, 0.55f, 0.88f, rng) { u -> (sin(u * PI).toFloat()).pow(1.2f) }
            }
            Fx.THUD -> FloatArray(n(0.4f)).also { o ->
                thump(o, 0f, 80f, 0.9f, 14f)
                noise(o, 0f, 0.06f, 0.3f, 0.8f, rng) { exp(-6f * it) }
            }
            Fx.CRUNCH -> FloatArray(n(0.35f)).also { o ->
                for (i in 0 until 5) noise(o, i * 0.06f + rng.nextFloat() * 0.02f, 0.03f, 0.6f, 0.25f, rng) { exp(-5f * it) }
            }
            Fx.GIFT -> FloatArray(n(1.0f)).also { o ->
                noise(o, 0f, 0.35f, 0.28f, 0.35f, rng) { u -> sin(u * PI * 3f).toFloat().let { it * it } * (1f - u) }
                bell(o, 0.3f, 2093f, 0.3f, 5f)
                bell(o, 0.4f, 2637f, 0.25f, 5f)
            }
        }
    }
}

/** Spielt Geräusche sofort ab (Vorschau und AR). */
class SoundPlayer {
    @Volatile var enabled = true

    fun play(fx: Fx, gain: Float = 1f) {
        if (!enabled) return
        val data = SoundSynth.get(fx)
        Thread {
            var track: AudioTrack? = null
            try {
                val pcm = ShortArray(data.size) { (data[it].coerceIn(-1f, 1f) * gain.coerceIn(0f, 1f) * 32767f).toInt().toShort() }
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SoundSynth.SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep(pcm.size * 1000L / SoundSynth.SAMPLE_RATE + 150L)
            } catch (_: Throwable) {
                // kein Ton ist besser als ein Absturz
            } finally {
                try {
                    track?.release()
                } catch (_: Throwable) {
                }
            }
        }.start()
    }
}

/** Mischt Geräusche in einen Mono-Puffer (Gleitkomma, Abtastrate [SoundSynth.SAMPLE_RATE]). */
object SoundMixer {
    fun mixInto(acc: FloatArray, events: List<SoundEvent>, volume: Float) {
        for (ev in events) {
            val data = SoundSynth.get(ev.fx)
            val start = (ev.timeMs * SoundSynth.SAMPLE_RATE / 1000L).toInt()
            val g = ev.gain * volume
            for (j in data.indices) {
                val idx = start + j
                if (idx >= acc.size) break
                if (idx >= 0) acc[idx] += data[j] * g
            }
        }
    }
}
