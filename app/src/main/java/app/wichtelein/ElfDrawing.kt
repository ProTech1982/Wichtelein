package app.wichtelein

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.max

/** Farbwahl in den Einstellungen; die Nummer ist die Farbvariante der Wichtel-Bilder (elf0..elf3). */
class ElfPalette(val name: String, val hat: Color, val dark: Color)

val ElfPalettes = listOf(
    ElfPalette("Grün", Color(0xFF2E9E4F), Color(0xFF1B6B33)),
    ElfPalette("Rot", Color(0xFFD93A3A), Color(0xFF8F2222)),
    ElfPalette("Blau", Color(0xFF3A7BD9), Color(0xFF244F94)),
    ElfPalette("Lila", Color(0xFF8E4FD9), Color(0xFF5E2F94)),
)

/** Die Einzelteile des Wichtels (Kopf mit Mütze, Körper, Arme, Beine mit Schuhen) als Bilder. */
class ElfSprites(private val images: Map<String, ImageBitmap>) {
    fun image(name: String): ImageBitmap = images.getValue(name)

    companion object {
        val partNames = listOf("head", "torso", "arml", "armr", "legl", "legr")

        fun load(context: Context, variant: Int): ElfSprites {
            val res = context.resources
            val v = variant.coerceIn(0, ElfPalettes.lastIndex)
            val opts = BitmapFactory.Options().apply { inScaled = false }
            val map = partNames.associateWith { n ->
                val id = res.getIdentifier("elf${v}_$n", "drawable", context.packageName)
                require(id != 0) { "Bild elf${v}_$n fehlt" }
                BitmapFactory.decodeResource(res, id, opts).asImageBitmap()
            }
            return ElfSprites(map)
        }
    }
}

@Composable
fun rememberElfSprites(variant: Int): ElfSprites {
    val context = LocalContext.current
    return remember(variant) { ElfSprites.load(context, variant) }
}

// Maße im Originalbild (614 x 807): Füße bei y = 800, Mütze oben bei y = 31.
private const val FEET_X = 352f
private const val FEET_Y = 800f
private const val ELF_H = 769f

private class Part(val name: String, val x: Float, val y: Float)

private val HEAD = Part("head", 200f, 31f)
private val TORSO = Part("torso", 228f, 263f)
private val ARM_L = Part("arml", 164f, 336f)
private val ARM_R = Part("armr", 400f, 300f)
private val LEG_L = Part("legl", 181f, 540f)
private val LEG_R = Part("legr", 352f, 540f)

// Gelenke
private val PIV_HEAD = Offset(345f, 285f)
private val PIV_ARM_L = Offset(250f, 360f)
private val PIV_ARM_R = Offset(440f, 350f)
private val PIV_HIP_L = Offset(300f, 555f)
private val PIV_HIP_R = Offset(435f, 555f)
private val PIV_BODY = Offset(352f, 500f)

private val SkinTone = Color(0xFFF1C3A3)
private val Ink = Color(0xFF3A2A20)

/**
 * Zeichnet den Wichtel. Der Fußpunkt liegt bei [base], [h] ist seine Höhe in Pixeln,
 * [facing] = 1 oder -1 spiegelt ihn. Arme und Beine drehen sich um ihre Gelenke,
 * Mund und Augen werden darübergezeichnet.
 */
fun DrawScope.drawElf(p: Pose, sprites: ElfSprites, base: Offset, h: Float, facing: Int) {
    if (p.scale < 0.02f) return
    val k = h / ELF_H
    withTransform({
        translate(base.x, base.y)
        scale(k * p.scale * facing, k * p.scale, Offset.Zero)
        translate(-FEET_X, -FEET_Y)
    }) {
        // Schatten
        drawOval(Color.Black.copy(alpha = 0.25f), Offset(FEET_X - 150f, FEET_Y - 22f), Size(300f, 44f))
        withTransform({
            translate(0f, -p.bob * ELF_H)
            scale(1f + p.squash, 1f - p.squash, Offset(FEET_X, FEET_Y))
            rotate(p.spin + p.lean, PIV_BODY)
        }) {
            drawLimb(sprites, LEG_L, PIV_HIP_L, p.legL * 0.5f)
            drawLimb(sprites, LEG_R, PIV_HIP_R, -p.legR * 0.5f)
            drawPart(sprites, TORSO)
            drawLimb(sprites, ARM_L, PIV_ARM_L, p.armL)
            withTransform({ rotate(-p.armR, PIV_ARM_R) }) {
                if (p.prop != Prop.NONE) drawProp(p.prop)
                drawPart(sprites, ARM_R)
            }
            withTransform({ rotate(p.hatTilt * 150f, PIV_HEAD) }) {
                drawPart(sprites, HEAD)
                drawFace(p)
            }
        }
    }
}

