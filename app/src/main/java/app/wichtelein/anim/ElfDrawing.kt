package app.wichtelein.anim

import app.wichtelein.camera.*
import app.wichtelein.scene.*
import app.wichtelein.speech.*
import app.wichtelein.videoexport.*
import app.wichtelein.settings.*
import app.wichtelein.ui.*

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
import org.json.JSONArray
import org.json.JSONObject

/**
 * Austauschbare Wichtelfigur. Heute zeichnet [ElfSprites] eine 2D-Puppe aus Bildteilen; die Aufführung (Pose, Position)
 * ist davon unabhängig. So lässt sich später eine Figur aus vorgerenderten Blender-Sequenzen oder ein echtes
 * 3D-Modell einsetzen, ohne Szenen, Zeitleiste, Editor oder Export zu ändern.
 */
interface ElfFigure {
    /** Zeichnet die Figur mit den Füßen bei [base], [h] Pixel hoch; [facing] = 1 oder -1 (gespiegelt). */
    fun draw(scope: DrawScope, pose: Pose, base: Offset, h: Float, facing: Int)
}

/** Position eines Bildteils (linke obere Ecke) im Layout. */
class PartPos(val x: Float, val y: Float)

/**
 * Wo sitzt was? Die Zeichenroutine kennt nur dieses Layout: Teilepositionen, Gelenke, Gesicht und Hand. So funktionieren
 * die Foto-Puppe und der in Blender gerenderte 3D-Wichtel mit derselben Animation.
 */
class ElfLayout(
    val feet: Offset,
    val height: Float,
    val head: PartPos, val torso: PartPos, val armL: PartPos, val armR: PartPos, val legL: PartPos, val legR: PartPos,
    val pivHead: Offset, val pivArmL: Offset, val pivArmR: Offset, val pivHipL: Offset, val pivHipR: Offset, val pivBody: Offset,
    /** Mittelpunkte der Augen sowie Breite/Höhe der Lider */
    val eyes: List<Offset>, val eyeW: Float, val eyeH: Float,
    /** Oberkante Mitte des Mundes und Breite */
    val mouth: Offset, val mouthW: Float,
    /** Hand (rechts) und Maßstab der Gegenstände relativ zur Foto-Puppe */
    val hand: Offset, val propK: Float,
    /** ab dieser Bildzeile (in den Beinteilen) beginnen die Schuhe */
    val bootRow: Int,
    val skin: Color,
) {
    companion object {
        /** Foto-Puppe (Referenzbild, 614 x 807): Füße bei y = 800, Mütze oben bei y = 31. */
        val PHOTO = ElfLayout(
            Offset(352f, 800f), 769f,
            PartPos(200f, 31f), PartPos(228f, 263f), PartPos(164f, 336f), PartPos(400f, 300f), PartPos(181f, 540f), PartPos(352f, 540f),
            Offset(345f, 285f), Offset(250f, 360f), Offset(440f, 350f), Offset(300f, 555f), Offset(435f, 555f), Offset(352f, 500f),
            listOf(Offset(318f, 183f), Offset(377f, 183f)), 54f, 46f,
            Offset(352f, 232f), 62f,
            Offset(495f, 535f), 1f, 110, Color(0xFFF1C3A3),
        )

        fun fromJson(json: String): ElfLayout {
            val o = JSONObject(json)
            fun pt(a: JSONArray) = Offset(a.getDouble(0).toFloat(), a.getDouble(1).toFloat())
            fun pp(k: String) = o.getJSONObject("parts").getJSONArray(k).let { PartPos(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) }
            val piv = o.getJSONObject("piv")
            val eyes = o.getJSONArray("eyes")
            return ElfLayout(
                pt(o.getJSONArray("feet")), o.getDouble("height").toFloat(),
                pp("head"), pp("torso"), pp("arml"), pp("armr"), pp("legl"), pp("legr"),
                pt(piv.getJSONArray("head")), pt(piv.getJSONArray("arml")), pt(piv.getJSONArray("armr")),
                pt(piv.getJSONArray("legl")), pt(piv.getJSONArray("legr")), pt(piv.getJSONArray("body")),
                (0 until eyes.length()).map { pt(eyes.getJSONArray(it)) }, o.getDouble("eyeW").toFloat(), o.getDouble("eyeH").toFloat(),
                pt(o.getJSONArray("mouth")), o.getDouble("mouthW").toFloat(),
                pt(o.getJSONArray("hand")), o.getDouble("propK").toFloat(), o.getInt("bootRow"),
                Color(0xFF000000L or o.getString("skin").removePrefix("#").toLong(16)),
            )
        }
    }
}

