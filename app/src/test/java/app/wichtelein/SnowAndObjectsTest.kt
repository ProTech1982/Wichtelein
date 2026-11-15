package app.wichtelein

import app.wichtelein.anim.*
import app.wichtelein.scene.Scenes
import app.wichtelein.settings.*
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

/** Schneefall als Aktion, Gegenstände in zwei Stilen (klassisch/Neon), Bett in der Seitenansicht. */
class SnowAndObjectsTest {
    private val settings = Settings(elfName = "Pippo").withProfile("Max")

    private fun record(steps: List<ScriptStep>, items: List<SceneryItem> = emptyList()) = Recorder.record(
        PerfRequest(Pt(0.5f, 0.85f), emptyList(), 0.3f, null, null, 3L, 120_000L, null, settings, script = steps, stopAfterScript = true, aspect = 0.56f, scenery = items, autoBuild = false),
    )

    @Test fun snowStartsSlowlyAndStaysUntilItIsSwitchedOff() {
        val p = record(listOf(ScriptStep(ElfAction.SNOW), ScriptStep(ElfAction.DANCE, Pt(0.4f, 0.85f))))
        assertEquals(0f, p.frames.first().snow, 0.001f)
        val max = p.frames.maxOf { it.snow }
        assertEquals(1f, max, 0.001f)
        // wächst nur (nie wieder weniger), weil niemand den Schnee beendet
        var last = 0f
        for (f in p.frames) {
            assertTrue("Schnee wird nicht weniger", f.snow >= last - 0.0001f)
            last = f.snow
        }
        assertTrue("Schnee setzt langsam ein (erste Sekunde unter halber Stärke)", p.frames[p.fps].snow < 0.5f)
        assertEquals(1f, p.frames.last().snow, 0.001f)
    }

    @Test fun snowStopsFadesOut() {
        val p = record(listOf(ScriptStep(ElfAction.SNOW), ScriptStep(ElfAction.SNOW_OFF), ScriptStep(ElfAction.WAVE, Pt(0.4f, 0.85f)), ScriptStep(ElfAction.DANCE, Pt(0.6f, 0.85f))))
        assertEquals(1f, p.frames.maxOf { it.snow }, 0.001f)
        val peak = p.frames.indexOfFirst { it.snow > 0.999f }
        assertTrue(p.frames.drop(peak).any { it.snow < 0.5f })
        assertEquals(0f, p.frames.last().snow, 0.001f)
    }

    @Test fun snowOffWithoutSnowAndDoubleSnowAreHarmless() {
        val p = record(listOf(ScriptStep(ElfAction.SNOW_OFF), ScriptStep(ElfAction.SNOW), ScriptStep(ElfAction.SNOW)))
        assertTrue(p.frames.all { it.snow in 0f..1f })
        assertEquals(1f, p.frames.last().snow, 0.001f)
        assertTrue(p.sceneNames.isNotEmpty())
    }

    @Test fun snowActionsNeedNoPointAndAreListed() {
        assertFalse(ElfAction.SNOW.needsPoint)
        assertFalse(ElfAction.SNOW_OFF.needsPoint)
        assertNotNull(Scenes.find("brav_schnee"))
        assertFalse(Scenes.find("brav_schnee")!!.needsDoor)
    }

    @Test fun snowSceneEndsWithSnowFalling() {
        val p = Recorder.record(PerfRequest(Pt(0.5f, 0.85f), emptyList(), 0.3f, null, "brav_schnee", 5L, 30_000L, null, settings, aspect = 0.56f))
        assertTrue(p.frames.any { it.snow > 0.99f })
    }

    @Test fun everyObjectHasMatchingImageAndMeta() {
        // die Bilder stehen im Projekt (Test läuft im Ordner app/)
        val dir = File("src/main/assets/scenery")
        assertTrue("Ordner ${dir.absolutePath}", dir.isDirectory)
        val ids = HashSet<String>()
        for (t in SceneryType.values()) {
            assertTrue("doppelte Nummer ${t.id}", ids.add(t.id))
            val png = File(dir, "${t.id}.png")
            val json = File(dir, "${t.id}.json")
            assertTrue("Bild fehlt: ${t.id}", png.isFile && png.length() > 5_000)
            assertTrue("Daten fehlen: ${t.id}", json.isFile)
            val size = JSONObject(json.readText()).getJSONArray("size")
            val ratio = size.getInt(0).toFloat() / size.getInt(1)
            assertEquals("Seitenverhältnis ${t.id}", t.ratio, ratio, 0.03f)
        }
        // Dateien ohne Typ gibt es nicht
        val extra = dir.listFiles { f -> f.extension == "png" }!!.map { it.nameWithoutExtension }.filter { it !in ids }
        assertTrue("Bilder ohne Gegenstand: $extra", extra.isEmpty())
    }

    @Test fun bothStylesExistAndBedsAreMarked() {
        assertTrue(SceneryType.values().any { it.group == SceneryGroup.CLASSIC })
        assertTrue(SceneryType.values().any { it.group == SceneryGroup.NEON })
        assertEquals(setOf(SceneryType.BED, SceneryType.BED_WOOD), SceneryType.values().filter { it.isBed }.toSet())
        // jeder klassische Gegenstand hat sein Neon-Gegenstück oder ist neu (Schneemann, Geschenke, …)
        assertTrue(SceneryType.values().size >= 17)
        assertEquals("Tannenbaum", SceneryType.TREE.spoken)
        assertEquals("Holzbett", SceneryType.BED_WOOD.spoken)
    }

    @Test fun elfLiesInTheWoodenBedToo() {
        val bed = SceneryItem(1, SceneryType.BED_WOOD, Pt(0.4f, 0.88f), 0.3f)
        val p = record(listOf(ScriptStep(ElfAction.BED)), listOf(bed))
        val lying = p.frames.filter { kotlin.math.abs(kotlin.math.abs(it.pose.spin) - 90f) < 1f && it.onItem == bed.id }
        assertTrue("liegt im Holzbett", lying.isNotEmpty())
        assertTrue(p.sounds.any { it.fx == app.wichtelein.sound.Fx.SNORE })
    }

    @Test fun woodenBedIsFoundByTheBedAction() {
        val scenes = Scenes.all.filter { it.needsBed }
        assertTrue(scenes.isNotEmpty())
        val p = Recorder.record(
            PerfRequest(Pt(0.3f, 0.85f), emptyList(), 0.3f, null, "brav_bett", 5L, 40_000L, null, settings, aspect = 0.56f, scenery = listOf(SceneryItem(1, SceneryType.BED_WOOD, Pt(0.6f, 0.88f), 0.3f)), autoBuild = false),
        )
        assertTrue(p.frames.any { it.onItem == 1 })
    }
}
