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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.sin
import org.json.JSONObject

/**
 * Dinge, die man ins Bild stellen kann (in Blender gerendert). [group] = Stil („klassisch“ mit Holz und warmem Licht oder „Neon“ im
 * Cyberpunk-Look), [isBed] = der Wichtel kann sich hineinlegen (Seitenansicht, Kopf links), [frontFrac] = Höhenanteil des Betts, der den
 * liegenden Wichtel von vorn teilweise zudeckt.
 */
enum class SceneryType(
    val id: String,
    val label: String,
    val emoji: String,
    val defaultScale: Float,
    val ratio: Float,
    val group: SceneryGroup = SceneryGroup.CLASSIC,
    val isBed: Boolean = false,
) {
    TREE_CLASSIC("tree_classic", "Tannenbaum", "🎄", 0.42f, 0.82f),
    FENCE_WOOD("fence_wood", "Holzzaun", "🚧", 0.14f, 2.14f),
    BED_WOOD("bed_wood", "Holzbett", "🛏", 0.26f, 1.79f, isBed = true),
    LAMP_CLASSIC("lamp_classic", "Laterne", "🏮", 0.34f, 0.60f),
    LIGHTS_CLASSIC("lights_classic", "Lichterkette", "💡", 0.16f, 1.73f),
    SNOWMAN("snowman", "Schneemann", "⛄", 0.34f, 0.775f),
    PRESENTS("presents", "Geschenke", "🎁", 0.14f, 1.54f),
    SLED("sled", "Schlitten", "🛷", 0.12f, 1.875f),
    CANDYCANE("candycane", "Zuckerstangen", "🍭", 0.28f, 0.875f),
    BENCH("bench", "Bank", "🪑", 0.16f, 1.61f),
    FIRE("fire", "Lagerfeuer", "🔥", 0.14f, 1.09f),
    TREE("tree", "Neon-Tannenbaum", "🎄", 0.42f, 0.82f, SceneryGroup.NEON),
    BED("bed", "Neon-Bett", "🛏", 0.26f, 1.79f, SceneryGroup.NEON, isBed = true),
    FENCE("fence", "Neon-Zaun", "🚧", 0.14f, 2.14f, SceneryGroup.NEON),
    LAMP("lamp", "Neon-Laterne", "🏮", 0.34f, 0.60f, SceneryGroup.NEON),
    LIGHTS("lights", "Neon-Lichterkette", "💡", 0.16f, 1.73f, SceneryGroup.NEON),
    SIGN("sign", "Neonschild", "🪧", 0.18f, 1.10f, SceneryGroup.NEON),
    ;

    /** Name im Satz („Jetzt stelle ich … auf!“) */
    val spoken: String get() = label.removePrefix("Neon-")
}

enum class SceneryGroup(val label: String) { CLASSIC("Klassisch"), NEON("Neon (Cyberpunk)") }

/** Ein platzierter Gegenstand: Fußpunkt [at] (relativ im Bild), Höhe [scale] in Bildhöhen, optional gespiegelt. */
/** [rot] = Drehung um den Fußpunkt in Grad (positiv = im Uhrzeigersinn). */
data class SceneryItem(val id: Int, val type: SceneryType, val at: Pt, val scale: Float, val flip: Boolean = false, val rot: Float = 0f)

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

/**
 * Aussehen einer Art von Gegenstand (Einstellungen): [hue] = Farbverschiebung in Grad (-1 = Originalfarben), [lights] = mit oder ohne
 * Licht, [lightColor] = Nummer in [SceneryPalette.lightColors] (-1 = Originalfarben der Lichter).
 */
data class SceneryStyle(val hue: Int = -1, val lights: Boolean = true, val lightColor: Int = -1)

object SceneryPalette {
    /** Lichtfarben zur Auswahl; null = die Originalfarben der Lichter (bunt). */
    val lightColors: List<Pair<String, Color?>> = listOf(
        "Original" to null,
        "Cyan" to Color(0xFF00D9FF),
        "Magenta" to Color(0xFFFF1AA6),
        "Gelb" to Color(0xFFFFC107),
        "Violett" to Color(0xFF8C40FF),
        "Grün" to Color(0xFF26FF73),
        "Weiß" to Color(0xFFFFEBD9),
        "Rot" to Color(0xFFFF2A2A),
    )

