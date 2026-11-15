package app.wichtelein.speech

import app.wichtelein.camera.*
import app.wichtelein.anim.*
import app.wichtelein.scene.*
import app.wichtelein.videoexport.*
import app.wichtelein.settings.*
import app.wichtelein.ui.*

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Stimmen-Einstellungen: Art der Stimme, Stimmenauswahl mit Download, Stimmung, Effekt, Tempo und Hörprobe. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettings(settings: Settings, onChange: (Settings) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val speaker = remember { Speaker(context) }
    DisposableEffect(Unit) { onDispose { speaker.shutdown() } }
    SideEffect {
        speaker.enabled = true
        speaker.effect = settings.effect
        speaker.rate = settings.rate
        speaker.mood = settings.mood
        speaker.useElfVoice = settings.elfVoice
        speaker.voiceId = settings.voiceFor(if (settings.elfin) 1 else 0)
    }
    val actors = when (settings.cast) { 1 -> listOf(1); 2 -> listOf(0, 1); else -> listOf(0) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Stimme", fontSize = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = settings.elfVoice,
                onClick = { onChange(settings.copy(elfVoice = true)) },
                label = { Text("Wichtelstimme (offline)") },
            )
            FilterChip(
                selected = !settings.elfVoice,
                onClick = { onChange(settings.copy(elfVoice = false)) },
                label = { Text("Systemstimme") },
            )
        }

        if (settings.elfVoice) {
            // der Wichtel bekommt nur männliche, die Elfin nur weibliche Stimmen
            for (actor in actors) {
                val current = settings.voiceFor(actor)
                if (actors.size > 1 || settings.elfin) Text(if (actor == 1) "🧝‍♀️ Stimme der Elfin (weiblich)" else "🧙 Stimme des Wichtels (männlich)", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                VoiceCatalog.forActor(actor).forEach { v ->
                    val installed = VoiceCatalog.isInstalled(context, v.id)
                    val dl = VoiceDownloads.states[v.id]
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = current == v.id,
                            enabled = installed,
                            onClick = { onChange(if (actor == 1) settings.copy(elfinVoiceId = v.id) else settings.copy(voiceId = v.id)) },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(v.name, fontSize = 15.sp)
                            Text("${v.note} (${v.sizeMb} MB)", fontSize = 12.sp, color = Color.Gray)
                            if (dl != null && !dl.done && dl.error == null) {
                                LinearProgressIndicator(progress = { dl.progress }, modifier = Modifier.fillMaxWidth())
                            }
                            if (dl?.error != null) Text(dl.error, fontSize = 12.sp, color = Color(0xFFB3261E))
                        }
                        if (!installed && (dl == null || dl.error != null)) {
                            OutlinedButton(onClick = { scope.launch { VoiceDownloads.download(context, v) } }) { Text("Laden") }
                        }
                    }
                }
                if (!VoiceCatalog.isInstalled(context, current)) {
                    Text("Diese Stimme ist noch nicht geladen – bitte oben „Laden“ tippen. Bis dahin spricht die Systemstimme.", fontSize = 12.sp, color = Color(0xFFB3261E))
                }
            }
            val active = VoiceCatalog.find(settings.voiceId)
            if (active.moods && settings.cast != 1) {
                Text("Stimmung (Wichtel)")
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
            Text("Wichtel-Effekt: natürlich ↔ Wichtel (Tonhöhe)")
            Slider(value = settings.effect, onValueChange = { onChange(settings.copy(effect = it)) }, valueRange = 0f..1f)
        } else {
            Text("Tonhöhe der Systemstimme")
            Slider(value = settings.effect, onValueChange = { onChange(settings.copy(effect = it)) }, valueRange = 0f..1f)
        }
        Text("Sprechtempo: langsam ↔ schnell")
        Slider(value = settings.rate, onValueChange = { onChange(settings.copy(rate = it)) }, valueRange = 0.7f..1.4f)

        for (actor in actors) {
            Button(
                onClick = {
                    val kid = settings.kidList().firstOrNull() ?: "mein Freund"
                    speaker.voiceId = settings.voiceFor(actor)
                    speaker.speak(fill("Hallo {name}! Ich bin {wichtel}, dein Wichtel. Heute habe ich wieder etwas angestellt!", kid, settings.nameFor(actor)))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (actors.size > 1) (if (actor == 1) "🔊  Elfin anhören" else "🔊  Wichtel anhören") else "🔊  Stimme anhören") }
        }
        Text("Status: ${speaker.statusText}", fontSize = 12.sp, color = Color.Gray)
    }
}
