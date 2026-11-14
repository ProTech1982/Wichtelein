package app.wichtelein.videoexport

import app.wichtelein.camera.*
import app.wichtelein.anim.*
import app.wichtelein.scene.*
import app.wichtelein.speech.*
import app.wichtelein.settings.*
import app.wichtelein.ui.*

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.FileProvider
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class ExportResultInfo(val file: File, val galleryUri: Uri?, val hasSpeech: Boolean)

sealed class ExportUi {
    class Running(val progress: Float, val text: String = "Video wird erstellt …") : ExportUi()
    class Done(val result: ExportResultInfo) : ExportUi()
    class Failed(val message: String) : ExportUi()
}

/** Erzeugt das fertige MP4: Originalvideo + Wichtel-Overlay + gesprochene Sätze, danach in die Galerie kopieren. */
object ExportManager {
    fun exportsDir(context: Context) = File(context.filesDir, "exports").apply { mkdirs() }

    @OptIn(UnstableApi::class)
    suspend fun export(
        context: Context,
        video: File,
        info: VideoInfo,
        perf: Performance,
        settings: Settings,
        onProgress: (Float) -> Unit,
    ): ExportResultInfo {
        val app = context.applicationContext
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.GERMANY).format(Date())
        val out = File(exportsDir(app), "Wichtelein-$stamp.mp4")
        val sprites = ElfSprites.load(app, settings.colorIndex)

        // 1) Sprache (nur mit der Wichtelstimme als Rohdaten möglich)
        var wav: File? = null
        var hasSpeech = false
        val clips: List<VoiceAudio?> = when {
            !settings.speak -> emptyList()
            SpeechRender.awaitVoice(app, settings, 60_000) ->
                withContext(Dispatchers.Default) { perf.speech.map { SpeechRender.render(settings, it) } }
            // Systemstimme (oder Wichtelstimme nicht verfügbar): Sprache über synthesizeToFile erzeugen
            else -> withContext(Dispatchers.Default) { SystemTtsRender.renderAll(app, settings, perf.speech) }
        }
        if (clips.any { it != null }) {
            val pcm = SpeechMixer.mix(perf.speech, clips, info.durationMs)
            wav = File(app.cacheDir, "wichtel-sprache.wav").also { it.writeBytes(SpeechMixer.toWav(pcm)) }
            hasSpeech = true
        }

        // 2) Overlay: pro Videobild die passende Szene der Aufführung
        val w = info.width
        val h = info.height
        val measurer = TextMeasurer(createFontFamilyResolver(app), Density(1f), LayoutDirection.Ltr)
        val overlay = object : BitmapOverlay() {
            override fun getBitmap(presentationTimeUs: Long): Bitmap {
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp.asImageBitmap())
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
                    drawWichtelFrame(perf.frameAt(presentationTimeUs / 1000L), sprites, settings.sizeScale, measurer)
                }
                return bmp
            }
        }
        val videoEffects = ImmutableList.of<Effect>(OverlayEffect(ImmutableList.of(overlay)))
        val videoItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(video)))
            .setEffects(Effects(ImmutableList.of<AudioProcessor>(), videoEffects))
            .build()
        val sequences = ArrayList<EditedMediaItemSequence>()
        sequences.add(EditedMediaItemSequence(videoItem))
        wav?.let {
            val audioItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(it))).setRemoveVideo(true).build()
            sequences.add(EditedMediaItemSequence(audioItem))
        }
        val composition = Composition.Builder(sequences).build()

        // 3) Transformer läuft auf dem Hauptthread
        try {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine<Unit> { cont ->
                    val handler = Handler(Looper.getMainLooper())
                    val holder = ProgressHolder()
                    lateinit var transformer: Transformer
                    val poll = object : Runnable {
                        override fun run() {
                            if (!cont.isActive) return
                            if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                                onProgress((holder.progress / 100f).coerceIn(0f, 1f))
                            }
                            handler.postDelayed(this, 300)
                        }
                    }
                    transformer = Transformer.Builder(app)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                handler.removeCallbacks(poll)
                                if (cont.isActive) cont.resume(Unit)
                            }

                            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                                handler.removeCallbacks(poll)
                                if (cont.isActive) cont.resumeWithException(exportException)
                            }
                        })
                        .build()
                    transformer.start(composition, out.absolutePath)
                    handler.post(poll)
                    cont.invokeOnCancellation {
                        handler.removeCallbacks(poll)
                        transformer.cancel()
                    }
                }
            }
        } catch (t: Throwable) {
            out.delete()
            throw IllegalStateException("Export fehlgeschlagen: ${t.message ?: t.javaClass.simpleName}", t)
        } finally {
            wav?.delete()
        }
        if (!out.exists() || out.length() < 1000) throw IllegalStateException("Export lieferte keine Datei")
        onProgress(1f)
        return ExportResultInfo(out, saveToGallery(app, out), hasSpeech)
    }

    /** Legt eine Kopie in „Filme/Wichtelein“ der Galerie ab. */
    fun saveToGallery(context: Context, file: File): Uri? = try {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Wichtelein")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { o -> file.inputStream().use { it.copyTo(o) } }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        uri
    } catch (_: Exception) {
        null
    }

    fun fileUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun share(context: Context, file: File) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, fileUri(context, file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Wichtelvideo teilen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun view(context: Context, file: File) {
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri(context, file), "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(i)
        } catch (_: Exception) {
        }
    }
}

@Composable
fun ExportDialog(state: ExportUi?, onClose: () -> Unit, onOpenVideos: () -> Unit) {
    val context = LocalContext.current
    when (state) {
        null -> Unit
        is ExportUi.Running -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Wichtelvideo wird erstellt") },
            text = {
                Column {
                    Text(state.text)
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    Text("${(state.progress * 100).toInt()} %")
                }
            },
        )
        is ExportUi.Done -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text("Fertig! 🎉") },
            text = {
                Text(
                    (if (state.result.galleryUri != null) "Das Video liegt in der Galerie (Filme/Wichtelein) und unter „Meine Wichtelvideos“." else "Das Video liegt unter „Meine Wichtelvideos“.") +
                        (if (!state.result.hasSpeech) "\n\nHinweis: Es konnte keine Sprache erzeugt werden (keine deutsche Stimme verfügbar oder Sprache aus). Das Video zeigt nur Sprechblasen." else ""),
                )
            },
            confirmButton = { TextButton(onClick = { ExportManager.share(context, state.result.file) }) { Text("Teilen") } },
            dismissButton = {
                Column {
                    TextButton(onClick = { ExportManager.view(context, state.result.file) }) { Text("Ansehen") }
                    TextButton(onClick = onOpenVideos) { Text("Meine Wichtelvideos") }
                    TextButton(onClick = onClose) { Text("Schließen") }
                }
            },
        )
        is ExportUi.Failed -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text("Export fehlgeschlagen") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = onClose) { Text("OK") } },
        )
    }
}
