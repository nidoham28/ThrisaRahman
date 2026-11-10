package com.naira.assistant.presentation.schedule

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ────────────────────────── Theme tokens ─────────────────────────── */

private val Surface = Color(0xFF0C1420)
private val Danger  = Color(0xFFFF6B6B)
private val Warning = Color(0xFFFFC857)
private val Success = Color(0xFF4ADE80)

private const val TriggerDateFormat = "EEE, MMM d · h:mm a"

/* ────────────────────────── Entry point ─────────────────────────── */

@Composable
fun ScheduleScreen(
    modifier: Modifier = Modifier,
    assistantName: String = "NAIRA",
    onBack: () -> Unit = {},
) {
    val repo = remember { SupabaseReminderRepository.getInstance() }
    val scope = rememberCoroutineScope()

    // Lifecycle-aware: pauses while backgrounded, resumes when the screen
    // comes back into the composition.
    val reminders by repo.observeAll().collectAsStateWithLifecycle()

    // Refresh on first mount AND every resume. Failures are non-fatal — the
    // local DB is authoritative for rendering, remote is best-effort.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch {
            try {
                repo.refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // no-op
            }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(Night)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        TopBar(onBack = onBack)

        if (reminders.isEmpty()) {
            EmptyState(assistantName = assistantName)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 4.dp,
                    bottom = 16.dp,
                ),
            ) {
                items(reminders, key = { it.id }) { reminder ->
                    ReminderCard(reminder)
                }
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
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // IconButton gives a 48dp Material touch target; the previous 44dp
        // Box was below the platform minimum.
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = TextHigh,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = "Schedule",
            color = TextHigh,
            fontSize = 23.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/* ────────────────────────── Empty state ─────────────────────────── */

@Composable
private fun EmptyState(assistantName: String) {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.NotificationsNone,
                contentDescription = null,
                tint = TextSoft.copy(alpha = 0.4f),
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "No reminders yet",
                color = TextHigh,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Ask $assistantName to set a reminder for you",
                color = TextSoft,
                fontSize = 14.sp,
            )
        }
    }
}

/* ────────────────────────── Reminder card ───────────────────────── */

@Composable
private fun ReminderCard(item: ReminderItem) {
    val statusColor = when (item.status) {
        ReminderStatus.ACTIVE    -> Aqua
        ReminderStatus.SNOOZED   -> Warning
        ReminderStatus.DONE      -> Success
        ReminderStatus.CANCELLED -> Danger
    }
    val priorityColor = when (item.priority) {
        ReminderPriority.LOW    -> TextSoft
        ReminderPriority.NORMAL -> Aqua
        ReminderPriority.HIGH   -> Warning
        ReminderPriority.URGENT -> Danger
    }

    // For SNOOZED reminders the *next* trigger is `snoozedUntilMs`, not
    // `nextTriggerMs` — the latter is the original time that already fired.
    // Showing the original time is what made snoozed reminders read "3:00 PM"
    // at 5:00 PM, confusing users about when the reminder will actually fire.
    val triggerMs = when {
        item.status == ReminderStatus.SNOOZED && item.snoozedUntilMs > 0L -> item.snoozedUntilMs
        else -> item.nextTriggerMs
    }

    val nowMs = remember(item.nextTriggerMs, triggerMs, item.status) {
        System.currentTimeMillis()
    }
    val isOverdue = item.status == ReminderStatus.ACTIVE &&
            triggerMs > 0L && triggerMs < nowMs
    val timeLabel = remember(triggerMs, nowMs) { formatTrigger(triggerMs) }

    // The status dot color doubles as the card's accent; overdue takes
    // priority so the accent reads "this needs attention".
    val timeColor = when {
        isOverdue -> Danger
        else -> Aqua
    }

    // Single accessibility node for the whole card so TalkBack doesn't read
    // title, description, time, recurrence, priority, tags and status as
    // seven separate announcements. The description is composed from the
    // visible content so voice users hear the same information sighted
    // users see.
    val cardDescription = buildString {
        append(item.title)
        if (item.description.isNotBlank()) append(". ${item.description}")
        append(". ")
        if (isOverdue) append("Overdue: ") else append("Scheduled for ")
        append(timeLabel)
        if (item.recurrence != RecurrenceType.NONE) {
            append(", repeats ${item.recurrence.id}")
        }
        append(", status ${item.status.id}")
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .border(1.dp, CardStroke.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .semantics(mergeDescendants = true) { contentDescription = cardDescription }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(statusColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (item.status == ReminderStatus.DONE) {
                    Icons.Outlined.CheckCircle
                } else {
                    Icons.Outlined.Schedule
                },
                contentDescription = null, // whole card carries the semantics
                tint = statusColor,
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = TextHigh,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (item.description.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = item.description,
                    color = TextSoft,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = timeLabel,
                    color = timeColor,
                    fontSize = 12.sp,
                    fontWeight = if (isOverdue) FontWeight.SemiBold else FontWeight.Medium,
                )

                if (item.recurrence != RecurrenceType.NONE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Repeat,
                            contentDescription = null,
                            tint = Lilac,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = item.recurrence.id.replaceFirstChar { it.uppercaseChar() },
                            color = Lilac,
                            fontSize = 11.sp,
                        )
                    }
                }

                if (item.priority != ReminderPriority.NORMAL) {
                    StatusPill(
                        text = item.priority.id.replaceFirstChar { it.uppercaseChar() },
                        color = priorityColor,
                    )
                }
            }

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

        StatusPill(
            text = item.status.id.replaceFirstChar { it.uppercaseChar() },
            color = statusColor,
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
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
        )
    }
}

/* ────────────────────────── Helpers ─────────────────────────────── */

/**
 * Formats a millisecond trigger time in the *current* default locale.
 *
 * Constructs a fresh `SimpleDateFormat` per call rather than sharing a
 * module-level instance — `SimpleDateFormat` is mutable and not thread-safe,
 * and a module-level instance would keep the locale captured at class-load
 * time, so a device-language change wouldn't take effect until process
 * restart. A single format call is ~microseconds; not worth caching.
 */
private fun formatTrigger(ms: Long): String {
    if (ms <= 0L) return "—"
    return SimpleDateFormat(TriggerDateFormat, Locale.getDefault()).format(Date(ms))
}