package app.wichtelein

import app.wichtelein.anim.*
import app.wichtelein.scene.ElfinLines
import app.wichtelein.scene.Lines
import app.wichtelein.scene.fill
import app.wichtelein.settings.*
import app.wichtelein.ui.EditOps
import app.wichtelein.ui.EditState
import app.wichtelein.ui.Sel
import java.io.File
import kotlin.math.abs
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.9.3: eigene Texte der Elfin, Elfin-Farben, eigener Startpunkt, gemeinsame Aktionen, Treffer bei Drehung, WebP-Bilder. */
class FinishTest {
    private val base = Settings(elfName = "Pippo", elfinName = "Elfi").withProfile("Max")

    private fun pools(b: app.wichtelein.scene.LineBook) = listOf(
        b.greet, b.tidy, b.tidyDone, b.chaos, b.dance, b.tumble, b.dizzy, b.wave, b.sleep, b.wake, b.snack, b.hide, b.hideFound, b.doorHome,
        b.doorExit, b.doorKnock, b.doorWelcome, b.sit, b.trip, b.tripEmbarrassed, b.gift, b.giftPlaced, b.giftPop, b.snowOn, b.snowLike,
        b.snowOff, b.magic, b.surprised, b.bow, b.sneaky, b.general, b.chat, b.build, b.buildDone, b.noBed, b.bedNight, b.bedMorning,
    )

    @Test fun elfinHasHerOwnCompleteTexts() {
        val e = pools(ElfinLines)
        assertEquals(pools(Lines).size, e.size)
        assertTrue(e.all { it.isNotEmpty() })
        assertTrue(ElfinLines.general.size >= 30)
        // keine Wichtel-Wörter (nur der Platzhalter {wichtel} für ihren Namen)
        for (line in e.flatten()) assertFalse("Wichtel-Wort in: $line", line.replace("{wichtel}", "").contains("Wichtel"))
        // nur bekannte Platzhalter
        for (line in e.flatten()) for (m in Regex("\\{(\\w+)\\}").findAll(line)) assertTrue(line, m.groupValues[1] in setOf("name", "kind", "wichtel", "item"))
        // kein Satz stimmt mit einem Wichtelsatz überein
        val wichtel = pools(Lines).flatten().toSet()
        for (line in e.flatten()) assertFalse("gleicher Satz wie beim Wichtel: $line", line in wichtel)
        assertTrue(ElfinLines.general.any { it.contains("Elfenstaub") } && ElfinLines.greet.any { it.contains("Elfin") })
    }

    @Test fun elfinOnlySpeaksHerOwnSentences() {
        val s = base.copy(cast = 1)
        val p = Recorder.record(PerfRequest(Pt(0.4f, 0.85f), emptyList(), 0.3f, null, null, 5L, 120_000L, null, s, aspect = 0.56f))
        val allowed = pools(ElfinLines).flatten().map { app.wichtelein.scene.SpeechText.prepare(fill(it, "Max", "Elfi")).text }.toSet()
        assertTrue(p.speech.size > 5)
        for (ev in p.speech) assertTrue("fremder Satz: ${ev.text}", ev.text in allowed)
        val w = Recorder.record(PerfRequest(Pt(0.4f, 0.85f), emptyList(), 0.3f, null, null, 5L, 120_000L, null, base, aspect = 0.56f))
        val wichtelAllowed = pools(Lines).flatten().map { app.wichtelein.scene.SpeechText.prepare(fill(it, "Max", "Pippo")).text }.toSet()
        for (ev in w.speech) assertTrue("fremder Satz beim Wichtel: ${ev.text}", ev.text in wichtelAllowed)
    }

    @Test fun elfinUsesOnlyHerOwnCustomLines() {
        val s = base.copy(cast = 1, customLines = "Wichtelsatz", elfinCustomLines = "Elfensatz")
        val p = Recorder.record(PerfRequest(Pt(0.4f, 0.85f), emptyList(), 0.3f, null, null, 5L, 200_000L, null, s, aspect = 0.56f))
        assertTrue(p.speech.none { it.text == "Wichtelsatz" })
        assertTrue(p.speech.any { it.text == "Elfensatz" })
        assertEquals(listOf("Elfensatz"), s.customListFor(1))
        assertEquals(listOf("Wichtelsatz"), s.customListFor(0))
    }

