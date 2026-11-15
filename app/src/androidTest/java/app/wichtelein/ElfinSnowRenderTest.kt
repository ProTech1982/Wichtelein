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

    /** Zelle Nummer [i] des Bildes (4 Zellen je Zeile, je 50 x 100 dp) */
    private fun cell(i: Int): android.graphics.Rect {
        val d = ctx.resources.displayMetrics.density
        val w = (50 * d).toInt()
        val h = (100 * d).toInt()
        return android.graphics.Rect((i % 4) * w, (i / 4) * h, (i % 4 + 1) * w, (i / 4 + 1) * h)
    }

    private fun count(b: Bitmap, i: Int, match: (Float, Float, Float) -> Boolean): Int {
        val hsv = FloatArray(3)
        var n = 0
        val r = cell(i)
        for (y in r.top until r.bottom step 2) for (x in r.left until r.right step 2) {
            val c = b.getPixel(x, y)
            AColor.colorToHSV(c, hsv)
            if (match(hsv[0], hsv[1], hsv[2])) n++
        }
        return n
    }

    private fun isBackground(c: Int) = kotlin.math.abs(AColor.red(c) - 0x8F) + kotlin.math.abs(AColor.green(c) - 0xA6) + kotlin.math.abs(AColor.blue(c) - 0xB8) < 30

    @Test fun elfinDrawsInAllPosesAndColors() {
        val settings = Settings(cast = 1)
        val figure = ElfFigures.load(ctx, settings)
        assertTrue("Elfin-Figur gewählt", figure is ElfinFigure)
        val blue = ElfFigures.load(ctx, settings.copy(elfinHatColor = 5, elfinJacketColor = 6, elfinLegColor = 8))
        val poses = listOf(
            Pose(),
            Pose().apply { armR = 150f; armL = 20f },
            Pose().apply { prop = Prop.BROOM; armR = 40f },
            Pose().apply { eyesClosed = true; mouth = 0.8f; legL = 25f; legR = -25f },
            Pose().apply { prop = Prop.GIFT; armR = 80f },
        )
        rule.setContent {
            androidx.compose.foundation.layout.Column(Modifier.background(bg)) {
                Row {
                    for (p in poses.take(4)) Canvas(Modifier.size(50.dp, 100.dp)) { figure.draw(this, p, Offset(size.width / 2, size.height * 0.97f), size.height * 0.9f, 1) }
                }
                Row {
                    Canvas(Modifier.size(50.dp, 100.dp)) { figure.draw(this, poses[4], Offset(size.width / 2, size.height * 0.97f), size.height * 0.9f, 1) }
                    Canvas(Modifier.size(50.dp, 100.dp)) { blue.draw(this, poses[0], Offset(size.width / 2, size.height * 0.97f), size.height * 0.9f, 1) }
                    Canvas(Modifier.size(50.dp, 100.dp)) { figure.draw(this, poses[1], Offset(size.width / 2, size.height * 0.97f), size.height * 0.9f, -1) }
                }
            }
        }
        rule.waitForIdle()
        val b = rule.onAllNodes(isRoot()).onFirst().captureToImage().asAndroidBitmap()
        File(File(ctx.filesDir, "shots").apply { mkdirs() }, "elfin.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        for (i in 0 until poses.size + 2) {
            var filled = 0
            val r = cell(i)
            for (y in r.top until r.bottom step 2) for (x in r.left until r.right step 2) if (!isBackground(b.getPixel(x, y))) filled++
            assertTrue("Elfin $i fast leer: $filled", filled > 400)
        }
        // Standard: rote Mütze und grünes Kleid; umgefärbt: blaue Mütze, lila Kleid
        val red = count(b, 0) { h, s, v -> (h < 15f || h > 345f) && s > 0.6f && v > 0.5f }
        val green = count(b, 0) { h, s, v -> h in 110f..160f && s > 0.5f && v > 0.4f }
        assertTrue("rote Mütze: $red", red > 25)
        assertTrue("grünes Kleid: $green", green > 40)
        val blueIdx = poses.size + 0
        val blueHat = count(b, blueIdx) { h, s, v -> h in 205f..235f && s > 0.5f && v > 0.5f }
        val violet = count(b, blueIdx) { h, s, v -> h in 255f..290f && s > 0.4f && v > 0.4f }
        assertTrue("blaue Mütze: $blueHat", blueHat > 20)
        assertTrue("lila Kleid: $violet", violet > 40)
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

    @Test fun threeDElfinIsCompleteAndSelectable() {
        assertTrue("3D-Elfin fehlt in den Assets", Elf3D.available(ctx, "elfin3d"))
        val s3 = Settings(cast = 1, figure3d = true)
        assertTrue("3D-Elfin gewählt", ElfFigures.load(ctx, s3) is Elf3D)
        assertTrue("2D-Elfin bleibt wählbar", ElfFigures.load(ctx, s3.copy(figure3d = false)) is ElfinFigure)
        val f = Elf3D(ctx, ElfColors(), "elfin3d")
        var frames = 0
        for ((name, c) in f.layout.clips) {
            for (h in 0 until c.headings) for (k in 0 until c.frames) {
                val bmp = f.image(FrameChoice(name, h, k)).asAndroidBitmap()
                assertTrue("Größe $name $h $k", bmp.width == f.layout.cellW && bmp.height == f.layout.cellH)
                var opaque = 0
                for (y in 0 until bmp.height step 4) for (x in 0 until bmp.width step 4) if (AColor.alpha(bmp.getPixel(x, y)) > 200) opaque++
                val total = (bmp.width / 4 + 1) * (bmp.height / 4 + 1)
                assertTrue("$name $h $k fast leer: $opaque von $total", opaque > total / 25)
                frames++
            }
        }
        assertTrue("alle Bilder da: $frames", frames >= 250)
        // Mütze lässt sich umfärben
        val red = Elf3D(ctx, ElfColors(), "elfin3d").image(FrameChoice("idle", 0, 0)).asAndroidBitmap()
        val blue = Elf3D(ctx, ElfColors(hat = 5), "elfin3d").image(FrameChoice("idle", 0, 0)).asAndroidBitmap()
        fun hatPixels(b: Bitmap, lo: Float, hi: Float): Int {
            val hsv = FloatArray(3)
            var n = 0
            for (y in 0 until b.height / 2 step 2) for (x in 0 until b.width step 2) {
                val c = b.getPixel(x, y)
                if (AColor.alpha(c) < 200) continue
                AColor.colorToHSV(c, hsv)
                if (hsv[0] in lo..hi && hsv[1] > 0.5f && hsv[2] > 0.4f) n++
            }
            return n
        }
        assertTrue("blaue Mütze nach Umfärben", hatPixels(blue, 200f, 240f) > 200 && hatPixels(red, 200f, 240f) < 50)
    }

    private fun frameAt(x: Float, doorRot: Float = 0f, door: Boolean = false) = FrameState(
        x = x, y = 0.85f, facing = 1, visible = true, pose = Pose(), bubble = null, mess = emptyList(), flying = null, particles = emptyList(),
        doorPos = if (door) Pt(0.5f, 0.86f) else null, doorScale = 0.5f, doorOpen = if (door) 0.65f else 0f, doorHingeLeft = true, doorRound = 1f, doorRot = doorRot,
    )

    @Test fun bothFiguresAreDrawnSideBySide() {
        val figure = ElfFigures.load(ctx, Settings(cast = 2))
        assertTrue("Besetzung mit zwei Figuren", figure is CastFigure && figure.partner != null)
        val f = frameAt(0.28f).also { it.partner = frameAt(0.72f) }
        rule.setContent { Row(Modifier.background(bg)) { androidx.compose.foundation.layout.Box(Modifier.size(150.dp, 260.dp)) { ElfOverlay(f, figure, 0.3f, Modifier.size(150.dp, 260.dp)) } } }
        rule.waitForIdle()
        val b = rule.onAllNodes(isRoot()).onFirst().captureToImage().asAndroidBitmap()
        File(File(ctx.filesDir, "shots").apply { mkdirs() }, "beide.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val d = ctx.resources.displayMetrics.density
        val x0 = 0
        val half = (75 * d).toInt()
        fun filled(from: Int, to: Int): Int {
            var n = 0
            for (y in 0 until (260 * d).toInt() step 2) for (x in from until to step 2) if (!isBackground(b.getPixel(x, y))) n++
            return n
        }
        val left = filled(x0, x0 + half)
        val right = filled(x0 + half, x0 + 2 * half)
        assertTrue("Wichtel links: $left", left > 150)
        assertTrue("Elfin rechts: $right", right > 150)
    }

    @Test fun rotatedDoorMovesItsPixels() {
        val figure = ElfFigures.load(ctx, Settings())
        rule.setContent {
            Row(Modifier.background(bg)) {
                for (rot in listOf(0f, 40f)) androidx.compose.foundation.layout.Box(Modifier.size(150.dp, 260.dp)) { ElfOverlay(frameAt(0.2f, rot, door = true), figure, 0.3f, Modifier.size(150.dp, 260.dp)) }
            }
        }
        rule.waitForIdle()
        val b = rule.onAllNodes(isRoot()).onFirst().captureToImage().asAndroidBitmap()
        File(File(ctx.filesDir, "shots").apply { mkdirs() }, "tuer-gedreht.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val d = ctx.resources.displayMetrics.density
        val w = (150 * d).toInt()
        fun centroidX(cell: Int): Float {
            var sum = 0f
            var n = 0
            for (y in 0 until (260 * d).toInt() step 2) for (x in cell * w + (w * 0.45f).toInt() until (cell + 1) * w step 2) {
                if (!isBackground(b.getPixel(x, y))) { sum += x - cell * w; n++ }
            }
            return if (n == 0) 0f else sum / n
        }
        val c0 = centroidX(0)
        val c1 = centroidX(1)
        assertTrue("Tür ist sichtbar", c0 > 0f && c1 > 0f)
        assertTrue("gedrehte Tür liegt anders: $c0 → $c1", kotlin.math.abs(c0 - c1) > 4f * d)
    }
}
