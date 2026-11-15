package app.wichtelein

import app.wichtelein.anim.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutTest {
    private val json = """
        {"feet":[750,1100],"height":1000,
         "parts":{"head":[600,100],"torso":[640,500],"arml":[560,520],"armr":[860,520],"legl":[650,800],"legr":[760,800]},
         "piv":{"head":[750,560],"arml":[640,620],"armr":[860,620],"legl":[698,900],"legr":[802,900],"body":[750,700]},
         "eyes":[[703,468],[797,468]],"eyeW":70,"eyeH":60,"mouth":[750,550],"mouthW":80,
         "hand":[890,817],"propK":1.3,"bootRow":120,"skin":"#EDA987"}
    """

    @Test fun layoutParsesFromJson() {
        val l = ElfLayout.fromJson(json)
        assertEquals(750f, l.feet.x, 0f)
        assertEquals(1000f, l.height, 0f)
        assertEquals(600f, l.head.x, 0f)
        assertEquals(2, l.eyes.size)
        assertEquals(797f, l.eyes[1].x, 0f)
        assertEquals(120, l.bootRow)
        assertEquals(1.3f, l.propK, 1e-6f)
        assertEquals(0xED / 255f, l.skin.red, 1e-3f)
    }

    @Test fun photoLayoutMatchesTheOriginalImageMeasures() {
        val l = ElfLayout.PHOTO
        assertEquals(769f, l.height, 0f)
        assertEquals(800f, l.feet.y, 0f)
        assertEquals(110, l.bootRow)
        assertTrue(l.eyes.size == 2 && l.mouthW > 0f)
    }

    @Test fun colorChoicesAreConsistent() {
        assertTrue(ElfPalette.clothes.first().hue == null && ElfPalette.boots.first().hue == null)
        assertTrue(ElfPalette.clothes.size >= 8)
        assertEquals(0f, Recolor.hueDiff(10f, 10f), 0f)
        assertEquals(20f, Recolor.hueDiff(10f, 350f), 1e-4f)
        assertEquals(-20f, Recolor.hueDiff(350f, 10f), 1e-4f)
        assertTrue(Recolor.isGreen(120f, 0.8f, 0.5f))
        assertTrue(!Recolor.isGreen(20f, 0.8f, 0.5f))
        assertTrue(Recolor.isBootRed(8f, 0.7f, 0.4f))
    }
}
