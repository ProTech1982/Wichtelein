package app.wichtelein.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wichtelein.anim.BubbleShape
import app.wichtelein.anim.BubbleSpec
import app.wichtelein.anim.drawBubble

/**
 * Einstellungen der Sprechblase: anzeigen oder nicht (gesprochen wird trotzdem), Form (Abgerundet, Rund, Viereck, Wolke, Zackig),
 * Größe – mit Vorschau. [onReset] (falls gesetzt) stellt „Standard“ wieder her (Aktion ohne eigene Blase).
 */
@Composable
fun BubbleControls(spec: BubbleSpec, onChange: (BubbleSpec) -> Unit, light: Boolean = false, onReset: (() -> Unit)? = null) {
    val textColor = if (light) Color.White else Color.Unspecified
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sprechblase anzeigen", color = textColor, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Switch(checked = spec.show, onCheckedChange = { onChange(spec.copy(show = it)) })
            if (onReset != null) TextButton(onClick = onReset) { Text("Standard") }
        }
        if (spec.show) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BubbleShape.values().forEach { sh ->
                    FilterChip(selected = spec.shape == sh, onClick = { onChange(spec.copy(shape = sh)) }, label = { Text(sh.label) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Größe ${(spec.size * 100).toInt()} %", color = textColor, fontSize = 13.sp)
                Slider(
                    value = spec.size,
                    onValueChange = { onChange(spec.copy(size = it)) },
                    valueRange = BubbleSpec.MIN_SIZE..BubbleSpec.MAX_SIZE,
                    modifier = Modifier.weight(1f),
                )
            }
            BubblePreview(spec)
        } else {
            Text("Der Wichtel spricht weiter (wenn die Stimme an ist), aber ohne Sprechblase.", color = textColor, fontSize = 12.sp)
        }
    }
}

/** Kleine Vorschau der Blase mit Beispieltext. */
@Composable
fun BubblePreview(spec: BubbleSpec) {
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.fillMaxWidth().height(110.dp).background(Color(0xFFCDB89A))) {
        val style = TextStyle(fontSize = (size.height * 0.16f * spec.size).toSp(), color = Color(0xFF222222), fontWeight = FontWeight.Medium)
        val shapeK = if (spec.shape == BubbleShape.ROUNDED || spec.shape == BubbleShape.RECT) 1f else 0.7f
        val maxW = (size.width * 0.6f * spec.size.coerceAtMost(1.3f) * shapeK).toInt().coerceIn(40, size.width.toInt().coerceAtLeast(41))
        val layout = measurer.measure("Hallo, ich bin der Wichtel!", style, constraints = Constraints(maxWidth = maxW))
        drawBubble(layout, spec, size.width * 0.5f, size.height * 1.02f, size.width, size.height * 1.6f)
    }
}
