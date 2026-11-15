package app.wichtelein

import android.graphics.Bitmap
import android.graphics.Color as AColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.wichtelein.anim.*
import app.wichtelein.settings.Settings
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Die Elfin (Wichtelfrau) lässt sich in allen Haltungen zeichnen und umfärben; der Schneefall ist sichtbar und hängt von der Stärke ab. */
@RunWith(AndroidJUnit4::class)
class ElfinSnowRenderTest {
    @get:Rule val rule = createComposeRule()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val bg = Color(0xFF8FA6B8)

    private fun count(b: Bitmap, x0: Int, x1: Int, match: (Float, Float, Float) -> Boolean): Int {
        val hsv = FloatArray(3)
        var n = 0
        for (y in 0 until b.height step 2) for (x in x0 until x1 step 2) {
            val c = b.getPixel(x, y)
            AColor.colorToHSV(c, hsv)
            if (match(hsv[0], hsv[1], hsv[2])) n++
        }
        return n
    }

    private fun isBackground(c: Int) = kotlin.math.abs(AColor.red(c) - 0x8F) + kotlin.math.abs(AColor.green(c) - 0xA6) + kotlin.math.abs(AColor.blue(c) - 0xB8) < 30

    @Test fun elfinDrawsInAllPosesAndColors() {
        val settings = Settings(elfin = true)
        val figure = ElfFigures.load(ctx, settings)
        assertTrue("Elfin-Figur gewählt", figure is ElfinFigure)
        val blue = ElfFigures.load(ctx, settings.copy(hatColor = 5, jacketColor = 6, legColor = 8))
        val poses = listOf(
            Pose(),
            Pose().apply { armR = 150f; armL = 20f },
            Pose().apply { prop = Prop.BROOM; armR = 40f },
            Pose().apply { eyesClosed = true; mouth = 0.8f; legL = 25f; legR = -25f },
            Pose().apply { prop = Prop.GIFT; armR = 80f },
        )
        rule.setContent {
            Row(Modifier.background(bg)) {
                for (p in poses) Canvas(Modifier.size(50.dp, 100.dp)) { figure.draw(this, p, Offset(size.width / 2, size.height * 0.97f), size.height * 0.9f, 1) }
                Canvas(Modifier.size(50.dp, 100.dp)) { blue.draw(this, poses[0], Offset(size.width / 2, size.height * 0.97f), size.height * 0.9f, 1) }
                Canvas(Modifier.size(50.dp, 100.dp)) { figure.draw(this, poses[1], Offset(size.width / 2, size.height * 0.97f), size.height * 0.9f, -1) }
            }
        }
        rule.waitForIdle()
        val b = rule.onAllNodes(isRoot()).onFirst().captureToImage().asAndroidBitmap()
        File(File(ctx.filesDir, "shots").apply { mkdirs() }, "elfin.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val cw = (50 * ctx.resources.displayMetrics.density).toInt()
        for (i in 0 until poses.size + 2) {
            var filled = 0
            for (y in 0 until b.height step 2) for (x in i * cw until (i + 1) * cw step 2) if (!isBackground(b.getPixel(x, y))) filled++
            assertTrue("Elfin $i fast leer: $filled", filled > 400)
        }
        // Standard: rote Mütze und grünes Kleid; umgefärbt: blaue Mütze, lila Kleid
        val red = count(b, 0, cw) { h, s, v -> (h < 15f || h > 345f) && s > 0.6f && v > 0.5f }
        val green = count(b, 0, cw) { h, s, v -> h in 110f..160f && s > 0.5f && v > 0.4f }
        assertTrue("rote Mütze: $red", red > 80)
        assertTrue("grünes Kleid: $green", green > 300)
        val blueIdx = poses.size
        val blueHat = count(b, blueIdx * cw, (blueIdx + 1) * cw) { h, s, v -> h in 205f..235f && s > 0.5f && v > 0.5f }
        val violet = count(b, blueIdx * cw, (blueIdx + 1) * cw) { h, s, v -> h in 255f..290f && s > 0.4f && v > 0.4f }
        assertTrue("blaue Mütze: $blueHat", blueHat > 80)
        assertTrue("lila Kleid: $violet", violet > 300)
    }

    @Test fun snowIsVisibleAndGrowsWithLevel() {
        rule.setContent {
            Row(Modifier.background(Color(0xFF203040))) {
                for (level in listOf(0f, 0.3f, 1f)) Canvas(Modifier.size(100.dp, 160.dp)) { drawSnow(level, 7.3f) }
            }
        }
        rule.waitForIdle()
        val b = rule.onAllNodes(isRoot()).onFirst().captureToImage().asAndroidBitmap()
        File(File(ctx.filesDir, "shots").apply { mkdirs() }, "schnee.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val cw = (100 * ctx.resources.displayMetrics.density).toInt()
        fun white(i: Int): Int {
            var n = 0
            for (y in 0 until b.height) for (x in i * cw until (i + 1) * cw) {
                val c = b.getPixel(x, y)
                if (AColor.red(c) > 170 && AColor.green(c) > 170 && AColor.blue(c) > 170) n++
            }
            return n
        }
        val a = white(0)
        val m = white(1)
        val f = white(2)
        assertTrue("ohne Schnee nichts Weißes: $a", a < 20)
        assertTrue("mehr Schnee bei mehr Stärke: $m < $f", m in 20 until f)
        assertTrue("volle Stärke deutlich sichtbar: $f", f > 150)
    }
}
