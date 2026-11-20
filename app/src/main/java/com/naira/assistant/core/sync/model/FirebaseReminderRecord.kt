package com.naira.assistant.core.sync.model

import com.google.firebase.database.IgnoreExtraProperties
import com.naira.assistant.core.database.reminder.RecurrenceType
import com.naira.assistant.core.database.reminder.ReminderItem
import com.naira.assistant.core.database.reminder.ReminderPriority
import com.naira.assistant.core.database.reminder.ReminderStatus

@IgnoreExtraProperties
data class FirebaseReminderRecord(
    val id: Long = 0,
    val uuid: String = "",
    val title: String = "",
    val description: String = "",
    val triggerAtMs: Long = 0,
    val recurrence: String = RecurrenceType.NONE.id,
    val recurrenceInterval: Int = 0,
    val status: String = ReminderStatus.ACTIVE.id,
    val priority: String = ReminderPriority.NORMAL.id,
    val tags: List<String> = emptyList(),
    val snoozedUntilMs: Long = 0,
    val nextTriggerMs: Long = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val source: String = "ai_tool",
) {
    fun toDomain(recordKey: String): ReminderItem {
        val resolvedUuid = uuid.ifBlank { recordKey }
        val resolvedTriggerAt = triggerAtMs.takeIf { it > 0 } ?: System.currentTimeMillis()
        val now = System.currentTimeMillis()
        return ReminderItem(
            id = id.takeIf { it != 0L } ?: resolvedUuid.toFirebaseId(),
            uuid = resolvedUuid,
            title = title,
            description = description,
            triggerAtMs = resolvedTriggerAt,
            recurrence = RecurrenceType.fromId(recurrence),
            recurrenceInterval = recurrenceInterval.coerceAtLeast(0),
            status = ReminderStatus.fromId(status),
            priority = ReminderPriority.fromId(priority),
            tags = tags,
            snoozedUntilMs = snoozedUntilMs,
            nextTriggerMs = nextTriggerMs.takeIf { it > 0 } ?: resolvedTriggerAt,
            createdAt = createdAt.takeIf { it > 0 } ?: now,
            updatedAt = updatedAt.takeIf { it > 0 } ?: now,
            source = source,
        )
    }

    companion object {
        fun fromDomain(item: ReminderItem): FirebaseReminderRecord = FirebaseReminderRecord(
            id = item.id.takeIf { it != 0L } ?: item.uuid.toFirebaseId(),
            uuid = item.uuid,
            title = item.title,
            description = item.description,
            triggerAtMs = item.triggerAtMs,
            recurrence = item.recurrence.id,
            recurrenceInterval = item.recurrenceInterval,
            status = item.status.id,
            priority = item.priority.id,
            tags = item.tags,
            snoozedUntilMs = item.snoozedUntilMs,
            nextTriggerMs = item.nextTriggerMs,
            createdAt = item.createdAt,
            updatedAt = item.updatedAt,
            source = item.source,
        )
    }
}
