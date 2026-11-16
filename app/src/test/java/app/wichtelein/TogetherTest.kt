package app.wichtelein

import app.wichtelein.anim.*
import app.wichtelein.settings.*
import org.junit.Assert.assertTrue
import org.junit.Test

/** Beide Figuren mit Weg, Aktionen, Gegenständen und Tür: keine bleibt stehen, keine hat einen Fehler. */
class TogetherTest {
    private val base = Settings(elfName = "Pippo", elfinName = "Elfi", cast = 2).withProfile("Max")
    private val path = listOf(Pt(0.3f, 0.85f), Pt(0.5f, 0.75f), Pt(0.7f, 0.85f))
    private val path2 = listOf(Pt(0.6f, 0.82f), Pt(0.4f, 0.72f), Pt(0.2f, 0.82f))
    private val bed = SceneryItem(2, SceneryType.BED, Pt(0.35f, 0.9f), 0.3f)
    private val tree = SceneryItem(1, SceneryType.TREE, Pt(0.85f, 0.88f), 0.4f)

    private fun req(script: List<ScriptStep>, items: List<SceneryItem>, door: Pt? = null) = PerfRequest(
        Pt(0.2f, 0.85f), path, 0.3f, null, null, 9L, 90_000L, null, base, aspect = 0.56f,
        start2 = Pt(0.8f, 0.82f), path2 = path2, script = script, scenery = items, door = door, stopAfterScript = script.isNotEmpty(),
    )

    private fun describe(p: Performance): String {
        val b = p.frames.mapIndexed { i, f -> i to f.partner!! }.filter { it.first % 150 == 0 }
            .joinToString { (i, f) -> "${i / 30}s:(${"%.2f".format(f.x)},${"%.2f".format(f.y)})" }
        return "Fehler=${p.errors} Elfin=$b Szenen=${p.sceneNames}"
    }

    private fun assertElfinKeepsMoving(p: Performance, fromSec: Int, toSec: Int) {
        val fs = p.frames.drop(fromSec * 30).take((toSec - fromSec) * 30)
        assertTrue("Elfin zu kurz aufgenommen ${describe(p)}", fs.size > 30)
        val xs = fs.map { it.partner!!.x }
        val ys = fs.map { it.partner!!.y }
        assertTrue("Elfin steht still von ${fromSec}s bis ${toSec}s ${describe(p)}", (xs.max() - xs.min()) + (ys.max() - ys.min()) > 0.03f)
    }

    @Test fun elfinMovesWhileWichtelBuildsObjects() {
        val p = Recorder.record(req(emptyList(), listOf(bed, tree)))
        assertTrue(describe(p), p.errors.isEmpty())
        assertElfinKeepsMoving(p, 0, 40)
        assertElfinKeepsMoving(p, 40, 80)
    }

    @Test fun elfinMovesWhileWichtelHasAnAction() {
        val p = Recorder.record(req(listOf(ScriptStep(ElfAction.WAVE, Pt(0.6f, 0.8f)), ScriptStep(ElfAction.DANCE, Pt(0.3f, 0.8f))), emptyList()))
        assertTrue(describe(p), p.errors.isEmpty())
        assertElfinKeepsMoving(p, 0, 20)
    }

    @Test fun elfinMovesWithObjectsAndAction() {
        val p = Recorder.record(req(listOf(ScriptStep(ElfAction.WAVE, Pt(0.6f, 0.8f))), listOf(bed, tree)))
        assertTrue(describe(p), p.errors.isEmpty())
        assertElfinKeepsMoving(p, 0, 24)
    }

    @Test fun bothGoIntoTheHouse() {
        val door = Pt(0.75f, 0.8f)
        val p = Recorder.record(req(listOf(ScriptStep(ElfAction.HOUSE_IN, null, who = 2)), emptyList(), door))
        assertTrue(describe(p), p.errors.isEmpty())
        // jeweils die letzte sichtbare Stelle vor dem Verschwinden liegt an der Tür
        fun lastSeen(f: (FrameState) -> FrameState): Pt {
            val v = p.frames.map(f).filter { it.visible && it.pose.scale > 0.4f }
            return Pt(v.last().x, v.last().y)
        }
        for ((n, pt) in listOf("Wichtel" to lastSeen { it }, "Elfin" to lastSeen { it.partner!! })) {
            assertTrue("$n verschwindet bei (${pt.x},${pt.y}) statt an der Tür ${describe(p)}", kotlin.math.abs(pt.x - door.x) < 0.15f && kotlin.math.abs(pt.y - door.y) < 0.15f)
        }
    }

    @Test fun bothGoToBed() {
        val p = Recorder.record(req(listOf(ScriptStep(ElfAction.BED, null, who = 2)), listOf(bed)))
        assertTrue(describe(p), p.errors.isEmpty())
        assertTrue("Wichtel lag nie im Bett ${describe(p)}", p.frames.any { it.onItem == bed.id })
        assertTrue("Elfin lag nie im Bett ${describe(p)}", p.frames.any { it.partner!!.onItem == bed.id })
    }

    @Test fun figureThatIsDoneKeepsWalkingItsPathWhileTheOtherStillWorks() {
        val steps = listOf(ScriptStep(ElfAction.WAVE, null, who = 1)) +
            List(6) { ScriptStep(ElfAction.DANCE, Pt(0.3f + 0.08f * it, 0.8f), who = 0) }
        val p = Recorder.record(req(steps, emptyList()))
        assertTrue(describe(p), p.errors.isEmpty())
        val end = (p.scriptEndMs / 33).toInt().coerceAtMost(p.frames.size - 1)
        val fs = p.frames.subList((end - 300).coerceAtLeast(0), end)
        val dx = fs.maxOf { it.partner!!.x } - fs.minOf { it.partner!!.x }
        assertTrue("Elfin steht nach ihrer Aktion still ($dx) ${describe(p)}", dx > 0.05f)
    }

    @Test fun elfinWaitingForJointStepKeepsMoving() {
        val steps = List(4) { ScriptStep(ElfAction.DANCE, Pt(0.3f + 0.1f * it, 0.8f), who = 0) } + ScriptStep(ElfAction.WAVE, null, who = 2)
        val p = Recorder.record(req(steps, listOf(bed, tree)))
        assertTrue(describe(p), p.errors.isEmpty())
        assertElfinKeepsMoving(p, 0, 25)
    }
}
