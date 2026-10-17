package com.primarysuite.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.primarysuite.app.R

// The LuckyTap dark palette (res/values/colors.xml).
object AppColors {
    val Background = Color(0xFF121212)
    val Surface = Color(0xFF1E1E1E)
    val SurfaceVariant = Color(0xFF2C2C2C)
    val OnSurface = Color(0xFFFFFFFF)
    val OnSurfaceMedium = Color(0xB3FFFFFF)
    val OnSurfaceDim = Color(0x66FFFFFF)
    val Outline = Color(0xFF3C3C3C)

    val PrimaryGreen = Color(0xFF66BB6A)
    val PrimaryGreenMuted = Color(0xFF388E3C)
    val OnPrimary = Color(0xFF121212)
    val SecondaryBlue = Color(0xFF64B5F6)
    val TertiaryAmber = Color(0xFFFFB74D)

    val Success = Color(0xFF4CAF50)
    val Error = Color(0xFFEF5350)
    val Warning = Color(0xFFFFAB40)

    val StatusConnected = Color(0xFF66BB6A)
    val StatusConnecting = Color(0xFFFFAB40)
    val StatusDisconnected = Color(0xFFEF5350)
}

/** The display font used for the big scanned number. */
val SocafeFontFamily = FontFamily(Font(R.font.socafe))

private val DarkColors = darkColorScheme(
    primary = AppColors.PrimaryGreen,
    onPrimary = AppColors.OnPrimary,
    primaryContainer = AppColors.PrimaryGreenMuted,
    onPrimaryContainer = Color.White,
    secondary = AppColors.SecondaryBlue,
    onSecondary = AppColors.OnPrimary,
    secondaryContainer = AppColors.SurfaceVariant,
    onSecondaryContainer = AppColors.OnSurface,
    tertiary = AppColors.TertiaryAmber,
    background = AppColors.Background,
    onBackground = AppColors.OnSurface,
    surface = AppColors.Surface,
    onSurface = AppColors.OnSurface,
    surfaceVariant = AppColors.SurfaceVariant,
    onSurfaceVariant = AppColors.OnSurfaceMedium,
    surfaceContainer = AppColors.SurfaceVariant,
    surfaceContainerHigh = AppColors.SurfaceVariant,
    outline = AppColors.Outline,
    outlineVariant = AppColors.Outline,
    error = AppColors.Error,
)

@Composable
fun PrimarySuiteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
