package net.runelite.mp.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import net.runelite.mp.R

internal object RlFonts {
    val Regular = FontFamily(Font(R.font.runescape), Font(R.font.runescape_bold, FontWeight.Bold))
    val Small = FontFamily(Font(R.font.runescape_small))
}

@Composable
internal fun RuneLiteMenuTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = RlPalette.Accent, onPrimary = Color.White,
        secondary = RlPalette.Accent, onSecondary = Color.White,
        surface = RlPalette.DarkerGray, onSurface = RlPalette.TextPrimary,
        background = RlPalette.DarkGray, onBackground = RlPalette.TextPrimary,
        outline = RlPalette.SurfaceBorder,
    )) {
        ProvideTextStyle(TextStyle(fontFamily = RlFonts.Regular, fontSize = 14.sp, lineHeight = 16.sp)) {
            content()
        }
    }
}

/**
 * Compose palette mirroring RuneLite's [net.runelite.client.ui.ColorScheme]. Kept as
 * plain values rather than a Material3 ColorScheme because the UI deliberately renders
 * its own components for parity with the in-game OSRS look — Material's defaults would
 * read as "Android app on top of OSRS" instead of "OSRS with a Compose chrome".
 */
internal object RlPalette
{
    val BrandOrange      = Color(0xFFDC8A00)
    val BrandOrangeDim   = Color(0xFF553600)
    val DarkerGray       = Color(0xFF1E1E1E)
    val DarkGray         = Color(0xFF242424)
    val MediumGray       = Color(0xFF2C2C2C)
    val Surface          = Color(0xFF1F2123)
    val SurfaceBorder    = Color(0xFFFFFFFF).copy(alpha = 0.10f)
    val DividerStrong    = Color(0xFFFFFFFF).copy(alpha = 0.35f)
    val DividerSoft      = Color(0xFFFFFFFF).copy(alpha = 0.06f)
    val TextPrimary      = Color.White
    val TextSecondary    = Color(0xFFAAAAAA)
    val TextDisabled     = Color(0xFF666666)
    val Accent           = BrandOrange
    val AccentSurface    = Color(0xFF332518)        // panel background when "active"
    val OkGreen          = Color(0xFF78D278)
    val DangerRed        = Color(0xFFFF6B6B)
}
