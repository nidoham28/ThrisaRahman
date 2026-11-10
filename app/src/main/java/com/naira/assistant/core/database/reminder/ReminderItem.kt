package com.naira.assistant.core.database.reminder

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Recurrence pattern for repeating reminders.
 */
@Serializable
enum class RecurrenceType(val id: String) {
    NONE("none"),
    DAILY("daily"),
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    YEARLY("yearly"),
    CUSTOM("custom"),       // custom interval in minutes via recurrence_interval
    WEEKDAYS("weekdays"),   // Monday–Friday only
    WEEKENDS("weekends"),   // Saturday–Sunday only
    ;

    companion object {
        fun fromId(raw: String?): RecurrenceType {
            if (raw.isNullOrBlank()) return NONE
            val norm = raw.trim().lowercase()
            return entries.firstOrNull { it.id == norm || it.name.lowercase() == norm } ?: NONE
        }
    }
}

/**
 * Lifecycle state of a reminder.
 */
@Serializable
enum class ReminderStatus(val id: String) {
    ACTIVE("active"),
    SNOOZED("snoozed"),
    DONE("done"),
    CANCELLED("cancelled"),
    ;

    companion object {
        fun fromId(raw: String?): ReminderStatus {
            if (raw.isNullOrBlank()) return ACTIVE
            val norm = raw.trim().lowercase()
            return entries.firstOrNull { it.id == norm || it.name.lowercase() == norm } ?: ACTIVE
        }
    }
}

/**
 * Priority level for a reminder.
 */
@Serializable
enum class ReminderPriority(val id: String, val level: Int) {
    LOW("low", 1),
    NORMAL("normal", 2),
    HIGH("high", 3),
    URGENT("urgent", 4),
    ;

    companion object {
        fun fromId(raw: String?): ReminderPriority {
            if (raw.isNullOrBlank()) return NORMAL
            val norm = raw.trim().lowercase()
            return entries.firstOrNull { it.id == norm || it.name.lowercase() == norm } ?: NORMAL
        }
    }
}

/**
 * A single reminder item stored locally by the AI assistant.
 *
 * @property id              Local SQLite primary key (autoincrement).
 * @property uuid            Globally unique ID (UUIDv4) for future sync.
 * @property title           Short human-readable label (e.g. "Call mom").
 * @property description     Optional longer note for the reminder.
 * @property triggerAtMs     Unix epoch milliseconds when this reminder should first fire.
 * @property recurrence      How often it repeats ([RecurrenceType]).
 * @property recurrenceInterval Custom interval in minutes (only used when recurrence = CUSTOM).
 * @property status          Current lifecycle state ([ReminderStatus]).
 * @property priority        Urgency level ([ReminderPriority]).
 * @property tags            Comma-separated tags for filtering.
 * @property snoozedUntilMs  If status = SNOOZED, the epoch ms to re-fire.
 * @property nextTriggerMs   Pre-computed next fire time (updated after each trigger).
 * @property createdAt       Creation timestamp in ms.
 * @property updatedAt       Last modification timestamp in ms.
 * @property source          Who created the reminder ("ai_tool" or "user_manual").
 */
@Serializable
data class ReminderItem(
    val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val triggerAtMs: Long,
    val recurrence: RecurrenceType = RecurrenceType.NONE,
    val recurrenceInterval: Int = 0,   // minutes; used only for CUSTOM recurrence
    val status: ReminderStatus = ReminderStatus.ACTIVE,
    val priority: ReminderPriority = ReminderPriority.NORMAL,
    val tags: List<String> = emptyList(),
    val snoozedUntilMs: Long = 0,
    val nextTriggerMs: Long = triggerAtMs,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val source: String = "ai_tool",
)
