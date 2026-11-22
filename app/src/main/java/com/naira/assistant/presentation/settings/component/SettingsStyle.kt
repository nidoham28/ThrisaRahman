@file:Suppress("SpellCheckingInspection")

package com.naira.assistant.presentation.settings.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.People
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.naira.assistant.core.theme.Aqua
import com.naira.assistant.core.theme.Lilac
import com.naira.assistant.domain.settings.RelationType


/* ------------------------------------------------------------------ */
/*  Theme tokens                                                       */
/* ------------------------------------------------------------------ */

internal val RowSurface = Color(0xFF0C1420)
internal val SheetSurface = Color(0xFF0A111B)
internal val OptionSurface = Color(0xFF091018)
internal val Danger     = Color(0xFFFF6B6B)
internal val Premium    = Color(0xFFFFC857)
internal val Success    = Color(0xFF4ADE80)

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
