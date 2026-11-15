package app.wichtelein

import android.graphics.Bitmap
import android.graphics.Color as AColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.wichtelein.anim.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Einstellbare Farbe und Licht der Gegenstände: Farbton verschiebt, „ohne Licht“ dunkelt und entfernt die Leuchtpunkte. */
@RunWith(AndroidJUnit4::class)
class SceneryStyleRenderTest {
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun brightNeon(b: Bitmap): Int {
        val hsv = FloatArray(3)
        var n = 0
        for (y in 0 until b.height step 3) for (x in 0 until b.width step 3) {
            val c = b.getPixel(x, y)
            if (AColor.alpha(c) < 200) continue
            AColor.colorToHSV(c, hsv)
            if (hsv[2] > 0.85f && hsv[1] > 0.3f) n++
        }
        return n
    }

    private fun meanHue(b: Bitmap): Float {
        val hsv = FloatArray(3)
        var sx = 0.0
        var sy = 0.0
        var n = 0
        for (y in 0 until b.height step 3) for (x in 0 until b.width step 3) {
            val c = b.getPixel(x, y)
            if (AColor.alpha(c) < 200) continue
            AColor.colorToHSV(c, hsv)
            if (hsv[1] < 0.5f || hsv[2] < 0.5f) continue
            sx += Math.cos(Math.toRadians(hsv[0].toDouble()))
            sy += Math.sin(Math.toRadians(hsv[0].toDouble()))
            n++
        }
        assertTrue("keine kräftigen Farben", n > 50)
        return ((Math.toDegrees(Math.atan2(sy, sx)) + 360) % 360).toFloat()
    }

    @Test fun hueShiftChangesTheColors() {
        val base = SceneryAssets.load(ctx).art(SceneryType.FENCE)!!.image.asAndroidBitmap()
        val shifted = SceneryAssets.load(ctx, mapOf("fence" to SceneryStyle(hue = 120))).art(SceneryType.FENCE)!!.image.asAndroidBitmap()
        val h0 = meanHue(base)
        val h1 = meanHue(shifted)
        val d = Math.abs(((h1 - h0 + 540f) % 360f) - 180f)
        assertTrue("Farbton $h0 -> $h1", d in 80f..160f)
    }

    @Test fun withoutLightsTheNeonIsDimmedAndTheGlowPointsAreGone() {
        val on = SceneryAssets.load(ctx).art(SceneryType.TREE)!!
        val off = SceneryAssets.load(ctx, mapOf("tree" to SceneryStyle(lights = false))).art(SceneryType.TREE)!!
        assertTrue(on.meta.lights.isNotEmpty())
        assertTrue(off.meta.lights.isEmpty())
        assertTrue(off.meta.groundGlow == null)
        val a = brightNeon(on.image.asAndroidBitmap())
        val b = brightNeon(off.image.asAndroidBitmap())
        assertTrue("Leuchtflächen: mit Licht $a, ohne $b", a > 30 && b < a / 3)
    }

    @Test fun lightColorOverridesEveryLight() {
        val art = SceneryAssets.load(ctx, mapOf("lights" to SceneryStyle(lightColor = 2))).art(SceneryType.LIGHTS)
        assertNotNull(art)
        val magenta = SceneryPalette.lightColors[2].second!!
        assertTrue(art!!.meta.lights.isNotEmpty() && art.meta.lights.all { it.color == magenta })
        // ohne Auswahl bleiben die bunten Originalfarben
        val orig = SceneryAssets.load(ctx).art(SceneryType.LIGHTS)!!
        assertTrue(orig.meta.lights.map { it.color }.toSet().size > 2)
        assertEquals(Color(0xFFFF1AA6), magenta)
    }
}
