package app.wichtelein

import app.wichtelein.anim.*
import app.wichtelein.settings.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/** Wege zu Zielen folgen dem gezeichneten Weg (z. B. Treppe), statt direkt durch das Bild zu laufen. */
class RouteTest {
    private val settings = Settings(elfName = "Pippo").withProfile("Max")
    private val stairs = listOf(Pt(0.45f, 0.80f), Pt(0.60f, 0.60f), Pt(0.78f, 0.40f))
    private val upstairsBed = SceneryItem(1, SceneryType.BED, Pt(0.42f, 0.42f), 0.2f)
    private val start = Pt(0.15f, 0.85f)

    private fun record(path: List<Pt>, items: List<SceneryItem>, script: List<ScriptStep>) = Recorder.record(
        PerfRequest(
            start, path, 0.3f, null, null, 3L, 60_000L, null, settings,
            script = script, stopAfterScript = true, aspect = 0.56f, scenery = items,
        ),
    )

    private fun reached(p: Performance, pt: Pt, from: Int = 0): Int {
        for (i in from until p.frames.size) {
            val f = p.frames[i]
            if (f.visible && abs(f.x - pt.x) < 0.03f && abs(f.y - pt.y) < 0.03f) return i
        }
        return -1
    }

    private fun distToSegment(px: Float, py: Float, a: Pt, b: Pt): Float {
        val asp = 0.56f
        val ax = a.x * asp
        val bx = b.x * asp
        val x = px * asp
        val dx = bx - ax
        val dy = b.y - a.y
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0f) 0f else (((x - ax) * dx + (py - a.y) * dy) / len2).coerceIn(0f, 1f)
        val cx = ax + t * dx
        val cy = a.y + t * dy
        return sqrt((x - cx) * (x - cx) + (py - cy) * (py - cy))
    }

    @Test fun walkingToTheBedUpstairsFollowsTheStairs() {
        val p = record(stairs, listOf(upstairsBed), listOf(ScriptStep(ElfAction.BED)))
        val i1 = reached(p, stairs[0])
        val i2 = reached(p, stairs[1], maxOf(i1, 0))
        val i3 = reached(p, stairs[2], maxOf(i2, 0))
        assertTrue("Treppenanfang erreicht ($i1)", i1 >= 0)
        assertTrue("Mitte danach ($i2)", i2 > i1)
        assertTrue("oben danach ($i3)", i3 > i2)
        // bis oben bleibt er auf dem gezeichneten Weg (kein Schweben in gerader Linie)
        val route = listOf(start) + stairs
        for (f in p.frames.take(i3)) {
            if (!f.visible) continue
            val d = route.zipWithNext().minOf { (a, b) -> distToSegment(f.x, f.y, a, b) }
            assertTrue("weicht vom Weg ab: (${f.x}, ${f.y}) Abstand $d", d < 0.04f)
        }
    }

    @Test fun withoutAPathTheElfWalksStraight() {
        val p = record(emptyList(), listOf(upstairsBed), listOf(ScriptStep(ElfAction.BED)))
        assertEquals(-1, reached(p, stairs[1]))
    }

    @Test fun buildingAnItemUpstairsAlsoFollowsThePath() {
        val tree = SceneryItem(1, SceneryType.TREE, Pt(0.72f, 0.42f), 0.25f)
        val p = record(stairs, listOf(tree), listOf(ScriptStep(ElfAction.WAVE, Pt(0.2f, 0.85f))))
        val firstBuild = p.frames.indexOfFirst { (it.built[1] ?: 0f) > 0f }
        assertTrue(firstBuild > 0)
        val mid = reached(p, stairs[1])
        assertTrue("über die Treppe zum Baum ($mid < $firstBuild)", mid in 0 until firstBuild)
    }
}