    @Test fun bothFiguresUseTheirOwnTextsInOneFilm() {
        val s = base.copy(cast = 2)
        val p = Recorder.record(PerfRequest(Pt(0.3f, 0.85f), emptyList(), 0.3f, null, null, 9L, 120_000L, null, s, aspect = 0.56f))
        val e = pools(ElfinLines).flatten().map { app.wichtelein.scene.SpeechText.prepare(fill(it, "Max", "Elfi")).text }.toSet()
        val w = pools(Lines).flatten().map { app.wichtelein.scene.SpeechText.prepare(fill(it, "Max", "Pippo")).text }.toSet()
        assertTrue(p.speech.any { it.actor == 0 } && p.speech.any { it.actor == 1 })
        for (ev in p.speech) assertTrue(ev.text, if (ev.actor == 1) ev.text in e else ev.text in w)
    }

    @Test fun elfinHasOwnColorsIndependentOfTheWichtel() {
        val s = base.copy(hatColor = 5, elfinHatColor = 7, elfinJacketColor = 3)
        assertEquals(5, s.colorsFor(0).hat)
        assertEquals(7, s.colorsFor(1).hat)
        assertEquals(3, s.colorsFor(1).jacket)
        assertEquals(0, s.colorsFor(0).jacket)
        assertEquals(0, Settings().colorsFor(1).hat)
        assertEquals(ElfPalette.clothes.lastIndex, base.copy(elfinHatColor = 99).colorsFor(1).hat)
    }

    @Test fun elfinStartsAtHerOwnPoint() {
        val s = base.copy(cast = 2)
        val p = Recorder.record(
            PerfRequest(Pt(0.2f, 0.85f), emptyList(), 0.3f, null, null, 3L, 4_000L, null, s, aspect = 0.56f, start2 = Pt(0.8f, 0.7f)),
        )
        val f = p.frames.first().partner!!
        assertEquals(0.8f, f.x, 0.02f)
        assertEquals(0.7f, f.y, 0.03f)
        assertEquals(0.2f, p.frames.first().x, 0.02f)
    }

    @Test fun editorStartTwoCanBeMovedAndRemoved() {
        var st = EditState(start = Pt(0.3f, 0.8f), start2 = Pt(0.6f, 0.8f))
        assertTrue(EditOps.contains(st, Sel.Start2))
        st = EditOps.move(st, Sel.Start2, Pt(0.7f, 0.75f))
        assertEquals(Pt(0.7f, 0.75f), st.start2)
        assertEquals(Pt(0.3f, 0.8f), st.start)
        assertEquals(Sel.Start2, EditOps.hit(st, 0.7f, 0.75f, 0.04f, 0.03f, { Pair(0.1f, 0.1f) }, null))
        assertEquals(Pt(0.7f, 0.75f), EditOps.position(st, Sel.Start2))
        st = EditOps.remove(st, Sel.Start2)
        assertEquals(null, st.start2)
    }

    @Test fun jointActionsBringBothTogether() {
        val steps = listOf(ScriptStep(ElfAction.WAVE, Pt(0.3f, 0.85f), who = 2), ScriptStep(ElfAction.DANCE, Pt(0.5f, 0.85f), who = 2))
        val s = base.copy(cast = 2)
        val p = Recorder.record(
            PerfRequest(Pt(0.1f, 0.85f), emptyList(), 0.3f, null, null, 3L, 90_000L, null, s, script = steps, stopAfterScript = true, aspect = 0.56f, start2 = Pt(0.9f, 0.85f)),
        )
        val last = p.frames.last()
        assertEquals("Wichtel am Punkt", 0.5f, last.x, 0.06f)
        assertEquals("Elfin daneben", 0.59f, last.partner!!.x, 0.06f)
        // beide winken gleichzeitig (gemeinsame Aktion wird abgestimmt)
        assertTrue("winken gleichzeitig", p.frames.any { it.pose.armR > 100f && (it.partner?.pose?.armR ?: 0f) > 100f })
        // beide tanzen zusammen
        assertTrue(p.sceneNames.count { it == "Winken" } == 1 && p.sceneNames.count { it == "Tanzen" } == 1)
    }

