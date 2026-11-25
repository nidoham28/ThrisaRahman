package com.naira.assistant.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = NairaColors.Indigo,
    onPrimary = Color.White,
    primaryContainer = NairaColors.IndigoLight,
    onPrimaryContainer = Color(0xFF14105B),
    secondary = NairaColors.Aqua,
    onSecondary = Color(0xFF003731),
    secondaryContainer = NairaColors.AquaLight,
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFF8E4A76),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8EB),
    onTertiaryContainer = Color(0xFF3B092A),
    error = NairaColors.Error,
    onError = Color.White,
    errorContainer = NairaColors.ErrorContainer,
    onErrorContainer = NairaColors.OnErrorContainer,
    background = NairaColors.Canvas,
    onBackground = NairaColors.Ink,
    surface = NairaColors.Surface,
    onSurface = NairaColors.Ink,
    surfaceVariant = NairaColors.SurfaceRaised,
    onSurfaceVariant = NairaColors.InkMuted,
    outline = NairaColors.Outline,
    outlineVariant = NairaColors.OutlineSoft,
    scrim = Color(0x990D0D18),
    inverseSurface = NairaColors.Ink,
    inverseOnSurface = NairaColors.Canvas,
    inversePrimary = NairaColors.IndigoDark,
    surfaceTint = NairaColors.Indigo
)

private val DarkColors = darkColorScheme(
    primary = NairaColors.AquaDark,
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF0A5049),
    onPrimaryContainer = NairaColors.AquaLight,
    secondary = NairaColors.IndigoDark,
    onSecondary = Color(0xFF241C70),
    secondaryContainer = Color(0xFF3B31A8),
    onSecondaryContainer = Color(0xFFE6E2FF),
    tertiary = Color(0xFFFFAFD8),
    onTertiary = Color(0xFF5D1945),
    tertiaryContainer = Color(0xFF7B305D),
    onTertiaryContainer = Color(0xFFFFD8EB),
    error = NairaColors.DarkError,
    onError = Color(0xFF690005),
    errorContainer = NairaColors.DarkErrorContainer,
    onErrorContainer = NairaColors.DarkOnErrorContainer,
    background = NairaColors.DarkCanvas,
    onBackground = NairaColors.DarkInk,
    surface = NairaColors.DarkSurface,
    onSurface = NairaColors.DarkInk,
    surfaceVariant = NairaColors.DarkSurfaceRaised,
    onSurfaceVariant = NairaColors.DarkInkMuted,
    outline = NairaColors.DarkOutline,
    outlineVariant = NairaColors.DarkOutlineSoft,
    scrim = Color(0x99000000),
    inverseSurface = NairaColors.DarkInk,
    inverseOnSurface = NairaColors.DarkCanvas,
    inversePrimary = NairaColors.Aqua,
    surfaceTint = NairaColors.AquaDark
)

private val NairaShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun NairaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = NairaTypography,
        shapes = NairaShapes,
        content = content
    )
}
