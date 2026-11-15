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
    // welche Bereiche aufgeklappt sind (anfangs alle zu, damit die Seite übersichtlich bleibt)
    val open = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    val titles = listOf("🎭 Figuren", "👧 Kinder", "🗣 Sprache und Stimmen", "🎄 Gegenstände: Farbe und Licht", "💬 Sprechblase", "🔊 Geräusche und Musik", "✏ Eigene Sätze", "🎨 Aussehen der App", "🔒 Datenschutz", "ℹ App und Updates")
    Column(
        Modifier
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Zurück") }
            Text("Einstellungen", fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
        }
        val anyOpen = open.values.any { it }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { titles.forEach { open[it] = !anyOpen } }) { Text(if (anyOpen) "▴ Alle zuklappen" else "▾ Alle aufklappen") }
        }

        Fold("🎭 Figuren", open, "Wichtel, Elfin oder beide · Namen · Kleidung") {
            Text("Wer erscheint?", fontWeight = FontWeight.Medium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.FilterChip(selected = settings.cast == 0, onClick = { onChange(settings.copy(cast = 0)) }, label = { Text("🧙 Wichtel") })
                androidx.compose.material3.FilterChip(selected = settings.cast == 1, onClick = { onChange(settings.copy(cast = 1)) }, label = { Text("🧝‍♀️ Elfin") })
                androidx.compose.material3.FilterChip(selected = settings.cast == 2, onClick = { onChange(settings.copy(cast = 2)) }, label = { Text("🧙🧝‍♀️ Beide") })
            }
            if (settings.cast != 1) OutlinedTextField(
                value = settings.elfName,
                onValueChange = { onChange(settings.copy(elfName = it)) },
                label = { Text("Name des Wichtels") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (settings.cast != 0) OutlinedTextField(
                value = settings.elfinName,
                onValueChange = { onChange(settings.copy(elfinName = it)) },
                label = { Text("Name der Elfin") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (settings.both) Text("Beide treten zusammen auf. Im Editor legst du bei jeder Aktion fest, wer sie macht; ohne Aktionen machen beide ihr eigenes Ding.", fontSize = 12.sp, color = Color.Gray)
            FigureStyle(settings, onChange)
            val actors = when (settings.cast) { 1 -> listOf(1); 2 -> listOf(0, 1); else -> listOf(0) }
            for (actor in actors) ClothingEditor(settings, onChange, actor, showTitle = actors.size > 1)
            Text("Standardgröße")
            Slider(value = settings.sizeScale, onValueChange = { onChange(settings.copy(sizeScale = it)) }, valueRange = 0.12f..0.6f)
        }

        Fold("👧 Kinder", open, "Namen der Kinder für die Sprüche") {
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
        }

        Fold("🗣 Sprache und Stimmen", open, "Sprechen an/aus · Stimme · Tempo · Effekt") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (settings.cast == 0) "Wichtel spricht" else if (settings.cast == 1) "Elfin spricht" else "Wichtel und Elfin sprechen")
                Switch(checked = settings.speak, onCheckedChange = { onChange(settings.copy(speak = it)) })
            }
            VoiceSettings(settings, onChange)
        }

        Fold("🎄 Gegenstände: Farbe und Licht", open, "Jeden Gegenstand umfärben, Licht an/aus") {
            SceneryStyles(settings, onChange)
        }

        Fold("💬 Sprechblase", open, "Standard für alle Aktionen: Form und Größe") {
            Text("Gilt für alle Aktionen. Im Editor lässt sich für jede Aktion eine eigene Blase einstellen.", fontSize = 12.sp, color = Color.Gray)
            BubbleControls(settings.bubble, { onChange(settings.copy(bubble = it)) })
        }

        Fold("🔊 Geräusche und Musik", open, "Glöckchen, Summen, Brummen") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Weihnachtsgeräusche (Glöckchen, Tür, Besen, Klopfen)", modifier = Modifier.weight(1f))
                Switch(checked = settings.soundFx, onCheckedChange = { onChange(settings.copy(soundFx = it)) })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Summen und Brummen", modifier = Modifier.weight(1f))
                Switch(checked = settings.hum, onCheckedChange = { onChange(settings.copy(hum = it)) })
            }
            Text(
                "Ist die Stimme aus, brummt die Figur statt zu sprechen. Ist sie an, summt sie in den Pausen kleine Weihnachtsmelodien dazu. " +
                    "Beides lässt sich mit Stimme und Effekt kombinieren. Hintergrundmusik wählst du im Editor unter „🎵 Musik“.",
                fontSize = 12.sp,
                color = Color.Gray,
            )
        }

        Fold("✏ Eigene Sätze", open, "Eigene Sprüche für den Wichtel und die Elfin") {
            if (settings.cast != 1) OutlinedTextField(
                value = settings.customLines,
                onValueChange = { onChange(settings.copy(customLines = it)) },
                label = { Text("Eigene Sätze des Wichtels (einer pro Zeile)") },
                supportingText = { Text("Platzhalter: {name} = Name des Kindes, {wichtel} = Name der Figur") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            if (settings.cast != 0) OutlinedTextField(
                value = settings.elfinCustomLines,
                onValueChange = { onChange(settings.copy(elfinCustomLines = it)) },
                label = { Text("Eigene Sätze der Elfin (einer pro Zeile)") },
                supportingText = { Text("Die Elfin spricht nur ihre eigenen Texte, nichts vom Wichtel.") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Fold("🎨 Aussehen der App", open, "Wie das Handy, hell oder dunkel") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Wie das Handy", "Hell", "Dunkel").forEachIndexed { i, label ->
                    androidx.compose.material3.FilterChip(selected = settings.themeMode == i, onClick = { onChange(settings.copy(themeMode = i)) }, label = { Text(label) })
                }
            }
        }

        Fold("🔒 Datenschutz", open, "Alles bleibt auf dem Gerät") {
            Text(
                "Alles bleibt auf diesem Gerät: Videos, Namen und Einstellungen. Es gibt kein Konto, keine Werbung und keine Tracker. " +
                    "Das Internet wird nur benutzt, wenn du nach Updates suchst oder eine weitere Stimme lädst. " +
                    "Die Kamera wird nur zum Filmen und Fotografieren gebraucht (ohne Ton).",
                fontSize = 13.sp,
            )
        }

        Fold("ℹ App und Updates", open, "Version und Update-Suche") {
            Text("Build ${Updater.installedVersion(LocalContext.current)}", fontSize = 13.sp)
            OutlinedButton(onClick = onCheckUpdate, Modifier.fillMaxWidth()) { Text("Nach Update suchen") }
            if (updateText != null) Text(updateText)
        }
    }
}

