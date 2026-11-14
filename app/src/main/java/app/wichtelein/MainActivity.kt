package app.wichtelein

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

enum class Screen { HOME, CAMERA, PLAY }

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

    var screen by remember { mutableStateOf(Screen.HOME) }
    var settings by remember { mutableStateOf(SettingsStore.load(context)) }
    var roomVersion by remember { mutableIntStateOf(if (roomFile.exists()) 1 else 0) }

    fun update(s: Settings) {
        settings = s
        SettingsStore.save(context, s)
    }

    fun adoptRoom(src: File) {
        try {
            src.copyTo(roomFile, overwrite = true)
            src.delete()
            roomVersion++
            screen = Screen.PLAY
        } catch (e: Exception) {
            Toast.makeText(context, "Video konnte nicht gespeichert werden: ${e.message}", Toast.LENGTH_LONG).show()
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
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(r.downloadUrl))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                } catch (e: Exception) {
                    updateText = "Kein Browser zum Öffnen des Downloads gefunden."
                }
            }
        }
    }

    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

    when (screen) {
        Screen.HOME -> HomeScreen(
            settings = settings,
            hasRoom = roomVersion > 0,
            onChange = ::update,
            onFilm = { screen = Screen.CAMERA },
            onPick = { picker.launch("video/*") },
            onUseLast = { screen = Screen.PLAY },
            updateText = updateText,
            onCheckUpdate = ::checkUpdate,
        )
        Screen.CAMERA -> CameraScreen(
            onRecorded = { adoptRoom(it) },
            onBack = { screen = Screen.HOME },
        )
        Screen.PLAY -> PlayScreen(
            file = roomFile,
            settings = settings,
            onSettings = ::update,
            onBack = { screen = Screen.HOME },
        )
    }
}
