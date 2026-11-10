package com.naira.assistant.presentation.main.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.naira.assistant.core.theme.Aqua
import com.naira.assistant.core.theme.CardBrush
import com.naira.assistant.core.theme.CardStroke
import com.naira.assistant.core.theme.Lilac
import com.naira.assistant.core.theme.Night
import com.naira.assistant.core.theme.OnAqua
import com.naira.assistant.core.theme.TextHigh
import com.naira.assistant.core.theme.TextSoft
import com.naira.assistant.core.util.GreetingProvider
import com.naira.assistant.domain.RelationType

// ──────────────────────────────────────────────────────────────────────────
// Design tokens
// ──────────────────────────────────────────────────────────────────────────

private val AvatarSize = 88.dp
private val Danger = Color(0xFFFF6B6B)
private val SectionShape = RoundedCornerShape(18.dp)
private val RowShape = RoundedCornerShape(14.dp)

// ──────────────────────────────────────────────────────────────────────────
// Public page
// ──────────────────────────────────────────────────────────────────────────

/**
 * Profile tab. A read-mostly "account home" that complements the drawer —
 * the drawer offers navigation, this surfaces identity, relationship, at-a-
 * glance stats, and the two actions (personalize, upgrade) most likely to
 * be wanted from a profile screen.
 *
 * All data parameters are optional so the page can be mounted before
 * backends are wired up. When [memoryCount] or [reminderCount] is null,
 * the corresponding stat card shows a placeholder rather than "0" — which
 * would falsely imply the user has none.
 *
 * @param displayName  User's preferred name. Drives initials + greeting.
 * @param relationship Configured relationship level.
 * @param memoryCount  Number of saved memories, or null if not yet loaded.
 * @param reminderCount Number of active reminders, or null if not yet loaded.
 */
@Composable
fun ProfilePage(
    modifier: Modifier = Modifier,
    displayName: String = "User",
    relationship: RelationType = RelationType.DEFAULT,
    memoryCount: Int? = null,
    reminderCount: Int? = null,
    onPersonalize: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenSchedule: () -> Unit = {},
    onOpenSubscription: () -> Unit = {},
    onLogout: () -> Unit = {},
) {
    // Greeting refreshes on resume so "Good morning" → "Good evening" tracks
    // the actual time of day when returning from background.
    var greeting by remember { mutableStateOf(GreetingProvider().currentMessage()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        greeting = GreetingProvider().currentMessage()
    }

    Column(
        modifier
            .fillMaxSize()
            .background(Night)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProfileHeader(
            displayName = displayName,
            greeting = greeting,
            relationship = relationship,
        )

        StatsRow(
            memoryCount = memoryCount,
            reminderCount = reminderCount,
        )

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SectionLabel("PERSONAL")
            ProfileActionRow(
                icon = Icons.Outlined.Tune,
                title = "Personalize",
                subtitle = "Name, tone, relationship and style",
                onClick = onPersonalize,
            )
            ProfileActionRow(
                icon = Icons.Outlined.WorkspacePremium,
                title = "Upgrade to Premium",
                subtitle = "Unlock extended memory and limits",
                badge = "PRO",
                onClick = onOpenSubscription,
            )
            ProfileActionRow(
                icon = Icons.Outlined.Schedule,
                title = "Schedule",
                subtitle = "View and manage reminders",
                onClick = onOpenSchedule,
            )
            ProfileActionRow(
                icon = Icons.Outlined.Settings,
                title = "Settings",
                subtitle = "App preferences and account",
                onClick = onOpenSettings,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SectionLabel("ACCOUNT")
            ProfileActionRow(
                icon = Icons.AutoMirrored.Outlined.Logout,
                title = "Log out",
                subtitle = "Sign out of this device",
                isDanger = true,
                onClick = onLogout,
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Made in Bangladesh  ❤\uFE0F",
            color = TextSoft.copy(alpha = 0.55f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Header
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileHeader(
    displayName: String,
    greeting: String,
    relationship: RelationType,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(SectionShape)
            .background(CardBrush)
            .border(1.dp, CardStroke, SectionShape)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProfileAvatar(displayName = displayName)
        Spacer(Modifier.height(14.dp))
        Text(
            text = displayName,
            color = TextHigh,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = greeting,
            color = TextSoft,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(14.dp))
        RelationshipChip(relationship = relationship)
    }
}

@Composable
private fun ProfileAvatar(displayName: String) {
    val initials = remember(displayName) { initialsOf(displayName) }
    val gradient = Brush.linearGradient(listOf(Aqua, Lilac))

    Box(
        Modifier
            .size(AvatarSize)
            .clip(CircleShape)
            .background(gradient)
            .border(2.dp, Color.White.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (initials != null) {
            Text(
                text = initials,
                color = OnAqua,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = OnAqua,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

/** First two letters of the name, or null if the name is generic ("User"). */
private fun initialsOf(displayName: String): String? {
    val trimmed = displayName.trim()
    if (trimmed.isBlank() || trimmed.equals("User", ignoreCase = true)) return null
    return trimmed
        .split(' ', ' ', '\t')
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .take(2)
        .joinToString("")
        .ifBlank { null }
}

@Composable
private fun RelationshipChip(relationship: RelationType) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Aqua.copy(alpha = 0.14f))
            .border(1.dp, Aqua.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(Aqua, CircleShape))
        Spacer(Modifier.size(8.dp))
        Text(
            text = relationship.title,
            color = Aqua,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp,
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Stats
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun StatsRow(memoryCount: Int?, reminderCount: Int?) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatCard(
            icon = Icons.Outlined.Layers,
            label = "Memories",
            value = memoryCount?.toString(),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            icon = Icons.Outlined.NotificationsActive,
            label = "Reminders",
            value = reminderCount?.toString(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(SectionShape)
            .background(CardBrush)
            .border(1.dp, CardStroke, SectionShape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Aqua,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = value ?: "—",
            color = TextHigh,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            text = label,
            color = TextSoft,
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Action list
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = TextSoft.copy(alpha = 0.70f),
        fontSize = 10.sp,
        letterSpacing = 1.6.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 6.dp),
    )
}

@Composable
private fun ProfileActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isDanger: Boolean = false,
    badge: String? = null,
) {
    val tint = if (isDanger) Danger else Aqua
    val titleColor = if (isDanger) Danger else TextHigh

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RowShape)
            .background(Color.Transparent)
            .then(
                Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
            .clip(RowShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = titleColor,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = TextSoft,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (badge != null) {
            Spacer(Modifier.size(8.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Lilac.copy(alpha = 0.18f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = badge,
                    color = Lilac,
                    fontSize = 9.sp,
                    letterSpacing = 0.8.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}