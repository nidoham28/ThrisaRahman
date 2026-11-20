package com.naira.assistant.core.sync.model

import com.naira.assistant.core.database.memory.MemoryItem
import com.naira.assistant.core.database.reminder.RecurrenceType
import com.naira.assistant.core.database.reminder.ReminderItem
import com.naira.assistant.core.database.reminder.ReminderPriority
import com.naira.assistant.core.database.reminder.ReminderStatus
import com.naira.assistant.domain.settings.AssistantProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class FirebaseRecordMappingTest {
    @Test
    fun memoryRecord_roundTripsDomainFields() {
        val uuid = UUID.randomUUID().toString()
        val memory = MemoryItem(
            id = uuid.toFirebaseId(),
            uuid = uuid,
            content = "Prefers concise answers",
            category = "preference",
            importance = 4,
            tags = listOf("style", "communication"),
            createdAt = 100L,
            updatedAt = 200L,
            source = "user_manual",
        )

        assertEquals(memory, FirebaseMemoryRecord.fromDomain(memory).toDomain(uuid))
    }

    @Test
    fun reminderRecord_roundTripsEnumValues() {
        val uuid = UUID.randomUUID().toString()
        val reminder = ReminderItem(
            id = uuid.toFirebaseId(),
            uuid = uuid,
            title = "Review the release",
            description = "Check regression tests",
            triggerAtMs = 1_000L,
            recurrence = RecurrenceType.WEEKLY,
            recurrenceInterval = 2,
            status = ReminderStatus.SNOOZED,
            priority = ReminderPriority.HIGH,
            tags = listOf("work"),
            snoozedUntilMs = 2_000L,
            nextTriggerMs = 2_000L,
            createdAt = 100L,
            updatedAt = 200L,
            source = "user_manual",
        )

        assertEquals(reminder, FirebaseReminderRecord.fromDomain(reminder).toDomain(uuid))
    }

    @Test
    fun providerKeyRecordRetainsProviderAndCredential() {
        val record = FirebaseProviderKeyRecord.from(AssistantProvider.OPENROUTER, " key-value ")

        assertEquals(
            AssistantProvider.OPENROUTER to "key-value",
            record.toEntry(),
        )
    }
}
