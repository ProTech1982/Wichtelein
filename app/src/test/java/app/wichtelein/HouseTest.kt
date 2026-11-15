package app.wichtelein

import app.wichtelein.anim.*
import app.wichtelein.scene.Scenes
import app.wichtelein.settings.*
import app.wichtelein.sound.Fx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Ins Haus gehen und drinnen bleiben, später wieder herauskommen. */
class HouseTest {
    private val settings = Settings(elfName = "Pippo", figure3d = true).withProfile("Max")
    private val door = Pt(0.62f, 0.6f)

    private fun record(steps: List<ScriptStep>) = Recorder.record(
        PerfRequest(Pt(0.2f, 0.85f), emptyList(), 0.3f, null, null, 3L, 120_000L, null, settings, door = door, script = steps, stopAfterScript = true, aspect = 0.56f),
    )

    @Test fun goingInsideLeavesTheElfInTheHouseUntilTheEnd() {
        val p = record(listOf(ScriptStep(ElfAction.HOUSE_IN)))
        assertEquals(listOf("Ins Haus gehen & drinnen bleiben"), p.sceneNames)
        // geht zur Tür, öffnet sie, geht hinein (wird dabei kleiner, aber nur bis etwa die Hälfte)
        val entering = p.frames.filter { it.doorOpen > 0.95f && it.pose.scale in 0.46f..0.99f && abs(it.x - door.x) < 0.2f }
        assertTrue("Hineingehen sichtbar", entering.isNotEmpty())
        assertTrue("mit dem Rücken zum Betrachter", entering.any { abs(((it.pose.heading - 180f + 540f) % 360f) - 180f) < 10f })
        val sizes = entering.map { it.pose.scale }
        assertTrue("wird beim Hineingehen kleiner", sizes.first() > sizes.last())
        // drinnen: klein, zum Betrachter gedreht, bei offener Tür
        val inside = p.frames.filter { it.doorOpen > 0.95f && abs(it.pose.scale - 0.45f) < 0.02f }
        assertTrue(inside.isNotEmpty())
        assertTrue("drinnen zum Betrachter gedreht", inside.any { it.pose.heading < 10f || it.pose.heading > 350f })
        // danach geschlossen und unsichtbar bis zum Ende
        val last = p.frames.last()
        assertTrue("Tür zu", last.doorOpen < 0.05f)
        assertTrue("bleibt drinnen", last.pose.scale < 0.02f)
        val hiddenFrom = p.frames.indexOfFirst { it.pose.scale < 0.02f && it.doorOpen < 0.05f && p.frames.indexOf(it) > 60 }
        assertTrue(hiddenFrom > 0)
        assertTrue(p.frames.drop(hiddenFrom).all { it.pose.scale < 0.02f })
        assertTrue(p.sounds.count { it.fx == Fx.DOOR_OPEN } == 1 && p.sounds.count { it.fx == Fx.DOOR_CLOSE } == 1)
    }

    @Test fun comingOutAgainEndsOutside() {
        val p = record(listOf(ScriptStep(ElfAction.HOUSE_IN), ScriptStep(ElfAction.HOUSE_OUT)))
        val last = p.frames.last()
        assertTrue("wieder draußen", last.pose.scale > 0.95f && last.visible)
        assertTrue(p.sounds.count { it.fx == Fx.DOOR_OPEN } == 2 && p.sounds.count { it.fx == Fx.DOOR_CLOSE } == 2)
        // war zwischendurch wirklich drinnen
        assertTrue(p.frames.any { it.pose.scale < 0.02f && it.doorOpen < 0.05f && p.frames.indexOf(it) > 60 })
        // beim Herauskommen wird er größer und blickt zum Betrachter
        val out = p.frames.filter { it.doorOpen > 0.95f && it.pose.scale in 0.5f..0.95f && it.y > door.y - 0.01f }
        assertTrue(out.isNotEmpty())
    }

    @Test fun anotherActionAfterGoingInsideBringsTheElfOutFirst() {
        val p = record(listOf(ScriptStep(ElfAction.HOUSE_IN), ScriptStep(ElfAction.DANCE, Pt(0.25f, 0.85f))))
        assertEquals(listOf("Ins Haus gehen & drinnen bleiben", "Tanzen"), p.sceneNames)
        val last = p.frames.last()
        assertTrue(last.pose.scale > 0.95f)
        assertEquals(0.25f, last.x, 0.05f)
    }

    @Test fun houseOutWithoutBeingInsideIsHarmless() {
        val p = record(listOf(ScriptStep(ElfAction.HOUSE_OUT)))
        assertTrue(p.scriptEndMs > 0L)
        assertTrue(p.frames.last().pose.scale > 0.95f)
    }

    @Test fun houseInTwiceDoesNothingTheSecondTime() {
        val p = record(listOf(ScriptStep(ElfAction.HOUSE_IN), ScriptStep(ElfAction.HOUSE_IN)))
        assertTrue(p.sounds.count { it.fx == Fx.DOOR_OPEN } == 1)
        assertTrue(p.frames.last().pose.scale < 0.02f)
    }

    @Test fun stayAtHomeSceneNeedsADoorAndEndsOutside() {
        assertTrue(Scenes.find("brav_zuhause")!!.needsDoor)
        val p = Recorder.record(
            PerfRequest(Pt(0.3f, 0.8f), emptyList(), 0.3f, null, "brav_zuhause", 5L, 40_000L, null, settings, door = door, aspect = 0.56f),
        )
        assertTrue("war drinnen", p.frames.any { it.pose.scale < 0.02f && it.doorOpen < 0.05f && p.frames.indexOf(it) > 60 })
        assertFalse("kommt wieder heraus", p.frames.drop(p.frames.indexOfFirst { it.pose.scale < 0.02f && p.frames.indexOf(it) > 60 }).all { it.pose.scale < 0.02f })
    }
}
