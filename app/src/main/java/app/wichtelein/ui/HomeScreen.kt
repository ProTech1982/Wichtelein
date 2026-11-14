package app.wichtelein.ui

import app.wichtelein.camera.*
import app.wichtelein.anim.*
import app.wichtelein.scene.*
import app.wichtelein.speech.*
import app.wichtelein.videoexport.*
import app.wichtelein.settings.*

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(
    settings: Settings,
    hasRoom: Boolean,
    videoCount: Int,
    onFilm: () -> Unit,
    onPick: () -> Unit,
    onVideos: () -> Unit,
    onActions: () -> Unit,
    onSettings: () -> Unit,
    onUseLast: () -> Unit,
    onFirstProfile: (String?) -> Unit,
) {
    Column(
        Modifier
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Wichtelein", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        val kid = settings.kidList().firstOrNull()
        Text(
            "Film dein Zimmer – dann kommt ${settings.elfName.ifBlank { "der Wichtel" }}" +
                (if (kid != null) " zu $kid" else "") + " und macht Unsinn oder räumt auf!",
        )

        val sprites = rememberElfSprites(settings.colorIndex)
        val pose = remember { Pose().apply { armR = 150f } }
        Canvas(Modifier.fillMaxWidth().height(170.dp)) {
            val eh = size.height * 0.92f
            drawElf(pose, sprites, Offset(size.width / 2f, size.height * 0.97f), eh, 1)
        }

        BigButton("🎥", "Video aufnehmen", "Den Raum filmen, 5 bis 10 Sekunden", onFilm)
        BigButton("🖼", "Video auswählen", "Ein vorhandenes Video aus der Galerie", onPick)
        if (hasRoom) BigButton("✏️", "Letztes Video weiter bearbeiten", "Das zuletzt benutzte Video noch einmal öffnen", onUseLast)
        BigButton("🎞", "Meine Wichtelvideos", if (videoCount == 0) "Noch keine fertigen Videos" else "$videoCount fertige Videos", onVideos)
        BigButton("🎭", "Wichtelaktionen", "Brav, frech oder Überraschung – was darf er tun?", onActions)
        BigButton("⚙️", "Einstellungen", "Namen, Stimme, Farbe, Profile", onSettings)
    }

    if (!settings.onboarded) FirstStartDialog(onFirstProfile)
}

@Composable
private fun BigButton(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(emoji, fontSize = 30.sp)
            Column {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, fontSize = 13.sp)
            }
        }
    }
}

/** Beim ersten Start gibt ein Erwachsener den Namen des Kindes ein. */
@Composable
private fun FirstStartDialog(onDone: (String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Willkommen bei Wichtelein!") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Für Eltern: Wie heißt das Kind? Der Wichtel spricht es damit an. Weitere Kinder lassen sich später in den Einstellungen hinzufügen.")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name des Kindes") },
                    singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onDone(name.trim().ifEmpty { null }) }, enabled = name.isNotBlank()) { Text("Weiter") } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Später") } },
    )
}
