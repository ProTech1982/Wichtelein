package app.wichtelein

import app.wichtelein.anim.*
import app.wichtelein.ui.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Das aktive Werkzeug fasst nur seine eigenen Dinge an – nichts anderes wird versehentlich ausgewählt oder verschoben. */
class ToolFilterTest {
    private val box = { _: SceneryItem -> Pair(0.3f, 0.4f) }

    // Alles liegt übereinander an derselben Stelle
    private val p = Pt(0.5f, 0.7f)
    private val s = EditState(
        start = p,
        path = listOf(p),
        script = listOf(ScriptStep(ElfAction.SWEEP, p)),
        door = p,
        scenery = listOf(SceneryItem(1, SceneryType.TREE, p, 0.4f)),
        nextId = 2,
    )
    private val doorBox = Pair(0.1f, 0.2f)

    private fun hit(vararg kinds: SelKind) = EditOps.hit(s, 0.5f, 0.68f, 0.04f, 0.03f, box, doorBox, kinds.toSet())

    @Test fun eachToolOnlyHitsItsOwnKind() {
        assertEquals(Sel.Start, hit(SelKind.START))
        assertEquals(Sel.Path(0), hit(SelKind.PATH))
        assertEquals(Sel.Step(0), hit(SelKind.STEP))
        assertEquals(Sel.Door, hit(SelKind.DOOR))
        assertEquals(Sel.Item(1), hit(SelKind.ITEM))
        assertNull("Szene-Werkzeug fasst nichts an", hit())
    }

    @Test fun withoutFilterTheSmallThingsStillWin() {
        assertEquals(Sel.Step(0), EditOps.hit(s, 0.5f, 0.68f, 0.04f, 0.03f, box, doorBox))
    }

    @Test fun selectionKindsMatch() {
        assertEquals(SelKind.START, Sel.Start.kind())
        assertEquals(SelKind.PATH, Sel.Path(2).kind())
        assertEquals(SelKind.STEP, Sel.Step(0).kind())
        assertEquals(SelKind.DOOR, Sel.Door.kind())
        assertEquals(SelKind.ITEM, Sel.Item(5).kind())
    }

    @Test fun placingAPathPointNextToAnObjectDoesNotSelectTheObject() {
        // Weg-Werkzeug: Tippen auf den Baum trifft keinen Wegpunkt, also wird ein neuer Punkt gesetzt
        val onlyTree = EditState(scenery = listOf(SceneryItem(1, SceneryType.TREE, p, 0.4f)), nextId = 2)
        assertNull(EditOps.hit(onlyTree, 0.5f, 0.6f, 0.04f, 0.03f, box, null, setOf(SelKind.PATH)))
        assertEquals(Sel.Item(1), EditOps.hit(onlyTree, 0.5f, 0.6f, 0.04f, 0.03f, box, null, setOf(SelKind.ITEM)))
    }
}