    @Test fun jointActionIsPlainWhenOnlyOneFigureAppears() {
        val steps = listOf(ScriptStep(ElfAction.WAVE, Pt(0.3f, 0.85f), who = 2))
        val p = Recorder.record(PerfRequest(Pt(0.1f, 0.85f), emptyList(), 0.3f, null, null, 3L, 40_000L, null, base, script = steps, stopAfterScript = true, aspect = 0.56f))
        assertEquals(0.3f, p.frames.last().x, 0.06f)
        assertTrue(p.frames.all { it.partner == null })
    }

    @Test fun tapOnARotatedItemUsesItsRotatedShape() {
        val tree = SceneryItem(1, SceneryType.TREE_CLASSIC, Pt(0.5f, 0.8f), 0.3f, rot = 90f)
        val st = EditState(scenery = listOf(tree), nextId = 2)
        val box = { _: SceneryItem -> Pair(0.1f, 0.3f) } // schmal und hoch
        // gedreht um 90° im Uhrzeigersinn: der Baum liegt nach rechts
        assertEquals(Sel.Item(1), EditOps.hit(st, 0.7f, 0.8f, 0.01f, 0.01f, box, null, aspect = 1f))
        assertEquals(null, EditOps.hit(st, 0.5f, 0.6f, 0.01f, 0.01f, box, null, aspect = 1f))
        // ungedreht ist es umgekehrt
        val upright = EditState(scenery = listOf(tree.copy(rot = 0f)), nextId = 2)
        assertEquals(Sel.Item(1), EditOps.hit(upright, 0.5f, 0.6f, 0.01f, 0.01f, box, null, aspect = 1f))
        assertEquals(null, EditOps.hit(upright, 0.7f, 0.8f, 0.01f, 0.01f, box, null, aspect = 1f))
    }

    @Test fun tapOnARotatedDoorUsesItsRotatedShape() {
        val st = EditState(door = Pt(0.5f, 0.8f), doorRot = 90f)
        val box = Pair(0.1f, 0.3f)
        assertEquals(Sel.Door, EditOps.hit(st, 0.7f, 0.8f, 0.01f, 0.01f, { Pair(0f, 0f) }, box, aspect = 1f))
        assertEquals(null, EditOps.hit(st, 0.5f, 0.6f, 0.01f, 0.01f, { Pair(0f, 0f) }, box, aspect = 1f))
    }

    @Test fun threeDFramesAreWebpAndComplete() {
        for ((dir, min) in listOf("elf3d" to 250, "elfin3d" to 250)) {
            val d = File("src/main/assets/$dir")
            val meta = JSONObject(File(d, "elf3d.json").readText())
            val clips = meta.getJSONObject("clips")
            var expected = 0
            for (k in clips.keys()) {
                val c = clips.getJSONObject(k)
                for (h in 0 until c.getInt("headings")) for (f in 0 until c.getInt("frames")) {
                    expected++
                    assertTrue("fehlt: $dir/${k}_%02d_%02d.webp".format(h, f), File(d, "${k}_%02d_%02d.webp".format(h, f)).isFile)
                }
            }
            assertTrue(expected >= min)
            assertTrue("PNG-Reste in $dir", d.listFiles { f -> f.extension == "png" }!!.isEmpty())
            val total = d.listFiles { f -> f.extension == "webp" }!!.sumOf { it.length() }
            assertTrue("$dir zu groß: ${total / 1_000_000} MB", total < 14_000_000L)
        }
        assertNotNull(Elf3DLayout.fileName(FrameChoice("idle", 0, 0), "elfin3d"))
    }
}