/** Die Einzelteile des Wichtels (Kopf mit Mütze, Körper, Arme, Beine mit Schuhen) als Bilder samt [layout]. */
class ElfSprites(private val images: Map<String, ImageBitmap>, val layout: ElfLayout = ElfLayout.PHOTO) : ElfFigure {
    fun image(name: String): ImageBitmap = images.getValue(name)

    override fun draw(scope: DrawScope, pose: Pose, base: Offset, h: Float, facing: Int) {
        scope.drawElf(pose, this, base, h, facing)
    }

    companion object {
        val partNames = listOf("head", "torso", "arml", "armr", "legl", "legr")
        const val STYLE_PHOTO = 0
        const val STYLE_3D = 1

        /** Gibt es die Bilder für diesen Stil in der App? */
        fun hasStyle(context: Context, style: Int): Boolean =
            style == STYLE_PHOTO || context.resources.getIdentifier("elf3d_head", "drawable", context.packageName) != 0

        /** Lädt die Bildteile und färbt Mütze, Jacke, Strumpfhose und Schuhe nach [colors] um. */
        fun load(context: Context, colors: ElfColors = ElfColors(), style: Int = STYLE_PHOTO): ElfSprites {
            val res = context.resources
            val c = colors.clamped()
            val use3d = style == STYLE_3D && hasStyle(context, STYLE_3D)
            val prefix = if (use3d) "elf3d" else "elf0"
            val layout = if (use3d) {
                val id = res.getIdentifier("elf3d_layout", "raw", context.packageName)
                ElfLayout.fromJson(res.openRawResource(id).bufferedReader().use { it.readText() })
            } else ElfLayout.PHOTO
            val opts = BitmapFactory.Options().apply { inScaled = false }
            val map = partNames.associateWith { n ->
                val id = res.getIdentifier("${prefix}_$n", "drawable", context.packageName)
                require(id != 0) { "Bild ${prefix}_$n fehlt" }
                val src = BitmapFactory.decodeResource(res, id, opts)
                val colored = when (n) {
                    "head" -> Recolor.apply(src, ElfPalette.clothes[c.hat], 120f, 0, Recolor::isGreen)
                    "torso", "arml", "armr" -> Recolor.apply(src, ElfPalette.clothes[c.jacket], 120f, 0, Recolor::isGreen)
                    "legl", "legr" -> {
                        // erst die Schuhe (rotbraun, unterer Teil), dann die grüne Strumpfhose
                        val shoes = Recolor.apply(src, ElfPalette.boots[c.boots], 0f, layout.bootRow, Recolor::isBootRed)
                        Recolor.apply(shoes, ElfPalette.clothes[c.legs], 120f, 0, Recolor::isGreen)
                    }
                    else -> src
                }
                colored.asImageBitmap()
            }
            return ElfSprites(map, layout)
        }
    }
}

@Composable
fun rememberElfSprites(colors: ElfColors = ElfColors(), style: Int = ElfSprites.STYLE_PHOTO): ElfSprites {
    val context = LocalContext.current
    return remember(colors, style) { ElfSprites.load(context, colors, style) }
}

private val Ink = Color(0xFF3A2A20)

/**
 * Zeichnet den Wichtel. Der Fußpunkt liegt bei [base], [h] ist seine Höhe in Pixeln,
 * [facing] = 1 oder -1 spiegelt ihn. Arme und Beine drehen sich um ihre Gelenke,
 * Mund und Augen werden darübergezeichnet.
 */
fun DrawScope.drawElf(p: Pose, sprites: ElfSprites, base: Offset, h: Float, facing: Int) {
    if (p.scale < 0.02f) return
    val L = sprites.layout
    val k = h / L.height
    withTransform({
        translate(base.x, base.y)
        scale(k * p.scale * facing, k * p.scale, Offset.Zero)
        translate(-L.feet.x, -L.feet.y)
    }) {
        // Schatten
        drawOval(Color.Black.copy(alpha = 0.25f), Offset(L.feet.x - L.height * 0.195f, L.feet.y - L.height * 0.029f), Size(L.height * 0.39f, L.height * 0.057f))
        withTransform({
            translate(0f, -p.bob * L.height)
            scale(1f + p.squash, 1f - p.squash, L.feet)
            rotate(p.spin + p.lean, L.pivBody)
        }) {
            drawLimb(sprites, L.legL, "legl", L.pivHipL, p.legL * 0.5f)
            drawLimb(sprites, L.legR, "legr", L.pivHipR, -p.legR * 0.5f)
            drawPart(sprites, L.torso, "torso")
            drawLimb(sprites, L.armL, "arml", L.pivArmL, p.armL)
            withTransform({ rotate(-p.armR, L.pivArmR) }) {
                if (p.prop != Prop.NONE) {
                    withTransform({
                        translate(L.hand.x, L.hand.y)
                        scale(L.propK, L.propK, Offset.Zero)
                        translate(-495f, -535f)
                    }) { drawProp(p.prop) }
                }
                drawPart(sprites, L.armR, "armr")
            }
            withTransform({ rotate(p.hatTilt * 150f + p.nod, L.pivHead) }) {
                drawPart(sprites, L.head, "head")
                drawFace(p, L)
            }
        }
    }
}

