package app.wichtelein.anim

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.sin
import org.json.JSONObject

/** Dinge, die man ins Bild stellen kann (in Blender gerendert, Cyberpunk-Weihnacht mit Neonlicht). */
enum class SceneryType(val id: String, val label: String, val emoji: String, val defaultScale: Float) {
    TREE("tree", "Tannenbaum", "🎄", 0.42f),
    BED("bed", "Bett", "🛏", 0.22f),
    FENCE("fence", "Zaun", "🚧", 0.14f),
    LAMP("lamp", "Laterne", "🏮", 0.34f),
    LIGHTS("lights", "Lichterkette", "💡", 0.16f),
    SIGN("sign", "Neonschild", "🪧", 0.18f),
}

/** Ein platzierter Gegenstand: Fußpunkt [at] (relativ im Bild), Höhe [scale] in Bildhöhen, optional gespiegelt. */
data class SceneryItem(val id: Int, val type: SceneryType, val at: Pt, val scale: Float, val flip: Boolean = false)

/** Eine leuchtende Stelle im Bild des Gegenstands (z. B. eine Lampe der Lichterkette). */
class SceneryLight(val x: Float, val y: Float, val r: Float, val color: Color, val speed: Float, val phase: Float)

/** Bildgröße, Fußpunkt und Lichter eines Gegenstands (`scenery/<id>.json`, erzeugt von `blender/szene3d.py`). */
class SceneryMeta(val w: Int, val h: Int, val anchorX: Float, val anchorY: Float, val lights: List<SceneryLight>, val groundGlow: Color?, val groundRx: Float) {
    companion object {
        fun fromJson(json: String): SceneryMeta {
            val o = JSONObject(json)
            val size = o.getJSONArray("size")
            val anchor = o.getJSONArray("anchor")
            val ls = o.optJSONArray("lights")
            val lights = ArrayList<SceneryLight>()
            if (ls != null) for (i in 0 until ls.length()) {
                val l = ls.getJSONObject(i)
                lights.add(
                    SceneryLight(
                        l.getDouble("x").toFloat(), l.getDouble("y").toFloat(), l.getDouble("r").toFloat(),
                        parseColor(l.getString("color")), l.optDouble("speed", 0.0).toFloat(), l.optDouble("phase", 0.0).toFloat(),
                    ),
                )
            }
            val g = o.optJSONObject("ground")
            return SceneryMeta(
                size.getInt(0), size.getInt(1), anchor.getDouble(0).toFloat(), anchor.getDouble(1).toFloat(), lights,
                g?.let { parseColor(it.getString("color")) }, (g?.optDouble("rx", 0.0) ?: 0.0).toFloat(),
            )
        }

        private fun parseColor(s: String) = Color(0xFF000000L or s.removePrefix("#").toLong(16))
    }
}

class SceneryArt(val image: ImageBitmap, val meta: SceneryMeta)

/** Die Bilder aller Gegenstände (fehlen sie, bleibt der Gegenstand unsichtbar statt abzustürzen). */
class SceneryAssets(private val arts: Map<SceneryType, SceneryArt>) {
    fun art(t: SceneryType): SceneryArt? = arts[t]

    companion object {
        val NONE = SceneryAssets(emptyMap())

        fun load(context: Context): SceneryAssets {
            val map = HashMap<SceneryType, SceneryArt>()
            val opts = BitmapFactory.Options().apply { inScaled = false }
            for (t in SceneryType.values()) {
                try {
                    val json = context.assets.open("scenery/${t.id}.json").bufferedReader().use { it.readText() }
                    val bmp = context.assets.open("scenery/${t.id}.png").use { BitmapFactory.decodeStream(it, null, opts) } ?: continue
                    map[t] = SceneryArt(bmp.asImageBitmap(), SceneryMeta.fromJson(json))
                } catch (_: Exception) {
                }
            }
            return SceneryAssets(map)
        }
    }
}

/** Gegenstände eines Bildes samt Bildern. */
class SceneryLayer(val items: List<SceneryItem>, val assets: SceneryAssets)

/** Größe (Breite, Höhe) eines Gegenstands relativ zum Bild; [aspect] = Bildbreite / Bildhöhe. */
fun sceneryBox(item: SceneryItem, art: SceneryArt?, aspect: Float): Pair<Float, Float> {
    val ratio = if (art != null) art.meta.w.toFloat() / art.meta.h else 1f
    return Pair(item.scale * ratio / aspect, item.scale)
}

/**
 * Zeichnet die Gegenstände (hinter oder vor dem Wichtel je nach Fußpunkt). Lichter leuchten additiv und blinken je nach Zeit
 * [timeSec]; so wirken Neonröhren und Lichterketten auch im fertigen Video lebendig.
 */
fun DrawScope.drawScenery(items: List<SceneryItem>, assets: SceneryAssets, timeSec: Float) {
    val w = size.width
    val h = size.height
    for (item in items) {
        val art = assets.art(item.type) ?: continue
        val m = art.meta
        val hpx = h * item.scale
        val k = hpx / m.h
        val base = Offset(item.at.x * w, item.at.y * h)
        val origin = Offset(base.x - m.anchorX * k, base.y - m.anchorY * k)
        withTransform({
            if (item.flip) scale(-1f, 1f, base)
        }) {
            // Bodenlicht (Neonschein auf dem Boden)
            val gc = m.groundGlow
            if (gc != null && m.groundRx > 0f) {
                val rx = m.groundRx * k
                withTransform({ scale(1f, 0.28f, base) }) {
                    drawCircle(
                        Brush.radialGradient(0f to gc.copy(alpha = 0.55f), 0.55f to gc.copy(alpha = 0.18f), 1f to Color.Transparent, center = base, radius = rx),
                        radius = rx, center = base, blendMode = BlendMode.Plus,
                    )
                }
            }
            drawImage(
                art.image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(art.image.width, art.image.height),
                dstOffset = IntOffset(origin.x.toInt(), origin.y.toInt()),
                dstSize = IntSize((m.w * k).toInt().coerceAtLeast(1), hpx.toInt().coerceAtLeast(1)),
                filterQuality = FilterQuality.High,
            )
            for (l in m.lights) {
                val a = if (l.speed <= 0f) 1f else (0.55f + 0.45f * sin(2.0 * PI * (l.speed * timeSec + l.phase)).toFloat())
                val c = Offset(origin.x + l.x * k, origin.y + l.y * k)
                val rad = (l.r * k * 2.6f).coerceAtLeast(3f)
                drawCircle(
                    Brush.radialGradient(0f to l.color.copy(alpha = 0.85f * a), 0.3f to l.color.copy(alpha = 0.35f * a), 1f to Color.Transparent, center = c, radius = rad),
                    radius = rad, center = c, blendMode = BlendMode.Plus,
                )
                drawCircle(Color.White.copy(alpha = 0.75f * a), (l.r * k * 0.35f).coerceAtLeast(1f), c, blendMode = BlendMode.Plus)
            }
        }
    }
}
