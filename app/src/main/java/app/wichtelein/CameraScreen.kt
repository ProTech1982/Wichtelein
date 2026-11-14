package app.wichtelein

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import kotlinx.coroutines.delay

private const val MAX_SECONDS = 30

/** Filmt den Raum (ohne Ton, deshalb keine Mikrofon-Berechtigung nötig) und liefert die Datei. */
@Composable
fun CameraScreen(onRecorded: (File) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (!granted) {
            Column(
                Modifier.align(Alignment.Center).safeDrawingPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Die App braucht die Kamera, um dein Zimmer zu filmen.", color = Color.White, fontSize = 18.sp)
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Kamera erlauben") }
                TextButton(onClick = onBack) { Text("Zurück", color = Color.White) }
            }
            return@Box
        }

        val previewView = remember {
            PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
        }
        var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
        var recording by remember { mutableStateOf<Recording?>(null) }
        var elapsed by remember { mutableIntStateOf(0) }
        var error by remember { mutableStateOf<String?>(null) }

        DisposableEffect(lifecycleOwner) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                try {
                    val provider = future.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val recorder = Recorder.Builder()
                        .setQualitySelector(
                            QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)),
                        )
                        .build()
                    val capture = VideoCapture.withOutput(recorder)
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                    videoCapture = capture
                } catch (e: Exception) {
                    error = "Kamera konnte nicht gestartet werden: ${e.message}"
                }
            }, ContextCompat.getMainExecutor(context))
            onDispose {
                try {
                    future.get().unbindAll()
                } catch (_: Exception) {
                }
            }
        }

        LaunchedEffect(recording != null) {
            if (recording != null) {
                elapsed = 0
                while (true) {
                    delay(1000)
                    elapsed++
                    if (elapsed >= MAX_SECONDS) {
                        recording?.stop()
                        break
                    }
                }
            }
        }

        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Column(
            Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (recording == null) "Halte das Handy ruhig und filme 5–10 Sekunden." else "Aufnahme läuft: $elapsed s",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.background(Color(0x99000000), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
            )
            error?.let { Text(it, color = Color(0xFFFFB4AB), modifier = Modifier.padding(top = 8.dp)) }
        }

        Box(Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 24.dp)) {
            val recording0 = recording
            Box(
                Modifier
                    .size(78.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(6.dp)
                    .clip(if (recording0 == null) CircleShape else RoundedCornerShape(14.dp))
                    .background(Color(0xFFD93A3A))
                    .clickable {
                        val capture = videoCapture
                        if (recording0 != null) {
                            recording0.stop()
                        } else if (capture != null) {
                            val file = File(context.cacheDir, "raum_${System.currentTimeMillis()}.mp4")
                            recording = capture.output
                                .prepareRecording(context, FileOutputOptions.Builder(file).build())
                                .start(ContextCompat.getMainExecutor(context)) { ev ->
                                    if (ev is VideoRecordEvent.Finalize) {
                                        recording = null
                                        if (!ev.hasError()) onRecorded(file)
                                        else error = "Aufnahme fehlgeschlagen (Code ${ev.error})."
                                    }
                                }
                        }
                    },
            )
        }

        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.BottomStart).safeDrawingPadding().padding(12.dp),
        ) { Text("Zurück", color = Color.White) }
    }
}
