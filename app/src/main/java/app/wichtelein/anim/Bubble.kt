package app.wichtelein.anim

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Form der Sprechblase. */
enum class BubbleShape(val label: String) {
    ROUNDED("Abgerundet"),
    OVAL("Rund"),
    RECT("Viereck"),
    CLOUD("Wolke"),
    SHOUT("Zackig"),
}

/** Aussehen der Sprechblase: [show] = anzeigen (gesprochen wird trotzdem), [shape] = Form, [size] = Größe (1 = normal). */
data class BubbleSpec(val show: Boolean = true, val shape: BubbleShape = BubbleShape.ROUNDED, val size: Float = 1f) {
    companion object {
        const val MIN_SIZE = 0.6f
        const val MAX_SIZE = 1.8f
    }
}

private val Fill = Color.White.copy(alpha = 0.96f)
private val Edge = Color(0xFF999999)

/**
 * Zeichnet eine Sprechblase mit dem Text [layout]. Die Blase sitzt über dem Kopf des Wichtels bei [elfX]/[elfTop] (Oberkante des
 * Wichtels); [w]/[h] ist die Zeichenfläche. Die Form bestimmt den Rand: Rechteck, Oval, Wolke (mit kleinen Gedankenblasen) oder
 * Zackenstern (Schrei).
 */
fun DrawScope.drawBubble(layout: TextLayoutResult, spec: BubbleSpec, elfX: Float, elfTop: Float, w: Float, h: Float) {
    val pad = h * 0.02f * spec.size
    val tw = layout.size.width.toFloat()
    val th = layout.size.height.toFloat()
    // Platz um den Text je nach Form (Rundungen brauchen mehr Rand)
    val (ex, ey) = when (spec.shape) {
        BubbleShape.ROUNDED, BubbleShape.RECT -> Pair(pad, pad)
        BubbleShape.OVAL -> Pair(pad + tw * 0.16f, pad + th * 0.32f)
        BubbleShape.CLOUD -> Pair(pad + tw * 0.14f, pad + th * 0.34f)
        BubbleShape.SHOUT -> Pair(pad + tw * 0.26f, pad + th * 0.62f)
    }
    val bw = tw + 2 * ex
    val bh = th + 2 * ey
    val tailLen = h * 0.026f * spec.size
    val left = (elfX - bw / 2).coerceIn(8f, (w - bw - 8f).coerceAtLeast(8f))
    val top = (elfTop - bh - tailLen - h * 0.01f).coerceAtLeast(8f)
    val stroke = Stroke(h * 0.003f)
    val rect = Offset(left, top)
    val sz = Size(bw, bh)
    val cx = left + bw / 2f
    val cy = top + bh / 2f
    val tailX = elfX.coerceIn(left + bw * 0.2f, (left + bw * 0.8f).coerceAtLeast(left + bw * 0.2f))
    val bottom = top + bh

    fun tail(baseY: Float) = Path().apply {
        moveTo(tailX - h * 0.016f * spec.size, baseY - 1f)
        lineTo(tailX + h * 0.016f * spec.size, baseY - 1f)
        lineTo(elfX.coerceIn(tailX - 30f, tailX + 30f), baseY + tailLen)
        close()
    }

    when (spec.shape) {
        BubbleShape.ROUNDED -> {
            val corner = CornerRadius(h * 0.03f * spec.size)
            drawRoundRect(Fill, rect, sz, corner)
            drawRoundRect(Edge, rect, sz, corner, style = stroke)
            drawPath(tail(bottom), Fill)
        }
        BubbleShape.RECT -> {
            val corner = CornerRadius(h * 0.004f)
            drawRoundRect(Fill, rect, sz, corner)
            drawRoundRect(Edge, rect, sz, corner, style = stroke)
            drawPath(tail(bottom), Fill)
        }
        BubbleShape.OVAL -> {
            drawOval(Fill, rect, sz)
            drawOval(Edge, rect, sz, style = stroke)
            drawPath(tail(bottom - bh * 0.06f), Fill)
        }
        BubbleShape.CLOUD -> {
            // Wolke: Kreise entlang des Randes einer Ellipse, innen gefüllt; darunter kleine Gedankenblasen
            val n = 12
            val rx = bw / 2f - bh * 0.14f
            val ry = bh / 2f - bh * 0.14f
            val r = bh * 0.24f
            val bumps = (0 until n).map {
                val a = it * 2.0 * PI / n
                Offset(cx + rx * cos(a).toFloat(), cy + ry * sin(a).toFloat())
            }
            drawOval(Fill, Offset(left + r * 0.6f, top + r * 0.6f), Size(bw - r * 1.2f, bh - r * 1.2f))
            for (b in bumps) drawCircle(Edge, r + h * 0.0015f, b)
            for (b in bumps) drawCircle(Fill, r, b)
            drawOval(Fill, Offset(left + r * 0.6f, top + r * 0.6f), Size(bw - r * 1.2f, bh - r * 1.2f))
            val a = Offset(tailX, bottom + tailLen * 0.3f)
            val b = Offset((tailX + elfX) / 2f, bottom + tailLen * 0.75f)
            val c = Offset(elfX.coerceIn(tailX - 30f, tailX + 30f), bottom + tailLen * 1.15f)
            drawCircle(Edge, tailLen * 0.34f + 1.5f, a)
            drawCircle(Fill, tailLen * 0.34f, a)
            drawCircle(Edge, tailLen * 0.24f + 1.5f, b)
            drawCircle(Fill, tailLen * 0.24f, b)
            drawCircle(Edge, tailLen * 0.15f + 1.5f, c)
            drawCircle(Fill, tailLen * 0.15f, c)
        }
        BubbleShape.SHOUT -> {
            // Zackenstern: abwechselnd außen und innen
            val n = 16
            val path = Path()
            for (i in 0 until n * 2) {
                val a = i * PI / n
                val k = if (i % 2 == 0) 1f else 0.76f
                val px = cx + (bw / 2f) * k * cos(a).toFloat()
                val py = cy + (bh / 2f) * k * sin(a).toFloat()
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            drawPath(path, Fill)
            drawPath(path, Edge, style = stroke)
            drawPath(tail(bottom - bh * 0.12f), Fill)
        }
    }
    drawText(layout, topLeft = Offset(cx - tw / 2f, cy - th / 2f))
}
