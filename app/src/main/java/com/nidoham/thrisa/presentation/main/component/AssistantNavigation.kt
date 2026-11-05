package com.nidoham.thrisa.presentation.main.component

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nidoham.thrisa.core.sound.VoiceState
import com.nidoham.thrisa.core.sound.isActive
import com.nidoham.thrisa.core.theme.Aqua
import com.nidoham.thrisa.core.theme.CardStroke
import com.nidoham.thrisa.core.theme.Dim
import com.nidoham.thrisa.core.theme.OnAqua
import com.nidoham.thrisa.core.theme.TextSoft
import com.nidoham.thrisa.domain.MicIndex
import com.nidoham.thrisa.domain.NavDestination
import com.nidoham.thrisa.domain.destinations

// ───────────────────────── Navigation ─────────────────────────
@Composable
fun AssistantNavigation(
    selectedTab: Int, voice: VoiceState, onSelect: (Int) -> Unit, onMicClick: () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF070C14)).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(CardStroke))
        Row(
            Modifier.fillMaxWidth().height(Dim.NavBar).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            destinations.forEachIndexed { index, dest ->
                if (index == MicIndex) {
                    MicButton(Modifier.weight(1f), active = voice.isActive, onClick = onMicClick)
                } else {
                    NavItem(dest, selectedTab == index, Modifier.weight(1f)) { onSelect(index) }
                }
            }
        }
    }
}

@Composable
fun NavItem(dest: NavDestination, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint = if (selected) Aqua else TextSoft
    Column(
        modifier.fillMaxHeight().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(width = 52.dp, height = 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) Aqua.copy(alpha = 0.14f) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) { Icon(dest.icon, dest.label, tint = tint, modifier = Modifier.size(Dim.IconNav)) }
        Spacer(Modifier.height(2.dp))
        Text(
            dest.label, color = tint, fontSize = 12.sp, maxLines = 1,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun MicButton(modifier: Modifier, active: Boolean, onClick: () -> Unit) {
    val lift = (-16).dp
    Box(modifier, contentAlignment = Alignment.Center) {
        if (active) {
            val pulse by rememberInfiniteTransition(label = "mic").animateFloat(
                initialValue = 1f, targetValue = 1.45f,
                animationSpec = infiniteRepeatable(tween(1100)), label = "micScale"
            )
            Box(
                Modifier
                    .offset(y = lift)
                    .size(Dim.Mic)
                    .graphicsLayer { scaleX = pulse; scaleY = pulse; alpha = (1.45f - pulse) * 1.5f }
                    .background(Aqua.copy(alpha = 0.35f), CircleShape)
            )
        }
        Box(
            Modifier
                .offset(y = lift) // raised above the bar
                .shadow(18.dp, CircleShape, ambientColor = Aqua, spotColor = Aqua)
                .size(Dim.Mic)
                .clip(CircleShape)
                .background(Aqua)
                .border(4.dp, Color(0xFF0B2F2F), CircleShape)
                .clickable(onClick = onClick)
                .semantics { contentDescription = if (active) "Stop voice conversation" else "Start voice conversation" },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (active) Icons.Filled.Stop else Icons.Filled.Mic,
                null, tint = OnAqua, modifier = Modifier.size(32.dp)
            )
        }
    }
}