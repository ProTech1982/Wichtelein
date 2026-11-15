package app.wichtelein

import app.wichtelein.anim.SceneryPalette
import app.wichtelein.anim.SceneryStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Farbe und Licht je Gegenstand (Einstellungen). */
class SceneryStyleTest {
    @Test fun stylesSurviveSavingAndLoading() {
        val styles = mapOf(
            "tree" to SceneryStyle(hue = 120, lights = true, lightColor = 3),
            "bed" to SceneryStyle(hue = -1, lights = false, lightColor = -1),
            "fence" to SceneryStyle(), // Standard wird nicht gespeichert
        )
        val text = SceneryPalette.encode(styles)
        assertTrue(text, !text.contains("fence"))
        val back = SceneryPalette.decode(text)
        assertEquals(SceneryStyle(120, true, 3), back["tree"])
        assertEquals(SceneryStyle(-1, false, -1), back["bed"])
        assertEquals(2, back.size)
    }

    @Test fun brokenSettingsTextIsIgnored() {
        assertTrue(SceneryPalette.decode(null).isEmpty())
        assertTrue(SceneryPalette.decode("").isEmpty())
        assertTrue(SceneryPalette.decode("unsinn").isEmpty())
        val m = SceneryPalette.decode("tree|999|1|99;lamp|x|1|1;sign|10|0|2")
        assertEquals(359, m["tree"]!!.hue) // auf den erlaubten Bereich begrenzt
        assertEquals(SceneryPalette.lightColors.lastIndex, m["tree"]!!.lightColor)
        assertTrue("lamp" !in m)
        assertEquals(SceneryStyle(10, false, 2), m["sign"])
    }

    @Test fun lightColorListStartsWithTheOriginal() {
        assertEquals("Original", SceneryPalette.lightColors.first().first)
        assertTrue(SceneryPalette.lightColors.first().second == null)
        assertTrue(SceneryPalette.lightColors.drop(1).all { it.second != null })
    }
}
