package app.wichtelein.anim

import app.wichtelein.camera.*
import app.wichtelein.scene.*
import app.wichtelein.speech.*
import app.wichtelein.videoexport.*
import app.wichtelein.settings.*
import app.wichtelein.ui.*

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.withTransform
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
    sprites: ElfFigure,
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
 * Bildet Positionen der Aufführung (x, y relativ 0..1) auf die Zeichenfläche ab. Normal ist das einfach x * Breite,
 * y * Höhe; im AR-Modus wird die Position auf den erkannten Boden projiziert.
 */
interface WichtelProjection {
    /** Bildschirmposition des Fußpunkts, oder null, wenn der Punkt nicht sichtbar ist (z. B. hinter der Kamera). */
    fun position(x: Float, y: Float): Offset?

    /** Höhe des Wichtels in Pixeln an dieser Stelle. */
    fun elfHeight(x: Float, y: Float): Float
}

/**
 * Zeichnet einen Moment der Aufführung: Gegenstände, Wichtel, Partikel und Sprechblase.
 * Wird für die Vorschau, den Videoexport und (mit [projection]) für AR benutzt.
 */
fun DrawScope.drawWichtelFrame(
    f: FrameState,
    sprites: ElfFigure,
    sizeScale: Float,
    measurer: TextMeasurer,
    projection: WichtelProjection? = null,
) {
    val w = size.width
    val h = size.height
    fun pos(x: Float, y: Float): Offset? = if (projection != null) projection.position(x, y) else Offset(x * w, y * h)
    fun eh(x: Float, y: Float): Float = if (projection != null) projection.elfHeight(x, y) else h * sizeScale * depth(y)

    if (projection == null) drawDoorBack(f, w, h)

    for (item in f.mess.sortedBy { it.y }) {
        val o = pos(item.x, item.y) ?: continue
        drawItem(item.kind, o, eh(item.x, item.y) * 0.14f, item.rot)
    }

    val elfPos = pos(f.x, f.y)
    val elfH = eh(f.x, f.y)
    if (f.visible && elfPos != null) {
        sprites.draw(this, f.pose, elfPos, elfH, f.facing)
    }

    f.flying?.let { fl ->
        val o = pos(fl.x, fl.y)
        if (o != null) drawItem(fl.kind, o, eh(fl.x, fl.y) * 0.14f, fl.rot)
    }

    if (projection == null) drawDoorLeaf(f, w, h)

    for (p in f.particles) {
        val o = pos(p.x, p.y) ?: continue
        drawParticle(p, o, eh(p.x, p.y) * 0.04f, h, measurer)
    }

    val text = f.bubble
    if (text != null && f.visible && f.pose.scale > 0.5f && elfPos != null) {
        val style = TextStyle(fontSize = (h * 0.027f).toSp(), color = Color(0xFF222222), fontWeight = FontWeight.Medium)
        val layout = measurer.measure(text, style, constraints = Constraints(maxWidth = (w * 0.62f).toInt().coerceAtLeast(40)))
        val pad = h * 0.02f
        val bw = layout.size.width + pad * 2
        val bh = layout.size.height + pad * 2
        val elfX = elfPos.x
        val left = (elfX - bw / 2).coerceIn(8f, (w - bw - 8f).coerceAtLeast(8f))
        val top = (elfPos.y - elfH * 1.12f - bh - h * 0.015f).coerceAtLeast(8f)
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

private fun DrawScope.drawParticle(p: Particle, c: Offset, base: Float, h: Float, measurer: TextMeasurer) {
    val f = (p.life / p.maxLife).coerceIn(0f, 1f)
    val unit = base.coerceAtLeast(h * 0.006f) * p.size
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

// ---------------------------------------------------------------- Tür des Wichtelhauses

/** Tür: Fußpunkt (cx, by), Breite dw, Höhe dh; [round] 0 = eckig, 1 = Rundbogen (Radius der oberen Ecken). */
fun doorPath(cx: Float, by: Float, dw: Float, dh: Float, round: Float): Path {
    val left = cx - dw / 2f
    val right = cx + dw / 2f
    val top = by - dh
    val r = (round.coerceIn(0f, 1f) * dw / 2f).coerceAtMost(dh / 2f)
    return Path().apply {
        moveTo(left, by)
        lineTo(left, top + r)
        if (r > 0.5f) arcTo(Rect(left, top, left + 2 * r, top + 2 * r), 180f, 90f, false)
        lineTo(right - r, top)
        if (r > 0.5f) arcTo(Rect(right - 2 * r, top, right, top + 2 * r), 270f, 90f, false)
        lineTo(right, by)
        close()
    }
}

/** Dunkler Innenraum mit warmem Licht; liegt hinter dem Wichtel, damit er hineingehen kann. */
private fun DrawScope.drawDoorBack(f: FrameState, w: Float, h: Float) {
    val pos = f.doorPos ?: return
    if (f.doorOpen < 0.004f) return
    val dh = h * f.doorScale
    val dw = dh * 0.56f
    val cx = pos.x * w
    val by = pos.y * h
    drawPath(doorPath(cx, by, dw, dh, f.doorRound), Color(0xFF2B1A10))
    drawPath(doorPath(cx, by, dw * 0.8f, dh * 0.9f, f.doorRound), Color(0xFFF6C768).copy(alpha = 0.55f * f.doorOpen))
    drawPath(doorPath(cx, by, dw * 0.45f, dh * 0.6f, f.doorRound), Color(0xFFFFE8A8).copy(alpha = 0.45f * f.doorOpen))
}

/** Das Türblatt: links angeschlagen, schwingt beim Öffnen zur Seite (schmaler werdend) und davor wieder zu. */
private fun DrawScope.drawDoorLeaf(f: FrameState, w: Float, h: Float) {
    val pos = f.doorPos ?: return
    if (f.doorOpen < 0.004f) return
    val dh = h * f.doorScale
    val dw = dh * 0.56f
    val cx = pos.x * w
    val by = pos.y * h
    val left = cx - dw / 2f
    val hingeX = if (f.doorHingeLeft) left else left + dw
    val sx = kotlin.math.cos(f.doorOpen.coerceIn(0f, 1f) * 1.45f).coerceAtLeast(0.1f)
    val shade = 1f - 0.25f * f.doorOpen
    withTransform({ scale(sx, 1f, Offset(hingeX, by)) }) {
        val arch = doorPath(cx, by, dw, dh, f.doorRound)
        drawPath(arch, Color(0.72f * shade, 0.48f * shade, 0.27f * shade))
        // Bretter
        for (i in 1..3) {
            val x = left + dw * i / 4f
            drawLine(Color(0x55402010), Offset(x, by - dh * 0.88f), Offset(x, by), dw * 0.012f)
        }
        // Querbalken, Rahmen, Klinke
        drawRect(Color(0x66402010), Offset(left, by - dh * 0.55f), Size(dw, dh * 0.04f))
        drawRect(Color(0x66402010), Offset(left, by - dh * 0.18f), Size(dw, dh * 0.04f))
        drawPath(arch, Color(0xFF5B3A1E), style = Stroke(dw * 0.045f))
        drawCircle(Color(0xFFF2C94C), dw * 0.045f, Offset(if (f.doorHingeLeft) left + dw * 0.8f else left + dw * 0.2f, by - dh * 0.42f))
    }
}
