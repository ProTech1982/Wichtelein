package app.wichtelein

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
        Text("Farbe")
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