/** Ein auf- und zuklappbarer Bereich der Einstellungen; [summary] steht unter dem Titel, solange er zu ist. */
@Composable
private fun Fold(
    title: String,
    open: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Boolean>,
    summary: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val isOpen = open[title] == true
    androidx.compose.material3.Card(Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { open[title] = !isOpen }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    if (!isOpen) Text(summary, fontSize = 12.sp, color = Color.Gray)
                }
                Text(if (isOpen) "▾" else "▸", fontSize = 22.sp)
            }
            if (isOpen) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
}

/** Darstellung der Figuren: 2D gezeichnet oder 3D (dreht sich). */
@Composable
private fun FigureStyle(settings: Settings, onChange: (Settings) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val has3d = if (settings.both) Elf3D.available(ctx) || Elf3D.available(ctx, "elfin3d") else Elf3D.available(ctx, if (settings.elfin) "elfin3d" else "elf3d")
    if (!has3d) return
    Text("Darstellung", fontWeight = FontWeight.Medium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.FilterChip(selected = !settings.figure3d, onClick = { onChange(settings.copy(figure3d = false)) }, label = { Text("2D (gezeichnet)") })
        androidx.compose.material3.FilterChip(selected = settings.figure3d, onClick = { onChange(settings.copy(figure3d = true)) }, label = { Text("3D (dreht sich)") })
    }
    if (settings.figure3d) Text("3D: Mützen- und Mantel- bzw. Kleidfarbe lassen sich ändern; Hose und Schuhe des Wichtels bleiben wie sie sind.", fontSize = 12.sp, color = Color.Gray)
}

/** Kleidung einer Figur ([actor]: 0 = Wichtel, 1 = Elfin): Vorschau und Farbwahl; die Elfin hat ihre eigenen Farben. */
@Composable
private fun ClothingEditor(settings: Settings, onChange: (Settings) -> Unit, actor: Int, showTitle: Boolean) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val sprites = remember(settings.colorsFor(actor), settings.figure3d, actor) { ElfFigures.load(ctx, settings, actor) }
    val pose = remember { Pose().apply { armR = 12f; armL = 6f } }
    if (showTitle) Text(if (actor == 1) "🧝‍♀️ Kleidung der Elfin" else "🧙 Kleidung des Wichtels", fontWeight = FontWeight.SemiBold)
    Canvas(Modifier.fillMaxWidth().height(180.dp)) {
        sprites.draw(this, pose, Offset(size.width / 2f, size.height * 0.97f), size.height * 0.93f, 1)
    }
    val e = actor == 1
    fun setColors(hat: Int, jacket: Int, legs: Int, boots: Int) = onChange(
        if (e) settings.copy(elfinHatColor = hat, elfinJacketColor = jacket, elfinLegColor = legs, elfinBootColor = boots)
        else settings.copy(hatColor = hat, jacketColor = jacket, legColor = legs, bootColor = boots),
    )
    val c = settings.colorsFor(actor)
    ColorRow("Mütze", ElfPalette.clothes, c.hat) { setColors(it, c.jacket, c.legs, c.boots) }
    ColorRow(if (e) "Kleid" else "Mantel", ElfPalette.clothes, c.jacket) { setColors(c.hat, it, c.legs, c.boots) }
    ColorRow(if (e) "Strumpfhose" else "Hose", ElfPalette.clothes, c.legs) { setColors(c.hat, c.jacket, it, c.boots) }
    ColorRow("Schuhe", ElfPalette.boots, c.boots) { setColors(c.hat, c.jacket, c.legs, it) }
    TextButton(onClick = { setColors(0, 0, 0, 0) }) { Text("Original-Farben") }
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
    var group by remember { androidx.compose.runtime.mutableStateOf<SceneryGroup?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.FilterChip(selected = group == null, onClick = { group = null }, label = { Text("Alle (${SceneryType.values().size})") })
        for (g in SceneryGroup.values()) androidx.compose.material3.FilterChip(
            selected = group == g, onClick = { group = g }, label = { Text("${g.label} (${SceneryType.values().count { it.group == g }})") },
        )
    }
    for (t in SceneryType.values().filter { group == null || it.group == group }) {
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
