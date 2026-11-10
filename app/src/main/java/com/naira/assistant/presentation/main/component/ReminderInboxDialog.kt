package com.naira.assistant.presentation.main.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naira.assistant.core.database.reminder.ReminderItem
import com.naira.assistant.core.database.reminder.ReminderStatus
import com.naira.assistant.core.theme.Aqua
import com.naira.assistant.core.theme.CardStroke
import com.naira.assistant.core.theme.TextHigh
import com.naira.assistant.core.theme.TextSoft
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val MAX_VISIBLE_REMINDERS = 5

@Composable
fun ReminderInboxDialog(
    reminders: List<ReminderItem>,
    onDismiss: () -> Unit,
    onOpenSchedule: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Locale only changes on activity recreation, so remember is safe here.
    val locale = remember { Locale.getDefault() }
    val visible = remember(reminders) { reminders.take(MAX_VISIBLE_REMINDERS) }
    val hiddenCount = remember(reminders) {
        (reminders.size - MAX_VISIBLE_REMINDERS).coerceAtLeast(0)
    }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0B1420),
        title = {
            Column {
                Text(
                    text = "A little nudge for later",
                    color = TextHigh,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when (reminders.size) {
                        0 -> "Your reminder space is clear."
                        1 -> "1 reminder to keep in view"
                        else -> "${reminders.size} reminders to keep in view"
                    },
                    color = TextSoft,
                    fontSize = 13.sp,
                )
            }
        },
        text = {
            if (reminders.isEmpty()) {
                EmptyState()
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    visible.forEach { reminder ->
                        ReminderInboxRow(reminder = reminder, locale = locale)
                    }
                    if (hiddenCount > 0) {
                        Text(
                            text = "$hiddenCount more in your schedule",
                            color = TextSoft,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenSchedule) {
                Text("Open schedule", color = Aqua, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSoft)
            }
        },
    )
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.NotificationsActive,
            contentDescription = null,
            tint = Aqua,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Ask me to remind you about something.",
            color = TextSoft,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun ReminderInboxRow(
    reminder: ReminderItem,
    locale: Locale,
) {
    val trigger = if (
        reminder.status == ReminderStatus.SNOOZED && reminder.snoozedUntilMs > 0
    ) {
        reminder.snoozedUntilMs
    } else {
        reminder.nextTriggerMs
    }

    // Computed once per (trigger, locale), not once per recomposition.
    // Recomputation only on trigger change or locale change (rare).
    val timeLabel = remember(trigger, locale) {
        relativeTimeLabel(triggerMs = trigger, locale = locale)
    }
    val titleLabel = reminder.title.ifBlank { "Untitled reminder" }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF101D2A))
            .border(1.dp, CardStroke, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Decorative state dot — `background(color, shape)` draws + clips in one.
        Box(Modifier.size(8.dp).background(Aqua, CircleShape))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = titleLabel,
                color = TextHigh,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = timeLabel,
                color = TextSoft,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (reminder.status == ReminderStatus.SNOOZED) {
            SnoozedBadge()
        }
    }
}

@Composable
private fun SnoozedBadge() {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Aqua.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = "SNOOZED",
            color = Aqua,
            fontSize = 10.sp,
            letterSpacing = 0.6.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Human-friendly trigger label:
 *  - 0/negative → "No time set"
 *  - today       → "Today · 3:00 PM"
 *  - tomorrow    → "Tomorrow · 9:00 AM"
 *  - other       → "Mon, Nov 10 · 3:00 PM"
 *
 * Uses [Calendar] (not java.time) so the caller doesn't need API 26 or
 * coreLibraryDesugaring.
 */
private fun relativeTimeLabel(
    triggerMs: Long,
    locale: Locale,
    nowMs: Long = System.currentTimeMillis(),
): String {
    if (triggerMs <= 0L) return "No time set"

    val zone = TimeZone.getDefault()
    val now = Calendar.getInstance(zone).apply { timeInMillis = nowMs }
    val trigger = Calendar.getInstance(zone).apply { timeInMillis = triggerMs }

    val sameDay = now.get(Calendar.ERA) == trigger.get(Calendar.ERA) &&
            now.get(Calendar.YEAR) == trigger.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == trigger.get(Calendar.DAY_OF_YEAR)

    val tomorrow = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
    val isTomorrow = tomorrow.get(Calendar.ERA) == trigger.get(Calendar.ERA) &&
            tomorrow.get(Calendar.YEAR) == trigger.get(Calendar.YEAR) &&
            tomorrow.get(Calendar.DAY_OF_YEAR) == trigger.get(Calendar.DAY_OF_YEAR)

    val timeOnly = SimpleDateFormat("h:mm a", locale).format(Date(triggerMs))
    return when {
        sameDay -> "Today · $timeOnly"
        isTomorrow -> "Tomorrow · $timeOnly"
        else -> SimpleDateFormat("EEE, MMM d · h:mm a", locale).format(Date(triggerMs))
    }
}