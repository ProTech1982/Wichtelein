package app.wichtelein

import app.wichtelein.camera.*
import app.wichtelein.anim.*
import app.wichtelein.scene.*
import app.wichtelein.speech.*
import app.wichtelein.videoexport.*
import app.wichtelein.settings.*
import app.wichtelein.ui.*

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceEffectTest {
    private val sr = 22050

    private fun sine(freq: Double, seconds: Double): FloatArray =
        FloatArray((sr * seconds).toInt()) { sin(2.0 * PI * freq * it / sr).toFloat() * 0.5f }

    /** Frequenz über Nulldurchgänge (aufsteigend) im mittleren Teil des Signals. */
    private fun estimateFreq(x: FloatArray): Double {
        val from = x.size / 4
        val to = x.size * 3 / 4
        var crossings = 0
        for (i in from + 1 until to) if (x[i - 1] <= 0f && x[i] > 0f) crossings++
        return crossings / ((to - from).toDouble() / sr)
    }

    @Test fun pitchShiftRaisesFrequencyAndKeepsDuration() {
        val input = sine(200.0, 1.5)
        val out = pitchShift(input, 1.5f)
        assertEquals(input.size, out.size)
        val f = estimateFreq(out)
        assertTrue("Frequenz $f statt ~300 Hz", f in 285.0..315.0)
    }

    @Test fun pitchShiftLowersFrequencyForFactorBelowOne() {
        val out = pitchShift(sine(300.0, 1.5), 0.8f)
        val f = estimateFreq(out)
        assertTrue("Frequenz $f statt ~240 Hz", f in 228.0..252.0)
    }

    @Test fun pitchShiftKeepsLoudnessRoughly() {
        fun rms(a: FloatArray) = sqrt(a.map { it * it }.average())
        val input = sine(250.0, 1.0)
        val out = pitchShift(input, 1.4f)
        assertTrue(rms(out) > rms(input) * 0.7 && rms(out) < rms(input) * 1.3)
    }

    @Test fun factorOneReturnsCopy() {
        val input = sine(200.0, 0.3)
        val out = pitchShift(input, 1.0f)
        assertEquals(input.size, out.size)
        assertTrue(out !== input)
        assertEquals(input[100], out[100], 1e-6f)
    }

    @Test fun veryShortInputDoesNotCrash() {
        val out = pitchShift(FloatArray(300) { 0.1f }, 1.4f)
        assertEquals(300, out.size)
        assertEquals(0, pitchShift(FloatArray(0), 1.4f).size)
    }

    @Test fun stretchChangesLengthByRatio() {
        val input = sine(200.0, 1.0)
        val out = wsolaStretch(input, 1.5f)
        assertTrue(abs(out.size - input.size * 1.5) < 2)
    }

    @Test fun normalizePeakScalesToTarget() {
        val out = normalizePeak(floatArrayOf(0.1f, -0.2f, 0.05f), 0.8f)
        assertEquals(0.8f, out.maxOf { abs(it) }, 1e-5f)
        val silent = floatArrayOf(0f, 0f)
        assertEquals(0f, normalizePeak(silent)[0], 0f)
    }
}
