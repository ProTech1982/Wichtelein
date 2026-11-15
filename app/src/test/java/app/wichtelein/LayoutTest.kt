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
         "hand":[890,817],"propK":1.3,"bootRow":120,"skin":"#EDA987","shadowW":0.5,
         "broom":{"h":300,"aspect":0.3,"gripFromBottom":0.4,"handleX":0.35}}
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
        assertEquals(0.5f, l.shadowW, 1e-6f)
        assertEquals(300f, l.broom.h, 0f)
        assertEquals(0.4f, l.broom.gripFromBottom, 1e-6f)
    }

    /** Das mitgelieferte Layout des Wichtels (aus dem Referenzbild geschnitten) ist stimmig. */
    @Test fun shippedLayoutIsConsistent() {
        val l = ElfLayout.fromJson(java.io.File("src/main/res/raw/elf_layout.json").readText())
        assertEquals(596f, l.height, 0f)
        assertEquals(658f, l.feet.y, 0f)
        assertTrue("Mütze beginnt ganz oben", l.head.y in 55f..75f)
        assertTrue("Stiefel beginnen unterhalb der Hosenoberkante", l.bootRow > 20)
        assertTrue("Gelenke liegen am Körper", l.pivArmL.x < l.pivHead.x && l.pivArmR.x > l.pivHead.x)
        assertTrue("keine sichtbaren Augen/Mund (Bart verdeckt sie)", l.eyes.isEmpty() && l.mouthW == 0f)
        assertTrue("Besen ist hochkant", l.broom.aspect in 0.2f..0.5f)
        assertTrue(l.broom.gripFromBottom in 0.2f..0.5f && l.broom.handleX in 0.1f..0.9f)
        for (n in listOf("head", "torso", "arml", "armr", "legl", "legr", "prop_broom")) {
            val f = java.io.File("src/main/res/drawable-nodpi/" + (if (n.startsWith("prop")) n else "elf_$n") + ".png")
            assertTrue("Bild fehlt: $f", f.exists() && f.length() > 500)
        }
    }

    @Test fun colorChoicesAreConsistent() {
        assertTrue(ElfPalette.clothes.first().hue == null && ElfPalette.boots.first().hue == null)
        assertTrue(ElfPalette.clothes.size >= 8)
        assertEquals(0f, Recolor.hueDiff(10f, 10f), 0f)
        assertEquals(20f, Recolor.hueDiff(10f, 350f), 1e-4f)
        assertEquals(-20f, Recolor.hueDiff(350f, 10f), 1e-4f)
        // Mantel grün, Mütze kräftig rot, Nase/Wangen (blasser) bleiben, Hose und Stiefel braun
        assertTrue(Recolor.isCoat(165f, 0.45f, 0.35f))
        assertTrue(!Recolor.isCoat(20f, 0.8f, 0.5f))
        assertTrue(Recolor.isHatRed(0f, 0.8f, 1f))
        assertTrue(Recolor.isHatRed(356f, 0.9f, 0.7f))
        assertTrue("Wangen dürfen nicht umgefärbt werden", !Recolor.isHatRed(0f, 0.51f, 0.86f))
        assertTrue("Nase darf nicht umgefärbt werden", !Recolor.isHatRed(26f, 0.26f, 1f))
        assertTrue(Recolor.isTrousers(36f, 0.36f, 0.55f))
        assertTrue(Recolor.isBoot(24f, 0.61f, 0.44f))
    }
}