    /** Als Text speichern: `art|farbton|licht|lichtfarbe;…` (nur Abweichungen vom Standard). */
    fun encode(styles: Map<String, SceneryStyle>): String =
        styles.filter { it.value != SceneryStyle() }.entries.joinToString(";") { (k, v) -> "$k|${v.hue}|${if (v.lights) 1 else 0}|${v.lightColor}" }

    fun decode(text: String?): Map<String, SceneryStyle> {
        if (text.isNullOrBlank()) return emptyMap()
        val out = HashMap<String, SceneryStyle>()
        for (part in text.split(';')) {
            val f = part.split('|')
            if (f.size != 4) continue
            val hue = f[1].toIntOrNull() ?: continue
            val l = f[2].toIntOrNull() ?: continue
            val c = f[3].toIntOrNull() ?: continue
            out[f[0]] = SceneryStyle(hue.coerceIn(-1, 359), l != 0, c.coerceIn(-1, lightColors.lastIndex))
        }
        return out
    }
}

/** Farbton verschieben (nur kräftige Farben, Dunkles und Graues bleibt) und bei „ohne Licht“ leuchtende Stellen abdunkeln. */
fun tintArt(src: android.graphics.Bitmap, hueShift: Int, lightsOff: Boolean): android.graphics.Bitmap {
    if (hueShift < 0 && !lightsOff) return src
    val w = src.width
    val h = src.height
    val px = IntArray(w * h)
    src.getPixels(px, 0, w, 0, 0, w, h)
    val hsv = FloatArray(3)
    for (i in px.indices) {
        val c = px[i]
        val a = android.graphics.Color.alpha(c)
        if (a < 8) continue
        android.graphics.Color.colorToHSV(c, hsv)
        var changed = false
        if (lightsOff) {
            // leuchtende Neonflächen und Lampen: sehr hell und kräftig
            if (hsv[2] > 0.8f && (hsv[1] > 0.3f || hsv[2] > 0.96f)) {
                hsv[2] *= if (hsv[1] > 0.3f) 0.42f else 0.7f
                hsv[1] *= 0.7f
                changed = true
            }
        }
        if (hueShift >= 0 && hsv[1] > 0.2f && hsv[2] > 0.12f) {
            hsv[0] = (hsv[0] + hueShift) % 360f
            changed = true
        }
        if (changed) px[i] = android.graphics.Color.HSVToColor(a, hsv)
    }
    val out = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    out.setPixels(px, 0, w, 0, 0, w, h)
    return out
}

/** Die Bilder aller Gegenstände (fehlen sie, bleibt der Gegenstand unsichtbar statt abzustürzen). */
class SceneryAssets(private val arts: Map<SceneryType, SceneryArt>) {
    fun art(t: SceneryType): SceneryArt? = arts[t]

