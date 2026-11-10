package com.naira.assistant.presentation.main.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.ui.graphics.vector.ImageVector
import com.naira.assistant.domain.NavDestination

/**
 * Compose icon for each destination.
 *
 * Lives in the presentation layer so [NavDestination] itself stays
 * framework-free. The centre action uses a *filled* icon so it reads as a
 * button, not a tab.
 */
val NavDestination.icon: ImageVector
    get() = when (this) {
        NavDestination.HOME -> Icons.Outlined.Home
        NavDestination.CHAT -> Icons.Outlined.ChatBubbleOutline
        NavDestination.SPEAK -> Icons.Filled.Mic
        NavDestination.MEMORIES -> Icons.Outlined.Layers
        NavDestination.PROFILE -> Icons.Outlined.PersonOutline
    }

/**
 * Ordered bottom-bar destinations — the same visual order as the enum.
 *
 * Alias kept so existing call sites that reference `destinations` keep
 * compiling. New code can use `NavDestination.entries` directly.
 */
val destinations: List<NavDestination>
    get() = NavDestination.entries