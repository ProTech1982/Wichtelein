package app.wichtelein

import app.wichtelein.camera.*
import app.wichtelein.anim.*
import app.wichtelein.scene.*
import app.wichtelein.speech.*
import app.wichtelein.videoexport.*
import app.wichtelein.settings.*
import app.wichtelein.ui.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceTest {
    private val settings = Settings(elfName = "Pippo").withProfile("Max")
    private fun req(
        seed: Long = 7,
        sceneId: String? = null,
        category: Category? = null,
        path: List<Pt> = emptyList(),
        ms: Long = 20_000,
        enabled: Set<String>? = null,
    ) = PerfRequest(Pt(0.3f, 0.8f), path, 0.3f, category, sceneId, seed, ms, enabled, settings)

    @Test fun recordingHasExpectedFrameCountAndDuration() {
        val p = Recorder.record(req(ms = 4_000))
        assertEquals(30, p.fps)
        assertEquals(4_000L * 30 / 1000 + 1, p.frames.size.toLong())
        assertTrue(p.durationMs in 3_900L..4_000L)
    }

    @Test fun sameSeedGivesIdenticalPerformance() {
        val a = Recorder.record(req(seed = 11))
        val b = Recorder.record(req(seed = 11))
        assertEquals(a.speech.map { it.text }, b.speech.map { it.text })
        assertEquals(a.speech.map { it.timeMs }, b.speech.map { it.timeMs })
        assertEquals(a.sceneNames, b.sceneNames)
        for (i in a.frames.indices step 17) {
            assertEquals(a.frames[i].x, b.frames[i].x, 0f)
            assertEquals(a.frames[i].y, b.frames[i].y, 0f)
            assertEquals(a.frames[i].pose.armR, b.frames[i].pose.armR, 0f)
            assertEquals(a.frames[i].mess.size, b.frames[i].mess.size)
        }
    }

    @Test fun differentSeedsGiveDifferentPerformances() {
        val a = Recorder.record(req(seed = 1))
        val b = Recorder.record(req(seed = 2))
        assertNotEquals(a.speech.map { it.text }, b.speech.map { it.text })
    }

    @Test fun elfAppearsAndGreetsByName() {
        val p = Recorder.record(req())
        assertTrue(p.frames.first().pose.scale < 0.05f) // erscheint erst mit Wölkchen
        assertTrue(p.frames.any { it.visible })
        val first = p.speech.first().text
        assertTrue(first, first.contains("Max") || first.contains("Pippo") || first.isNotEmpty())
        assertTrue(p.speech.all { !it.text.contains("{") && !it.text.contains("}") })
        assertTrue(p.speech.any { it.text.contains("Max") })
    }

    @Test fun everySceneRunsAndSpeaks() {
        for (s in Scenes.all) {
            val p = Recorder.record(req(sceneId = s.id, ms = 30_000))
            assertTrue("${s.id}: nicht sichtbar", p.frames.any { it.visible })
            assertTrue("${s.id}: nichts gesagt", p.speech.size >= 2)
            assertEquals("${s.id}: falsche erste Szene", s.name, p.sceneNames.first())
            assertTrue("${s.id}: zu viel Zeug", p.frames.all { it.mess.size <= 14 })
            assertTrue("${s.id}: Position außerhalb", p.frames.all { it.x in -0.05f..1.05f && it.y in 0.3f..1.05f })
        }
    }

    @Test fun categoriesHaveSeveralScenesAndRandomPickStaysInCategory() {
        for (c in Category.values()) {
            val names = Scenes.inCategory(c).map { it.name }.toSet()
            assertTrue("${c.label} braucht mehrere Szenen", names.size >= 3)
            repeat(8) { seed ->
                val p = Recorder.record(req(seed = seed.toLong(), category = c, ms = 40_000))
                assertTrue("${c.label}: ${p.sceneNames}", p.sceneNames.all { it in names })
            }
        }
    }

    @Test fun randomCategoryMixesCategories() {
        val cats = HashSet<Category>()
        for (seed in 1L..12L) {
            val p = Recorder.record(req(seed = seed, ms = 40_000))
            p.sceneNames.forEach { n -> Scenes.all.first { it.name == n }.let { cats += it.category } }
        }
        assertEquals(Category.values().toSet(), cats)
    }

    @Test fun elfWalksToTheDrawnPathPoint() {
        val target = Pt(0.8f, 0.7f)
        val p = Recorder.record(req(sceneId = "brav_fegen", path = listOf(target), ms = 15_000))
        assertTrue(p.frames.any { kotlin.math.abs(it.x - target.x) < 0.03f && kotlin.math.abs(it.y - target.y) < 0.03f })
    }

    @Test fun disabledScenesAreNotChosen() {
        val only = setOf("ueb_tanzparty")
        val p = Recorder.record(req(seed = 3, category = Category.BRAV, enabled = only, ms = 40_000))
        assertTrue(p.sceneNames.all { it == "Tanzparty" })
    }

    @Test fun chaosThrowsItemsAndGiftSceneLeavesGift() {
        val chaos = Recorder.record(req(sceneId = "frech_chaos", ms = 25_000))
        assertTrue(chaos.frames.any { it.mess.size >= 3 })
        val gift = Recorder.record(req(sceneId = "ueb_geschenk", ms = 20_000))
        assertTrue(gift.frames.any { f -> f.mess.any { it.kind == MessItem.GIFT } })
    }

    @Test fun thereAreAtLeastThirtySayingsWithNamePlaceholder() {
        assertTrue(Lines.general.size >= 30)
        assertTrue(Lines.general.count { it.contains("{name}") } >= 10)
        val allowed = setOf("{name}", "{wichtel}", "{kind}")
        Lines.general.forEach { l ->
            Regex("[{][^}]*[}]").findAll(l).forEach { assertTrue("$l: ${it.value}", it.value in allowed) }
        }
    }

    @Test fun customLinesAreUsed() {
        val s = Settings(customLines = "Das ist mein eigener Satz, {name}!").withProfile("Max")
        val seen = (1L..20L).any { seed ->
            Recorder.record(PerfRequest(Pt(0.5f, 0.8f), emptyList(), 0.3f, null, null, seed, 30_000, null, s))
                .speech.any { it.text == "Das ist mein eigener Satz, Max!" }
        }
        assertTrue(seen)
    }
}