    companion object {
        val NONE = SceneryAssets(emptyMap())

        fun load(context: Context, styles: Map<String, SceneryStyle> = emptyMap()): SceneryAssets {
            val map = HashMap<SceneryType, SceneryArt>()
            val opts = BitmapFactory.Options().apply { inScaled = false }
            for (t in SceneryType.values()) {
                try {
                    val json = context.assets.open("scenery/${t.id}.json").bufferedReader().use { it.readText() }
                    val bmp = context.assets.open("scenery/${t.id}.png").use { BitmapFactory.decodeStream(it, null, opts) } ?: continue
                    val style = styles[t.id] ?: SceneryStyle()
                    var meta = SceneryMeta.fromJson(json)
                    val lc = SceneryPalette.lightColors.getOrNull(style.lightColor)?.second
                    meta = when {
                        !style.lights -> SceneryMeta(meta.w, meta.h, meta.anchorX, meta.anchorY, emptyList(), null, 0f)
                        lc != null -> SceneryMeta(
                            meta.w, meta.h, meta.anchorX, meta.anchorY,
                            meta.lights.map { SceneryLight(it.x, it.y, it.r, lc, it.speed, it.phase) },
                            meta.groundGlow?.let { lc }, meta.groundRx,
                        )
                        else -> meta
                    }
                    map[t] = SceneryArt(tintArt(bmp, style.hue, !style.lights).asImageBitmap(), meta)
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
fun DrawScope.drawScenery(
    items: List<SceneryItem>,
    assets: SceneryAssets,
    timeSec: Float,
    /** Aufbaufortschritt 0..1 je Gegenstand (der Wichtel stellt sie nacheinander auf); 0 = noch nicht da */
    progressOf: (SceneryItem) -> Float = { 1f },
    /** noch nicht aufgebaute Gegenstände blass andeuten (im Editor, damit man sie platzieren kann) */
    ghost: Boolean = false,
    /** nur die vordere Kante (untere Höhenanteile) zeichnen, z. B. die Bettkante vor dem liegenden Wichtel */
    frontOnlyFrac: Float = 0f,
) {
    val w = size.width
    val h = size.height
    for (item in items) {
        val art = assets.art(item.type) ?: continue
        val prog = progressOf(item).coerceIn(0f, 1f)
        if (prog <= 0f && !ghost) continue
        val m = art.meta
        val hpx = h * item.scale
        val k = hpx / m.h
        val base = Offset(item.at.x * w, item.at.y * h)
        val origin = Offset(base.x - m.anchorX * k, base.y - m.anchorY * k)
        withTransform({
            if (item.rot != 0f) rotate(item.rot, base)
            if (item.flip) scale(-1f, 1f, base)
        }) {
            if (frontOnlyFrac > 0f) {
                clipRect(top = base.y - hpx * frontOnlyFrac) {
                    drawImage(
                        art.image,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(art.image.width, art.image.height),
                        dstOffset = IntOffset(origin.x.toInt(), origin.y.toInt()),
                        dstSize = IntSize((m.w * k).toInt().coerceAtLeast(1), hpx.toInt().coerceAtLeast(1)),
                        filterQuality = FilterQuality.High,
                    )
                }
                return@withTransform
            }
            if (prog <= 0f) {
                // Geisterbild: so sieht man, wo der Gegenstand später steht
                drawImage(
                    art.image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(art.image.width, art.image.height),
                    dstOffset = IntOffset(origin.x.toInt(), origin.y.toInt()),
                    dstSize = IntSize((m.w * k).toInt().coerceAtLeast(1), hpx.toInt().coerceAtLeast(1)),
                    alpha = 0.3f,
                    filterQuality = FilterQuality.Medium,
                )
                return@withTransform
            }
            // Bodenlicht (Neonschein auf dem Boden)
            val gc = m.groundGlow
            if (gc != null && m.groundRx > 0f) {
                val rx = m.groundRx * k
                withTransform({ scale(1f, 0.28f, base) }) {
                    drawCircle(
                        Brush.radialGradient(0f to gc.copy(alpha = 0.4f), 0.55f to gc.copy(alpha = 0.12f), 1f to Color.Transparent, center = base, radius = rx),
                        radius = rx, center = base, blendMode = BlendMode.Plus,
                    )
                }
            }
            // beim Aufbau wächst das Bild von unten nach oben
            clipRect(top = if (prog < 1f) origin.y + hpx * (1f - prog) else 0f) {
                drawImage(
                    art.image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(art.image.width, art.image.height),
                    dstOffset = IntOffset(origin.x.toInt(), origin.y.toInt()),
                    dstSize = IntSize((m.w * k).toInt().coerceAtLeast(1), hpx.toInt().coerceAtLeast(1)),
                    filterQuality = FilterQuality.High,
                )
            }
            for (l in m.lights) {
                if (prog < 1f && origin.y + l.y * k < origin.y + hpx * (1f - prog)) continue
                val a = if (l.speed <= 0f) 1f else (0.55f + 0.45f * sin(2.0 * PI * (l.speed * timeSec + l.phase)).toFloat())
                val c = Offset(origin.x + l.x * k, origin.y + l.y * k)
                val rad = (l.r * k * 1.9f).coerceAtLeast(3f)
                drawCircle(
                    Brush.radialGradient(0f to l.color.copy(alpha = 0.7f * a), 0.3f to l.color.copy(alpha = 0.25f * a), 1f to Color.Transparent, center = c, radius = rad),
                    radius = rad, center = c, blendMode = BlendMode.Plus,
                )
                drawCircle(Color.White.copy(alpha = 0.75f * a), (l.r * k * 0.35f).coerceAtLeast(1f), c, blendMode = BlendMode.Plus)
            }
        }
    }
}
