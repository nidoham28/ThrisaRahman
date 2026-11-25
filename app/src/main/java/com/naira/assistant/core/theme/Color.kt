package com.naira.assistant.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Brand and semantic colors for Naira's digital assistant. */
internal object NairaColors {
    // Electric indigo with a bright aqua accent gives the assistant a clear, modern identity.
    val Indigo = Color(0xFF4F46E5)
    val IndigoLight = Color(0xFFE4E2FF)
    val IndigoDark = Color(0xFFBEB9FF)
    val Aqua = Color(0xFF00B8A9)
    val AquaLight = Color(0xFFB9F2EB)
    val AquaDark = Color(0xFF56D9CC)

    val Ink = Color(0xFF151827)
    val InkMuted = Color(0xFF4A4C60)
    val Canvas = Color(0xFFF7F8FC)
    val CanvasDim = Color(0xFFECEDF4)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceRaised = Color(0xFFEFF0F8)
    val Outline = Color(0xFF7A7C90)
    val OutlineSoft = Color(0xFFCBCDDB)

    val DarkCanvas = Color(0xFF0A0E18)
    val DarkCanvasDim = Color(0xFF131826)
    val DarkSurface = Color(0xFF151B29)
    val DarkSurfaceRaised = Color(0xFF1E2536)
    val DarkInk = Color(0xFFE8EAF4)
    val DarkInkMuted = Color(0xFFA8B2C6)
    val DarkOutline = Color(0xFF8B94A9)
    val DarkOutlineSoft = Color(0xFF2C3448)

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
val Night = Color(0xFF05070D)
val CardStroke = Color(0xFF243448)
val Aqua = Color(0xFF3FE6D2)
val AquaDeep = Color(0xFF0E5E58)
val Lilac = Color(0xFFA78BFA)
val TextHigh = Color(0xFFF3F7FC)
val TextSoft = Color(0xFF93A2B8) // lifted for contrast at readable sizes
val OnAqua = Color(0xFF04211D)
val ErrorSoft = Color(0xFFFF9F9F)

// Shared semantic accents (drawer, schedule, dialogs).
val Danger = Color(0xFFFF6B6B)
val SuccessAccent = Color(0xFF4ADE80)
val WarningAccent = Color(0xFFFFC857)

// Component surfaces.
val NavBarSurface = Color(0xFF070B13)
val DrawerSurface = Color(0xFF070C14)
val SheetSurface = Color(0xFF0B1420)
val FieldSurface = Color(0xFF0D1622)
val MicRing = Color(0xFF0B3531)

val HeroShape = RoundedCornerShape(30.dp)
val CardShape = RoundedCornerShape(20.dp)

// Aurora hero: teal glow fading through midnight into a violet bloom.
val HeroBrush = Brush.linearGradient(
    0f to Color(0xFF0E2E3E),
    0.52f to Color(0xFF0A111D),
    1f to Color(0xFF1B1440),
)
val HeroStroke = Brush.verticalGradient(
    listOf(
        Color(0x593FE6D2),
        Color(0x1C2A3A50),
        Color(0x59A78BFA),
    )
)
val CardBrush = Brush.verticalGradient(listOf(Color(0xFF111B29), Color(0xFF0A101A)))
val CardStrokeBrush = Brush.verticalGradient(
    listOf(Color(0x66243448), Color(0x3316202D))
)
