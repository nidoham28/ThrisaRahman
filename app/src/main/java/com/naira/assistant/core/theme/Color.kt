package com.naira.assistant.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Brand and semantic colors for Thrisa's digital assistant. */
internal object ThrisaColors {
    // Electric indigo with a bright aqua accent gives the assistant a clear, modern identity.
    val Indigo = Color(0xFF5146E5)
    val IndigoLight = Color(0xFFE7E5FF)
    val IndigoDark = Color(0xFFB9B3FF)
    val Aqua = Color(0xFF00B8A9)
    val AquaLight = Color(0xFFB9F2EB)
    val AquaDark = Color(0xFF56D9CC)

    val Ink = Color(0xFF171827)
    val InkMuted = Color(0xFF49495B)
    val Canvas = Color(0xFFF8F8FC)
    val CanvasDim = Color(0xFFEDEDF4)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceRaised = Color(0xFFF0EFF8)
    val Outline = Color(0xFF79798A)
    val OutlineSoft = Color(0xFFCAC9D8)

    val DarkCanvas = Color(0xFF11121B)
    val DarkCanvasDim = Color(0xFF1A1B27)
    val DarkSurface = Color(0xFF20212E)
    val DarkSurfaceRaised = Color(0xFF2A2B39)
    val DarkInk = Color(0xFFE7E6F1)
    val DarkInkMuted = Color(0xFFC5C4D2)
    val DarkOutline = Color(0xFF9291A3)
    val DarkOutlineSoft = Color(0xFF454655)

    val Error = Color(0xFFBA1A1A)
    val ErrorContainer = Color(0xFFFFDAD6)
    val OnErrorContainer = Color(0xFF410002)
    val DarkError = Color(0xFFFFB4AB)
    val DarkErrorContainer = Color(0xFF93000A)
    val DarkOnErrorContainer = Color(0xFFFFDAD6)

    val Success = Color(0xFF18794E)
    val SuccessContainer = Color(0xFFB7F1D0)
    val Warning = Color(0xFF8A5700)
    val WarningContainer = Color(0xFFFFDEA6)
    val Info = Color(0xFF2457A7)
    val InfoContainer = Color(0xFFD9E2FF)
}

// ───────────────────────── Design tokens ─────────────────────────
val Night = Color(0xFF04070D)
val CardStroke = Color(0xFF1F2B38)
val Aqua = Color(0xFF3FE6D2)
val AquaDeep = Color(0xFF0E5E58)
val Lilac = Color(0xFFB794F6)
val TextHigh = Color(0xFFF2F6FA)
val TextSoft = Color(0xFF9AA7B7) // lifted for contrast at readable sizes
val OnAqua = Color(0xFF031412)
val ErrorSoft = Color(0xFFFF9F9F)

val HeroShape = RoundedCornerShape(28.dp)
val CardShape = RoundedCornerShape(18.dp)

val HeroBrush = Brush.radialGradient(
    listOf(Color(0xFF14324A), Color(0xFF0A1422), Color(0xFF060B14)),
    center = Offset(500f, 700f), radius = 1100f
)
val CardBrush = Brush.verticalGradient(listOf(Color(0xFF0E1621), Color(0xFF090E16)))
