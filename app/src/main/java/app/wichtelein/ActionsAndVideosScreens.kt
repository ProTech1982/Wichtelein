package app.wichtelein

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Wichtelaktionen: welche Szenen der Wichtel spielen darf (je Kategorie ein-/ausschaltbar). */
@Composable
fun ActionsScreen(settings: Settings, onChange: (Settings) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Zurück") }
            Text("Wichtelaktionen", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "Hier bestimmst du, welche Geschichten der Wichtel spielen darf. Im Editor kannst du außerdem eine Szene gezielt auswählen oder würfeln.",
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Category.values().forEach { c ->
                val scenes = Scenes.inCategory(c)
                item(key = "h_${c.name}") {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${c.emoji} ${c.label}", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        val allOn = scenes.none { it.id in settings.disabledScenes }
                        TextButton(onClick = {
                            val ids = scenes.map { it.id }.toSet()
                            onChange(settings.copy(disabledScenes = if (allOn) settings.disabledScenes + ids else settings.disabledScenes - ids))
                        }) { Text(if (allOn) "Alle aus" else "Alle an") }
                    }
                }
                items(scenes, key = { it.id }) { s ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Text(s.description, fontSize = 12.sp)
                            }
                            Switch(
                                checked = s.id !in settings.disabledScenes,
                                onCheckedChange = { on ->
                                    onChange(settings.copy(disabledScenes = if (on) settings.disabledScenes - s.id else settings.disabledScenes + s.id))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

fun listExports(context: android.content.Context): List<File> =
    ExportManager.exportsDir(context).listFiles { f -> f.extension == "mp4" }?.sortedByDescending { it.lastModified() } ?: emptyList()

/** Meine Wichtelvideos: fertige Videos ansehen, teilen, löschen. */
@Composable
fun MyVideosScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val files = remember(version) { listExports(context) }
    var toDelete by remember { mutableStateOf<File?>(null) }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Zurück") }
            Text("Meine Wichtelvideos", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        if (files.isEmpty()) {
            Text("Noch keine fertigen Videos. Nimm ein Video auf, wähle im Editor eine Szene und tippe auf „Als Video speichern“.", modifier = Modifier.padding(16.dp))
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(files, key = { it.path }) { f ->
                val thumb by produceState<ImageBitmap?>(null, f.path) { value = withContext(Dispatchers.IO) { thumbnail(f) } }
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(84.dp).background(Color.Black)) {
                            thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(f.lastModified())), fontWeight = FontWeight.Medium)
                            Text("${f.length() / 1024} KB", fontSize = 12.sp)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                val pad = PaddingValues(horizontal = 6.dp)
                                OutlinedButton(onClick = { ExportManager.view(context, f) }, Modifier.weight(1f), contentPadding = pad) { Text("▶", fontSize = 13.sp, maxLines = 1) }
                                OutlinedButton(onClick = { ExportManager.share(context, f) }, Modifier.weight(2f), contentPadding = pad) { Text("Teilen", fontSize = 13.sp, maxLines = 1) }
                                OutlinedButton(onClick = { toDelete = f }, Modifier.weight(1f), contentPadding = pad) { Text("🗑", fontSize = 13.sp, maxLines = 1) }
                            }
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Video löschen?") },
            text = { Text("Das Video verschwindet aus „Meine Wichtelvideos“. Die Kopie in der Galerie bleibt bestehen.") },
            confirmButton = { TextButton(onClick = { f.delete(); toDelete = null; version++ }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Abbrechen") } },
        )
    }
}

private fun thumbnail(file: File): ImageBitmap? {
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(file.absolutePath)
        r.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { Bitmap.createScaledBitmap(it, 168, 168 * it.height / it.width.coerceAtLeast(1), true).asImageBitmap() }
    } catch (_: Exception) {
        null
    } finally {
        r.release()
    }
}
