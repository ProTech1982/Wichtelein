package app.wichtelein.ui

import app.wichtelein.camera.*
import app.wichtelein.anim.*
import app.wichtelein.scene.*
import app.wichtelein.speech.*
import app.wichtelein.videoexport.*
import app.wichtelein.settings.*

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    settings: Settings,
    onChange: (Settings) -> Unit,
    onBack: () -> Unit,
    updateText: String?,
    onCheckUpdate: () -> Unit,
) {
    Column(
        Modifier
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Zurück") }
            Text("Einstellungen", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        Section("Wichtel")
        OutlinedTextField(
            value = settings.elfName,
            onValueChange = { onChange(settings.copy(elfName = it)) },
            label = { Text("Name des Wichtels") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        ClothingEditor(settings, onChange)
        Text("Standardgröße")
        Slider(value = settings.sizeScale, onValueChange = { onChange(settings.copy(sizeScale = it)) }, valueRange = 0.12f..0.6f)

        Section("Kinder")
        settings.profiles.forEach { pr ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = settings.activeProfile()?.id == pr.id,
                    onClick = { onChange(settings.copy(activeProfileId = pr.id)) },
                )
                OutlinedTextField(
                    value = pr.name,
                    onValueChange = { n -> onChange(settings.copy(profiles = settings.profiles.map { if (it.id == pr.id) it.copy(name = n) else it })) },
                    label = { Text("Name (Geschwister mit Komma)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    val rest = settings.profiles.filter { it.id != pr.id }
                    onChange(settings.copy(profiles = rest, activeProfileId = if (settings.activeProfileId == pr.id) rest.firstOrNull()?.id ?: "" else settings.activeProfileId))
                }) { Text("Löschen") }
            }
        }
        OutlinedButton(onClick = { onChange(settings.withProfile("Kind ${settings.profiles.size + 1}")) }, modifier = Modifier.fillMaxWidth()) {
            Text("➕ Kind hinzufügen")
        }
        Text("Der ausgewählte Punkt ist das aktive Kind. Im Editor lässt sich das Kind auch direkt wechseln.", fontSize = 12.sp, color = Color.Gray)

        Section("Sprache")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Wichtel spricht")
            Switch(checked = settings.speak, onCheckedChange = { onChange(settings.copy(speak = it)) })
        }
        VoiceSettings(settings, onChange)

        Section("Eigene Sätze")
        OutlinedTextField(
            value = settings.customLines,
            onValueChange = { onChange(settings.copy(customLines = it)) },
            label = { Text("Eigene Sätze (einer pro Zeile)") },
            supportingText = { Text("Platzhalter: {name} = Name des Kindes, {wichtel} = Name des Wichtels") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        Section("Datenschutz")
        Text(
            "Alles bleibt auf diesem Gerät: Videos, Namen und Einstellungen. Es gibt kein Konto, keine Werbung und keine Tracker. " +
                "Das Internet wird nur benutzt, wenn du nach Updates suchst oder eine weitere Stimme lädst. " +
                "Die Kamera wird nur zum Filmen gebraucht (ohne Ton).",
            fontSize = 13.sp,
        )

        Section("App")
        Text("Build ${Updater.installedVersion(LocalContext.current)}", fontSize = 13.sp)
        OutlinedButton(onClick = onCheckUpdate, Modifier.fillMaxWidth()) { Text("Nach Update suchen") }
        if (updateText != null) Text(updateText)
    }
}

@Composable
private fun Section(title: String) {
    Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
}

/** Kleidung: Vorschau des Wichtels und Farbwahl für Mütze, Jacke, Strumpfhose und Schuhe. */
@Composable
private fun ClothingEditor(settings: Settings, onChange: (Settings) -> Unit) {
    val sprites = rememberElfSprites(settings.colors())
    val pose = remember { Pose().apply { armR = 12f; armL = 6f } }
    Canvas(Modifier.fillMaxWidth().height(200.dp)) {
        val eh = size.height * 0.93f
        sprites.draw(this, pose, Offset(size.width / 2f, size.height * 0.97f), eh, 1)
    }
    Text("Kleidung", fontWeight = FontWeight.Medium)
    ColorRow("Mütze", ElfPalette.clothes, settings.hatColor) { onChange(settings.copy(hatColor = it)) }
    ColorRow("Jacke", ElfPalette.clothes, settings.jacketColor) { onChange(settings.copy(jacketColor = it)) }
    ColorRow("Strumpfhose", ElfPalette.clothes, settings.legColor) { onChange(settings.copy(legColor = it)) }
    ColorRow("Schuhe", ElfPalette.boots, settings.bootColor) { onChange(settings.copy(bootColor = it)) }
    TextButton(onClick = { onChange(settings.copy(hatColor = 0, jacketColor = 0, legColor = 0, bootColor = 0)) }) {
        Text("Original-Farben")
    }
}

@Composable
private fun ColorRow(label: String, choices: List<ColorChoice>, selected: Int, onPick: (Int) -> Unit) {
    val sel = selected.coerceIn(0, choices.lastIndex)
    Text("$label: ${choices[sel].name}", fontSize = 13.sp)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        choices.forEachIndexed { i, c ->
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(c.swatch)
                    .border(
                        width = if (i == sel) 4.dp else 1.dp,
                        color = if (i == sel) Color(0xFF222222) else Color(0xFFBBBBBB),
                        shape = CircleShape,
                    )
                    .clickable { onPick(i) },
            )
        }
    }
}
