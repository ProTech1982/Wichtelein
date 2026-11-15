package app.wichtelein

import app.wichtelein.anim.*
import app.wichtelein.settings.*
import app.wichtelein.sound.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Geräusche (selbst erzeugt) und Aktionspunkte („Drehbuch“) des Wichtels. */
class SoundAndScriptTest {
    private val settings = Settings(elfName = "Pippo").withProfile("Max")

    private fun req(script: List<ScriptStep>, door: Pt? = null, stop: Boolean = true, ms: Long = 60_000L) = PerfRequest(
        start = Pt(0.2f, 0.8f), sizeScale = 0.3f, seed = 5L, durationMs = ms, settings = settings,
        door = door, script = script, stopAfterScript = stop,
    )

    // ---------------------------------------------------------------- Geräusche

    @Test fun everySoundIsAudibleAndNotClipping() {
        for (fx in Fx.values()) {
            val d = SoundSynth.get(fx)
            assertTrue("${fx.name} zu kurz: ${d.size}", d.size > SoundSynth.SAMPLE_RATE / 10)
            val peak = d.maxOf { abs(it) }
            assertTrue("${fx.name} fast stumm: $peak", peak > 0.1f)
            assertTrue("${fx.name} übersteuert: $peak", peak <= 0.8001f)
            assertFalse("${fx.name} enthält ungültige Werte", d.any { it.isNaN() || it.isInfinite() })
            // endet leise (kein Knacken am Ende)
            assertTrue("${fx.name} endet laut: ${abs(d.last())}", abs(d.last()) < 0.05f)
        }
    }

    @Test fun soundsAreDeterministic() {
        // gleiche Klänge bei jedem Aufruf (Cache liefert dieselben Daten)
        assertTrue(SoundSynth.get(Fx.JINGLE) === SoundSynth.get(Fx.JINGLE))
        // Glöckchen und Besen klingen verschieden
        val a = SoundSynth.get(Fx.JINGLE)
        val b = SoundSynth.get(Fx.SWEEP)
        assertFalse(a.size == b.size && a.indices.all { a[it] == b[it] })
    }

    @Test fun mixerPlacesSoundAtItsTime() {
        val rate = SoundSynth.SAMPLE_RATE
        val acc = FloatArray(rate * 3)
        SoundMixer.mixInto(acc, listOf(SoundEvent(1500L, Fx.KNOCK, 1f)), 1f)
        val startIdx = rate * 3 / 2
        assertTrue("vor dem Zeitpunkt muss Stille sein", (0 until startIdx).all { acc[it] == 0f })
        assertTrue("danach muss etwas zu hören sein", (startIdx until startIdx + rate / 10).any { abs(acc[it]) > 0.1f })
        // Ereignis hinter dem Ende wird abgeschnitten, nicht verschluckt oder abgestürzt
        SoundMixer.mixInto(FloatArray(100), listOf(SoundEvent(5000L, Fx.POOF, 1f)), 1f)
    }

    @Test fun speechMixerMixesSoundsEvenWithoutSpeech() {
        val pcm = app.wichtelein.speech.SpeechMixer.mix(emptyList(), emptyList(), 2000L, listOf(SoundEvent(100L, Fx.BELL, 1f)))
        assertEquals(2 * 44100, pcm.size)
        assertTrue(pcm.any { abs(it.toInt()) > 1000 })
    }

    // ---------------------------------------------------------------- Aktionspunkte

    @Test fun scriptPlaysStepsInOrderAndEndsAfterwards() {
        val a = Pt(0.7f, 0.85f)
        val b = Pt(0.3f, 0.75f)
        val p = Recorder.record(req(listOf(ScriptStep(ElfAction.SWEEP, a), ScriptStep(ElfAction.WAVE, b))))
        assertEquals(listOf("Fegen", "Winken"), p.sceneNames)
        assertTrue("Drehbuch muss fertig werden", p.scriptEndMs > 3000L)
        assertTrue("Aufnahme endet kurz nach dem letzten Punkt: ${p.durationMs} / ${p.scriptEndMs}", p.durationMs in p.scriptEndMs..(p.scriptEndMs + TAIL_MS + 200L))
        assertTrue("Besen wurde benutzt", p.frames.any { it.pose.prop == Prop.BROOM })
        // beim Fegen steht er am ersten Punkt
        val sweepFrame = p.frames.first { it.pose.prop == Prop.BROOM && it.pose.armR > 20f }
        assertEquals(a.x, sweepFrame.x, 0.03f)
        assertEquals(a.y, sweepFrame.y, 0.03f)
        // am Ende steht er (verbeugt) in der Nähe des zweiten Punkts
        val last = p.frames.last()
        assertEquals(b.x, last.x, 0.04f)
        assertTrue(last.visible)
    }

