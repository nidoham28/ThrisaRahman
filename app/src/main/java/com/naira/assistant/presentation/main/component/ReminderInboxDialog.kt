package com.naira.assistant.presentation.main.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import java.util.Date
import java.util.Locale

@Composable
fun ReminderInboxDialog(
    reminders: List<ReminderItem>,
    onDismiss: () -> Unit,
    onOpenSchedule: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0B1420),
        titleContentColor = TextHigh,
        textContentColor = TextSoft,
        title = {
            Column {
                Text("A little nudge for later", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (reminders.isEmpty()) "Your reminder space is clear."
                    else "${reminders.size} reminder${if (reminders.size == 1) "" else "s"} to keep in view",
                    color = TextSoft,
                    fontSize = 13.sp
                )
            }
        },
        text = {
            if (reminders.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Outlined.NotificationsActive, null, tint = Aqua, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Ask me to remind you about something.", color = TextSoft, fontSize = 14.sp)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    reminders.take(5).forEach { reminder ->
                        ReminderInboxRow(reminder)
                    }
                    if (reminders.size > 5) {
                        Text("And ${reminders.size - 5} more in your schedule", color = TextSoft, fontSize = 12.sp)
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
            TextButton(onClick = onDismiss) { Text("Done", color = TextSoft) }
        }
    )
}

@Composable
private fun ReminderInboxRow(reminder: ReminderItem) {
    val trigger = if (reminder.status == ReminderStatus.SNOOZED && reminder.snoozedUntilMs > 0) {
        reminder.snoozedUntilMs
    } else reminder.nextTriggerMs
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF101D2A))
            .border(1.dp, CardStroke, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.size(8.dp).clip(CircleShape).background(Aqua))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(reminder.title, color = TextHigh, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault()).format(Date(trigger)),
                color = TextSoft,
                fontSize = 12.sp
            )
        }
        if (reminder.status == ReminderStatus.SNOOZED) {
            Text("SNOOZED", color = Aqua, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}
