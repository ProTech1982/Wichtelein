package app.wichtelein

import android.media.MediaMetadataRetriever
import java.io.File

/** Angezeigte Größe (Drehung berücksichtigt) und Länge eines Videos. */
class VideoInfo(val width: Int, val height: Int, val durationMs: Long) {
    val aspect: Float get() = width.toFloat() / height.toFloat()
}

fun readVideoInfo(file: File): VideoInfo {
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(file.absolutePath)
        val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 720
        val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1280
        val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 10_000L
        if (rot == 90 || rot == 270) VideoInfo(h, w, d) else VideoInfo(w, h, d)
    } catch (_: Exception) {
        VideoInfo(720, 1280, 10_000L)
    } finally {
        r.release()
    }
}
