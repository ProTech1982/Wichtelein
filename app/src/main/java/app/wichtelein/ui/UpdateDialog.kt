package app.wichtelein.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wichtelein.settings.Updater

/** Ergebnis der Update-Suche, wie es der Dialog zeigt. */
sealed interface UpdateDialogState {
    object Checking : UpdateDialogState
    class Error(val message: String) : UpdateDialogState
    class UpToDate(val version: String?, val notes: String?) : UpdateDialogState
    class Available(val version: String?, val notes: String?, val url: String) : UpdateDialogState
}

fun updateDialogFrom(r: Updater.Result): UpdateDialogState = when {
    r.error != null -> UpdateDialogState.Error(r.error)
    r.available && r.downloadUrl != null -> UpdateDialogState.Available(r.version, r.notes, r.downloadUrl)
    else -> UpdateDialogState.UpToDate(r.version, r.notes)
}

/** Änderungsprotokoll lesbar machen (Markdown-Zeichen weglassen, leer = Hinweis). */
fun cleanNotes(notes: String?): String {
    val t = notes.orEmpty().lines().joinToString("\n") { it.trimEnd().removePrefix("###").removePrefix("##").removePrefix("#").trimStart() }.trim()
    return t.ifEmpty { "Keine Beschreibung der Änderungen vorhanden." }
}

/**
 * Update-Fenster: bei neuer Version mit Änderungsprotokoll und den Knöpfen „Jetzt updaten“ / „Abbrechen“ – der Download startet erst
 * nach dem Tippen auf „Jetzt updaten“. Ist die App aktuell, zeigt es das Änderungsprotokoll der aktuellen Version.
 */
@Composable
fun UpdateDialog(state: UpdateDialogState?, onUpdate: (String) -> Unit, onDismiss: () -> Unit) {
    when (state) {
        null -> Unit
        UpdateDialogState.Checking -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Suche nach Updates …") },
            text = { Text("Einen Moment bitte.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
        )
        is UpdateDialogState.Error -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Update-Suche fehlgeschlagen") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
        is UpdateDialogState.UpToDate -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Du hast die neueste Version") },
            text = { Notes(state.version, state.notes, "Änderungen dieser Version") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
        is UpdateDialogState.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Neues Update verfügbar") },
            text = { Notes(state.version, state.notes, "Änderungen") },
            confirmButton = { TextButton(onClick = { onUpdate(state.url) }) { Text("Jetzt updaten") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun Notes(version: String?, notes: String?, heading: String) {
    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
        if (!version.isNullOrBlank()) Text(version, fontWeight = FontWeight.SemiBold)
        Text(heading, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier)
        Text(cleanNotes(notes), fontSize = 13.sp)
    }
}
