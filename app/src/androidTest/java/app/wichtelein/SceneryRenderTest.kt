package app.wichtelein

import android.graphics.Bitmap
import android.graphics.Color as AColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Gegenstände fürs Bild: alle sechs sind in der App, mit stimmigen Lichtern, und lassen sich zeichnen. */
@RunWith(AndroidJUnit4::class)
class SceneryRenderTest {
    @get:Rule val rule = createComposeRule()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun everyObjectIsPresentWithConsistentMeta() {
        val assets = SceneryAssets.load(ctx)
        for (t in SceneryType.values()) {
            val art = assertNotNull_(assets.art(t), "Gegenstand fehlt: ${t.id}")
            val m = art.meta
            assertEquals(t.id, m.w, art.image.width)
            assertEquals(t.id, m.h, art.image.height)
            assertTrue("${t.id}: Fußpunkt im Bild", m.anchorX in 0f..m.w.toFloat() && m.anchorY in 0f..m.h.toFloat())
            assertTrue("${t.id}: Lichter im Bild", m.lights.all { it.x in 0f..m.w.toFloat() && it.y in 0f..m.h.toFloat() && it.r > 0f })
        }
        assertTrue("Baum und Kette haben Lichter", assets.art(SceneryType.TREE)!!.meta.lights.size >= 8 && assets.art(SceneryType.LIGHTS)!!.meta.lights.size >= 8)
    }

    private fun <T : Any> assertNotNull_(v: T?, msg: String): T {
        assertNotNull(msg, v)
        return v!!
    }

    @Test fun drawsAllObjectsWithBlinkingLights() {
        val assets = SceneryAssets.load(ctx)
        val types = SceneryType.values().toList()
        val cols = 6
        val rows = (types.size + cols - 1) / cols
        rule.setContent {
            Column(Modifier.size((100 * cols).dp, (150 * rows).dp).background(Color(0xFFB8A898))) {
                for (row in 0 until rows) Row {
                    for (col in 0 until cols) {
                        val t = types.getOrNull(row * cols + col)
                        Canvas(Modifier.size(100.dp, 150.dp)) {
                            if (t != null) drawScenery(listOf(SceneryItem(1, t, Pt(0.5f, 0.92f), 0.8f)), assets, 0.4f)
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val b = rule.onAllNodes(isRoot()).onFirst().captureToImage().asAndroidBitmap()
        File(File(ctx.filesDir, "shots").apply { mkdirs() }, "scenery.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val bg = AColor.rgb(0xB8, 0xA8, 0x98)
        for (i in types.indices) {
            val cw = b.width / cols
            val ch = b.height / rows
            var changed = 0
            for (y in 0 until ch step 3) for (x in 0 until cw step 3) {
                val c = b.getPixel((i % cols) * cw + x, (i / cols) * ch + y)
                if (kotlin.math.abs(AColor.red(c) - AColor.red(bg)) + kotlin.math.abs(AColor.green(c) - AColor.green(bg)) + kotlin.math.abs(AColor.blue(c) - AColor.blue(bg)) > 60) changed++
            }
            assertTrue("Gegenstand ${types[i].id} nicht sichtbar: $changed", changed > 150)
        }
    }
}
