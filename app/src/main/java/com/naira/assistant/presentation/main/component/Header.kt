package com.naira.assistant.presentation.main.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naira.assistant.core.theme.Dim

// ───────────────────────── Header ─────────────────────────
@Composable
fun Header(
    modifier: Modifier = Modifier,
    onMenuClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    notificationCount: Int = 0,
    userInitials: String? = null,   // NEW: show avatar instead of generic menu icon
    isOnline: Boolean = false,      // NEW: presence dot
) {
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val hasUnread = notificationCount > 0

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        // ── Leading: avatar (modern) or fallback menu ──
        Box(Modifier.align(Alignment.CenterStart)) {
            if (userInitials != null) {
                AvatarButton(
                    initials = userInitials,
                    isOnline = isOnline,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onMenuClick()
                    },
                )
            } else {
                IconButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onMenuClick()
                }) {
                    Icon(
                        imageVector = Icons.Outlined.Menu,
                        contentDescription = "Open menu",
                        tint = colors.onSurface,
                        modifier = Modifier.size(Dim.IconNav),
                    )
                }
            }
        }

        // ── Center: wordmark (bold + tracking, recentred) ──
        Text(
            text = "NAIRA",
            color = colors.onSurface,
            fontSize = 20.sp,
            letterSpacing = 4.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.offset(x = 2.dp),
        )

        // ── Trailing: notifications with spring-animated badge ──
        IconButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onNotificationsClick()
            },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            BadgedBox(
                badge = {
                    AnimatedVisibility(
                        visible = hasUnread,
                        enter = scaleIn(
                            spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        ),
                        exit = scaleOut(spring()),
                    ) {
                        Badge(
                            containerColor = colors.errorContainer,
                            contentColor = colors.onErrorContainer,
                        ) {
                            Text(
                                text = if (notificationCount > 99) "99+"
                                else notificationCount.toString(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                // Prevent TalkBack from reading the number twice —
                                // it's already in the Icon's contentDescription.
                                modifier = Modifier.clearAndSetSemantics {},
                            )
                        }
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = if (hasUnread)
                        "$notificationCount reminders unread" else "Reminders",
                    tint = colors.onSurface,
                    modifier = Modifier.size(Dim.IconNav),
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))
}

// ─────────────────────── Avatar button ───────────────────────
@Composable
private fun AvatarButton(
    initials: String,
    isOnline: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val gradient = Brush.linearGradient(listOf(colors.primary, colors.tertiary))
    val onlineGreen = Color(0xFF22C55E)

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)          // circular ripple
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(gradient)
                .border(1.5.dp, colors.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials.take(2).uppercase(),
                color = colors.onPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )
        }

        if (isOnline) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-8).dp, y = (-8).dp)
                    .size(10.dp)
                    .background(onlineGreen, CircleShape)
                    .border(2.dp, colors.surface, CircleShape),
            )
        }
    }
}


@Preview(showSystemUi = true)
@Composable
fun PreviewLayout(){
    Header()
}