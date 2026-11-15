package app.wichtelein

import android.graphics.Bitmap
import android.graphics.Color as AColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Der 3D-Wichtel: alle vorgerenderten Bilder sind da, er lässt sich aus allen Richtungen zeichnen und umfärben. */
@RunWith(AndroidJUnit4::class)
class Figure3DRenderTest {
    @get:Rule val rule = createComposeRule()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun count(bmp: Bitmap, match: (Float, Float, Float) -> Boolean): Int {
        val hsv = FloatArray(3)
        var n = 0
        for (y in 0 until bmp.height step 2) for (x in 0 until bmp.width step 2) {
            val c = bmp.getPixel(x, y)
            if (AColor.alpha(c) < 200) continue
            AColor.colorToHSV(c, hsv)
            if (match(hsv[0], hsv[1], hsv[2])) n++
        }
        return n
    }

    @Test fun everyRenderedFrameIsPresentAndVisible() {
        assertTrue("3D-Bilder fehlen in den Assets", Elf3D.available(ctx))
        val f = Elf3D(ctx)
        var frames = 0
        for ((name, c) in f.layout.clips) {
            for (h in 0 until c.headings) for (k in 0 until c.frames) {
                val bmp = f.image(FrameChoice(name, h, k)).asAndroidBitmap()
                assertEquals("Breite $name $h $k", f.layout.cellW, bmp.width)
                assertEquals("Höhe $name $h $k", f.layout.cellH, bmp.height)
                var opaque = 0
                for (y in 0 until bmp.height step 4) for (x in 0 until bmp.width step 4) if (AColor.alpha(bmp.getPixel(x, y)) > 200) opaque++
                val total = (bmp.width / 4 + 1) * (bmp.height / 4 + 1)
                assertTrue("$name $h $k fast leer: $opaque von $total", opaque > total / 25)
                frames++
            }
        }
        assertTrue("zu wenige Bilder: $frames", frames >= 200)
        assertTrue("Laufen, Fegen, Winken, Stehen vorhanden", f.layout.clips.keys.containsAll(listOf("walk", "sweep", "wave", "idle")))
        assertTrue(f.layout.clips.getValue("walk").headings >= 8)
    }

    @Test fun rendersAllDirectionsAndRecolors() {
        val base = Elf3D(ctx)
        val blue = Elf3D(ctx, ElfColors(hat = 5, jacket = 1))
        val front = FrameChoice("idle", 0, 0)
        val hat = { h: Float, s: Float, v: Float -> Recolor.isHatRed(h, s, v) }
        val blueish = { h: Float, s: Float, v: Float -> h in 180f..260f && s > 0.3f && v > 0.2f }
        val baseHat = count(base.image(front).asAndroidBitmap(), hat)
        assertTrue("Mütze nicht rot genug: $baseHat", baseHat > 1500)
        val blueBmp = blue.image(front).asAndroidBitmap()
        assertTrue("Mütze noch rot", count(blueBmp, hat) < baseHat / 10)
        assertTrue("Mütze nicht blau", count(blueBmp, blueish) > baseHat / 2)

        // Drehteller: vier Richtungen beim Laufen, vier beim Fegen
        val poses = listOf(
            Pose().apply { legL = 25f; legR = -25f; stepPhase = 1.5f; heading = 0f },
            Pose().apply { legL = 25f; legR = -25f; stepPhase = 1.5f; heading = 90f },
            Pose().apply { legL = 25f; legR = -25f; stepPhase = 1.5f; heading = 180f },
            Pose().apply { legL = 25f; legR = -25f; stepPhase = 1.5f; heading = 270f },
            Pose().apply { prop = Prop.BROOM; armR = 28f; heading = 90f },
            Pose().apply { prop = Prop.BROOM; armR = 28f; heading = 270f },
            Pose().apply { armR = 150f; heading = 0f },
            Pose().apply { heading = 45f },
        )
        rule.setContent {
            Column(Modifier.size(300.dp, 300.dp).background(Color(0xFFE8DCC8))) {
                for (row in 0..1) Row {
                    for (col in 0..3) {
                        val i = row * 4 + col
                        Canvas(Modifier.size(75.dp, 150.dp)) {
                            base.draw(this, poses[i], Offset(size.width / 2f, size.height * 0.96f), size.height * 0.85f, 1)
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val b = rule.onAllNodes(isRoot()).onFirst().captureToImage().asAndroidBitmap()
        File(File(ctx.filesDir, "shots").apply { mkdirs() }, "figure3d.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(b.width > 100)
        // Jede der acht Zellen enthält einen Wichtel (rote Mütze)
        for (i in 0 until 8) {
            val cw = b.width / 4
            val ch = b.height / 2
            val cell = Bitmap.createBitmap(b, (i % 4) * cw, (i / 4) * ch, cw, ch)
            assertTrue("Zelle $i ohne Wichtel", count(cell) { h, s, v -> Recolor.isHatRed(h, s, v) } > 30)
        }
    }
}
