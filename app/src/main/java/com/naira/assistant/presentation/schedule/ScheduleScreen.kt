package com.naira.assistant.presentation.schedule

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naira.assistant.core.database.reminder.RecurrenceType
import com.naira.assistant.core.database.reminder.ReminderItem
import com.naira.assistant.core.database.reminder.ReminderPriority
import com.naira.assistant.core.database.reminder.ReminderStatus
import com.naira.assistant.core.sync.SupabaseReminderRepository
import com.naira.assistant.core.theme.Aqua
import com.naira.assistant.core.theme.CardStroke
import com.naira.assistant.core.theme.Lilac
import com.naira.assistant.core.theme.Night
import com.naira.assistant.core.theme.TextHigh
import com.naira.assistant.core.theme.TextSoft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ────────────────────────── Theme tokens ─────────────────────────── */

private val Surface = Color(0xFF0C1420)
private val Danger  = Color(0xFFFF6B6B)
private val Warning = Color(0xFFFFC857)
private val Success = Color(0xFF4ADE80)

/* ────────────────────────── Entry point ─────────────────────────── */

@Composable
fun ScheduleScreen(
    context: Context = LocalContext.current,
    onBack: () -> Unit = {}
) {
    val repo = remember { SupabaseReminderRepository.getInstance() }
    LaunchedEffect(repo) { repo.refresh() }
    val reminders by repo.observeAll().collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(Night)
            .statusBarsPadding()
    ) {
        // ── Top bar ──────────────────────────────────────────────────
        TopBar(onBack = onBack)

        // ── Content ──────────────────────────────────────────────────
        if (reminders.isEmpty()) {
            EmptyState()
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { Spacer(Modifier.height(4.dp)) }
                items(reminders, key = { it.id }) { reminder ->
                    ReminderCard(reminder)
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

/* ────────────────────────── Top bar ─────────────────────────────── */

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = TextHigh,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Schedule",
            color = TextHigh,
            fontSize = 23.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/* ────────────────────────── Empty state ─────────────────────────── */

@Composable
private fun EmptyState() {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.NotificationsNone,
                contentDescription = null,
                tint = TextSoft.copy(alpha = 0.4f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "No reminders yet",
                color = TextHigh,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Ask Thrisa to set a reminder for you",
                color = TextSoft,
                fontSize = 14.sp
            )
        }
    }
}

/* ────────────────────────── Reminder card ───────────────────────── */

@Composable
private fun ReminderCard(item: ReminderItem) {
    val statusColor = when (item.status) {
        ReminderStatus.ACTIVE   -> Aqua
        ReminderStatus.SNOOZED  -> Warning
        ReminderStatus.DONE     -> Success
        ReminderStatus.CANCELLED -> Danger
    }
    val priorityColor = when (item.priority) {
        ReminderPriority.LOW    -> TextSoft
        ReminderPriority.NORMAL -> Aqua
        ReminderPriority.HIGH   -> Warning
        ReminderPriority.URGENT -> Danger
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .border(1.dp, CardStroke.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Status dot / icon
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(statusColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (item.status == ReminderStatus.DONE)
                    Icons.Outlined.CheckCircle
                else
                    Icons.Outlined.Schedule,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        // Content
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = TextHigh,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (item.description.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = item.description,
                    color = TextSoft,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(8.dp))

            // Time + recurrence row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Trigger time
                Text(
                    text = formatMs(item.nextTriggerMs),
                    color = Aqua,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                // Recurrence badge
                if (item.recurrence != RecurrenceType.NONE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Repeat,
                            contentDescription = null,
                            tint = Lilac,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = item.recurrence.id.replaceFirstChar { it.uppercaseChar() },
                            color = Lilac,
                            fontSize = 11.sp
                        )
                    }
                }

                // Priority badge (only non-NORMAL)
                if (item.priority != ReminderPriority.NORMAL) {
                    StatusPill(
                        text = item.priority.id.replaceFirstChar { it.uppercaseChar() },
                        color = priorityColor
                    )
                }
            }

            // Tags row
            if (item.tags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.tags.take(3).forEach { tag ->
                        StatusPill(text = "#$tag", color = TextSoft)
                    }
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        // Status pill (top-right)
        StatusPill(
            text = item.status.id.replaceFirstChar { it.uppercaseChar() },
            color = statusColor
        )
    }
}

/* ────────────────────────── Small pill ──────────────────────────── */

@Composable
private fun StatusPill(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
    }
}

/* ────────────────────────── Helpers ─────────────────────────────── */

private val dateFormat = SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault())

private fun formatMs(ms: Long): String =
    if (ms <= 0L) "—" else dateFormat.format(Date(ms))
