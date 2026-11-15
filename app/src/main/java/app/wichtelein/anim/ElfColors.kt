package app.wichtelein.anim

import android.graphics.Bitmap
import android.graphics.Color as AColor
import androidx.compose.ui.graphics.Color

/** Eine Farbwahl für ein Kleidungsstück. [hue] = Zielton in Grad, null = Originalfarbe des Bildes. */
class ColorChoice(val name: String, val swatch: Color, val hue: Float?, val sat: Float = 1f, val value: Float = 1f)

/** Gewählte Farben der Kleidung (Nummern in [ElfPalette]). */
data class ElfColors(val hat: Int = 0, val jacket: Int = 0, val legs: Int = 0, val boots: Int = 0) {
    fun clamped() = ElfColors(
        hat.coerceIn(0, ElfPalette.clothes.lastIndex),
        jacket.coerceIn(0, ElfPalette.clothes.lastIndex),
        legs.coerceIn(0, ElfPalette.clothes.lastIndex),
        boots.coerceIn(0, ElfPalette.boots.lastIndex),
    )
}

object ElfPalette {
    /** Für Mütze, Jacke und Hose (das Original: rote Mütze, grüner Mantel, braune Hose). */
    val clothes = listOf(
        ColorChoice("Original", Color(0xFF9A9A9A), null),
        ColorChoice("Rot", Color(0xFFD93A3A), 0f),
        ColorChoice("Orange", Color(0xFFF08A24), 28f),
        ColorChoice("Gelb", Color(0xFFF2C94C), 48f),
        ColorChoice("Hellblau", Color(0xFF5BB5F0), 200f, 0.8f, 1.1f),
        ColorChoice("Blau", Color(0xFF3A7BD9), 220f),
        ColorChoice("Lila", Color(0xFF8E4FD9), 275f),
        ColorChoice("Pink", Color(0xFFE86AA8), 330f, 0.75f, 1.15f),
        ColorChoice("Weiß", Color(0xFFF2F2F2), 0f, 0.06f, 1.45f),
        ColorChoice("Grau", Color(0xFF9A9A9A), 0f, 0.08f, 0.85f),
        ColorChoice("Schwarz", Color(0xFF2A2A2A), 0f, 0.12f, 0.4f),
    )

    /** Für die Schuhe (das Original ist rotbraun). */
    val boots = listOf(
        ColorChoice("Original (braun)", Color(0xFF6B4423), null),
        ColorChoice("Schwarz", Color(0xFF2A2A2A), 0f, 0.12f, 0.45f),
        ColorChoice("Braun", Color(0xFF6B4423), 28f, 0.7f, 0.75f),
        ColorChoice("Rot", Color(0xFFD93A3A), 0f, 1.0f, 1.25f),
        ColorChoice("Blau", Color(0xFF3A7BD9), 220f, 1.0f, 0.95f),
        ColorChoice("Grün", Color(0xFF2E9E4F), 130f, 0.9f, 0.95f),
        ColorChoice("Gelb", Color(0xFFF2C94C), 48f, 1.0f, 1.2f),
        ColorChoice("Weiß", Color(0xFFF2F2F2), 0f, 0.06f, 1.5f),
    )
}

/** Umfärben der Bildteile zur Laufzeit (HSV), getrennt nach Kleidungsstück. */
object Recolor {
    /** Winkeldifferenz in Grad, im Bereich -180..180. */
    fun hueDiff(h: Float, ref: Float): Float {
        var d = (h - ref) % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return d
    }

    /** Mittlere Farbtöne der Originalteile (Grad). */
    const val HAT_HUE = 0f
    const val COAT_HUE = 160f
    const val TROUSER_HUE = 36f
    const val BOOT_HUE = 24f

    /** Rote Mütze samt Band (kräftiges Rot; Nase und Wangen sind blasser und bleiben unberührt). */
    fun isHatRed(h: Float, s: Float, v: Float) = (h <= 14f || h >= 340f) && s > 0.66f && v > 0.2f

    /** Grüner Mantel. */
    fun isCoat(h: Float, s: Float, v: Float) = h in 90f..200f && s > 0.22f && v > 0.08f

    /** Braune Hose (heller, blasser als die Stiefel). */
    fun isTrousers(h: Float, s: Float, v: Float) = h in 12f..70f && s > 0.12f && v > 0.12f

    /** Braune Stiefel. */
    fun isBoot(h: Float, s: Float, v: Float) = h in 0f..45f && s > 0.3f && v > 0.1f

    /**
     * Färbt alle Pixel um, auf die [match] zutrifft (und deren Zeile [rowFrom] oder tiefer liegt).
     * [refHue] ist der Mittelton des Originals (Grün 120, Rotbraun 0).
     */
    fun apply(src: Bitmap, choice: ColorChoice, refHue: Float, rowFrom: Int = 0, match: (Float, Float, Float) -> Boolean): Bitmap =
        applyRows(src, choice, refHue, rowFrom, src.height, match)

    /** Wie [apply], aber nur für die Zeilen [rowFrom] bis [rowTo] (ausschließlich). */
    fun applyRows(src: Bitmap, choice: ColorChoice, refHue: Float, rowFrom: Int, rowTo: Int, match: (Float, Float, Float) -> Boolean): Bitmap {
        val target = choice.hue ?: return src
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val hsv = FloatArray(3)
        for (y in rowFrom until minOf(rowTo, h)) {
            for (x in 0 until w) {
                val c = px[y * w + x]
                val a = AColor.alpha(c)
                if (a < 8) continue
                AColor.colorToHSV(c, hsv)
                if (!match(hsv[0], hsv[1], hsv[2])) continue
                val spread = hueDiff(hsv[0], refHue)
                hsv[0] = ((target + spread) % 360f + 360f) % 360f
                hsv[1] = (hsv[1] * choice.sat).coerceIn(0f, 1f)
                hsv[2] = (hsv[2] * choice.value).coerceIn(0f, 1f)
                px[y * w + x] = AColor.HSVToColor(a, hsv)
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }
}
