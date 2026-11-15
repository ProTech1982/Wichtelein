package app.wichtelein

import app.wichtelein.anim.*
import app.wichtelein.scene.Scenes
import app.wichtelein.scene.SpeechText
import app.wichtelein.settings.*
import app.wichtelein.sound.Fx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Gegenstände stellt der Wichtel selbst auf, legt sich ins Bett und schnarcht; „Psst“ wird nicht buchstabiert. */
class BuildAndBedTest {
    private val settings = Settings(elfName = "Pippo").withProfile("Max")
    private val tree = SceneryItem(1, SceneryType.TREE, Pt(0.75f, 0.85f), 0.4f)
    private val bed = SceneryItem(2, SceneryType.BED, Pt(0.35f, 0.88f), 0.3f)

    private fun req(items: List<SceneryItem>, script: List<ScriptStep> = emptyList(), seed: Long = 4L) = PerfRequest(
        Pt(0.5f, 0.8f), emptyList(), 0.3f, null, null, seed, 60_000L, null, settings,
        script = script, stopAfterScript = true, aspect = 0.56f, scenery = items,
    )

    @Test fun itemsAreNotThereAtFirstAndGrowWhileTheElfBuildsThem() {
        val p = Recorder.record(req(listOf(tree, bed), listOf(ScriptStep(ElfAction.WAVE, Pt(0.5f, 0.8f)))))
        assertTrue("am Anfang nichts aufgebaut", p.frames.first().built.isEmpty())
        fun firstFull(id: Int) = p.frames.indexOfFirst { (it.built[id] ?: 0f) >= 1f }
        fun firstStart(id: Int) = p.frames.indexOfFirst { (it.built[id] ?: 0f) > 0f }
        assertTrue(firstStart(1) in 1 until firstFull(1))
        assertTrue("Bett erst nach dem Baum", firstStart(2) > firstFull(1))
        assertTrue(firstFull(2) > firstStart(2))
        for (id in listOf(1, 2)) {
            var last = 0f
            for (f in p.frames) {
                val v = f.built[id] ?: 0f
                assertTrue("Fortschritt sinkt: $v < $last", v >= last - 1e-6f)
                last = v
            }
            assertEquals(1f, p.frames.last().built[id] ?: 0f, 1e-6f)
        }
        val building = p.frames.filter { (it.built[1] ?: 0f) in 0.2f..0.8f }
        assertTrue(building.isNotEmpty())
        assertTrue("neben dem Baum", building.all { abs(it.x - tree.at.x) < 0.35f && abs(it.y - tree.at.y) < 0.03f })
        assertTrue(p.sounds.count { it.fx == Fx.KNOCK } >= 4)
        assertTrue(p.sounds.any { it.fx == Fx.SPARKLE })
        assertTrue("Funken beim Aufbau", p.frames.flatMap { it.particles }.any { it.kind == PK.SPARK })
    }

    @Test fun withoutItemsNothingChanges() {
        val p = Recorder.record(req(emptyList(), listOf(ScriptStep(ElfAction.WAVE, Pt(0.5f, 0.8f)))))
        assertTrue(p.frames.all { it.built.isEmpty() })
        assertFalse(p.sounds.any { it.fx == Fx.SNORE })
    }

    @Test fun elfLiesDownInTheBedAndSnores() {
        val p = Recorder.record(req(listOf(bed), listOf(ScriptStep(ElfAction.BED))))
        assertEquals(listOf("Ins Bett & schnarchen"), p.sceneNames)
        val lying = p.frames.filter { abs(abs(it.pose.spin) - 90f) < 1f && it.onItem == bed.id }
        assertTrue("liegt: ${lying.size}", lying.size > 100)
        assertTrue(lying.all { abs(it.x - bed.at.x) < 0.01f })
        assertTrue(lying.all { it.y < bed.at.y + 0.2f && it.y > bed.at.y - 0.3f })
        assertTrue("schnarcht mehrmals", p.sounds.count { it.fx == Fx.SNORE } >= 3)
        assertTrue("Z-Wölkchen", p.frames.flatMap { it.particles }.any { it.kind == PK.Z })
        val last = p.frames.last()
        assertTrue(abs(last.pose.spin) < 1f && last.onItem == -1)
    }

    @Test fun bedActionWithoutABedIsHarmless() {
        val p = Recorder.record(req(emptyList(), listOf(ScriptStep(ElfAction.BED))))
        assertTrue(p.scriptEndMs > 0L)
        assertFalse(p.sounds.any { it.fx == Fx.SNORE })
    }

    @Test fun bedSceneOnlyWhenABedExists() {
        val rng = kotlin.random.Random(1)
        val without = (0 until 80).map { Scenes.pick(null, null, null, rng, false, false).id }
        assertFalse("brav_bett" in without)
        val with = (0 until 500).map { Scenes.pick(null, null, null, rng, false, true).id }
        assertTrue("brav_bett" in with)
    }

    @Test fun itemsOfEveryTypeCanBeBuilt() {
        val all = SceneryType.values().mapIndexed { i, t -> SceneryItem(i + 1, t, Pt(0.05f + 0.055f * i, 0.85f), t.defaultScale) }
        // viele Gegenstände dauern lange: genug Zeit zum Aufbauen aller
        val p = Recorder.record(
            PerfRequest(
                Pt(0.5f, 0.8f), emptyList(), 0.3f, null, null, 4L, 300_000L, null, settings,
                script = listOf(ScriptStep(ElfAction.WAVE, Pt(0.5f, 0.8f))), stopAfterScript = true, aspect = 0.56f, scenery = all,
            ),
        )
        for (it in all) assertEquals(it.type.id, 1f, p.frames.last().built[it.id] ?: 0f, 1e-6f)
    }

    // ---------------------------------------------------------------- „Psst“ nicht buchstabieren

    @Test fun hissWordsBecomeASoundInsteadOfLetters() {
        val a = SpeechText.prepare("Psst, Max! Ich bin Pippo und wohne heimlich hier.")
        assertTrue(a.hiss)
        assertEquals("Max! Ich bin Pippo und wohne heimlich hier.", a.text)
        val b = SpeechText.prepare("Psst, ein Geschenk ist unterwegs.")
        assertTrue(b.hiss)
        assertEquals("ein Geschenk ist unterwegs.", b.text)
        val c = SpeechText.prepare("Pssssst! Leise.")
        assertTrue(c.hiss)
        assertEquals("Leise.", c.text)
        val d = SpeechText.prepare("Hallo Max!")
        assertFalse(d.hiss)
        assertEquals("Hallo Max!", d.text)
        assertEquals("Das Psychologie-Buch steht auf der Post.", SpeechText.prepare("Das Psychologie-Buch steht auf der Post.").text)
        assertEquals("mir ist kalt.", SpeechText.prepare("Brrr, mir ist kalt.").text)
        assertEquals("ich weiß nicht.", SpeechText.prepare("Hmmm, ich weiß nicht.").text)
    }

    @Test fun spokenTextNeverContainsPsst() {
        val p = Recorder.record(req(emptyList(), listOf(ScriptStep(ElfAction.WAVE, Pt(0.5f, 0.8f)))))
        assertTrue(p.speech.none { Regex("(?i)\\bp+s+t+\\b").containsMatchIn(it.text) })
        assertTrue(p.sounds.filter { it.fx == Fx.PSST }.all { it.gain <= 0.4f })
    }
}
