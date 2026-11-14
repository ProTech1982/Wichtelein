package app.wichtelein

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp

/** Perspektive: weiter oben im Bild (kleineres y) = weiter weg = kleiner. */
fun depth(y: Float): Float = 0.55f + 0.6f * y

/**
 * Zeichenfläche über dem Video: Chaos-Gegenstände, Wichtel, Partikel und Sprechblase.
 * [onTap] bekommt die Tippposition relativ zur Fläche (0..1).
 */
@Composable
fun ElfOverlay(
    engine: ElfEngine,
    sprites: ElfSprites,
    sizeScale: Float,
    onTap: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val tap by rememberUpdatedState(onTap)
    var widthPx by remember { mutableIntStateOf(0) }
    val bubbleText = engine.bubble
    val bubbleStyle = remember {
        TextStyle(fontSize = 17.sp, color = Color(0xFF222222), fontWeight = FontWeight.Medium)
    }
    val bubbleLayout = remember(bubbleText, widthPx) {
        if (bubbleText != null && widthPx > 0) {
            measurer.measure(bubbleText, bubbleStyle, constraints = Constraints(maxWidth = (widthPx * 0.62f).toInt()))
        } else null
    }

    Canvas(
        modifier
            .onSizeChanged { widthPx = it.width }
            .pointerInput(Unit) {
                detectTapGestures { o -> tap(o.x / size.width, o.y / size.height) }
            },
    ) {
        if (engine.frame < 0) return@Canvas // liest den Frame-Zähler, damit neu gezeichnet wird
        val w = size.width
        val h = size.height

        // Chaos-Gegenstände, hinten zuerst
        for (item in engine.mess.sortedBy { it.y }) {
            val s = h * sizeScale * depth(item.y) * 0.14f
            drawItem(item.kind, Offset(item.x * w, item.y * h), s, item.rot)
        }

        if (engine.visible) {
            val eh = h * sizeScale * depth(engine.y)
            drawElf(engine.pose, sprites, Offset(engine.x * w, engine.y * h), eh, engine.facing)
        }

        engine.flying?.let { f ->
            val s = h * sizeScale * depth(f.y) * 0.14f
            drawItem(f.kind, Offset(f.x * w, f.y * h), s, f.rot)
        }

        for (p in engine.particles) drawParticle(p, w, h, measurer)

        val layout = bubbleLayout
        if (layout != null && engine.visible && engine.pose.scale > 0.5f) {
            val eh = h * sizeScale * depth(engine.y)
            val pad = 14f
            val bw = layout.size.width + pad * 2
            val bh = layout.size.height + pad * 2
            val elfX = engine.x * w
            val left = (elfX - bw / 2).coerceIn(8f, (w - bw - 8f).coerceAtLeast(8f))
            val top = (engine.y * h - eh * 1.12f - bh - 12f).coerceAtLeast(8f)
            drawRoundRect(Color.White.copy(alpha = 0.96f), Offset(left, top), Size(bw, bh), CornerRadius(22f))
            drawRoundRect(Color(0xFF999999), Offset(left, top), Size(bw, bh), CornerRadius(22f), style = Stroke(2f))
            val tailX = elfX.coerceIn(left + 24f, left + bw - 24f)
            val tail = Path().apply {
                moveTo(tailX - 12f, top + bh - 1f)
                lineTo(tailX + 12f, top + bh - 1f)
                lineTo(elfX.coerceIn(tailX - 30f, tailX + 30f), top + bh + 14f)
                close()
            }
            drawPath(tail, Color.White.copy(alpha = 0.96f))
            drawText(layout, topLeft = Offset(left + pad, top + pad))
        }
    }
}

private fun DrawScope.drawParticle(
    p: Particle,
    w: Float,
    h: Float,
    measurer: androidx.compose.ui.text.TextMeasurer,
) {
    val f = (p.life / p.maxLife).coerceIn(0f, 1f)
    val c = Offset(p.x * w, p.y * h)
    val unit = h * 0.012f * p.size
    when (p.kind) {
        PK.POOF -> drawCircle(Color.White.copy(alpha = 0.8f * f), unit * (1.5f + (1f - f) * 2f), c)
        PK.DUST -> drawCircle(Color(0xFFB9A98C).copy(alpha = 0.7f * f), unit * 0.9f, c)
        PK.CRUMB -> drawCircle(Color(0xFF8A5A2B).copy(alpha = f), unit * 0.45f, c)
        PK.SPARK -> {
            val col = Color(0xFFF2C94C).copy(alpha = f)
            drawCircle(col, unit * 0.7f, c)
            drawLine(col, c - Offset(unit * 1.4f, 0f), c + Offset(unit * 1.4f, 0f), 3f)
            drawLine(col, c - Offset(0f, unit * 1.4f), c + Offset(0f, unit * 1.4f), 3f)
        }
        PK.Z -> drawText(
            measurer, "Z", c,
            style = TextStyle(
                fontSize = (14f + (1f - f) * 12f).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF3A5BD9).copy(alpha = f),
            ),
        )
    }
}
