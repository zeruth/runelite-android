package net.runelite.mp.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.core.text.HtmlCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawBehind
import net.runelite.mp.ui.RlPalette
import net.runelite.mp.ui.RlFonts
import kotlin.math.roundToInt

/** Flat section bars match the desktop sidebar and can collapse long forms. */
@Composable
fun SectionHeader(title: String, expanded: Boolean = true, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().background(RlPalette.DarkerGray)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .heightIn(min = 32.dp).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (onClick != null) Text(if (expanded) "▾  " else "▸  ", color = RlPalette.TextSecondary)
        Text(title, color = RlPalette.Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RowLabel(label: String, description: String? = null, modifier: Modifier = Modifier) {
    var showHelp by remember(label) { mutableStateOf(false) }
    val help = remember(description) {
        description?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim() }.orEmpty()
    }
    Column(modifier.animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = RlPalette.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f, fill = false))
            if (help.isNotEmpty()) Box(Modifier.size(24.dp).clickable { showHelp = !showHelp }, contentAlignment = Alignment.Center) {
                Text(if (showHelp) "−" else "?", color = RlPalette.TextSecondary, fontSize = 12.sp)
            }
        }
        if (showHelp) Text(help, color = RlPalette.TextSecondary, fontSize = 12.sp)
    }
}

@Composable
fun ToggleRow(
    label: String,
    description: String?,
    value: Boolean,
    onChange: (Boolean) -> Unit,
)
{
    Box(
        Modifier
            .fillMaxWidth()
            .background(RlPalette.DarkGray)
            .clickable { onChange(!value) }
            .heightIn(min = 36.dp).padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowLabel(label, description, Modifier.weight(1f))
            Spacer(Modifier.size(8.dp))
            Box(Modifier.size(18.dp).background(RlPalette.DarkerGray)
                .border(1.dp, if (value) RlPalette.Accent else RlPalette.SurfaceBorder),
                contentAlignment = Alignment.Center) {
                if (value) Text("✓", color = RlPalette.Accent, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun IntSliderRow(
    label: String,
    description: String?,
    value: Int,
    min: Int,
    max: Int,
    unit: String? = null,
    onChange: (Int) -> Unit,
)
{
    Box(
        Modifier
            .fillMaxWidth()
            .background(RlPalette.DarkGray)
            .heightIn(min = 36.dp).padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RowLabel(label, description, Modifier.weight(1f))
                Spacer(Modifier.size(8.dp))
                Text(
                    if (unit != null) "$value $unit" else value.toString(),
                    color = RlPalette.Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(4.dp))
            DiscreteSlider(value, min, max, onChange)
        }
    }
}

@Composable
private fun DiscreteSlider(value: Int, min: Int, max: Int, onChange: (Int) -> Unit)
{
    val steps = (max - min).coerceAtLeast(1)
    val frac = ((value - min).toFloat() / steps).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(18.dp)
            .pointerInput(min, max) {
                val edge = 6.dp.toPx()
                detectTapGestures { p ->
                    val usable = (size.width - edge * 2).coerceAtLeast(1f)
                    val f = ((p.x - edge) / usable).coerceIn(0f, 1f)
                    onChange(min + (f * steps).roundToInt())
                }
            }
            .pointerInput(min, max) {
                val edge = 6.dp.toPx()
                detectHorizontalDragGestures { ch, _ ->
                    val usable = (size.width - edge * 2).coerceAtLeast(1f)
                    val f = ((ch.position.x - edge) / usable).coerceIn(0f, 1f)
                    onChange(min + (f * steps).roundToInt())
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val edge = 6.dp.toPx()
            val usable = (size.width - edge * 2).coerceAtLeast(1f)
            val cy = size.height / 2f
            val track = 3.dp.toPx()
            drawRoundRect(
                color = Color.White.copy(alpha = 0.12f),
                topLeft = Offset(edge, cy - track / 2),
                size = Size(usable, track),
                cornerRadius = CornerRadius(track / 2),
            )
            drawRoundRect(
                color = RlPalette.Accent,
                topLeft = Offset(edge, cy - track / 2),
                size = Size(usable * frac, track),
                cornerRadius = CornerRadius(track / 2),
            )
            val thumbX = edge + usable * frac
            drawCircle(RlPalette.Accent.copy(alpha = 0.25f), 7.dp.toPx(), Offset(thumbX, cy))
            drawCircle(RlPalette.Accent, 5.dp.toPx(), Offset(thumbX, cy))
            drawCircle(Color.White, 1.5.dp.toPx(), Offset(thumbX, cy))
        }
    }
}

/** A bounded dropdown keeps long enum lists inside the phone sidebar. */
@Composable
fun SegmentedRow(label: String, description: String?, options: List<String>, selectedIndex: Int, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 38.dp).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RowLabel(label, description, Modifier.weight(1f))
        Spacer(Modifier.size(6.dp))
        Box(Modifier.widthIn(max = 126.dp)) {
            Row(Modifier.border(1.dp, RlPalette.SurfaceBorder).background(RlPalette.DarkerGray)
                .clickable { open = true }.padding(horizontal = 7.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(options.getOrNull(selectedIndex).orEmpty(), color = RlPalette.TextPrimary,
                    fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                Text("  ▾", color = RlPalette.TextSecondary, fontSize = 12.sp)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false },
                modifier = Modifier.widthIn(max = 230.dp).heightIn(max = 300.dp).background(RlPalette.DarkerGray)) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(text = { Text(option,
                        color = if (index == selectedIndex) RlPalette.Accent else RlPalette.TextPrimary,
                        fontFamily = RlFonts.Regular, fontSize = 14.sp, maxLines = 2) },
                        onClick = { open = false; onChange(index) })
                }
            }
        }
    }
}

@Composable
fun TextRow(
    label: String,
    description: String?,
    value: String,
    onChange: (String) -> Unit,
)
{
    Box(
        Modifier
            .fillMaxWidth()
            .background(RlPalette.DarkGray)
            .heightIn(min = 36.dp).padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Column {
            RowLabel(label, description)
            Spacer(Modifier.height(4.dp))
            var local by remember(value) { mutableStateOf(value) }
            BasicTextField(
                value = local,
                onValueChange = { local = it; onChange(it) },
                singleLine = true,
                textStyle = TextStyle(color = RlPalette.TextPrimary, fontSize = 13.sp),
                cursorBrush = SolidColor(RlPalette.Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(RlPalette.DarkerGray)
                    .border(1.dp, RlPalette.SurfaceBorder, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun DangerNote(text: String)
{
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF3A1A1A))
            .padding(8.dp),
    ) {
        Text(text, color = RlPalette.DangerRed, fontSize = 11.sp)
    }
}
