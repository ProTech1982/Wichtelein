package app.wichtelein

import app.wichtelein.anim.*
import app.wichtelein.settings.*
import app.wichtelein.ui.EditOps
import app.wichtelein.ui.EditState
import app.wichtelein.ui.Sel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Eigener Weg für Wichtel und Elfin. */
class PathsTest {
    private val base = Settings(elfName = "Pippo", elfinName = "Elfi", cast = 2).withProfile("Max")

    @Test fun editorKeepsTwoSeparatePaths() {
        var st = EditState(start = Pt(0.2f, 0.8f), start2 = Pt(0.8f, 0.8f))
        st = EditOps.addPath(st, Pt(0.3f, 0.7f))
        st = EditOps.addPath2(st, Pt(0.7f, 0.7f))
        st = EditOps.addPath2(st, Pt(0.6f, 0.65f))
        assertEquals(1, st.path.size)
        assertEquals(2, st.path2.size)
        assertTrue(EditOps.contains(st, Sel.Path2(1)) && !EditOps.contains(st, Sel.Path2(2)))
        st = EditOps.move(st, Sel.Path2(0), Pt(0.75f, 0.72f))
        assertEquals(Pt(0.75f, 0.72f), st.path2[0])
        assertEquals(Pt(0.3f, 0.7f), st.path[0])
        assertEquals(Pt(0.6f, 0.65f), EditOps.position(st, Sel.Path2(1)))
        assertEquals(Sel.Path2(0), EditOps.hit(st, 0.75f, 0.72f, 0.03f, 0.03f, { Pair(0f, 0f) }, null, setOf(app.wichtelein.ui.SelKind.PATH)))
        assertEquals(Sel.Path(0), EditOps.hit(st, 0.3f, 0.7f, 0.03f, 0.03f, { Pair(0f, 0f) }, null, setOf(app.wichtelein.ui.SelKind.PATH)))
        st = EditOps.remove(st, Sel.Path2(0))
        assertEquals(listOf(Pt(0.6f, 0.65f)), st.path2)
        assertEquals(1, st.path.size)
        assertTrue(EditOps.label(st, Sel.Path2(0)).contains("Elfin"))
        repeat(20) { st = EditOps.addPath2(st, Pt(0.5f, 0.6f)) }
        assertEquals(EditOps.MAX_WAYPOINTS, st.path2.size)
    }

    @Test fun eachFigureWalksItsOwnPath() {
        val req = PerfRequest(
            Pt(0.15f, 0.85f), listOf(Pt(0.1f, 0.85f), Pt(0.3f, 0.85f)), 0.3f, null, null, 7L, 60_000L, null, base, aspect = 0.56f,
            start2 = Pt(0.85f, 0.8f), path2 = listOf(Pt(0.9f, 0.8f), Pt(0.7f, 0.8f)),
        )
        val p = Recorder.record(req)
        val a = p.frames.map { it.x }
        val b = p.frames.map { it.partner!!.x }
        assertTrue("Wichtel bleibt links: ${a.average()}", a.average() < 0.45)
        assertTrue("Elfin bleibt rechts: ${b.average()}", b.average() > 0.55)
        assertTrue(a.maxOrNull()!! < 0.6f && b.minOrNull()!! > 0.4f)
    }

    @Test fun withoutOwnPathTheElfinDoesNotFollowTheWichtelsPath() {
        val req = PerfRequest(
            Pt(0.15f, 0.85f), listOf(Pt(0.1f, 0.85f), Pt(0.3f, 0.85f)), 0.3f, null, null, 7L, 60_000L, null, base, aspect = 0.56f, start2 = Pt(0.85f, 0.8f),
        )
        val p = Recorder.record(req)
        assertTrue(p.frames.map { it.partner!!.x }.average() > 0.55)
    }
}
