package app.wichtelein

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.media.ExifInterface
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.wichtelein.anim.*
import app.wichtelein.camera.*
import app.wichtelein.settings.*
import app.wichtelein.sound.Fx
import app.wichtelein.videoexport.*
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Foto-Film: Foto aufbereiten und daraus ein Video mit Wichtel und Weihnachtsgeräuschen erzeugen. */
@RunWith(AndroidJUnit4::class)
class PhotoFilmTest {
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun makePhoto(w: Int, h: Int, name: String): File {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(AColor.rgb(180, 160, 130))
        val f = File(ctx.cacheDir, name)
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bmp.recycle()
        return f
    }

    private fun size(f: File): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        return o.outWidth to o.outHeight
    }

    @Test fun photoIsNormalizedUprightAndEven() {
        // Querformat mit EXIF-Drehung 90° wird zum Hochformat, ungerade Seiten werden gerade
        val src = makePhoto(301, 201, "p_src.jpg")
        ExifInterface(src.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val dst = File(ctx.cacheDir, "p_dst.jpg")
        assertTrue(normalizePhoto(src, dst))
        assertEquals(200 to 300, size(dst))

        // große Fotos werden auf Full HD verkleinert
        val big = makePhoto(4000, 3000, "p_big.jpg")
        assertTrue(normalizePhoto(big, dst))
        val (w, h) = size(dst)
        assertEquals(MAX_PHOTO_SIDE, max(w, h))
        assertTrue("gerade Seiten: $w x $h", w % 2 == 0 && h % 2 == 0)
        assertTrue("Seitenverhältnis bleibt: $w x $h", abs(w.toFloat() / h - 4f / 3f) < 0.02f)

        // kaputte Datei: kein Absturz, sondern false
        val bad = File(ctx.cacheDir, "p_bad.jpg").apply { writeText("kein Bild") }
        assertFalse(normalizePhoto(bad, dst))
        val info = readPhotoInfo(dst)
        assertTrue(info.width > 0 && info.height > 0)
    }

    @Test fun photoFilmExportsVideoWithSoundAndElf() {
        val photo = makePhoto(640, 360, "film_foto.jpg")
        ExportManager.exportsDir(ctx).listFiles()?.forEach { it.delete() }
        val settings = Settings(elfName = "Pippo", onboarded = true, speak = false, soundFx = true).withProfile("Max")
        val perf = Recorder.record(
            PerfRequest(
                start = Pt(0.5f, 0.8f), sizeScale = 0.5f, seed = 3L, durationMs = 60_000L, settings = settings,
                script = listOf(ScriptStep(ElfAction.WAVE, Pt(0.5f, 0.8f))), stopAfterScript = true,
            ),
        )
        assertTrue("Film zu kurz: ${perf.durationMs}", perf.durationMs > 4000L)
        assertTrue("Geräusche vorhanden", perf.sounds.any { it.fx == Fx.JINGLE })

        val info = VideoInfo(640, 360, perf.durationMs)
        val result = runBlocking { ExportManager.export(ctx, photo, info, perf, settings, {}, isPhoto = true) }
        val out = result.file
        assertTrue("Datei zu klein: ${out.length()}", out.length() > 10_000)

        val ex = MediaExtractor()
        ex.setDataSource(out.absolutePath)
        val mimes = (0 until ex.trackCount).map { ex.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME) ?: "" }
        ex.release()
        assertTrue("keine Videospur: $mimes", mimes.any { it.startsWith("video/") })
        assertTrue("keine Tonspur (Geräusche): $mimes", mimes.any { it.startsWith("audio/") })

        val outInfo = readVideoInfo(out)
        assertTrue("Dauer ${outInfo.durationMs} statt ${perf.durationMs}", abs(outInfo.durationMs - perf.durationMs) < 1000)
        assertTrue("Seitenverhältnis ${outInfo.width}x${outInfo.height}", abs(outInfo.aspect - 640f / 360f) < 0.05f)

        // Der Wichtel (rot/grün/weiß) ist im Bild: viele Pixel weichen vom einfarbigen Hintergrund ab
        val r = MediaMetadataRetriever()
        r.setDataSource(out.absolutePath)
        val frame = r.getFrameAtTime(2_000_000L, MediaMetadataRetriever.OPTION_CLOSEST)
        r.release()
        assertTrue("kein Bild", frame != null)
        var diff = 0
        for (y in 0 until frame!!.height step 2) for (x in 0 until frame.width step 2) {
            val c = frame.getPixel(x, y)
            val d = abs(AColor.red(c) - 180) + abs(AColor.green(c) - 160) + abs(AColor.blue(c) - 130)
            if (d > 90) diff++
        }
        val total = (frame.width / 2) * (frame.height / 2)
        assertTrue("Wichtel nicht im Bild: $diff von $total Punkten", diff > total / 60)
    }
}
