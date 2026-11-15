package app.wichtelein

import app.wichtelein.anim.*
import app.wichtelein.settings.*
import app.wichtelein.sound.*
import app.wichtelein.speech.SpeechMixer
import app.wichtelein.ui.EditOps
import app.wichtelein.ui.EditState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

/** Hintergrundmusik, Summen und Brummen, Sprechblasen pro Aktion. */
class MusicAndBubbleTest {
    private fun req(settings: Settings, script: List<ScriptStep> = emptyList(), only: String? = null, ms: Long = 60_000L, stop: Boolean = true) = PerfRequest(
        Pt(0.5f, 0.8f), emptyList(), 0.3f, null, only, 4L, ms, only?.let { setOf(it) }, settings,
        script = script, stopAfterScript = stop,
    )

    // ---------------------------------------------------------------- Musik

    @Test fun noteNamesBecomeFrequencies() {
        assertEquals(440f, MusicSynth.hz("A4"), 0.01f)
        assertEquals(261.63f, MusicSynth.hz("C4"), 0.05f)
        assertEquals(369.99f, MusicSynth.hz("F#4"), 0.05f)
    }

    @Test fun everyBuiltInTuneIsLongAudibleAndEndsQuietly() {
        for (t in MusicTrack.values()) {
            val d = MusicSynth.render(t)
            assertTrue("${t.name}: ${MusicSynth.durationSec(t)} s", MusicSynth.durationSec(t) > 15f)
            val peak = d.maxOf { abs(it) }
            assertTrue("${t.name} Pegel $peak", peak in 0.15f..0.91f)
            assertTrue("${t.name} endet laut", d.takeLast(200).all { abs(it) < 0.02f })
            assertFalse(d.any { it.isNaN() })
        }
        assertEquals(4, MusicTrack.values().size)
        assertEquals(MusicTrack.TANNE, MusicChoice.track(2))
        assertTrue(MusicChoice.track(0) == null && MusicChoice.track(MusicChoice.CUSTOM) == null)
    }

    @Test fun musicLoopsAndFadesOut() {
        val rate = MusicSynth.RATE
        val music = FloatArray(rate) { 0.5f }
        val acc = FloatArray(rate * 4)
        MusicMixer.mixInto(acc, music, 0.5f, 1.5f)
        assertEquals(0.25f, acc[100], 1e-4f)
        assertEquals("wiederholt sich", 0.25f, acc[rate * 2], 1e-4f)
        assertTrue("blendet aus", abs(acc[acc.size - 2]) < 0.001f)
        assertTrue(abs(acc[(rate * 3.5).toInt()]) > 0.05f && abs(acc[(rate * 3.5).toInt()]) < 0.25f)
    }

    @Test fun musicAloneMakesAnAudioTrack() {
        val music = MusicSynth.render(MusicTrack.JINGLE)
        val pcm = SpeechMixer.mix(emptyList(), emptyList(), 5000L, emptyList(), music, 0.4f)
        assertEquals(5 * MusicSynth.RATE, pcm.size)
        assertTrue(pcm.any { abs(it.toInt()) > 1500 })
    }

    @Test fun resamplingKeepsTheLengthInSeconds() {
        val src = FloatArray(48_000) { sin(it * 0.05f) }
        val out = AudioDecoder.resample(src, 48_000, 44_100)
        assertEquals(44_100, out.size)
        assertEquals(src, AudioDecoder.resample(src, 44_100, 44_100))
    }

    // ---------------------------------------------------------------- Summen und Brummen

    @Test fun withoutVoiceTheElfMumblesOneSyllablePerWord() {
        val s = Settings(elfName = "Pippo", speak = false, hum = true).withProfile("Max")
        val p = Recorder.record(req(s, listOf(ScriptStep(ElfAction.WAVE, Pt(0.5f, 0.8f)))))
        val mumbles = p.sounds.filter { it.fx == Fx.MUMBLE1 || it.fx == Fx.MUMBLE2 || it.fx == Fx.MUMBLE3 }
        assertTrue("Brummen: ${mumbles.size}", mumbles.size >= 4)
        assertTrue(mumbles.all { it.fx.voiceLike })
        // auch wenn „Geräusche“ aus sind, werden sie abgespielt und exportiert
        assertTrue(Settings(soundFx = false, hum = true).allowsFx(Fx.MUMBLE1))
        assertFalse(Settings(soundFx = false, hum = true).allowsFx(Fx.JINGLE))
    }

