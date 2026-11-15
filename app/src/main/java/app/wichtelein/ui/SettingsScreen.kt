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
import androidx.compose.foundation.layout.width
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

        Section("Gegenstände: Farbe und Licht")
        SceneryStyles(settings, onChange)

        Section("Sprechblase (Standard)")
        Text("Gilt für alle Aktionen. Im Editor lässt sich für jede Aktion eine eigene Blase einstellen.", fontSize = 12.sp, color = Color.Gray)
        BubbleControls(settings.bubble, { onChange(settings.copy(bubble = it)) })

        Section("Geräusche")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Weihnachtsgeräusche (Glöckchen, Tür, Besen, Klopfen)")
            Switch(checked = settings.soundFx, onCheckedChange = { onChange(settings.copy(soundFx = it)) })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Summen und Brummen", modifier = Modifier.weight(1f))
            Switch(checked = settings.hum, onCheckedChange = { onChange(settings.copy(hum = it)) })
        }
        Text(
            "Ist die Stimme aus, brummt der Wichtel statt zu sprechen. Ist sie an, summt er in den Pausen kleine Weihnachtsmelodien dazu. " +
                "Beides lässt sich mit Stimme und Effekt kombinieren.",
            fontSize = 12.sp,
            color = Color.Gray,
        )

        Section("Eigene Sätze")
        OutlinedTextField(
            value = settings.customLines,
            onValueChange = { onChange(settings.copy(customLines = it)) },
            label = { Text("Eigene Sätze (einer pro Zeile)") },
            supportingText = { Text("Platzhalter: {name} = Name des Kindes, {wichtel} = Name des Wichtels") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        Section("Aussehen der App")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Wie das Handy", "Hell", "Dunkel").forEachIndexed { i, label ->
                androidx.compose.material3.FilterChip(selected = settings.themeMode == i, onClick = { onChange(settings.copy(themeMode = i)) }, label = { Text(label) })
            }
        }

        Section("Datenschutz")
        Text(
            "Alles bleibt auf diesem Gerät: Videos, Namen und Einstellungen. Es gibt kein Konto, keine Werbung und keine Tracker. " +
                "Das Internet wird nur benutzt, wenn du nach Updates suchst oder eine weitere Stimme lädst. " +
                "Die Kamera wird nur zum Filmen und Fotografieren gebraucht (ohne Ton).",
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

/** Kleidung: Vorschau des Wichtels und Farbwahl für Mütze, Mantel, Hose und Schuhe. */
@Composable
private fun ClothingEditor(settings: Settings, onChange: (Settings) -> Unit) {
    val sprites = rememberElfFigure(settings)
    val pose = remember { Pose().apply { armR = 12f; armL = 6f } }
    Canvas(Modifier.fillMaxWidth().height(200.dp)) {
        val eh = size.height * 0.93f
        sprites.draw(this, pose, Offset(size.width / 2f, size.height * 0.97f), eh, 1)
    }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Text("Wer erscheint?", fontWeight = FontWeight.Medium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.FilterChip(selected = !settings.elfin, onClick = { onChange(settings.copy(elfin = false)) }, label = { Text("🧙 Wichtel") })
        androidx.compose.material3.FilterChip(selected = settings.elfin, onClick = { onChange(settings.copy(elfin = true)) }, label = { Text("🧝‍♀️ Wichtelfrau (Elfin)") })
    }
    if (settings.elfin) Text("Die Elfin hat Mütze, Kleid, Strumpfhose und Stiefel zum Umfärben (Mantel = Kleid, Hose = Strumpfhose). Sie ist als 2D-Figur gezeichnet.", fontSize = 12.sp, color = Color.Gray)
    if (Elf3D.available(ctx) && !settings.elfin) {
        Text("Figur", fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.FilterChip(
                selected = !settings.figure3d,
                onClick = { onChange(settings.copy(figure3d = false)) },
                label = { Text("Wichtel 2D (Bild)") },
            )
            androidx.compose.material3.FilterChip(
                selected = settings.figure3d,
                onClick = { onChange(settings.copy(figure3d = true)) },
                label = { Text("Wichtel 3D (dreht sich)") },
            )
        }
        if (settings.figure3d) Text("Der 3D-Wichtel hat Mützen- und Mantelfarbe; Hose und Schuhe bleiben wie sie sind.", fontSize = 12.sp, color = Color.Gray)
    }
    Text("Kleidung", fontWeight = FontWeight.Medium)
    ColorRow("Mütze", ElfPalette.clothes, settings.hatColor) { onChange(settings.copy(hatColor = it)) }
    ColorRow("Mantel", ElfPalette.clothes, settings.jacketColor) { onChange(settings.copy(jacketColor = it)) }
    ColorRow("Hose", ElfPalette.clothes, settings.legColor) { onChange(settings.copy(legColor = it)) }
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


/** Farbe und Licht jeder Art von Gegenstand (Tannenbaum, Bett, Zaun …): Farbton, mit/ohne Licht, Lichtfarbe, mit Vorschau. */
@Composable
private fun SceneryStyles(settings: Settings, onChange: (Settings) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val assets = remember(settings.sceneryStyles) { SceneryAssets.load(ctx, settings.sceneryStyles) }
    fun set(t: SceneryType, st: SceneryStyle) {
        val m = settings.sceneryStyles.toMutableMap()
        if (st == SceneryStyle()) m.remove(t.id) else m[t.id] = st
        onChange(settings.copy(sceneryStyles = m))
    }
    Text("Jeder Gegenstand lässt sich einzeln umfärben, mit oder ohne Licht und mit eigener Lichtfarbe zeigen.", fontSize = 12.sp, color = Color.Gray)
    for (t in SceneryType.values()) {
        val st = settings.sceneryStyles[t.id] ?: SceneryStyle()
        androidx.compose.material3.Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val art = assets.art(t)
                    Canvas(Modifier.height(70.dp).width(70.dp)) {
                        if (art != null) {
                            val k = minOf(size.width / art.image.width, size.height / art.image.height)
                            val dw = (art.image.width * k).toInt()
                            val dh = (art.image.height * k).toInt()
                            drawImage(
                                art.image,
                                dstOffset = androidx.compose.ui.unit.IntOffset(((size.width - dw) / 2).toInt(), ((size.height - dh) / 2).toInt()),
                                dstSize = androidx.compose.ui.unit.IntSize(dw, dh),
                                filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
                            )
                        }
                    }
                    Text("${t.emoji} ${t.label}", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text("Licht", fontSize = 13.sp)
                    Switch(checked = st.lights, onCheckedChange = { set(t, st.copy(lights = it)) })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilterChip(
                        selected = st.hue < 0,
                        onClick = { set(t, st.copy(hue = if (st.hue < 0) 0 else -1)) },
                        label = { Text(if (st.hue < 0) "Originalfarbe" else "Farbe: ${st.hue}°") },
                    )
                    if (st.hue >= 0) {
                        androidx.compose.material3.Slider(
                            value = st.hue.toFloat(),
                            onValueChange = { set(t, st.copy(hue = it.toInt().coerceIn(0, 359))) },
                            valueRange = 0f..359f,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (st.lights) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Lichtfarbe:", fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                        SceneryPalette.lightColors.forEachIndexed { i, (name, c) ->
                            androidx.compose.material3.FilterChip(
                                selected = st.lightColor == (if (i == 0) -1 else i),
                                onClick = { set(t, st.copy(lightColor = if (i == 0) -1 else i)) },
                                label = { Text(name) },
                                leadingIcon = if (c != null) ({ Box(Modifier.size(12.dp).background(c, androidx.compose.foundation.shape.CircleShape)) }) else null,
                            )
                        }
                    }
                }
            }
        }
    }
}
