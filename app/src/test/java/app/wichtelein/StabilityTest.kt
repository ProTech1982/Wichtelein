package app.wichtelein

import app.wichtelein.anim.*
import app.wichtelein.settings.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stabilität: gemeinsames Wetter, Absturzbericht, keine Figur steht tot herum. */
class StabilityTest {
    private val base = Settings(elfName = "Pippo", elfinName = "Elfi", cast = 2).withProfile("Max")

    private fun record(s: Settings, steps: List<ScriptStep>, ms: Long = 60_000L, door: Pt? = null) = Recorder.record(
        PerfRequest(Pt(0.3f, 0.85f), emptyList(), 0.3f, null, null, 3L, ms, null, s, door = door, script = steps, stopAfterScript = steps.isNotEmpty(), aspect = 0.56f),
    )

    @Test fun snowStartedByTheElfinFallsForBoth() {
        val p = record(base, listOf(ScriptStep(ElfAction.SNOW, null, who = 1), ScriptStep(ElfAction.WAVE, Pt(0.2f, 0.85f), who = 0)))
        val last = p.frames.last()
        assertEquals(1f, last.snow, 0.001f)
        assertEquals(1f, last.partner!!.snow, 0.001f)
        assertTrue(p.frames.all { it.snow == it.partner!!.snow })
    }

    @Test fun snowSwitchedOffByTheWichtelStopsForBoth() {
        val p = record(base, listOf(ScriptStep(ElfAction.SNOW, null, who = 1), ScriptStep(ElfAction.WAVE, Pt(0.2f, 0.85f), who = 0), ScriptStep(ElfAction.SNOW_OFF, null, who = 0), ScriptStep(ElfAction.DANCE, Pt(0.3f, 0.85f), who = 0)), ms = 90_000L)
        assertEquals(0f, p.frames.last().snow, 0.001f)
        assertEquals(0f, p.frames.last().partner!!.snow, 0.001f)
    }

    @Test fun doorOpenedByTheElfinIsReportedInHerFrames() {
        val p = record(base, listOf(ScriptStep(ElfAction.ENTER, null, who = 1)), door = Pt(0.7f, 0.6f))
        assertTrue("Elfin öffnet die Tür", p.frames.any { it.partner!!.doorOpen > 0.5f })
        assertTrue("beim Wichtel bleibt sie zu", p.frames.all { it.doorOpen < 0.05f })
    }

    @Test fun crashReportHasTheImportantPartsAndIsShort() {
        val e = IllegalStateException("kaputt", RuntimeException("Ursache"))
        val text = CrashLog.format("main", e, time = 0L, maxMemMb = 256L)
        assertTrue(text.contains("IllegalStateException") && text.contains("kaputt") && text.contains("Ursache"))
        assertTrue(text.contains("Thread: main") && text.contains("256 MB"))
        assertFalse(text.length > 6000)
        val huge = CrashLog.format("t", RuntimeException("x".repeat(20_000)))
        assertTrue(huge.length <= 6000)
    }

    @Test fun neitherFigureEverGetsStuckInALongFilm() {
        for (seed in 1L..4L) {
            val p = Recorder.record(PerfRequest(Pt(0.3f, 0.85f), emptyList(), 0.3f, null, null, seed, 90_000L, null, base, aspect = 0.56f))
            // in der zweiten Hälfte des Films bewegen sich beide noch (keine erstarrte Figur)
            val half = p.frames.drop(p.frames.size / 2)
            fun changes(sel: (FrameState) -> FrameState): Int = half.zipWithNext().count { (a, b) ->
                val x = sel(a)
                val y = sel(b)
                x.x != y.x || x.pose.armR != y.pose.armR || x.pose.legL != y.pose.legL || x.pose.spin != y.pose.spin || x.pose.bob != y.pose.bob
            }
            assertTrue("Wichtel erstarrt (Seed $seed)", changes { it } > 50)
            assertTrue("Elfin erstarrt (Seed $seed)", changes { it.partner!! } > 50)
        }
    }
}
