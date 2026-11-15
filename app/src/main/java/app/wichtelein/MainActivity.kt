package app.wichtelein

import app.wichtelein.camera.*
import app.wichtelein.anim.*
import app.wichtelein.ar.ArScreen
import app.wichtelein.scene.*
import app.wichtelein.speech.*
import app.wichtelein.videoexport.*
import app.wichtelein.settings.*
import app.wichtelein.ui.*

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { HOME, CAMERA, PHOTO_CAMERA, EDITOR, VIDEOS, ACTIONS, SETTINGS, AR }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF2E9E4F),
                    secondary = Color(0xFFD93A3A),
                    background = Color(0xFFFFF8EC),
                    surface = Color(0xFFFFF8EC),
                    surfaceVariant = Color(0xFFF1E6CF),
                ),
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    WichtelApp()
                }
            }
        }
    }
}

@Composable
fun WichtelApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val roomFile = remember { File(context.filesDir, "room.mp4") }
    val photoFile = remember { File(context.filesDir, "room.jpg") }

    var screen by remember { mutableStateOf(Screen.HOME) }
    var settings by remember { mutableStateOf(SettingsStore.load(context)) }
    var roomVersion by remember { mutableIntStateOf(if (roomFile.exists() || photoFile.exists()) 1 else 0) }
    // zuletzt benutzt: Foto oder Video?
    var roomIsPhoto by remember { mutableStateOf(photoFile.exists() && (!roomFile.exists() || photoFile.lastModified() >= roomFile.lastModified())) }
    var videoCount by remember { mutableIntStateOf(listExports(context).size) }

    fun update(s: Settings) {
        settings = s
        SettingsStore.save(context, s)
    }

    fun adoptRoom(src: File) {
        try {
            src.copyTo(roomFile, overwrite = true)
            src.delete()
            roomIsPhoto = false
            roomVersion++
            screen = Screen.EDITOR
        } catch (e: Exception) {
            Toast.makeText(context, "Video konnte nicht gespeichert werden: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /** Übernimmt ein Foto (Kamera oder Galerie): drehen, verkleinern, als Hintergrund des Foto-Films speichern. */
    fun adoptPhoto(src: File) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { normalizePhoto(src, photoFile) }
            src.delete()
            if (ok) {
                roomIsPhoto = true
                roomVersion++
                screen = Screen.EDITOR
            } else {
                Toast.makeText(context, "Das Foto konnte nicht gelesen werden.", Toast.LENGTH_LONG).show()
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val tmp = File(context.cacheDir, "import_foto.jpg")
                val ok = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            tmp.outputStream().use { input.copyTo(it) }
                        } != null
                    } catch (_: Exception) {
                        false
                    }
                }
                if (ok) adoptPhoto(tmp)
                else Toast.makeText(context, "Das Foto konnte nicht gelesen werden.", Toast.LENGTH_LONG).show()
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val tmp = File(context.cacheDir, "import.mp4")
                val ok = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            tmp.outputStream().use { input.copyTo(it) }
                        } != null
                    } catch (_: Exception) {
                        false
                    }
                }
                if (ok) adoptRoom(tmp)
                else Toast.makeText(context, "Video konnte nicht gelesen werden.", Toast.LENGTH_LONG).show()
            }
        }
    }

    var updateText by remember { mutableStateOf<String?>(null) }
    fun checkUpdate() {
        updateText = "Suche nach Updates …"
        scope.launch {
            val r = Updater.check(context)
            updateText = when {
                r.error != null -> r.error
                r.available -> "Neue Version gefunden: ${r.version}. Der Download startet im Browser."
                else -> "Du hast die neueste Version."
            }
            if (r.available && r.downloadUrl != null) {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.downloadUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    updateText = "Kein Browser zum Öffnen des Downloads gefunden."
                }
            }
        }
    }

    BackHandler(enabled = screen != Screen.HOME) {
        if (screen == Screen.VIDEOS) videoCount = listExports(context).size
        screen = Screen.HOME
    }

    when (screen) {
        Screen.HOME -> HomeScreen(
            settings = settings,
            hasRoom = roomVersion > 0,
            roomIsPhoto = roomIsPhoto,
            videoCount = videoCount,
            onFilm = { screen = Screen.CAMERA },
            onPick = { picker.launch("video/*") },
            onPhoto = { screen = Screen.PHOTO_CAMERA },
            onPickPhoto = { photoPicker.launch("image/*") },
            onVideos = { videoCount = listExports(context).size; screen = Screen.VIDEOS },
            onActions = { screen = Screen.ACTIONS },
            onSettings = { screen = Screen.SETTINGS },
            onAr = { screen = Screen.AR },
            onUseLast = { screen = Screen.EDITOR },
            onFirstProfile = { name ->
                val base = settings.copy(onboarded = true)
                update(if (name != null) base.withProfile(name) else base)
            },
        )
        Screen.CAMERA -> CameraScreen(
            onRecorded = { adoptRoom(it) },
            onBack = { screen = Screen.HOME },
        )
        Screen.PHOTO_CAMERA -> PhotoCameraScreen(
            onCaptured = { adoptPhoto(it) },
            onBack = { screen = Screen.HOME },
        )
        Screen.EDITOR -> EditorScreen(
            file = if (roomIsPhoto) photoFile else roomFile,
            isPhoto = roomIsPhoto,
            settings = settings,
            onSettings = ::update,
            onBack = { videoCount = listExports(context).size; screen = Screen.HOME },
            onOpenVideos = { videoCount = listExports(context).size; screen = Screen.VIDEOS },
        )
        Screen.AR -> ArScreen(settings, ::update, onBack = { screen = Screen.HOME })
        Screen.VIDEOS -> MyVideosScreen(onBack = { videoCount = listExports(context).size; screen = Screen.HOME })
        Screen.ACTIONS -> ActionsScreen(settings, ::update, onBack = { screen = Screen.HOME })
        Screen.SETTINGS -> SettingsScreen(
            settings = settings,
            onChange = ::update,
            onBack = { screen = Screen.HOME },
            updateText = updateText,
            onCheckUpdate = ::checkUpdate,
        )
    }
}
