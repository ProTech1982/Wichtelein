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
enum class Fx(val label: String, val voiceLike: Boolean = false) {
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
    /** Schnarchen: langsam ein, rumpelnd aus */
    SNORE("Schnarchen"),
    /** „Psst“: kurzes Zischen */
    PSST("Psst"),
    /** Brummen statt Sprechen: drei verschiedene Silben */
    MUMBLE1("Brummen 1", true),
    MUMBLE2("Brummen 2", true),
    MUMBLE3("Brummen 3", true),
    /** Summen kleiner Weihnachtsmelodien */
    HUM_TANNE("Summen: O Tannenbaum", true),
    HUM_JINGLE("Summen: Jingle Bells", true),
    HUM_NACHT("Summen: Stille Nacht", true),
}

class SoundEvent(val timeMs: Long, val fx: Fx, val gain: Float = 1f)

/** Darf dieses Geräusch gespielt werden? Weihnachtsgeräusche mit „Geräusche“, Brummen und Summen mit „Summen und Brummen“. */
fun app.wichtelein.settings.Settings.allowsFx(fx: Fx): Boolean = soundFx || (hum && fx.voiceLike)

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

    /** Eine gebrummte Silbe: tiefer, leicht fallender Ton mit Obertönen und Zittern („brumm“). */
    private fun mumble(out: FloatArray, f0: Float, durSec: Float, rng: Random) {
        val len = n(durSec)
        var ph = 0.0
        for (i in 0 until min(len, out.size)) {
            val u = i.toFloat() / len
            val f = f0 * (1.08f - 0.22f * u) * (1f + 0.012f * sin(2.0 * PI * 24.0 * i / SAMPLE_RATE).toFloat())
            ph += 2.0 * PI * f / SAMPLE_RATE
            val v = sin(ph).toFloat() + 0.55f * sin(2 * ph).toFloat() + 0.3f * sin(3 * ph).toFloat()
            val env = min(1f, u * 14f) * (1f - u).coerceAtLeast(0f).pow(0.8f)
            out[i] += 0.35f * env * v * (0.75f + 0.25f * sin(2.0 * PI * 30.0 * i / SAMPLE_RATE).toFloat())
        }
    }

    /** Gesummte Melodie: weicher Ton mit leichtem Vibrato, Länge je Note in Schlägen. */
    private fun humTune(freqs: FloatArray, beats: FloatArray, beatSec: Float): FloatArray {
        val total = beats.sum() * beatSec + 0.25f
        val out = FloatArray(n(total))
        var t = 0f
        for (k in freqs.indices) {
            val dur = beats[k] * beatSec
            val s0 = n(t)
            val len = min(n(dur * 0.97f + 0.06f), out.size - s0)
            var ph = 0.0
            for (i in 0 until len) {
                val tt = i.toFloat() / SAMPLE_RATE
                val f = freqs[k] * (1f + 0.009f * sin(2.0 * PI * 5.2 * tt).toFloat())
                ph += 2.0 * PI * f / SAMPLE_RATE
                val v = sin(ph).toFloat() + 0.32f * sin(2 * ph).toFloat() + 0.1f * sin(3 * ph).toFloat()
                val attack = min(1f, tt / 0.05f)
                val release = min(1f, ((len - i).toFloat() / SAMPLE_RATE) / 0.08f)
                out[s0 + i] += 0.3f * attack * release * v
            }
            t += dur
        }
        return out
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
            Fx.SNORE -> FloatArray(n(1.7f)).also { o ->
                // Einatmen: Rauschen schwillt an (rau durch schnelles Zittern); Ausatmen: tiefes Rumpeln
                noise(o, 0.0f, 0.65f, 0.5f, 0.82f, rng) { u -> (sin(u * PI).toFloat()).pow(1.3f) * (0.6f + 0.4f * sin(u * 150f)) }
                var ph = 0.0
                val s0 = n(0.72f)
                val len = n(0.85f)
                for (i in 0 until len) {
                    val u = i.toFloat() / len
                    ph += 2.0 * PI * (62f - 18f * u) / SAMPLE_RATE
                    val saw = ((ph / (2 * PI)) % 1.0).toFloat() * 2f - 1f
                    val env = (sin(u * PI).toFloat()).pow(0.8f)
                    o[s0 + i] += 0.5f * env * saw * (0.65f + 0.35f * sin(u * 120f)) + 0.15f * env * (rng.nextFloat() * 2f - 1f)
                }
            }
            Fx.MUMBLE1 -> FloatArray(n(0.4f)).also { o -> mumble(o, 118f, 0.26f, rng) }
            Fx.MUMBLE2 -> FloatArray(n(0.45f)).also { o -> mumble(o, 148f, 0.30f, rng) }
            Fx.MUMBLE3 -> FloatArray(n(0.5f)).also { o -> mumble(o, 98f, 0.34f, rng) }
            Fx.HUM_TANNE -> humTune(floatArrayOf(293.7f, 392f, 392f, 392f, 440f, 493.9f, 493.9f, 493.9f, 440f, 392f), floatArrayOf(0.5f, 0.75f, 0.25f, 1f, 0.5f, 0.75f, 0.25f, 1f, 0.5f, 1.5f), 0.3f)
            Fx.HUM_JINGLE -> humTune(floatArrayOf(329.6f, 329.6f, 329.6f, 329.6f, 329.6f, 329.6f, 329.6f, 392f, 261.6f, 293.7f, 329.6f), floatArrayOf(0.5f, 0.5f, 1f, 0.5f, 0.5f, 1f, 0.5f, 0.5f, 0.75f, 0.25f, 2f), 0.26f)
            Fx.HUM_NACHT -> humTune(floatArrayOf(392f, 440f, 392f, 329.6f, 392f, 440f, 392f, 329.6f, 587.3f, 587.3f, 493.9f), floatArrayOf(0.75f, 0.25f, 0.5f, 1.5f, 0.75f, 0.25f, 0.5f, 1.5f, 1f, 0.5f, 1.5f), 0.4f)
            Fx.PSST -> FloatArray(n(0.7f)).also { o ->
                // leises, weiches Zischen (geflüstert): sanft ein- und ausblenden
                noise(o, 0.02f, 0.58f, 0.32f, 0.3f, rng) { u -> (sin(u * PI).toFloat()).pow(1.1f) }
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
