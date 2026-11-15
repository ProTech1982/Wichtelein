package app.wichtelein

import app.wichtelein.anim.*
import app.wichtelein.settings.Updater
import app.wichtelein.ui.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Platzieren im Editor: auswählen, verschieben, löschen, Rückgängig; Update-Fenster. */
class EditStateTest {
    private val box = { _: SceneryItem -> Pair(0.2f, 0.3f) }

    @Test fun undoRedoWalkThroughEveryStep() {
        val h = EditHistory()
        var s = EditState()
        val states = ArrayList<EditState>()
        repeat(4) {
            h.push(s)
            s = EditOps.addPath(s, Pt(0.1f * (it + 1), 0.7f))
            states.add(s)
        }
        assertEquals(4, s.path.size)
        s = h.undo(s)!!
        assertEquals(3, s.path.size)
        s = h.undo(s)!!
        assertEquals(2, s.path.size)
        s = h.redo(s)!!
        assertEquals(3, s.path.size)
        // ein neuer Schritt löscht die Wiederholen-Liste
        h.push(s)
        s = EditOps.addPath(s, Pt(0.9f, 0.9f))
        assertFalse(h.canRedo)
        // alles zurück bis zum Anfang
        while (h.canUndo) s = h.undo(s)!!
        assertEquals(EditState(), s)
        assertNull(h.undo(s))
    }

    @Test fun removeOnePointKeepsTheOthers() {
        var s = EditState()
        for (i in 1..4) s = EditOps.addPath(s, Pt(0.1f * i, 0.6f))
        s = EditOps.remove(s, Sel.Path(1))
        assertEquals(listOf(0.1f, 0.3f, 0.4f), s.path.map { it.x })
        s = EditOps.addStep(s, ElfAction.SWEEP, Pt(0.5f, 0.8f))
        s = EditOps.addStep(s, ElfAction.WAVE, Pt(0.6f, 0.8f))
        s = EditOps.remove(s, Sel.Step(0))
        assertEquals(listOf(ElfAction.WAVE), s.script.map { it.action })
    }

    @Test fun moveClampsIntoTheImage() {
        var s = EditState(start = Pt(0.5f, 0.7f))
        s = EditOps.move(s, Sel.Start, Pt(2f, -1f))
        assertEquals(0.97f, s.start!!.x, 1e-6f)
        assertEquals(EditOps.MIN_Y, s.start!!.y, 1e-6f)
        s = EditOps.addPath(s, Pt(0.3f, 0.7f))
        s = EditOps.move(s, Sel.Path(0), Pt(0.35f, 0.75f))
        assertEquals(Pt(0.35f, 0.75f), s.path[0])
    }

    @Test fun hitPrefersSmallPointsOverBigItems() {
        var s = EditState(start = Pt(0.5f, 0.8f))
        s = EditOps.addItem(s, SceneryItem(0, SceneryType.TREE, Pt(0.5f, 0.85f), 0.4f))
        s = EditOps.addPath(s, Pt(0.5f, 0.7f))
        // Punkt mitten im Baum bleibt greifbar
        assertEquals(Sel.Path(0), EditOps.hit(s, 0.5f, 0.7f, 0.04f, 0.03f, box, null))
        assertEquals(Sel.Start, EditOps.hit(s, 0.51f, 0.8f, 0.04f, 0.03f, box, null))
        // sonst trifft man den Baum (Rechteck oberhalb des Fußpunkts)
        assertEquals(Sel.Item(1), EditOps.hit(s, 0.55f, 0.6f, 0.04f, 0.03f, box, null))
        // leere Stelle
        assertNull(EditOps.hit(s, 0.9f, 0.5f, 0.04f, 0.03f, box, null))
    }

    @Test fun doorIsHitByItsRectangle() {
        val s = EditState(door = Pt(0.3f, 0.7f))
        assertEquals(Sel.Door, EditOps.hit(s, 0.3f, 0.6f, 0.04f, 0.03f, box, Pair(0.1f, 0.2f)))
        assertNull(EditOps.hit(s, 0.6f, 0.6f, 0.04f, 0.03f, box, Pair(0.1f, 0.2f)))
    }