private fun DrawScope.drawPart(sprites: ElfSprites, part: Part) {
    val img = sprites.image(part.name)
    drawImage(
        img,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(img.width, img.height),
        dstOffset = IntOffset(part.x.toInt(), part.y.toInt()),
        dstSize = IntSize(img.width, img.height),
        filterQuality = FilterQuality.Medium,
    )
}

/** Teil, das sich um [pivot] dreht; positiver Winkel = im Uhrzeigersinn. */
private fun DrawScope.drawLimb(sprites: ElfSprites, part: Part, pivot: Offset, angle: Float) {
    withTransform({ rotate(angle, pivot) }) { drawPart(sprites, part) }
}

/** Mund (beim Sprechen/Kauen offen) und Lider (beim Schlafen). Koordinaten im Originalbild. */
private fun DrawScope.drawFace(p: Pose) {
    val open = max(p.mouth, p.talk)
    if (open > 0.05f) {
        val mw = 62f
        val mh = 10f + 40f * open
        drawOval(Color(0xFF7A2323), Offset(352f - mw / 2, 232f), Size(mw, mh))
        drawRoundRect(Color.White, Offset(352f - mw * 0.36f, 233f), Size(mw * 0.72f, 9f), CornerRadius(4f))
        if (open > 0.5f) drawOval(Color(0xFFD9667A), Offset(352f - 16f, 232f + mh - 16f), Size(32f, 13f))
    }
    if (p.eyesClosed) {
        for (cx in listOf(318f, 377f)) {
            drawOval(SkinTone, Offset(cx - 27f, 160f), Size(54f, 46f))
            drawArc(
                Ink, 10f, 160f, false, Offset(cx - 22f, 168f), Size(44f, 24f),
                style = Stroke(width = 5f, cap = StrokeCap.Round),
            )
        }
    }
}

/** Gegenstand in der rechten Hand (Koordinaten im Originalbild, Hand bei etwa (495, 535)). */
private fun DrawScope.drawProp(prop: Prop) {
    when (prop) {
        Prop.BROOM -> {
            drawLine(Color(0xFF8A5A2B), Offset(497f, 330f), Offset(497f, 770f), 16f, StrokeCap.Round)
            val bristles = Path().apply {
                moveTo(470f, 735f)
                lineTo(524f, 735f)
                lineTo(566f, 805f)
                lineTo(430f, 805f)
                close()
            }
            drawPath(bristles, Color(0xFFD9B35B))
            drawLine(Color(0xFFA9822F), Offset(462f, 758f), Offset(532f, 758f), 8f)
            drawLine(Color(0xFFA9822F), Offset(450f, 782f), Offset(546f, 782f), 8f)
        }
        Prop.COOKIE -> {
            drawCircle(Color(0xFFD9A05B), 46f, Offset(500f, 520f))
            drawCircle(Color(0xFF5B3A24), 8f, Offset(486f, 508f))
            drawCircle(Color(0xFF5B3A24), 8f, Offset(514f, 530f))
            drawCircle(Color(0xFF5B3A24), 7f, Offset(496f, 540f))
        }
        Prop.NONE -> Unit
    }
}

/** Kleine Dinge, die der Wichtel herumwirft und wieder aufräumt. */
fun DrawScope.drawItem(kind: Int, c: Offset, s: Float, rot: Float) {
    withTransform({
        translate(c.x, c.y)
        rotate(rot, Offset.Zero)
    }) {
        when (kind) {
            0 -> { // Socke
                drawRoundRect(Color(0xFFE4572E), Offset(-s * 0.5f, -s * 0.25f), Size(s, s * 0.5f), CornerRadius(s * 0.2f))
                drawRect(Color.White, Offset(-s * 0.25f, -s * 0.25f), Size(s * 0.15f, s * 0.5f))
                drawRect(Color.White, Offset(s * 0.1f, -s * 0.25f), Size(s * 0.15f, s * 0.5f))
            }
            1 -> { // Bauklotz
                drawRect(Color(0xFF3A7BD9), Offset(-s * 0.4f, -s * 0.4f), Size(s * 0.8f, s * 0.8f))
                drawRect(Color(0xFFF2C94C), Offset(-s * 0.4f, -s * 0.4f), Size(s * 0.8f, s * 0.2f))
            }
            2 -> { // Ball
                drawCircle(Color(0xFFD93A3A), s * 0.45f, Offset.Zero)
                drawCircle(Color.White.copy(alpha = 0.7f), s * 0.12f, Offset(-s * 0.15f, -s * 0.15f))
            }
            else -> { // Papierknäuel
                drawCircle(Color(0xFFF1EFE8), s * 0.42f, Offset.Zero)
                drawCircle(Color(0xFFD5D2C8), s * 0.18f, Offset(s * 0.1f, s * 0.08f))
            }
        }
    }
}