    @Test fun scriptWalksToEachPointAndMakesFitSounds() {
        val p = Recorder.record(req(listOf(ScriptStep(ElfAction.SWEEP, Pt(0.8f, 0.8f)))))
        val kinds = p.sounds.map { it.fx }.toSet()
        assertTrue("Schritte beim Laufen", Fx.STEP in kinds)
        assertTrue("Besenstriche beim Fegen", Fx.SWEEP in kinds)
        assertTrue("Glöckchen beim Auftreten", Fx.JINGLE in kinds)
        assertTrue("Puff beim Erscheinen", Fx.POOF in kinds)
        // Geräusche liegen in der Aufnahmezeit und sind zeitlich sortiert
        assertTrue(p.sounds.all { it.timeMs in 0..p.durationMs + 100 })
        assertEquals(p.sounds.map { it.timeMs }, p.sounds.map { it.timeMs }.sorted())
    }

    @Test fun doorActionsOpenCloseAndKnock() {
        val door = Pt(0.6f, 0.7f)
        val p = Recorder.record(
            req(listOf(ScriptStep(ElfAction.KNOCK), ScriptStep(ElfAction.ENTER), ScriptStep(ElfAction.EXIT)), door = door),
        )
        assertEquals(listOf("Anklopfen", "Tür öffnen & reinschauen", "Geschenk aus dem Haus holen"), p.sceneNames)
        val kinds = p.sounds.map { it.fx }
        assertTrue("dreimal Klopfen", kinds.count { it == Fx.KNOCK } == 3)
        assertTrue("Tür quietscht auf", kinds.count { it == Fx.DOOR_OPEN } >= 2)
        assertTrue("Tür fällt zu", kinds.count { it == Fx.DOOR_CLOSE } >= 2)
        assertTrue("Tür geht auf", p.frames.any { it.doorOpen > 0.9f })
        assertTrue("bleibt immer sichtbar und groß", p.frames.filter { it.doorOpen > 0.02f }.all { it.visible && it.pose.scale > 0.95f })
        assertTrue("holt ein Geschenk heraus", p.frames.any { it.pose.prop == Prop.GIFT })
    }

    @Test fun anotherActionAfterTheDoorActionContinuesNormally() {
        val door = Pt(0.5f, 0.7f)
        val p = Recorder.record(
            req(listOf(ScriptStep(ElfAction.ENTER), ScriptStep(ElfAction.DANCE, Pt(0.25f, 0.85f))), door = door),
        )
        assertTrue("Tanzen nach dem Betreten: ${p.sceneNames}", p.sceneNames == listOf("Tür öffnen & reinschauen", "Tanzen"))
        val last = p.frames.last()
        assertTrue("Wichtel sichtbar", last.pose.scale > 0.9f)
        assertEquals(0.25f, last.x, 0.05f)
    }

    @Test fun scriptWithoutStopRunsForTheRequestedTime() {
        val p = Recorder.record(req(listOf(ScriptStep(ElfAction.WAVE, Pt(0.5f, 0.8f))), stop = false, ms = 25_000L))
        assertTrue("Länge ${p.durationMs}", p.durationMs in 24_900L..25_000L)
        assertTrue("Drehbuch fertig: ${p.scriptEndMs}", p.scriptEndMs in 1L..25_000L)
    }

    @Test fun scriptIsDeterministic() {
        val steps = listOf(ScriptStep(ElfAction.DANCE, Pt(0.4f, 0.8f)), ScriptStep(ElfAction.SNACK, Pt(0.6f, 0.82f)))
        val a = Recorder.record(req(steps))
        val b = Recorder.record(req(steps))
        assertEquals(a.durationMs, b.durationMs)
        assertEquals(a.speech.map { it.text }, b.speech.map { it.text })
        assertEquals(a.sounds.map { it.timeMs to it.fx }, b.sounds.map { it.timeMs to it.fx })
    }

    @Test fun everyActionRunsToTheEnd() {
        val door = Pt(0.55f, 0.7f)
        for (a in ElfAction.values()) {
            val steps = listOf(ScriptStep(a, if (a.needsPoint) Pt(0.4f, 0.8f) else null))
            val p = Recorder.record(req(steps, door = door))
            assertTrue("${a.name} wird nicht fertig", p.scriptEndMs > 0L)
            assertEquals(listOf(a.label), p.sceneNames)
        }
    }

    @Test fun actionDefinitionsAreSane() {
        assertTrue(ElfAction.values().filter { it.needsDoor }.all { !it.needsPoint })
        assertTrue(ElfAction.values().map { it.label }.toSet().size == ElfAction.values().size)
    }
}