    @Test fun itemsGetUniqueIdsAndCanBeResizedAndFlipped() {
        var s = EditState()
        s = EditOps.addItem(s, SceneryItem(0, SceneryType.TREE, Pt(0.2f, 0.8f), 0.4f))
        s = EditOps.addItem(s, SceneryItem(0, SceneryType.BED, Pt(0.7f, 0.8f), 0.2f))
        assertEquals(listOf(1, 2), s.scenery.map { it.id })
        s = EditOps.resizeItem(s, 2, 5f)
        assertEquals(0.9f, s.scenery[1].scale, 1e-6f)
        s = EditOps.flipItem(s, 1)
        assertTrue(s.scenery[0].flip)
        s = EditOps.remove(s, Sel.Item(1))
        assertEquals(listOf(2), s.scenery.map { it.id })
        s = EditOps.addItem(s, SceneryItem(0, SceneryType.FENCE, Pt(0.4f, 0.8f), 0.1f))
        assertEquals(3, s.scenery.last().id) // Nummern werden nicht wiederverwendet
        assertFalse(EditOps.contains(s, Sel.Item(1)))
    }

    @Test fun waypointsMayLieHighUpAndCanBeNudgedFinely() {
        var s = EditState()
        s = EditOps.addPath(s, Pt(0.5f, 0.12f)) // weit oben im Bild (Treppe)
        assertEquals(0.12f, s.path[0].y, 1e-6f)
        s = EditOps.nudge(s, Sel.Path(0), 0.004f, -0.004f)
        assertEquals(0.504f, s.path[0].x, 1e-5f)
        assertEquals(0.116f, s.path[0].y, 1e-5f)
        // am Rand bleibt es im Bild
        s = EditOps.nudge(s, Sel.Path(0), 0f, -5f)
        assertEquals(EditOps.MIN_Y, s.path[0].y, 1e-6f)
    }

    @Test fun perspectiveCanBeSwitchedOff() {
        assertEquals(depth(0.2f), depth(0.2f, 1f), 1e-6f)
        assertEquals(depth(0.78f), depth(0.2f, 0f), 1e-6f)
        assertTrue(depth(0.2f, 0.5f) > depth(0.2f, 1f) && depth(0.2f, 0.5f) < depth(0.78f))
    }

    @Test fun limitsAreRespected() {
        var s = EditState()
        repeat(EditOps.MAX_WAYPOINTS + 5) { s = EditOps.addPath(s, Pt(0.5f, 0.7f)) }
        assertEquals(EditOps.MAX_WAYPOINTS, s.path.size)
    }

    // ---------------------------------------------------------------- Update-Fenster

    @Test fun updateDialogShowsChangelogAndOnlyOffersUpdateWhenNewer() {
        val newer = Updater.Result(available = true, version = "Wichtelein v0.5.0", notes = "## Neu\n- Zaun", downloadUrl = "https://x/y.apk")
        val a = updateDialogFrom(newer)
        assertTrue(a is UpdateDialogState.Available && a.url == "https://x/y.apk")
        val same = updateDialogFrom(Updater.Result(available = false, version = "v0.4.0", notes = "Notizen"))
        assertTrue(same is UpdateDialogState.UpToDate)
        assertTrue(updateDialogFrom(Updater.Result(error = "Keine Verbindung")) is UpdateDialogState.Error)
        assertEquals("Neu\n- Zaun", cleanNotes("## Neu\n- Zaun"))
        assertEquals("Keine Beschreibung der Änderungen vorhanden.", cleanNotes("  "))
        assertEquals("Keine Beschreibung der Änderungen vorhanden.", cleanNotes(null))
    }

    @Test fun sceneryMetaParsesLightsAndGround() {
        val m = SceneryMeta.fromJson("""{"size":[600,800],"anchor":[300,760],"lights":[{"x":10,"y":20,"r":5,"color":"#FF00AA","speed":0.5,"phase":0.25}],"ground":{"color":"#00FFFF","rx":120}}""")
        assertEquals(600, m.w)
        assertEquals(1, m.lights.size)
        assertEquals(0.5f, m.lights[0].speed, 1e-6f)
        assertEquals(120f, m.groundRx, 1e-6f)
        val box = sceneryBox(SceneryItem(1, SceneryType.TREE, Pt(0.5f, 0.9f), 0.4f), null, 0.5f)
        assertEquals(0.4f, box.second, 1e-6f)
        assertEquals(0.4f / 0.5f, box.first, 1e-5f)
    }
}
