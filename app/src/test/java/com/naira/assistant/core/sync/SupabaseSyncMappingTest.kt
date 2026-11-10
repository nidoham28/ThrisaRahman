package com.naira.assistant.core.sync

import com.naira.assistant.core.database.reminder.RecurrenceType
import com.naira.assistant.core.database.reminder.ReminderItem
import com.naira.assistant.core.database.reminder.ReminderPriority
import com.naira.assistant.core.database.reminder.ReminderStatus
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseSyncMappingTest {

    @Test
    fun `memory row maps to item with server timestamps`() {
        val now = 1_700_000_000_000L
        val row = MemoryRow(
            id = 42,
            uid = "user-1",
            uuid = "uuid-1",
            content = "Likes tea",
            category = "preference",
            importance = 4,
            tags = listOf("tea", "drinks"),
            source = "ai_tool",
            createAt = "2026-11-07T10:00:00Z",
            updateAt = "2026-11-07T11:30:00Z",
        )

        val item = row.toItem(now)

        assertEquals("uuid-1", item.uuid)
        assertEquals("Likes tea", item.content)
        assertEquals("preference", item.category)
        assertEquals(4, item.importance)
        assertEquals(listOf("tea", "drinks"), item.tags)
        assertEquals(42L, item.id) // cloud row id is the item id (cloud-only)
        assertEquals(Instant.parse("2026-11-07T10:00:00Z").toEpochMilli(), item.createdAt)
        assertEquals(Instant.parse("2026-11-07T11:30:00Z").toEpochMilli(), item.updatedAt)
    }

    @Test
    fun `memory row without timestamps falls back to now`() {
        val now = 1_700_000_000_000L
        val item = MemoryRow(uuid = "u", content = "c").toItem(now)

        assertEquals(now, item.createdAt)
        assertEquals(now, item.updatedAt)
    }

    @Test
    fun `schedule row maps to reminder with enums and fallbacks`() {
        val now = 1_700_000_000_000L
        val row = ScheduleRow(
            id = 7,
            uuid = "uuid-7",
            title = "Call mom",
            description = "note",
            triggerAt = "2026-11-08T09:00:00Z",
            recurrence = "weekly",
            recurrenceInterval = 0,
            status = "active",
            priority = "urgent",
            tags = listOf("family"),
            snoozedUntil = null,
            nextTriggerAt = null, // falls back to trigger_at
            source = "ai_tool",
        )

        val item = row.toItem(now)

        assertEquals("Call mom", item.title)
        assertEquals(RecurrenceType.WEEKLY, item.recurrence)
        assertEquals(ReminderStatus.ACTIVE, item.status)
        assertEquals(ReminderPriority.URGENT, item.priority)
        assertEquals(0L, item.snoozedUntilMs)
        assertEquals(item.triggerAtMs, item.nextTriggerMs)
    }

    @Test
    fun `invalid row timestamps fall back to now`() {
        val now = 1_700_000_000_000L
        val item = ScheduleRow(uuid = "u", title = "t", triggerAt = "not-a-date").toItem(now)
        assertEquals(now, item.triggerAtMs)
    }

    @Test
    fun `reminder push body carries uid, uuid and iso timestamps`() {
        val triggerMs = Instant.parse("2026-11-08T09:00:00Z").toEpochMilli()
        val item = ReminderItem(
            id = 3,
            uuid = "uuid-3",
            title = "Take medication",
            description = "after breakfast",
            triggerAtMs = triggerMs,
            recurrence = RecurrenceType.DAILY,
            recurrenceInterval = 0,
            status = ReminderStatus.ACTIVE,
            priority = ReminderPriority.HIGH,
            tags = listOf("health"),
            snoozedUntilMs = 0,
            nextTriggerMs = triggerMs,
            source = "ai_tool",
        )

        val body = item.toPushBody("user-1", uuid = "uuid-3")

        assertEquals("user-1", body["uid"]?.jsonPrimitive?.content)
        assertEquals("uuid-3", body["uuid"]?.jsonPrimitive?.content)
        assertEquals("Take medication", body["title"]?.jsonPrimitive?.content)
        assertEquals("daily", body["recurrence"]?.jsonPrimitive?.content)
        assertEquals("2026-11-08T09:00:00Z", body["trigger_at"]?.jsonPrimitive?.content)
        assertEquals("2026-11-08T09:00:00Z", body["next_trigger_at"]?.jsonPrimitive?.content)
        assertTrue(body["tags"]!!.jsonArray.map { it.jsonPrimitive.content } == listOf("health"))
        assertTrue(body["snoozed_until"] is JsonNull) // cleared snooze is an explicit null
        assertNull(body["id"]) // server-assigned identity must not be sent
        assertNull(body["create_at"]) // immutable column must not be sent
    }
}
