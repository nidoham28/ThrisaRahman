package com.nidoham.thrisa.domain

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.nidoham.thrisa.core.theme.Aqua
import com.nidoham.thrisa.core.theme.Lilac

data class Inspiration(val title: String, val subtitle: String, val icon: ImageVector, val tint: Color)

val inspirations = listOf(
    Inspiration("Music", "Find your rhythm", Icons.Outlined.MusicNote, Aqua),
    Inspiration("Learning", "Feed your curiosity", Icons.AutoMirrored.Outlined.MenuBook, Lilac),
    Inspiration("Research", "Go a little deeper", Icons.Outlined.Search, Aqua),
)