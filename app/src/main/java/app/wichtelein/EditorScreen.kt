package app.wichtelein

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
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

enum class EditMode { START, PATH }

private const val MAX_WAYPOINTS = 8

/** Editor: Startpunkt, Größe und Weg festlegen, Szene wählen oder würfeln, Vorschau ansehen und als Video speichern. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    file: File,
    settings: Settings,
    onSettings: (Settings) -> Unit,
    onBack: () -> Unit,
    onOpenVideos: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val info = remember(file.path, file.lastModified()) { readVideoInfo(file) }
    val sprites = rememberElfSprites(settings.colorIndex)
    val speaker = remember { Speaker(context) }
    DisposableEffect(Unit) { onDispose { speaker.shutdown() } }
    SideEffect {
        speaker.enabled = settings.speak
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
    var category by remember { mutableStateOf<Category?>(null) }
    var sceneId by remember { mutableStateOf<String?>(null) }
    var seed by remember { mutableLongStateOf(System.nanoTime() and 0xFFFFFFL) }
    var perf by remember { mutableStateOf<Performance?>(null) }
    var clips by remember { mutableStateOf<List<VoiceAudio?>>(emptyList()) }
    var voiceStatus by remember { mutableStateOf<String?>(null) }
    var posMs by remember { mutableLongStateOf(0L) }
    var export by remember { mutableStateOf<ExportUi?>(null) }
    val videoRef = remember { arrayOfNulls<VideoView>(1) }
    val duration = min(info.durationMs, 90_000L).coerceAtLeast(3_000L)

    // 1) Aufführung aufnehmen, sobald sich etwas ändert
    LaunchedEffect(settings, start, path, category, sceneId, seed, duration) {
        val req = PerfRequest(
            start = start ?: Pt(0.5f, 0.78f),
            path = path,
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

    // 3) Wiedergabeposition des Videos verfolgen und Sprache zum richtigen Zeitpunkt abspielen
    LaunchedEffect(perf, clips) {
        var last = -1L
        while (true) {
            withFrameNanos { }
            val pos = videoRef[0]?.currentPosition?.toLong() ?: 0L
            posMs = pos
            val p = perf ?: continue
            if (pos < last - 400) last = -1L
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
            Text("Wichtel-Editor", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.aspectRatio(info.aspect)) {
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
                ElfOverlay(
                    frame = frame,
                    sprites = sprites,
                    sizeScale = settings.sizeScale,
                    modifier = Modifier.fillMaxSize(),
                    onTap = { px, py ->
                        val p = Pt(px.coerceIn(0.03f, 0.97f), py.coerceIn(0.4f, 0.96f))
                        if (mode == EditMode.START) start = p
                        else if (path.size < MAX_WAYPOINTS) path = path + p
                    },
                )
                EditMarkers(start, path, Modifier.fillMaxSize())
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
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = mode == EditMode.START, onClick = { mode = EditMode.START }, label = { Text("📍 Start") })
                FilterChip(selected = mode == EditMode.PATH, onClick = { mode = EditMode.PATH }, label = { Text("🛤 Weg (${path.size})") })
                TextButton(onClick = { path = emptyList() }, enabled = path.isNotEmpty()) { Text("Weg löschen") }
            }
            Text(
                if (mode == EditMode.START) "Tippe ins Video: Dort erscheint der Wichtel."
                else "Tippe nacheinander Stellen, die der Wichtel ablaufen soll (höchstens $MAX_WAYPOINTS).",
                color = Color(0xFFCCCCCC),
                fontSize = 12.sp,
            )

            Text("Größe", color = Color.White, fontSize = 13.sp)
            Slider(
                value = settings.sizeScale,
                onValueChange = { onSettings(settings.copy(sizeScale = it)) },
                valueRange = 0.12f..0.6f,
            )

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
                        label = { Text(s.name) },
                    )
                }
            }
            val chosen = sceneId?.let { Scenes.find(it) }
            if (chosen != null) Text(chosen.description, color = Color(0xFFCCCCCC), fontSize = 12.sp)

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
                                    info = info,
                                    perf = p,
                                    settings = settings,
                                    onProgress = { export = ExportUi.Running(it) },
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
private fun EditMarkers(start: Pt?, path: List<Pt>, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val w = size.width
        val h = size.height
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
            drawText(measurer, "${i + 1}", c - Offset(6f, 12f), style = TextStyle(fontSize = 13.sp, color = Color.Black, fontWeight = FontWeight.Bold))
        }
    }
}
