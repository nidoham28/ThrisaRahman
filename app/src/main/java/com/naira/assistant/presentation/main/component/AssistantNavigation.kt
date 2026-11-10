package com.naira.assistant.presentation.main.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naira.assistant.core.sound.VoiceState
import com.naira.assistant.core.sound.isActive
import com.naira.assistant.core.theme.Aqua
import com.naira.assistant.core.theme.CardStroke
import com.naira.assistant.core.theme.Dim
import com.naira.assistant.core.theme.OnAqua
import com.naira.assistant.core.theme.TextSoft
import com.naira.assistant.domain.MicIndex
import com.naira.assistant.domain.NavDestination

// ───────────────────────── Navigation ─────────────────────────
@Composable
fun AssistantNavigation(
    selectedTab: Int,
    voice: VoiceState,
    onSelect: (Int) -> Unit,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Color(0xFF070C14))
            .navigationBarsPadding()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(CardStroke)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(Dim.NavBar)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEachIndexed { index, dest ->
                if (index == MicIndex) {
                    MicButton(
                        modifier = Modifier.weight(1f),
                        active = voice.isActive,
                        onClick = onMicClick,
                    )
                } else {
                    NavItem(
                        dest = dest,
                        selected = selectedTab == index,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(index) },
                    )
                }
            }
        }
    }
}

@Composable
fun NavItem(
    dest: NavDestination,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(
        targetValue = if (selected) Aqua else TextSoft,
        label = "navItemTint",
    )
    val pillBackground by animateColorAsState(
        targetValue = if (selected) Aqua.copy(alpha = 0.14f) else Color.Transparent,
        label = "navItemPill",
    )

    Column(
        modifier
            .fillMaxHeight()
            // `selectable` (not `clickable`) — announces "selected" to TalkBack
            // and gives the node Role.Tab automatically.
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(width = 52.dp, height = 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(pillBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = dest.icon,
                // Null: the Text below already names the tab. Non-null here
                // makes TalkBack announce the label twice.
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(Dim.IconNav),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = dest.label,
            color = tint,
            fontSize = 12.sp,
            maxLines = 1,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
fun MicButton(
    modifier: Modifier,
    active: Boolean,
    onClick: () -> Unit,
) {
    val lift = (-16).dp
    Box(modifier, contentAlignment = Alignment.Center) {
        if (active) {
            MicPulse(lift = lift)
        }
        Box(
            Modifier
                .offset(y = lift) // raised above the bar
                .shadow(18.dp, CircleShape, ambientColor = Aqua, spotColor = Aqua)
                .size(Dim.Mic)
                .clip(CircleShape)
                .background(Aqua)
                .border(4.dp, Color(0xFF0B2F2F), CircleShape)
                .clickable(
                    role = Role.Button,
                    onClick = onClick,
                )
                .semantics {
                    contentDescription =
                        if (active) "Stop voice conversation" else "Start voice conversation"
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (active) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = null, // parent Box owns the description
                tint = OnAqua,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

/**
 * Expanding ring while the mic is active.
 *
 * Two rings offset by half a period so one fades in as the other fades out —
 * hides the "snap" that a single ring has on restart.
 */
@Composable
private fun MicPulse(lift: androidx.compose.ui.unit.Dp) {
    val progress by rememberInfiniteTransition(label = "mic").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        // Linear easing: the ring should expand at a constant rate like a radar
        // pulse. FastOutSlowInEasing makes it look like it stutters.
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "micPulse",
    )
    repeat(2) { i ->
        // Half-offset each ring so their fade in/out overlaps.
        val p = (progress + i * 0.5f) % 1f
        val scale = 1f + p * 0.45f
        val alpha = (1f - p) * 0.35f
        Box(
            Modifier
                .offset(y = lift)
                .size(Dim.Mic)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .background(Aqua, CircleShape)
        )
    }
}