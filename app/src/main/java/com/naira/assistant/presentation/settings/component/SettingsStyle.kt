@file:Suppress("SpellCheckingInspection")

package com.naira.assistant.presentation.settings.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.People
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.naira.assistant.core.theme.Aqua
import com.naira.assistant.core.theme.Danger as DangerToken
import com.naira.assistant.core.theme.Lilac
import com.naira.assistant.core.theme.SuccessAccent
import com.naira.assistant.core.theme.WarningAccent
import com.naira.assistant.domain.settings.RelationType


/* ------------------------------------------------------------------ */
/*  Theme tokens                                                       */
/* ------------------------------------------------------------------ */

internal val RowSurface = Color(0xFF0D1622)
internal val SheetSurface = Color(0xFF0B1420)
internal val OptionSurface = Color(0xFF0A111C)
internal val Danger     = DangerToken
internal val Premium    = WarningAccent
internal val Success    = SuccessAccent

/* ------------------------------------------------------------------ */
/*  Relation helpers                                                   */
/* ------------------------------------------------------------------ */

internal fun RelationType.accent(): Color = when {
    isMature -> Danger
    isRomantic -> Lilac
    else -> Aqua
}

internal fun RelationType.icon(): ImageVector = when (this) {
    RelationType.FRIENDS -> Icons.Outlined.People
    RelationType.ROMANCE -> Icons.Outlined.FavoriteBorder
    RelationType.INTIMATE -> Icons.Outlined.Favorite
}
