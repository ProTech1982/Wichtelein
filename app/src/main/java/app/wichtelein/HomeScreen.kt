package app.wichtelein

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    settings: Settings,
    hasRoom: Boolean,
    onChange: (Settings) -> Unit,
    onFilm: () -> Unit,
    onPick: () -> Unit,
    onUseLast: () -> Unit,
    updateText: String? = null,
    onCheckUpdate: () -> Unit = {},
) {
    Column(
        Modifier
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Wichtelein", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("Film dein Zimmer – dann kommt ${settings.elfName.ifBlank { "der Wichtel" }} und macht Unsinn oder räumt auf!")

        val sprites = rememberElfSprites(settings.colorIndex)
        val pose = androidx.compose.runtime.remember { Pose().apply { armR = 150f } }
        Canvas(Modifier.fillMaxWidth().height(190.dp)) {
            val eh = size.height * 0.92f
            drawElf(pose, sprites, Offset(size.width / 2f, size.height * 0.97f), eh, 1)
        }

        Button(onClick = onFilm, Modifier.fillMaxWidth().height(56.dp)) {
            Text("🎥  Raum filmen", fontSize = 18.sp)
        }
        OutlinedButton(onClick = onPick, Modifier.fillMaxWidth()) { Text("Video aus der Galerie wählen") }
        if (hasRoom) {
            OutlinedButton(onClick = onUseLast, Modifier.fillMaxWidth()) { Text("Letzten Raum nochmal verwenden") }
        }

        Text("Einstellungen", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))

        OutlinedTextField(
            value = settings.elfName,
            onValueChange = { onChange(settings.copy(elfName = it)) },
            label = { Text("Name des Wichtels") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = settings.kidNames,
            onValueChange = { onChange(settings.copy(kidNames = it)) },
            label = { Text("Name(n) des Kindes – mehrere mit Komma trennen") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Farbe der Mütze")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ElfPalettes.forEachIndexed { i, p ->
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(p.hat)
                        .border(
                            width = if (i == settings.colorIndex) 4.dp else 1.dp,
                            color = if (i == settings.colorIndex) Color(0xFF222222) else Color(0xFFBBBBBB),
                            shape = CircleShape,
                        )
                        .clickable { onChange(settings.copy(colorIndex = i)) },
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Wichtel spricht (Sprachausgabe)")
            Switch(checked = settings.speak, onCheckedChange = { onChange(settings.copy(speak = it)) })
        }
        Text("Stimme")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = settings.elfVoice,
                onClick = { onChange(settings.copy(elfVoice = true)) },
                label = { Text("Wichtelstimme") },
            )
            FilterChip(
                selected = !settings.elfVoice,
                onClick = { onChange(settings.copy(elfVoice = false)) },
                label = { Text("Systemstimme") },
            )
        }
        if (settings.elfVoice) {
            Text("Stimmung")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Mood.choices.forEach { (label, id) ->
                    FilterChip(
                        selected = settings.mood == id,
                        onClick = { onChange(settings.copy(mood = id)) },
                        label = { Text(label) },
                    )
                }
            }
        }
        Text("Tonhöhe: tief ↔ hoch")
        Slider(
            value = settings.pitch,
            onValueChange = { onChange(settings.copy(pitch = it)) },
            valueRange = 0.8f..2.0f,
        )
        Text("Sprechtempo: langsam ↔ schnell")
        Slider(
            value = settings.rate,
            onValueChange = { onChange(settings.copy(rate = it)) },
            valueRange = 0.7f..1.4f,
        )

        OutlinedTextField(
            value = settings.customLines,
            onValueChange = { onChange(settings.copy(customLines = it)) },
            label = { Text("Eigene Sätze (einer pro Zeile)") },
            supportingText = { Text("Platzhalter: {kind} = Name des Kindes, {wichtel} = Name des Wichtels") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Build ${Updater.installedVersion(androidx.compose.ui.platform.LocalContext.current)}", fontSize = 13.sp)
        OutlinedButton(onClick = onCheckUpdate, Modifier.fillMaxWidth()) { Text("Nach Update suchen") }
        if (updateText != null) Text(updateText)
    }
}