private fun DrawScope.drawPart(sprites: ElfSprites, pos: PartPos, name: String) {
    val img = sprites.image(name)
    drawImage(
        img,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(img.width, img.height),
        dstOffset = IntOffset(pos.x.toInt(), pos.y.toInt()),
        dstSize = IntSize(img.width, img.height),
        filterQuality = FilterQuality.Medium,
    )
}

/** Teil, das sich um [pivot] dreht; positiver Winkel = im Uhrzeigersinn. */
private fun DrawScope.drawLimb(sprites: ElfSprites, pos: PartPos, name: String, pivot: Offset, angle: Float) {
    withTransform({ rotate(angle, pivot) }) { drawPart(sprites, pos, name) }
}

/** Mund (beim Sprechen/Kauen offen) und Lider (beim Schlafen/Blinzeln). */
private fun DrawScope.drawFace(p: Pose, L: ElfLayout) {
    val open = max(p.mouth, p.talk)
    if (open > 0.05f) {
        val k = L.mouthW / 62f
        val mw = L.mouthW
        val mh = (10f + 40f * open) * k
        val mx = L.mouth.x
        val my = L.mouth.y
        drawOval(Color(0xFF7A2323), Offset(mx - mw / 2, my), Size(mw, mh))
        drawRoundRect(Color.White, Offset(mx - mw * 0.36f, my + k), Size(mw * 0.72f, 9f * k), CornerRadius(4f * k))
        if (open > 0.5f) drawOval(Color(0xFFD9667A), Offset(mx - 16f * k, my + mh - 16f * k), Size(32f * k, 13f * k))
    }
    if (p.eyesClosed || p.blink) {
        val ks = L.eyeW / 54f
        for (c in L.eyes) {
            drawOval(L.skin, Offset(c.x - L.eyeW / 2, c.y - L.eyeH / 2), Size(L.eyeW, L.eyeH))
            drawArc(
                Ink, 10f, 160f, false, Offset(c.x - 22f * ks, c.y - 15f * ks), Size(44f * ks, 24f * ks),
                style = Stroke(width = 5f * ks, cap = StrokeCap.Round),
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
        Prop.GIFT -> {
            drawRoundRect(Color(0xFFD93A3A), Offset(455f, 480f), Size(100f, 85f), CornerRadius(8f))
            drawRect(Color(0xFFF2C94C), Offset(497f, 480f), Size(16f, 85f))
            drawRect(Color(0xFFF2C94C), Offset(455f, 513f), Size(100f, 14f))
            drawCircle(Color(0xFFF2C94C), 14f, Offset(488f, 474f))
            drawCircle(Color(0xFFF2C94C), 14f, Offset(522f, 474f))
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
            MessItem.GIFT -> { // Geschenk
                val g = s * 1.7f
                drawRoundRect(Color(0xFFD93A3A), Offset(-g * 0.5f, -g * 0.45f), Size(g, g * 0.9f), CornerRadius(g * 0.08f))
                drawRect(Color(0xFFF2C94C), Offset(-g * 0.07f, -g * 0.45f), Size(g * 0.14f, g * 0.9f))
                drawRect(Color(0xFFF2C94C), Offset(-g * 0.5f, -g * 0.07f), Size(g, g * 0.14f))
                drawCircle(Color(0xFFF2C94C), g * 0.13f, Offset(-g * 0.12f, -g * 0.52f))
                drawCircle(Color(0xFFF2C94C), g * 0.13f, Offset(g * 0.12f, -g * 0.52f))
            }
            else -> { // Papierknäuel
                drawCircle(Color(0xFFF1EFE8), s * 0.42f, Offset.Zero)
                drawCircle(Color(0xFFD5D2C8), s * 0.18f, Offset(s * 0.1f, s * 0.08f))
            }
        }
    }
}
