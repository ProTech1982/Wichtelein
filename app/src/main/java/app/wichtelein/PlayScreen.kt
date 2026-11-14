package app.wichtelein

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Seitenverhältnis des Videos, wie es angezeigt wird (Drehung berücksichtigt). */
private fun videoAspect(file: File): Float {
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(file.absolutePath)
        val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull() ?: 9f
        val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull() ?: 16f
        val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (rot == 90 || rot == 270) h / w else w / h
    } catch (_: Exception) {
        9f / 16f
    } finally {
        r.release()
    }
}

@Composable
fun PlayScreen(
    file: File,
    settings: Settings,
    onSettings: (Settings) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val aspect = remember(file.path, file.lastModified()) { videoAspect(file) }
    val speaker = remember { Speaker(context) }
    DisposableEffect(Unit) { onDispose { speaker.shutdown() } }
    SideEffect {
        speaker.enabled = settings.speak
        speaker.pitch = settings.pitch
        speaker.rate = settings.rate
        speaker.mood = settings.mood
        speaker.useElfVoice = settings.elfVoice
    }

    val latest by rememberUpdatedState(settings)
    val engine = remember { ElfEngine({ latest }, { text, mood -> speaker.speak(text, mood) }) }
    val scope = rememberCoroutineScope()
    val jobHolder = remember { arrayOfNulls<Job>(1) }
    var placed by remember { mutableStateOf(false) }

    fun launchElf(first: (suspend () -> Unit)?) {
        jobHolder[0]?.cancel()
        jobHolder[0] = scope.launch { engine.loop(first) }
    }

    val sprites = rememberElfSprites(settings.colorIndex)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.align(Alignment.Center).aspectRatio(aspect)) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoURI(Uri.fromFile(file))
                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            mp.setVolume(0f, 0f)
                            start()
                        }
                    }
                },
                onRelease = { it.stopPlayback() },
                modifier = Modifier.fillMaxSize(),
            )
            ElfOverlay(
                engine = engine,
                sprites = sprites,
                sizeScale = settings.sizeScale,
                onTap = { px, py ->
                    val tx = px.coerceIn(0.05f, 0.95f)
                    val ty = py.coerceIn(0.45f, 0.95f)
                    if (!placed) {
                        placed = true
                        launchElf {
                            engine.appear(tx, ty)
                            engine.greet()
                        }
                    } else {
                        launchElf { engine.walkTo(tx, ty) }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(8.dp),
        ) { Text("← Zurück", color = Color.White, fontSize = 16.sp) }

        if (!placed) {
            Text(
                "Tippe auf den Boden, wo ${settings.elfName.ifBlank { "der Wichtel" }} erscheinen soll.",
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
                    .background(Color(0xAA000000), RoundedCornerShape(16.dp))
                    .padding(16.dp),
            )
        } else {
            Column(
                Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(10.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Stimme: ${speaker.statusText}", color = Color(0xFFFFE08A), fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(onClick = { launchElf { engine.doTidy() } }, Modifier.weight(1f)) { Text("🧹 Aufräumen", fontSize = 13.sp, maxLines = 1) }
                    FilledTonalButton(onClick = { launchElf { engine.doChaos() } }, Modifier.weight(1f)) { Text("🌀 Chaos", fontSize = 13.sp, maxLines = 1) }
                    FilledTonalButton(onClick = { launchElf { engine.runRandom() } }, Modifier.weight(1f)) { Text("🎲 Zufall", fontSize = 13.sp, maxLines = 1) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(
                        onClick = { onSettings(settings.copy(sizeScale = (settings.sizeScale - 0.04f).coerceAtLeast(0.12f))) },
                        Modifier.weight(1f),
                    ) { Text("Kleiner", fontSize = 13.sp, maxLines = 1) }
                    FilledTonalButton(
                        onClick = { onSettings(settings.copy(sizeScale = (settings.sizeScale + 0.04f).coerceAtMost(0.6f))) },
                        Modifier.weight(1f),
                    ) { Text("Größer", fontSize = 13.sp, maxLines = 1) }
                }
            }
        }
    }
}
