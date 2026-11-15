package app.wichtelein.anim

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.wichtelein.settings.Settings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import org.json.JSONObject

/** Eine Bewegung des 3D-Wichtels: [headings] Blickrichtungen (gleichmäßig um 360°), je [frames] Bilder. */
class Clip(val headings: Int, val frames: Int)

/** Welches vorgerenderte Bild gehört zur Haltung? */
class FrameChoice(val clip: String, val heading: Int, val frame: Int)

/** Beschreibung der vorgerenderten Bilder (`elf3d.json`, erzeugt von `blender/wichtel3d.py`). */
class Elf3DLayout(val cellW: Int, val cellH: Int, val feet: Offset, val height: Float, val clips: Map<String, Clip>) {
    private fun clip(name: String) = clips.getValue(name)

    private fun headingIndex(heading: Float, n: Int): Int {
        val h = ((heading % 360f) + 360f) % 360f
        return (h / (360f / n)).roundToInt() % n
    }

    /**
     * Wählt Bewegung, Blickrichtung und Bild aus der Haltung der Engine:
     * Besen in der Hand = Fegen (Armwinkel bestimmt den Strich), erhobener Arm = Winken, schwingende Beine = Laufen
     * (Schrittphase bestimmt das Bild), sonst Stehen.
     */
    fun choose(p: Pose): FrameChoice {
        if (p.prop == Prop.BROOM && clips.containsKey("sweep")) {
            val c = clip("sweep")
            val u = ((p.armR - 10f) / 36f).coerceIn(0f, 1f)
            return FrameChoice("sweep", headingIndex(p.heading, c.headings), (u * (c.frames - 1)).roundToInt())
        }
        val walking = abs(p.legL) > 3f
        if (!walking && p.armR in 118f..182f && clips.containsKey("wave")) {
            val c = clip("wave")
            val u = ((p.armR - 125f) / 50f).coerceIn(0f, 1f)
            return FrameChoice("wave", headingIndex(p.heading, c.headings), (u * (c.frames - 1)).roundToInt())
        }
        if (walking && clips.containsKey("walk")) {
            val c = clip("walk")
            val twoPi = (2.0 * PI).toFloat()
            val ph = ((p.stepPhase % twoPi) + twoPi) % twoPi
            return FrameChoice("walk", headingIndex(p.heading, c.headings), ((ph / twoPi) * c.frames).toInt() % c.frames)
        }
        val c = clip("idle")
        return FrameChoice("idle", headingIndex(p.heading, c.headings), 0)
    }

    companion object {
        fun fromJson(json: String): Elf3DLayout {
            val o = JSONObject(json)
            val cell = o.getJSONArray("cell")
            val feet = o.getJSONArray("feet")
            val cl = o.getJSONObject("clips")
            val clips = HashMap<String, Clip>()
            for (k in cl.keys()) {
                val c = cl.getJSONObject(k)
                clips[k] = Clip(c.getInt("headings"), c.getInt("frames"))
            }
            return Elf3DLayout(
                cell.getInt(0), cell.getInt(1),
                Offset(feet.getDouble(0).toFloat(), feet.getDouble(1).toFloat()),
                o.getDouble("height").toFloat(), clips,
            )
        }

        fun fileName(c: FrameChoice) = "elf3d/%s_%02d_%02d.png".format(c.clip, c.heading, c.frame)
    }
}

/**
 * Der 3D-Wichtel: in Blender modelliert und aus allen Richtungen gerendert (`blender/wichtel3d.py`). Er dreht sich in der
 * Szene um 360° (Blickrichtung aus der Laufrichtung), läuft, fegt und winkt mit echten Bildern aus der jeweiligen Richtung.
 * Übrige Bewegungen (Hinsetzen, Purzeln, Schlafen …) entstehen aus dem Stehbild mit Verschiebung, Stauchung und Drehung.
 */
class Elf3D(private val context: Context, private val colors: ElfColors = ElfColors()) : ElfFigure {
    val layout: Elf3DLayout = Elf3DLayout.fromJson(context.assets.open("elf3d/elf3d.json").bufferedReader().use { it.readText() })
    private val cache = object : LruCache<String, ImageBitmap>(80) {}
    private val c = colors.clamped()

