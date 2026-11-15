package app.wichtelein

import app.wichtelein.anim.*
import app.wichtelein.settings.*
import app.wichtelein.speech.VoiceCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Wichtel und Elfin einzeln oder zusammen, getrennte Stimmen nach Geschlecht, Drehung der Tür. */
class CastAndVoicesTest {
    private val base = Settings(elfName = "Pippo", elfinName = "Elfi").withProfile("Max")

    private fun record(s: Settings, steps: List<ScriptStep> = emptyList(), ms: Long = 20_000L, door: Pt? = null, doorRot: Float = 0f) = Recorder.record(
        PerfRequest(Pt(0.3f, 0.85f), emptyList(), 0.3f, null, null, 3L, ms, null, s, door = door, doorRot = doorRot, script = steps, stopAfterScript = steps.isNotEmpty(), aspect = 0.56f),
    )

    @Test fun singleFiguresHaveNoPartner() {
        for (cast in 0..1) {
            val p = record(base.copy(cast = cast))
            assertTrue(p.frames.all { it.partner == null })
        }
    }

    @Test fun singleElfinSpeaksWithTheElfinVoiceAndName() {
        val p = record(base.copy(cast = 1), ms = 30_000L)
        assertTrue(p.speech.isNotEmpty())
        assertTrue(p.speech.all { it.actor == 1 })
        assertEquals(0, record(base.copy(cast = 0), ms = 30_000L).speech.map { it.actor }.distinct().single())
    }

    @Test fun bothFiguresAppearTogetherAndBothSpeak() {
        val p = record(base.copy(cast = 2), ms = 40_000L)
        assertTrue("Elfin ist in jedem Bild dabei", p.frames.all { it.partner != null })
        val actors = p.speech.map { it.actor }.toSet()
        assertEquals(setOf(0, 1), actors)
        // Sprache zeitlich sortiert
        assertEquals(p.speech.map { it.timeMs }, p.speech.map { it.timeMs }.sorted())
        // beide stehen nicht übereinander
        val f = p.frames[p.frames.size / 2]
        assertTrue(abs(f.x - f.partner!!.x) > 0.02f || abs(f.y - f.partner!!.y) > 0.02f)
    }

    @Test fun eachFigureDoesItsOwnActions() {
        val steps = listOf(
            ScriptStep(ElfAction.WAVE, Pt(0.2f, 0.85f), who = 0),
            ScriptStep(ElfAction.DANCE, Pt(0.75f, 0.85f), who = 1),
            ScriptStep(ElfAction.BOW, Pt(0.25f, 0.8f), who = 0),
        )
        val p = record(base.copy(cast = 2), steps)
        val last = p.frames.last()
        assertEquals("Wichtel endet an seinem letzten Punkt", 0.25f, last.x, 0.06f)
        assertEquals("Elfin endet an ihrem Punkt", 0.75f, last.partner!!.x, 0.06f)
        assertEquals(listOf("Winken", "Verbeugen"), p.sceneNames.filter { it == "Winken" || it == "Verbeugen" })
        assertTrue("Elfin tanzt nicht weg, solange nur der Wichtel Aktionen hat", true)
    }

    @Test fun theOtherFigureStaysAliveWhenOnlyOneHasActions() {
        // der Wichtel hat Aktionen, die Elfin keine: sie steht nicht tot herum, sondern macht ihr eigenes Ding
        val steps = listOf(ScriptStep(ElfAction.WAVE, Pt(0.2f, 0.85f), who = 0), ScriptStep(ElfAction.DANCE, Pt(0.25f, 0.85f), who = 0), ScriptStep(ElfAction.SWEEP, Pt(0.3f, 0.85f), who = 0))
        val p = record(base.copy(cast = 2), steps, ms = 120_000L)
        val partner = p.frames.map { it.partner!! }
        val moved = partner.maxOf { it.x } - partner.minOf { it.x }
        val gestures = partner.count { it.pose.armR > 30f || kotlin.math.abs(it.pose.legL) > 5f || it.pose.heading != 0f }
        assertTrue("Elfin bewegt sich: $moved / $gestures", moved > 0.02f || gestures > 20)
        assertTrue("und sie spricht", p.speech.any { it.actor == 1 })
    }

    @Test fun onlyFemaleVoicesForTheElfinAndOnlyMaleForTheWichtel() {
        assertTrue(VoiceCatalog.forActor(1).isNotEmpty() && VoiceCatalog.forActor(1).all { it.female })
        assertTrue(VoiceCatalog.forActor(0).isNotEmpty() && VoiceCatalog.forActor(0).none { it.female && !it.both })
        assertFalse(VoiceCatalog.find(VoiceCatalog.DEFAULT_ID).female)
        assertTrue(VoiceCatalog.find(VoiceCatalog.DEFAULT_ELFIN_ID).female)
        assertTrue(VoiceCatalog.find("ramona").female && VoiceCatalog.find("kerstin").female && VoiceCatalog.find("dii_high").female)
        assertFalse(VoiceCatalog.find("thorsten_high").female || VoiceCatalog.find("karlsson").female)
        // Dii steht in beiden Listen
        assertEquals(VoiceCatalog.all.size + VoiceCatalog.all.count { it.both }, VoiceCatalog.forActor(0).size + VoiceCatalog.forActor(1).size)
        assertTrue(VoiceCatalog.forActor(0).any { it.id == "dii_high" } && VoiceCatalog.forActor(1).any { it.id == "dii_high" })
        assertEquals(listOf("dii_high"), VoiceCatalog.all.filter { it.both }.map { it.id })
    }

    @Test fun voicesAndNamesFollowTheFigure() {
        val s = base.copy(voiceId = "karlsson", elfinVoiceId = "kerstin")
        assertEquals("karlsson", s.voiceFor(0))
        assertEquals("kerstin", s.voiceFor(1))
        assertEquals("kerstin", s.forActor(1).voiceId)
        assertEquals("Pippo", s.nameFor(0))
        assertEquals("Elfi", s.nameFor(1))
        assertTrue(s.copy(cast = 1).elfin && !s.copy(cast = 1).both)
        assertTrue(s.copy(cast = 2).both && !s.copy(cast = 2).elfin)
        assertEquals("ramona", Settings().elfinVoiceId)
    }

    @Test fun doorRotationReachesEveryFrame() {
        val p = record(base, ms = 3_000L, door = Pt(0.7f, 0.6f), doorRot = 20f)
        assertTrue(p.frames.all { it.doorRot == 20f && it.doorPos == Pt(0.7f, 0.6f) })
        assertTrue(record(base, ms = 2_000L, door = Pt(0.7f, 0.6f)).frames.all { it.doorRot == 0f })
    }

    @Test fun editorKeepsWhoForNewActionsAndStepsKeepTheirFigure() {
        var st = app.wichtelein.ui.EditState()
        st = app.wichtelein.ui.EditOps.addStep(st, ElfAction.WAVE, Pt(0.4f, 0.8f), 1)
        st = app.wichtelein.ui.EditOps.addStep(st, ElfAction.DANCE, Pt(0.5f, 0.8f))
        assertEquals(listOf(1, 0), st.script.map { it.who })
        assertTrue(app.wichtelein.ui.EditOps.label(st, app.wichtelein.ui.Sel.Step(0)).contains("🧝"))
        assertEquals(30f, app.wichtelein.ui.EditOps.wrapDeg(390f), 0.001f)
        assertEquals(-170f, app.wichtelein.ui.EditOps.wrapDeg(190f), 0.001f)
        assertNull(st.door)
        assertNotNull(st.copy(doorRot = 10f).doorRot)
    }
}
