package app.wichtelein

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp

/** Perspektive: weiter oben im Bild (kleineres y) = weiter weg = kleiner. */
fun depth(y: Float): Float = 0.55f + 0.6f * y

private val ConfettiColors = listOf(
    Color(0xFFE53935), Color(0xFFFDD835), Color(0xFF43A047),
    Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFFFB8C00),
)

/**
 * Zeichenfläche über dem Video. Zeigt ein [FrameState] der aufgenommenen Aufführung; [onTap] bekommt die
 * Tippposition relativ zur Fläche (0..1), wenn gesetzt.
 */
@Composable
fun ElfOverlay(
    frame: FrameState?,
    sprites: ElfSprites,
    sizeScale: Float,
    modifier: Modifier = Modifier,
    onTap: ((Float, Float) -> Unit)? = null,
) {
    val measurer = rememberTextMeasurer()
    val tap by rememberUpdatedState(onTap)
    Canvas(
        modifier.pointerInput(Unit) {
            detectTapGestures { o -> tap?.invoke(o.x / size.width, o.y / size.height) }
        },
    ) {
        if (frame != null) drawWichtelFrame(frame, sprites, sizeScale, measurer)
    }
}

/**
 * Zeichnet einen Moment der Aufführung: Gegenstände, Wichtel, Partikel und Sprechblase.
 * Wird für die Vorschau und für den Videoexport benutzt, deshalb gibt es hier nur Größen relativ zur Fläche.
 */
fun DrawScope.drawWichtelFrame(f: FrameState, sprites: ElfSprites, sizeScale: Float, measurer: TextMeasurer) {
    val w = size.width
    val h = size.height

    for (item in f.mess.sortedBy { it.y }) {
        drawItem(item.kind, Offset(item.x * w, item.y * h), h * sizeScale * depth(item.y) * 0.14f, item.rot)
    }

    if (f.visible) {
        val eh = h * sizeScale * depth(f.y)
        drawElf(f.pose, sprites, Offset(f.x * w, f.y * h), eh, f.facing)
    }

    f.flying?.let { fl ->
        drawItem(fl.kind, Offset(fl.x * w, fl.y * h), h * sizeScale * depth(fl.y) * 0.14f, fl.rot)
    }

    for (p in f.particles) drawParticle(p, w, h, measurer)

    val text = f.bubble
    if (text != null && f.visible && f.pose.scale > 0.5f) {
        val eh = h * sizeScale * depth(f.y)
        val style = TextStyle(fontSize = (h * 0.027f).toSp(), color = Color(0xFF222222), fontWeight = FontWeight.Medium)
        val layout = measurer.measure(text, style, constraints = Constraints(maxWidth = (w * 0.62f).toInt().coerceAtLeast(40)))
        val pad = h * 0.02f
        val bw = layout.size.width + pad * 2
        val bh = layout.size.height + pad * 2
        val elfX = f.x * w
        val left = (elfX - bw / 2).coerceIn(8f, (w - bw - 8f).coerceAtLeast(8f))
        val top = (f.y * h - eh * 1.12f - bh - h * 0.015f).coerceAtLeast(8f)
        val corner = CornerRadius(h * 0.03f)
        drawRoundRect(Color.White.copy(alpha = 0.96f), Offset(left, top), Size(bw, bh), corner)
        drawRoundRect(Color(0xFF999999), Offset(left, top), Size(bw, bh), corner, style = Stroke(h * 0.003f))
        val tailX = elfX.coerceIn(left + 24f, left + bw - 24f)
        val tail = Path().apply {
            moveTo(tailX - h * 0.018f, top + bh - 1f)
            lineTo(tailX + h * 0.018f, top + bh - 1f)
            lineTo(elfX.coerceIn(tailX - 30f, tailX + 30f), top + bh + h * 0.022f)
            close()
        }
        drawPath(tail, Color.White.copy(alpha = 0.96f))
        drawText(layout, topLeft = Offset(left + pad, top + pad))
    }
}

private fun DrawScope.drawParticle(p: Particle, w: Float, h: Float, measurer: TextMeasurer) {
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
            drawLine(col, c - Offset(unit * 1.4f, 0f), c + Offset(unit * 1.4f, 0f), unit * 0.25f)
            drawLine(col, c - Offset(0f, unit * 1.4f), c + Offset(0f, unit * 1.4f), unit * 0.25f)
        }
        PK.CONFETTI -> rotate(p.life * 360f, c) {
            drawRect(
                ConfettiColors[p.color.coerceIn(0, ConfettiColors.lastIndex)].copy(alpha = f.coerceAtLeast(0.2f)),
                c - Offset(unit * 0.5f, unit * 0.3f),
                Size(unit, unit * 0.6f),
            )
        }
        PK.Z -> drawText(
            measurer, "Z", c,
            style = TextStyle(
                fontSize = (h * 0.022f * (1f + (1f - f))).toSp(),
                fontWeight = FontWeight.Bold,
                color = Color(0xFF3A5BD9).copy(alpha = f),
            ),
        )
    }
}
