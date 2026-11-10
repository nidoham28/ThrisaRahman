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
    primary = ThrisaColors.Indigo,
    onPrimary = Color.White,
    primaryContainer = ThrisaColors.IndigoLight,
    onPrimaryContainer = Color(0xFF17105D),
    secondary = ThrisaColors.Aqua,
    onSecondary = Color(0xFF003731),
    secondaryContainer = ThrisaColors.AquaLight,
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFF9A4678),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8EB),
    onTertiaryContainer = Color(0xFF3B092A),
    error = ThrisaColors.Error,
    onError = Color.White,
    errorContainer = ThrisaColors.ErrorContainer,
    onErrorContainer = ThrisaColors.OnErrorContainer,
    background = ThrisaColors.Canvas,
    onBackground = ThrisaColors.Ink,
    surface = ThrisaColors.Surface,
    onSurface = ThrisaColors.Ink,
    surfaceVariant = ThrisaColors.SurfaceRaised,
    onSurfaceVariant = ThrisaColors.InkMuted,
    outline = ThrisaColors.Outline,
    outlineVariant = ThrisaColors.OutlineSoft,
    scrim = Color(0x990D0D18),
    inverseSurface = ThrisaColors.Ink,
    inverseOnSurface = ThrisaColors.Canvas,
    inversePrimary = ThrisaColors.IndigoDark,
    surfaceTint = ThrisaColors.Indigo
)

private val DarkColors = darkColorScheme(
    primary = ThrisaColors.IndigoDark,
    onPrimary = Color(0xFF211975),
    primaryContainer = Color(0xFF3930B7),
    onPrimaryContainer = Color(0xFFE5E1FF),
    secondary = ThrisaColors.AquaDark,
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF005047),
    onSecondaryContainer = Color(0xFFB9F2EB),
    tertiary = Color(0xFFFFAFD8),
    onTertiary = Color(0xFF5D1945),
    tertiaryContainer = Color(0xFF7B305D),
    onTertiaryContainer = Color(0xFFFFD8EB),
    error = ThrisaColors.DarkError,
    onError = Color(0xFF690005),
    errorContainer = ThrisaColors.DarkErrorContainer,
    onErrorContainer = ThrisaColors.DarkOnErrorContainer,
    background = ThrisaColors.DarkCanvas,
    onBackground = ThrisaColors.DarkInk,
    surface = ThrisaColors.DarkSurface,
    onSurface = ThrisaColors.DarkInk,
    surfaceVariant = ThrisaColors.DarkSurfaceRaised,
    onSurfaceVariant = ThrisaColors.DarkInkMuted,
    outline = ThrisaColors.DarkOutline,
    outlineVariant = ThrisaColors.DarkOutlineSoft,
    scrim = Color(0x99000000),
    inverseSurface = ThrisaColors.DarkInk,
    inverseOnSurface = ThrisaColors.DarkCanvas,
    inversePrimary = ThrisaColors.Indigo,
    surfaceTint = ThrisaColors.IndigoDark
)

private val ThrisaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun ThrisaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ThrisaTypography,
        shapes = ThrisaShapes,
        content = content
    )
}
