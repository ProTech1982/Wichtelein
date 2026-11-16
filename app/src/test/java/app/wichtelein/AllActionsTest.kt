package app.wichtelein

import app.wichtelein.anim.*
import app.wichtelein.settings.*
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Jede Aktion einzeln für Wichtel, Elfin und beide, mit und ohne Weg, mit Gegenständen und Tür: fertig, ohne Fehler, am richtigen Ort. */
class AllActionsTest {
    private val door = Pt(0.8f, 0.8f)
    private val tree = SceneryItem(1, SceneryType.TREE, Pt(0.12f, 0.9f), 0.4f)
    private val bed = SceneryItem(2, SceneryType.BED, Pt(0.4f, 0.9f), 0.3f)
    private val path = listOf(Pt(0.3f, 0.85f), Pt(0.5f, 0.75f), Pt(0.65f, 0.85f))
    private val path2 = listOf(Pt(0.6f, 0.82f), Pt(0.45f, 0.72f), Pt(0.25f, 0.82f))

    private fun settings(cast: Int) = Settings(elfName = "Pippo", elfinName = "Elfi", cast = cast).withProfile("Max")

    private fun record(cast: Int, steps: List<ScriptStep>, withPath: Boolean, items: List<SceneryItem> = listOf(tree, bed)) = Recorder.record(
        PerfRequest(
            Pt(0.2f, 0.85f), if (withPath) path else emptyList(), 0.3f, null, null, 11L, 240_000L, null, settings(cast), aspect = 0.56f,
            door = door, script = steps, scenery = items, stopAfterScript = true,
            start2 = Pt(0.9f, 0.85f), path2 = if (withPath) path2 else emptyList(),
        ),
    )

    private fun frames(p: Performance, partner: Boolean) = p.frames.map { if (partner) it.partner ?: it else it }

    private fun check(label: String, p: Performance, partner: Boolean) {
        assertTrue("$label: Fehler ${p.errors}", p.errors.isEmpty())
        assertTrue("$label: Drehbuch wurde nicht fertig", p.scriptEndMs >= 0)
        for (f in frames(p, partner)) {
            assertTrue("$label: Position ungültig (${f.x},${f.y})", f.x in -0.01f..1.01f && f.y in -0.01f..1.01f && f.x.isFinite() && f.y.isFinite())
        }
    }

    private fun step(a: ElfAction, who: Int) = ScriptStep(a, if (a.needsPoint) Pt(0.55f, 0.8f) else null, who = who)

    @Test fun everyActionAlone() {
        for (cast in listOf(0, 1)) for (withPath in listOf(false, true)) for (a in ElfAction.values()) {
            val label = "${a.name} cast=$cast weg=$withPath"
            check(label, record(cast, listOf(step(a, cast)), withPath), cast == 2)
        }
    }

    @Test fun everyActionForBothFigures() {
        for (withPath in listOf(false, true)) for (a in ElfAction.values()) for (who in 0..2) {
            val p = record(2, listOf(step(a, who)), withPath)
            val label = "${a.name} beide who=$who weg=$withPath"
            assertTrue("$label: Fehler ${p.errors}", p.errors.isEmpty())
            assertTrue("$label: Drehbuch wurde nicht fertig", p.scriptEndMs >= 0)
            for (f in p.frames) for (g in listOfNotNull(f, f.partner)) {
                assertTrue("$label: Position ungültig (${g.x},${g.y})", g.x in -0.01f..1.01f && g.y in -0.01f..1.01f)
            }
        }
    }

    @Test fun doorAndBedActionsReachTheirTarget() {
        for (cast in 0..2) for (who in (if (cast == 2) 0..2 else listOf(cast))) for (withPath in listOf(false, true)) {
            val p = record(cast, listOf(step(ElfAction.BED, who), step(ElfAction.HOUSE_IN, who)), withPath)
            val label = "Bett dann Haus cast=$cast who=$who weg=$withPath"
            assertTrue("$label: Fehler ${p.errors}", p.errors.isEmpty())
            val figs = buildList {
                if (cast == 2) { if (who != 1) add(false); if (who != 0) add(true) } else add(false)
            }
            for (partner in figs) {
                val fs = frames(p, partner)
                // beim Bett gewesen
                assertTrue("$label: nie am Bett (partner=$partner)", fs.any { it.onItem == bed.id })
                // zuletzt sichtbar an der Tür
                val last = fs.last { it.visible && it.pose.scale > 0.3f }
                assertTrue("$label: verschwindet bei (${last.x},${last.y}) statt an der Tür (partner=$partner)", abs(last.x - door.x) < 0.12f && abs(last.y - door.y) < 0.12f)
                assertTrue("$label: am Ende nicht im Haus (partner=$partner)", !fs.last().visible || fs.last().pose.scale < 0.1f)
            }
        }
    }

    @Test fun placingObjectsWorksForEveryCast() {
        for (cast in 0..2) for (withPath in listOf(false, true)) {
            val p = record(cast, listOf(step(ElfAction.WAVE, if (cast == 1) 1 else 0)), withPath)
            val label = "Objekte cast=$cast weg=$withPath"
            assertTrue("$label: Fehler ${p.errors}", p.errors.isEmpty())
            for (it in listOf(tree, bed)) {
                // wer baut, wird mit cast=1 die Elfin; der Hauptdarsteller trägt die gebauten Gegenstände
                assertTrue("$label: ${it.type} nie fertig gebaut", p.frames.any { f -> (f.built[it.id] ?: 0f) >= 1f })
            }
            if (cast == 2) {
                val fs = p.frames.drop(60).take(600)
                val dx = fs.maxOf { it.partner!!.x } - fs.minOf { it.partner!!.x }
                val dy = fs.maxOf { it.partner!!.y } - fs.minOf { it.partner!!.y }
                assertTrue("$label: Elfin steht still ($dx,$dy)", dx + dy > 0.03f)
            }
        }
    }
}
