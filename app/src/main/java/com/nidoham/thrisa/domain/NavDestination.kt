package com.nidoham.thrisa.domain

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.ui.graphics.vector.ImageVector

data class NavDestination(val label: String, val icon: ImageVector)

val destinations = listOf(
    NavDestination("Home", Icons.Outlined.Home),
    NavDestination("Chat", Icons.Outlined.ChatBubbleOutline),
    NavDestination("Speak", Icons.Filled.Mic), // centre action
    NavDestination("Memories", Icons.Outlined.Layers),
    NavDestination("Profile", Icons.Outlined.PersonOutline),
)

const val MicIndex = 2