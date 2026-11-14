package app.wichtelein

import app.wichtelein.camera.*
import app.wichtelein.anim.*
import app.wichtelein.scene.*
import app.wichtelein.speech.*
import app.wichtelein.videoexport.*
import app.wichtelein.settings.*
import app.wichtelein.ui.*

import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WavTest {
    @Test fun wavWrittenByMixerCanBeParsedBack() {
        val ev = listOf(SpeechEvent(100, "x", null))
        val clip = VoiceAudio(FloatArray(4410) { sin(it / 20.0).toFloat() * 0.5f }, 44100)
        val pcm = SpeechMixer.mix(ev, listOf(clip), 500)
        assertEquals(22050, pcm.size)
        val parsed = parseWav(SpeechMixer.toWav(pcm))
        assertNotNull(parsed)
        assertEquals(44100, parsed!!.sampleRate)
        assertEquals(22050, parsed.samples.size)
        // Stille vor dem Einsatz bei 100 ms, danach Signal
        assertEquals(0f, parsed.samples[100], 1e-4f)
        assertTrue(parsed.samples.drop(4410).take(4410).any { abs(it) > 0.1f })
    }

    @Test fun invalidDataIsRejected() {
        assertNull(parseWav(ByteArray(10)))
        assertNull(parseWav(ByteArray(100) { 1 }))
    }

    @Test fun mixerPlacesClipAtEventTimeAndClipsOverflow() {
        val clip = VoiceAudio(FloatArray(1000) { 0.5f }, 44100)
        val pcm = SpeechMixer.mix(listOf(SpeechEvent(0, "a", null), SpeechEvent(0, "b", null)), listOf(clip, clip), 100)
        assertEquals(4410, pcm.size)
        assertEquals(32767, pcm[10].toInt()) // 0,5 + 0,5 wird begrenzt
        assertEquals(0, pcm[2000].toInt())
    }
}
