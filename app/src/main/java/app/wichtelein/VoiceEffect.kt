package app.wichtelein

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * Wichtel-Effekt: hebt die Tonhöhe um [factor] an (Formanten wandern mit, die Stimme klingt kleiner),
 * ohne dass die Sprache schneller wird. Verfahren: erst mit WSOLA um [factor] dehnen, dann um [factor] schneller abspielen.
 */
fun pitchShift(x: FloatArray, factor: Float): FloatArray {
    if (x.isEmpty() || abs(factor - 1f) < 0.01f) return x.copyOf()
    val stretched = wsolaStretch(x, factor)
    val out = FloatArray(x.size)
    for (i in out.indices) {
        val pos = i * factor
        val i0 = pos.toInt()
        if (i0 >= stretched.size - 1) break
        val frac = pos - i0
        out[i] = stretched[i0] * (1f - frac) + stretched[i0 + 1] * frac
    }
    return out
}

/** Zeitdehnung ohne Tonhöhenänderung (Waveform Similarity Overlap-Add). Ausgabelänge ≈ Eingabelänge · [ratio]. */
fun wsolaStretch(x: FloatArray, ratio: Float, frame: Int = 1024, tolerance: Int = 256): FloatArray {
    val hs = frame / 2
    val outLen = max(frame, (x.size * ratio).toInt())
    if (x.size < frame * 2) {
        // zu kurz für Überlappung: einfach linear dehnen
        return FloatArray(outLen) { i -> x[min(x.size - 1, (i / ratio).toInt())] }
    }
    val win = FloatArray(frame) { (0.5 - 0.5 * cos(2.0 * PI * it / frame)).toFloat() }
    val out = FloatArray(outLen + frame)
    val norm = FloatArray(outLen + frame)

    fun addFrame(start: Int, outPos: Int) {
        for (i in 0 until frame) {
            val o = outPos + i
            if (o >= out.size) break
            out[o] += x[start + i] * win[i]
            norm[o] += win[i]
        }
    }

    var prevStart = 0
    addFrame(0, 0)
    var outPos = hs
    while (outPos < outLen) {
        val nominal = (outPos / ratio).toInt()
        val natural = prevStart + hs // so würde das vorige Fenster weiterlaufen
        var best = 0
        var bestScore = Float.NEGATIVE_INFINITY
        val lo = max(-tolerance, -nominal)
        val hi = min(tolerance, x.size - frame - nominal)
        if (natural + hs <= x.size && hi >= lo) {
            var d = lo
            while (d <= hi) {
                val cand = nominal + d
                var s = 0f
                var i = 0
                while (i < hs) {
                    s += x[natural + i] * x[cand + i]
                    i += 2
                }
                if (s > bestScore) {
                    bestScore = s
                    best = d
                }
                d += 2
            }
        }
        val start = (nominal + best).coerceIn(0, x.size - frame)
        addFrame(start, outPos)
        prevStart = start
        outPos += hs
    }
    val result = FloatArray(outLen)
    for (i in result.indices) result[i] = if (norm[i] > 1e-3f) out[i] / norm[i] else 0f
    return result
}

/** Skaliert so, dass die Spitze bei [peak] liegt (nur wenn das Signal nicht still ist). */
fun normalizePeak(x: FloatArray, peak: Float = 0.9f): FloatArray {
    var m = 0f
    for (v in x) m = max(m, abs(v))
    if (m < 1e-4f) return x
    val g = peak / m
    return FloatArray(x.size) { x[it] * g }
}