    @Test fun withVoiceThereAreNoMumblesButTunesInThePauses() {
        val s = Settings(elfName = "Pippo", speak = true, hum = true).withProfile("Max")
        val p = Recorder.record(req(s, only = "brav_fegen", ms = 80_000L, stop = false))
        assertTrue("kein Brummen neben der Stimme", p.sounds.none { it.fx == Fx.MUMBLE1 || it.fx == Fx.MUMBLE2 || it.fx == Fx.MUMBLE3 })
        val tunes = p.sounds.filter { it.fx == Fx.HUM_TANNE || it.fx == Fx.HUM_JINGLE || it.fx == Fx.HUM_NACHT }
        assertTrue("Melodien gesummt: ${tunes.size}", tunes.isNotEmpty())
        assertTrue("nicht zu dicht hintereinander", tunes.zipWithNext().all { (a, b) -> b.timeMs - a.timeMs > 6000L })
        assertTrue(tunes.all { it.gain <= 0.6f })
    }

    @Test fun humIsOffByDefaultAndChangesNothing() {
        val s = Settings(elfName = "Pippo").withProfile("Max")
        val p = Recorder.record(req(s, only = "brav_fegen", ms = 40_000L, stop = false))
        assertTrue(p.sounds.none { it.fx.voiceLike })
    }

    @Test fun humSoundsAreShortOrTunesAndNotClipping() {
        for (f in Fx.values().filter { it.voiceLike }) {
            val d = SoundSynth.get(f)
            assertTrue(f.name, d.size > SoundSynth.SAMPLE_RATE / 5)
            assertTrue("${f.name} Pegel", d.maxOf { abs(it) } in 0.1f..0.8001f)
            assertTrue("${f.name} endet laut", abs(d.last()) < 0.05f)
        }
    }

    // ---------------------------------------------------------------- Sprechblase pro Aktion

    @Test fun eachActionCanHaveItsOwnBubble() {
        val s = Settings(elfName = "Pippo", bubble = BubbleSpec(true, BubbleShape.RECT, 1f)).withProfile("Max")
        val steps = listOf(
            ScriptStep(ElfAction.WAVE, Pt(0.4f, 0.8f), BubbleSpec(false, BubbleShape.ROUNDED, 1f)), // ohne Blase
            ScriptStep(ElfAction.DANCE, Pt(0.6f, 0.8f), BubbleSpec(true, BubbleShape.CLOUD, 1.5f)), // Wolke, groß
            ScriptStep(ElfAction.BOW, Pt(0.5f, 0.8f)), // Standard
        )
        val p = Recorder.record(req(s, steps))
        val talking = p.frames.filter { it.bubble != null }
        assertTrue(talking.isNotEmpty())
        assertTrue("ohne Blase", talking.any { !it.bubbleSpec.show })
        assertTrue("Wolke", talking.any { it.bubbleSpec.show && it.bubbleSpec.shape == BubbleShape.CLOUD && it.bubbleSpec.size == 1.5f })
        assertTrue("Standard aus den Einstellungen", talking.any { it.bubbleSpec == BubbleSpec(true, BubbleShape.RECT, 1f) })
        // die Reihenfolge stimmt: erst ausgeblendet, dann Wolke, dann Standard
        val firstHidden = talking.indexOfFirst { !it.bubbleSpec.show }
        val firstCloud = talking.indexOfFirst { it.bubbleSpec.shape == BubbleShape.CLOUD }
        val lastDefault = talking.indexOfLast { it.bubbleSpec == BubbleSpec(true, BubbleShape.RECT, 1f) }
        assertTrue(firstHidden < firstCloud && firstCloud < lastDefault)
        // gesprochen wird auch ohne sichtbare Blase
        assertTrue(p.speech.size >= 3)
    }

    @Test fun bubbleSpecCanBeSetAndResetInTheEditor() {
        var st = EditState(script = listOf(ScriptStep(ElfAction.WAVE, Pt(0.5f, 0.8f))))
        st = EditOps.setBubble(st, 0, BubbleSpec(true, BubbleShape.SHOUT, 1.2f))
        assertEquals(BubbleShape.SHOUT, st.script[0].bubble!!.shape)
        st = EditOps.setBubble(st, 0, null)
        assertTrue(st.script[0].bubble == null)
        assertEquals(st, EditOps.setBubble(st, 7, BubbleSpec())) // ungültige Nummer ändert nichts
    }

    @Test fun defaultsAreTheOldLook() {
        assertEquals(BubbleSpec(true, BubbleShape.ROUNDED, 1f), Settings().bubble)
        assertEquals(5, BubbleShape.values().size)
        assertEquals(0, Settings().music)
        assertFalse(Settings().hum)
    }
}
