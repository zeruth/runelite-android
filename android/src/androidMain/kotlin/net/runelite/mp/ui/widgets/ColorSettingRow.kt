package net.runelite.mp.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import net.runelite.mp.input.ConfigColors
import net.runelite.mp.ui.RlPalette
import kotlin.math.roundToInt

@Composable
internal fun ColorSettingRow(label: String, description: String?, stored: String?, alpha: Boolean, save: (String) -> Unit) {
    val current = stored?.toIntOrNull() ?: -1
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { open = true }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = RlPalette.TextPrimary)
            if (!description.isNullOrBlank()) Text(description, color = RlPalette.TextSecondary)
            Text("#${ConfigColors.hex(current, alpha)}", color = RlPalette.TextSecondary)
        }
        ColorPreview(current, Modifier.size(32.dp))
    }
    if (open) {
        ColorPickerDialog(label, current, alpha, onDismiss = { open = false }) { save(it.toString()); open = false }
    }
}

/**
 * The color picker of a config row. It is also the system dialog that answers a plugin's
 * `JColorChooser.showDialog` (see SwingDialogs), so both pick colors the same way.
 */
@Composable
internal fun ColorPickerDialog(title: String, initial: Int, alpha: Boolean, onDismiss: () -> Unit, onApply: (Int) -> Unit) {
    run {
        var draft by remember { mutableStateOf(initial) }
        var hex by remember { mutableStateOf(ConfigColors.hex(initial, alpha)) }
        val parsed = ConfigColors.parseHex(hex, alpha)
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ColorPreview(draft, Modifier.fillMaxWidth().height(40.dp))
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { text ->
                            hex = text
                            ConfigColors.parseHex(text, alpha)?.let { draft = it }
                        },
                        label = { Text(if (alpha) "Hex AARRGGBB" else "Hex RRGGBB") },
                        singleLine = true,
                        isError = parsed == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val channels = if (alpha) listOf("Red" to 16, "Green" to 8, "Blue" to 0, "Opacity" to 24)
                        else listOf("Red" to 16, "Green" to 8, "Blue" to 0)
                    for ((name, shift) in channels) {
                        val value = (draft ushr shift) and 255
                        Text("$name: $value")
                        Slider(value = value.toFloat(), onValueChange = {
                            draft = (draft and (255 shl shift).inv()) or (it.roundToInt() shl shift)
                            hex = ConfigColors.hex(draft, alpha)
                        }, valueRange = 0f..255f)
                    }
                }
            },
            confirmButton = { TextButton(enabled = parsed != null, onClick = {
                parsed?.let { onApply(it) }
            }) { Text("Apply") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ColorPreview(argb: Int, modifier: Modifier) {
    Canvas(modifier) {
        val cell = 8.dp.toPx()
        for (y in 0..(size.height / cell).toInt()) {
            for (x in 0..(size.width / cell).toInt()) {
                drawRect(if ((x + y) % 2 == 0) Color.LightGray else Color.DarkGray,
                    Offset(x * cell, y * cell),
                    Size(minOf(cell, size.width - x * cell), minOf(cell, size.height - y * cell)))
            }
        }
        drawRect(Color(argb))
    }
}
