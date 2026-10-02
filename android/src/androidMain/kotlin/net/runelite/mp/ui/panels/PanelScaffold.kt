package net.runelite.mp.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.runelite.mp.ui.RlPalette

/**
 * Common chrome every Compose-replacement panel sits inside: a header strip with the
 * plugin's title (matches the icon-strip tooltip) and a scrollable content region.
 * The scaffold *intentionally* does not own scrolling — some panels (chart-style XP
 * Tracker, the world list) want a fixed header with their own scroll region. Pass
 * `scrollable = false` for those.
 */
@Composable
internal fun PanelScaffold(
    title: String,
    subtitle: String? = null,
    scrollable: Boolean = true,
    content: @Composable () -> Unit,
)
{
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RlPalette.DarkerGray)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(RlPalette.DarkGray)
                .padding(horizontal = 8.dp, vertical = 5.dp),
        ) {
            Text(title, color = RlPalette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null)
            {
                Text(subtitle, color = RlPalette.TextSecondary, fontSize = 11.sp)
            }
        }
        if (scrollable)
        {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(6.dp),
            ) {
                content()
            }
        }
        else
        {
            Box(modifier = Modifier.fillMaxSize()) { content() }
        }
    }
}

/** Borderless edit field that matches the rest of the sidebar's "input pill" look. */
@Composable
internal fun PanelTextInput(
    value: String,
    placeholder: String,
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
)
{
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(RlPalette.DarkerGray)
            .border(1.dp, RlPalette.SurfaceBorder, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        if (value.isEmpty())
        {
            Text(placeholder, color = RlPalette.TextDisabled, fontSize = 12.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
            cursorBrush = SolidColor(RlPalette.Accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Compact pill button shared across panels (search/save/refresh/etc). */
@Composable
internal fun PanelButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
)
{
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (enabled) RlPalette.MediumGray else Color(0xFF2A2A2A))
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 32.dp)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) Color.White else RlPalette.TextDisabled,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
        )
    }
}

/** A divider that matches Sidebar.kt's 1dp soft divider. */
@Composable
internal fun PanelDivider()
{
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(RlPalette.DividerSoft),
    )
}

@Composable
internal fun PanelEmptyState(message: String)
{
    Box(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, color = RlPalette.TextSecondary, fontSize = 12.sp)
    }
}
