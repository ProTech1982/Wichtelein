package app.wichtelein.ui

import app.wichtelein.camera.*
import app.wichtelein.anim.*
import app.wichtelein.scene.*
import app.wichtelein.speech.*
import app.wichtelein.videoexport.*
import app.wichtelein.settings.*
import app.wichtelein.sound.SoundPlayer

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class EditMode { START, PATH, DOOR, ACTION }

private const val MAX_WAYPOINTS = 8
private const val MAX_STEPS = 20

/**
 * Editor: Startpunkt, Größe, Weg und Aktionspunkte festlegen, Szene wählen oder würfeln, Vorschau ansehen und als Video
 * speichern. [file] ist ein Video oder (bei [isPhoto]) ein Foto, aus dem ein Film entsteht.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    file: File,
    settings: Settings,
    onSettings: (Settings) -> Unit,
    onBack: () -> Unit,
    onOpenVideos: () -> Unit,
    isPhoto: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val info = remember(file.path, file.lastModified(), isPhoto) { if (isPhoto) readPhotoInfo(file) else readVideoInfo(file) }
    val photo by produceState<ImageBitmap?>(null, file.path, file.lastModified(), isPhoto) {
        value = if (isPhoto) withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.path)?.asImageBitmap() } else null
    }
    val sprites = rememberElfFigure(settings)
    val speaker = remember { Speaker(context) }
    val fxPlayer = remember { SoundPlayer() }
    DisposableEffect(Unit) { onDispose { speaker.shutdown() } }
    SideEffect {
        speaker.enabled = settings.speak
        fxPlayer.enabled = settings.soundFx
        speaker.effect = settings.effect
        speaker.rate = settings.rate
        speaker.mood = settings.mood
        speaker.useElfVoice = settings.elfVoice
        speaker.voiceId = settings.voiceId
    }
    val latestSettings by rememberUpdatedState(settings)

    var start by remember { mutableStateOf<Pt?>(null) }
    var path by remember { mutableStateOf(emptyList<Pt>()) }
    var mode by remember { mutableStateOf(EditMode.START) }
    var door by remember { mutableStateOf<Pt?>(null) }
    var doorScale by remember { mutableFloatStateOf(0.2f) }
    var doorHingeLeft by remember { mutableStateOf(true) }
    var doorRound by remember { mutableFloatStateOf(1f) }
    var script by remember { mutableStateOf(emptyList<ScriptStep>()) }
    var action by remember { mutableStateOf(ElfAction.SWEEP) }
    var photoSeconds by remember { mutableFloatStateOf(12f) }
    var category by remember { mutableStateOf<Category?>(null) }
    var sceneId by remember { mutableStateOf<String?>(null) }
    var seed by remember { mutableLongStateOf(System.nanoTime() and 0xFFFFFFL) }
    var perf by remember { mutableStateOf<Performance?>(null) }
    var clips by remember { mutableStateOf<List<VoiceAudio?>>(emptyList()) }
    var voiceStatus by remember { mutableStateOf<String?>(null) }
    var posMs by remember { mutableLongStateOf(0L) }
    var export by remember { mutableStateOf<ExportUi?>(null) }
    val videoRef = remember { arrayOfNulls<VideoView>(1) }
    val scriptMode = script.isNotEmpty()
    val duration = when {
        isPhoto && scriptMode -> 60_000L // endet automatisch kurz nach dem letzten Aktionspunkt
        isPhoto -> (photoSeconds * 1000f).toLong()
        else -> min(info.durationMs, 90_000L).coerceAtLeast(3_000L)
    }

    // 1) Aufführung aufnehmen, sobald sich etwas ändert
    LaunchedEffect(settings, start, path, category, sceneId, seed, duration, door, doorScale, doorHingeLeft, doorRound, script) {
        val req = PerfRequest(
            start = start ?: Pt(0.5f, 0.78f),
            path = path,
            door = door,
            doorScale = doorScale,
            doorHingeLeft = doorHingeLeft,
            doorRound = doorRound,
            script = script,
            stopAfterScript = isPhoto && script.isNotEmpty(),
            aspect = info.aspect,
            sizeScale = settings.sizeScale,
            category = category,
            sceneId = sceneId,
            seed = seed,
            durationMs = duration,
            enabledScenes = settings.enabledScenes(),
            settings = settings,
        )
        perf = withContext(Dispatchers.Default) { Recorder.record(req) }
    }

    // 2) Sprachclips für die Vorschau erzeugen
    LaunchedEffect(perf, settings.voiceId, settings.effect, settings.rate, settings.mood, settings.elfVoice, settings.speak) {
        val p = perf ?: return@LaunchedEffect
        clips = emptyList()
        if (!settings.speak || !settings.elfVoice) {
            voiceStatus = null
            return@LaunchedEffect
        }
        voiceStatus = "Wichtelstimme wird vorbereitet …"
        val ok = withContext(Dispatchers.Default) { SpeechRender.awaitVoice(context, settings) }
        if (!ok) {
            voiceStatus = "Wichtelstimme nicht verfügbar – Systemstimme oder nur Text"
            return@LaunchedEffect
        }
        val out = ArrayList<VoiceAudio?>()
        for ((i, ev) in p.speech.withIndex()) {
            voiceStatus = "Stimme wird erzeugt (${i + 1}/${p.speech.size}) …"
            out.add(withContext(Dispatchers.Default) { SpeechRender.render(settings, ev) })
        }
        clips = out
        voiceStatus = null
    }

    // 3) Wiedergabeposition verfolgen (Video oder Foto-Uhr) und Sprache und Geräusche zum richtigen Zeitpunkt abspielen
    LaunchedEffect(perf, clips) {
        var last = -1L
        var t0 = -1L
        while (true) {
            val now = withFrameNanos { it }
            if (t0 < 0) t0 = now
            val pos = if (isPhoto) {
                val d = (perf?.durationMs ?: 1000L).coerceAtLeast(1000L)
                ((now - t0) / 1_000_000L) % d
            } else {
                videoRef[0]?.currentPosition?.toLong() ?: 0L
            }
            posMs = pos
            val p = perf ?: continue
            if (pos < last - 400) last = -1L
            if (latestSettings.soundFx) {
                for (fe in p.sounds) {
                    if (fe.timeMs > last && fe.timeMs <= pos) fxPlayer.play(fe.fx, fe.gain)
                }
            }
            for ((i, ev) in p.speech.withIndex()) {
                if (ev.timeMs > last && ev.timeMs <= pos) {
                    val clip = clips.getOrNull(i)
                    val s = latestSettings
                    if (clip != null) speaker.playClip(clip)
                    else if (s.speak && !s.elfVoice) speaker.speak(ev.text, ev.mood)
                }
            }
            last = pos
        }
    }

    val frame = perf?.frameAt(posMs)

    Column(Modifier.fillMaxSize().background(Color(0xFF101010)).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Zurück", color = Color.White) }
            Text(if (isPhoto) "Foto-Film" else "Wichtel-Editor", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.aspectRatio(info.aspect)) {
                if (isPhoto) {
                    photo?.let {
                        Image(bitmap = it, contentDescription = "Foto", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                    }
                } else {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoURI(Uri.fromFile(file))
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    start()
                                }
                                videoRef[0] = this
                            }
                        },
                        onRelease = {
                            it.stopPlayback()
                            videoRef[0] = null
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                ElfOverlay(
                    frame = frame,
                    sprites = sprites,
                    sizeScale = settings.sizeScale,
                    modifier = Modifier.fillMaxSize().testTag("bildflaeche"),
                    onTap = { px, py ->
                        val p = Pt(px.coerceIn(0.03f, 0.97f), py.coerceIn(0.4f, 0.96f))
                        when (mode) {
                            EditMode.START -> start = p
                            EditMode.PATH -> if (path.size < MAX_WAYPOINTS) path = path + p
                            EditMode.DOOR -> door = p
                            EditMode.ACTION -> if (action.needsPoint && script.size < MAX_STEPS) script = script + ScriptStep(action, p)
                        }
                    },
                )
                EditMarkers(start, path, door, doorScale, doorRound, script, Modifier.fillMaxSize())
            }
            voiceStatus?.let {
                Text(
                    it,
                    color = Color(0xFFFFE08A),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                        .background(Color(0x99000000), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 230.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = mode == EditMode.START, onClick = { mode = EditMode.START }, label = { Text("📍 Start") })
                FilterChip(selected = mode == EditMode.PATH, onClick = { mode = EditMode.PATH }, label = { Text("🛤 Weg (${path.size})") })
                FilterChip(selected = mode == EditMode.DOOR, onClick = { mode = EditMode.DOOR }, label = { Text(if (door == null) "🚪 Tür" else "🚪 Tür ✓") })
                FilterChip(selected = mode == EditMode.ACTION, onClick = { mode = EditMode.ACTION }, label = { Text("🎯 Aktionen (${script.size})") })
                TextButton(onClick = { path = emptyList() }, enabled = path.isNotEmpty()) { Text("Weg löschen") }
            }
            Text(
                when (mode) {
                    EditMode.START -> "Tippe ins Video: Dort erscheint der Wichtel."
                    EditMode.PATH -> "Tippe nacheinander Stellen, die der Wichtel ablaufen soll (höchstens $MAX_WAYPOINTS)."
                    EditMode.DOOR -> "Wichtelhaus: Tippe auf die Stelle am Boden direkt unter der Tür. Dort öffnet, betritt und schließt der Wichtel die Tür."
                    EditMode.ACTION -> "Wähle eine Aktion und tippe ins Bild, wo sie stattfinden soll. Der Wichtel geht die Punkte der Reihe nach ab. " +
                        "Aktionen mit Tür (🚪) brauchen das markierte Wichtelhaus."
                },
                color = Color(0xFFCCCCCC),
                fontSize = 12.sp,
            )

            if (mode == EditMode.ACTION) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ElfAction.values().forEach { a ->
                        FilterChip(
                            selected = action == a,
                            onClick = {
                                action = a
                                if (!a.needsPoint && door != null && script.size < MAX_STEPS) script = script + ScriptStep(a, null)
                            },
                            label = { Text("${a.emoji} ${a.label}") },
                        )
                    }
                }
                if (action.needsDoor && door == null) {
                    Text("Setze zuerst die Tür (Chip „🚪 Tür“), dann lässt sich diese Aktion hinzufügen.", color = Color(0xFFFFE08A), fontSize = 12.sp)
                }
            }
            if (script.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    script.forEachIndexed { i, st ->
                        FilterChip(
                            selected = false,
                            onClick = { script = script.filterIndexed { j, _ -> j != i } },
                            label = { Text("${i + 1}. ${st.action.emoji} ${st.action.label} ✕") },
                        )
                    }
                    TextButton(onClick = { script = emptyList() }) { Text("Alle löschen") }
                }
            }

            if (door != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Türgröße", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { door = null }) { Text("Tür entfernen") }
                }
                Slider(value = doorScale, onValueChange = { doorScale = it }, valueRange = 0.06f..0.5f)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Öffnet nach", color = Color.White, fontSize = 13.sp)
                    FilterChip(selected = doorHingeLeft, onClick = { doorHingeLeft = true }, label = { Text("◀ links") })
                    FilterChip(selected = !doorHingeLeft, onClick = { doorHingeLeft = false }, label = { Text("rechts ▶") })
                }
                Text("Form: eckig ↔ rund", color = Color.White, fontSize = 13.sp)
                Slider(value = doorRound, onValueChange = { doorRound = it }, valueRange = 0f..1f)
            }
            if (isPhoto) {
                if (scriptMode) {
                    Text(
                        "Länge: automatisch, etwa ${(perf?.durationMs ?: 0L) / 1000L} s (endet kurz nach der letzten Aktion)",
                        color = Color(0xFFCCCCCC),
                        fontSize = 12.sp,
                    )
                } else {
                    Text("Länge des Films: ${photoSeconds.toInt()} s", color = Color.White, fontSize = 13.sp)
                    Slider(value = photoSeconds, onValueChange = { photoSeconds = it }, valueRange = 6f..40f)
                }
            }
            Text("Größe", color = Color.White, fontSize = 13.sp)
            Slider(
                value = settings.sizeScale,
                onValueChange = { onSettings(settings.copy(sizeScale = it)) },
                valueRange = 0.12f..0.6f,
            )

            if (scriptMode) {
                Text(
                    "Aktionspunkte aktiv: Der Wichtel spielt genau deine Aktionen der Reihe nach. Zum Würfeln der Szenen die Aktionen löschen.",
                    color = Color(0xFFCCCCCC),
                    fontSize = 12.sp,
                )
            } else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = category == null, onClick = { category = null; sceneId = null }, label = { Text("🎲 Zufall") })
                Category.values().forEach { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { category = c; sceneId = null },
                        label = { Text("${c.emoji} ${c.label}") },
                    )
                }
            }
            val shown = if (category == null) Scenes.all else Scenes.inCategory(category!!)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = sceneId == null, onClick = { sceneId = null }, label = { Text("Automatisch") })
                shown.forEach { s ->
                    FilterChip(
                        selected = sceneId == s.id,
                        onClick = { sceneId = s.id; category = s.category },
                        label = { Text(if (s.needsDoor) "🚪 ${s.name}" else s.name) },
                    )
                }
            }
            val chosen = sceneId?.let { Scenes.find(it) }
            if (chosen != null) Text(chosen.description, color = Color(0xFFCCCCCC), fontSize = 12.sp)
            if (chosen?.needsDoor == true && door == null) {
                Text("Diese Szene braucht die Tür deines Wichtelhauses: markiere sie mit „🚪 Tür“. Bis dahin nimmt der Wichtel eine Standardstelle.", color = Color(0xFFFFE08A), fontSize = 12.sp)
            }
            }

            if (settings.profiles.size > 1) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    settings.profiles.forEach { pr ->
                        FilterChip(
                            selected = settings.activeProfile()?.id == pr.id,
                            onClick = { onSettings(settings.copy(activeProfileId = pr.id)) },
                            label = { Text("👦 ${pr.name}") },
                        )
                    }
                }
            }
        }

        // feste Leiste: immer sichtbar, auch wenn das Bedienfeld scrollt
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { seed = System.nanoTime() and 0xFFFFFFL }, modifier = Modifier.weight(1f)) { Text("🎲 Neu würfeln", color = Color.White) }
            Button(
                onClick = {
                    val p = perf ?: return@Button
                    export = ExportUi.Running(0f)
                    scope.launch {
                        export = try {
                            val result = ExportManager.export(
                                context = context,
                                video = file,
                                info = if (isPhoto) VideoInfo(info.width, info.height, p.durationMs) else info,
                                perf = p,
                                settings = settings,
                                onProgress = { export = ExportUi.Running(it) },
                                isPhoto = isPhoto,
                            )
                            ExportUi.Done(result)
                        } catch (t: Throwable) {
                            ExportUi.Failed(t.message ?: "Unbekannter Fehler")
                        }
                    }
                },
                enabled = perf != null && voiceStatus == null && export == null,
                modifier = Modifier.weight(1f),
            ) { Text("💾 Als Video speichern") }
        }
        Text(
            perf?.sceneNames?.joinToString(" → ") ?: "Szene wird erzeugt …",
            color = Color(0xFF9E9E9E),
            fontSize = 11.sp,
        )
        }
    }

    ExportDialog(export, onClose = { export = null }, onOpenVideos = { export = null; onOpenVideos() })
}

/** Start- und Wegmarkierungen im Editor (nicht im exportierten Video). */
@Composable
private fun EditMarkers(start: Pt?, path: List<Pt>, door: Pt?, doorScale: Float, doorRound: Float, script: List<ScriptStep>, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // drawText braucht Platz unter und neben der Position (sonst Absturz): in die Fläche klemmen
        fun safe(o: Offset) = Offset(o.x.coerceIn(0f, (w - 40f).coerceAtLeast(0f)), o.y.coerceIn(0f, (h - 40f).coerceAtLeast(0f)))
        door?.let {
            val dh = h * doorScale
            val dw = dh * 0.56f
            drawPath(doorPath(it.x * w, it.y * h, dw, dh, doorRound), Color(0xCCFF7043), style = Stroke(5f))
        }
        val pts = listOfNotNull(start) + path
        for (i in 0 until pts.size - 1) {
            drawLine(
                Color(0xCCFFC107),
                Offset(pts[i].x * w, pts[i].y * h),
                Offset(pts[i + 1].x * w, pts[i + 1].y * h),
                strokeWidth = 4f,
            )
        }
        start?.let {
            drawCircle(Color(0xFF43A047), 18f, Offset(it.x * w, it.y * h))
            drawCircle(Color.White, 18f, Offset(it.x * w, it.y * h), style = Stroke(3f))
        }
        path.forEachIndexed { i, p ->
            val c = Offset(p.x * w, p.y * h)
            drawCircle(Color(0xFFFFC107), 16f, c)
            drawText(measurer, "${i + 1}", safe(c - Offset(6f, 12f)), style = TextStyle(fontSize = 13.sp, color = Color.Black, fontWeight = FontWeight.Bold))
        }
        // Aktionspunkte: blaue Pins, durch eine Linie in der Reihenfolge verbunden
        val stepPts = script.mapIndexedNotNull { i, st -> st.at?.let { Triple(i, st, Offset(it.x * w, it.y * h)) } }
        for (k in 0 until stepPts.size - 1) {
            drawLine(Color(0xAA42A5F5), stepPts[k].third, stepPts[k + 1].third, strokeWidth = 3f)
        }
        stepPts.forEach { (i, st, c) ->
            drawCircle(Color(0xFF1E88E5), 20f, c)
            drawCircle(Color.White, 20f, c, style = Stroke(2.5f))
            drawText(measurer, "${i + 1}", safe(c - Offset(6f, 12f)), style = TextStyle(fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold))
            drawText(measurer, st.action.emoji, safe(c + Offset(-10f, 22f)), style = TextStyle(fontSize = 16.sp))
        }
    }
}
