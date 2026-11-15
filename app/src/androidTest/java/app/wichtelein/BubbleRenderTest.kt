package app.wichtelein

import android.graphics.Bitmap
import android.graphics.Color as AColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.wichtelein.anim.*
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Alle Formen und Größen der Sprechblase lassen sich zeichnen (auch kurz und lang, ohne Absturz). */
@RunWith(AndroidJUnit4::class)
class BubbleRenderTest {
    @get:Rule val rule = createComposeRule()

    @Composable
    private fun Cell(spec: BubbleSpec, text: String) {
        val measurer = rememberTextMeasurer()
        Canvas(Modifier.size(150.dp, 90.dp).background(Color(0xFFCDB89A))) {
            val style = TextStyle(fontSize = (size.height * 0.13f * spec.size).toSp(), color = Color(0xFF222222), fontWeight = FontWeight.Medium)
            val k = if (spec.shape == BubbleShape.ROUNDED || spec.shape == BubbleShape.RECT) 1f else 0.7f
            val layout = measurer.measure(text, style, constraints = Constraints(maxWidth = (size.width * 0.6f * spec.size.coerceAtMost(1.3f) * k).toInt().coerceAtLeast(40)))
            drawBubble(layout, spec, size.width / 2f, size.height * 1.05f, size.width, size.height * 1.4f)
        }
    }

    @Test fun everyShapeAndSizeIsDrawn() {
        val shapes = BubbleShape.values()
        rule.setContent {
            Column(Modifier.size(300.dp, 450.dp)) {
                for (sh in shapes) Row {
                    Cell(BubbleSpec(true, sh, 0.6f), "Hi!")
                    Cell(BubbleSpec(true, sh, 1.6f), "Ich bin der Wichtel und wohne in deinem Zimmer.")
                }
            }
        }
        rule.waitForIdle()
        val b = rule.onAllNodes(isRoot()).onFirst().captureToImage().asAndroidBitmap()
        File(File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "shots").apply { mkdirs() }, "bubbles.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val cw = b.width / 2
        val ch = b.height / shapes.size
        for (r in shapes.indices) for (c in 0..1) {
            var white = 0
            for (y in 0 until ch step 3) for (x in 0 until cw step 3) {
                val p = b.getPixel(c * cw + x, r * ch + y)
                if (AColor.red(p) > 240 && AColor.green(p) > 240 && AColor.blue(p) > 240) white++
            }
            assertTrue("${shapes[r]} Spalte $c: zu wenig Blase ($white)", white > 30)
        }
    }
}