    /** Das Bild zu [choice] (aus dem Zwischenspeicher oder von der Platte, mit den gewählten Kleiderfarben). */
    fun image(choice: FrameChoice): ImageBitmap {
        val name = Elf3DLayout.fileName(choice)
        cache.get(name)?.let { return it }
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val src: Bitmap = context.assets.open(name).use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("Bild fehlt: $name")
        val coat = Recolor.apply(src, ElfPalette.clothes[c.jacket], Recolor.COAT_HUE, 0, Recolor::isCoat)
        val out = Recolor.apply(coat, ElfPalette.clothes[c.hat], Recolor.HAT_HUE, 0, Recolor::isHatRed)
        val img = out.asImageBitmap()
        cache.put(name, img)
        return img
    }

    override fun draw(scope: DrawScope, pose: Pose, base: Offset, h: Float, facing: Int) {
        if (pose.scale < 0.02f) return
        val L = layout
        val k = h / L.height
        val choice = L.choose(pose)
        val img = image(choice)
        scope.withTransform({
            translate(base.x, base.y)
            scale(k * pose.scale, k * pose.scale, Offset.Zero)
            translate(-L.feet.x, -L.feet.y)
        }) {
            // Schatten
            drawOval(Color.Black.copy(alpha = 0.25f), Offset(L.feet.x - L.height * 0.22f, L.feet.y - L.height * 0.03f), Size(L.height * 0.44f, L.height * 0.06f))
            withTransform({
                translate(0f, -pose.bob * L.height)
                scale(1f + pose.squash, 1f - pose.squash, L.feet)
                rotate(pose.spin + pose.lean, Offset(L.feet.x, L.feet.y - L.height * 0.4f))
            }) {
                drawImage(
                    img,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(img.width, img.height),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(L.cellW, L.cellH),
                    filterQuality = FilterQuality.High,
                )
                drawHandProp(pose.prop, L)
            }
        }
    }

    /** Keks und Geschenk werden vor der Brust gehalten (der Besen steckt schon in den Bildern). */
    private fun DrawScope.drawHandProp(prop: Prop, L: Elf3DLayout) {
        val cx = L.feet.x
        val cy = L.feet.y - L.height * 0.30f
        when (prop) {
            Prop.COOKIE -> {
                drawCircle(Color(0xFFD9A05B), L.height * 0.05f, Offset(cx, cy - L.height * 0.08f))
                drawCircle(Color(0xFF5B3A24), L.height * 0.009f, Offset(cx - L.height * 0.015f, cy - L.height * 0.095f))
                drawCircle(Color(0xFF5B3A24), L.height * 0.009f, Offset(cx + L.height * 0.02f, cy - L.height * 0.07f))
            }
            Prop.GIFT -> {
                val s = L.height * 0.17f
                drawRect(Color(0xFFD93A3A), Offset(cx - s / 2, cy - s / 2), Size(s, s * 0.85f))
                drawRect(Color(0xFFF2C94C), Offset(cx - s * 0.07f, cy - s / 2), Size(s * 0.14f, s * 0.85f))
                drawRect(Color(0xFFF2C94C), Offset(cx - s / 2, cy - s * 0.1f), Size(s, s * 0.14f))
            }
            else -> Unit
        }
    }

    companion object {
        fun available(context: Context): Boolean = try {
            context.assets.open("elf3d/elf3d.json").close()
            true
        } catch (_: Exception) {
            false
        }
    }
}

/** Wählt die Figur nach den Einstellungen (3D, wenn gewünscht und vorhanden, sonst die 2D-Puppe). */
object ElfFigures {
    fun load(context: Context, settings: Settings): ElfFigure =
        when {
            settings.elfin -> ElfinFigure(settings.colors())
            settings.figure3d && Elf3D.available(context) -> Elf3D(context, settings.colors())
            else -> ElfSprites.load(context, settings.colors())
        }
}

@Composable
fun rememberElfFigure(settings: Settings): ElfFigure {
    val context = LocalContext.current
    return remember(settings.colors(), settings.figure3d, settings.elfin) { ElfFigures.load(context, settings) }
}
