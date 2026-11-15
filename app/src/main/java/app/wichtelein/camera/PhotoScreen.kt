package app.wichtelein.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.view.OrientationEventListener
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
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
import kotlin.math.max

/** Längste Seite des Hintergrundfotos in Pixeln (Video-Ausgabe in Full HD). */
const val MAX_PHOTO_SIDE = 1920

/** Größe eines Fotos (ohne es ganz zu laden); die Dauer ist die Standardlänge eines Foto-Films. */
fun readPhotoInfo(file: File): VideoInfo {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, o)
    val w = if (o.outWidth > 0) o.outWidth else 720
    val h = if (o.outHeight > 0) o.outHeight else 1280
    return VideoInfo(w, h, 60_000L)
}

/**
 * Macht aus einem Foto (beliebige Drehung per EXIF, beliebige Größe) ein JPEG, das die App sicher verarbeiten kann:
 * richtig gedreht, höchstens [MAX_PHOTO_SIDE] Pixel an der langen Seite, gerade Breite und Höhe (Videocodec).
 * Gibt false zurück, wenn das Bild nicht lesbar ist.
 */
fun normalizePhoto(src: File, dst: File): Boolean {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_PHOTO_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = BitmapFactory.decodeFile(src.absolutePath, opts) ?: return false
        val orientation = try {
            ExifInterface(src.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
        }
        var bmp = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        val longSide = max(bmp.width, bmp.height)
        var w = bmp.width
        var h = bmp.height
        if (longSide > MAX_PHOTO_SIDE) {
            val k = MAX_PHOTO_SIDE.toFloat() / longSide
            w = (w * k).toInt()
            h = (h * k).toInt()
        }
        w = max(2, w and 1.inv())
        h = max(2, h and 1.inv())
        if (w != bmp.width || h != bmp.height) bmp = Bitmap.createScaledBitmap(bmp, w, h, true)
        dst.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        true
    } catch (_: Throwable) {
        false
    }
}

/** Macht ein Foto des Raums (z. B. mit dem Wichtelhaus) und liefert die JPEG-Datei. */
@Composable
fun PhotoCameraScreen(onCaptured: (File) -> Unit, onBack: () -> Unit) {
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
                Text("Die App braucht die Kamera, um ein Foto zu machen.", color = Color.White, fontSize = 18.sp)
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Kamera erlauben") }
                TextButton(onClick = onBack) { Text("Zurück", color = Color.White) }
            }
            return@Box
        }

        val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
        var capture by remember { mutableStateOf<ImageCapture?>(null) }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }

        DisposableEffect(lifecycleOwner) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                try {
                    val provider = future.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val ic = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, ic)
                    capture = ic
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

        DisposableEffect(capture) {
            val listener = object : OrientationEventListener(context) {
                override fun onOrientationChanged(orientation: Int) {
                    if (orientation == ORIENTATION_UNKNOWN) return
                    capture?.targetRotation = when (orientation) {
                        in 45..134 -> Surface.ROTATION_270
                        in 135..224 -> Surface.ROTATION_180
                        in 225..314 -> Surface.ROTATION_90
                        else -> Surface.ROTATION_0
                    }
                }
            }
            listener.enable()
            onDispose { listener.disable() }
        }

        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Column(
            Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Fotografiere den Platz, an dem der Wichtel auftreten soll – zum Beispiel sein Wichtelhaus.",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.background(Color(0x99000000), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
            )
            error?.let { Text(it, color = Color(0xFFFFB4AB), modifier = Modifier.padding(top = 8.dp)) }
        }

        Box(Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 24.dp)) {
            Box(
                Modifier
                    .size(78.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(if (busy) Color(0xFF9E9E9E) else Color(0xFF43A047))
                    .clickable(enabled = !busy) {
                        val ic = capture ?: return@clickable
                        busy = true
                        val file = File(context.cacheDir, "foto_${System.currentTimeMillis()}.jpg")
                        ic.takePicture(
                            ImageCapture.OutputFileOptions.Builder(file).build(),
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    busy = false
                                    onCaptured(file)
                                }

                                override fun onError(exc: ImageCaptureException) {
                                    busy = false
                                    error = "Foto fehlgeschlagen: ${exc.message}"
                                }
                            },
                        )
                    },
            )
        }

        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.BottomStart).safeDrawingPadding().padding(12.dp),
        ) { Text("Zurück", color = Color.White) }
    }
}
