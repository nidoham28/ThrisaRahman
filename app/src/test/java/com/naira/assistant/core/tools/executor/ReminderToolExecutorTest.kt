package com.naira.assistant.core.tools.executor

import com.naira.assistant.core.database.reminder.RecurrenceType
import com.naira.assistant.core.database.reminder.ReminderItem
import com.naira.assistant.core.database.reminder.ReminderPriority
import com.naira.assistant.core.database.reminder.ReminderRepository
import com.naira.assistant.core.database.reminder.ReminderStatus
import com.naira.assistant.domain.FunctionCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderToolExecutorTest {

    @Test
    fun `all list filters are passed to repository as no filter`() = runBlocking {
        val repo = RecordingReminderRepository()
        val call = FunctionCall(
            name = "list_reminders",
            args = buildJsonObject {
                put("status", "all")
                put("priority", "all")
            },
        )

        ReminderToolExecutor.executeListReminders(call, repo)

        assertNull(repo.listStatus)
        assertNull(repo.listPriority)
    }

    @Test
    fun `all search status is passed to repository as no filter`() = runBlocking {
        val repo = RecordingReminderRepository()
        val call = FunctionCall(
            name = "search_reminders",
            args = buildJsonObject {
                put("query", "medicine")
                put("status", "all")
            },
        )

        ReminderToolExecutor.executeSearchReminders(call, repo)

        assertNull(repo.searchStatus)
        assertEquals("medicine", repo.searchQuery)
    }

    @Test
    fun `empty description and tags explicitly clear reminder fields`() = runBlocking {
        val repo = RecordingReminderRepository()
        val call = FunctionCall(
            name = "update_reminder",
            args = buildJsonObject {
                put("id", 4)
                put("description", "")
                put("tags", "")
            },
        )

        ReminderToolExecutor.executeUpdateReminder(call, repo)

        assertEquals("", repo.updatedDescription)
        assertEquals(emptyList<String>(), repo.updatedTags)
    }

    @Test
    fun `malformed tags are rejected without updating a reminder`() = runBlocking {
        val repo = RecordingReminderRepository()
        val call = FunctionCall(
            name = "update_reminder",
            args = buildJsonObject {
                put("id", 4)
                put("tags", buildJsonArray {
                    add("valid")
                    add(7)
                })
            },
        )

        ReminderToolExecutor.executeUpdateReminder(call, repo)

        assertNull(repo.updatedTags)
        assertEquals(false, repo.updateCalled)
    }

    private class RecordingReminderRepository : ReminderRepository {
        var listStatus: String? = "not-called"
        var listPriority: String? = "not-called"
        var searchStatus: String? = "not-called"
        var searchQuery: String? = null
        var updatedDescription: String? = null
        var updatedTags: List<String>? = null
        var updateCalled = false

        override suspend fun insert(
            title: String,
            description: String,
            triggerAtMs: Long,
            recurrence: RecurrenceType,
            recurrenceInterval: Int,
            priority: ReminderPriority,
            tags: List<String>,
            source: String,
        ): ReminderItem = error("Not used by this test.")

        override suspend fun update(
            id: Long,
            title: String?,
            description: String?,
            triggerAtMs: Long?,
            recurrence: RecurrenceType?,
            recurrenceInterval: Int?,
            status: ReminderStatus?,
            priority: ReminderPriority?,
            tags: List<String>?,
            snoozedUntilMs: Long?,
        ): ReminderItem? {
            updateCalled = true
            updatedDescription = description
            updatedTags = tags
            return ReminderItem(
                id = id,
                title = title ?: "existing",
                description = description.orEmpty(),
                triggerAtMs = triggerAtMs ?: 1L,
                recurrence = recurrence ?: RecurrenceType.NONE,
                recurrenceInterval = recurrenceInterval ?: 0,
                priority = priority ?: ReminderPriority.NORMAL,
                tags = tags.orEmpty(),
            )
        }

        override suspend fun delete(id: Long): Boolean = error("Not used by this test.")
        override suspend fun markDone(id: Long): ReminderItem? = error("Not used by this test.")
        override suspend fun snooze(id: Long, until: Long): ReminderItem? = error("Not used by this test.")
        override suspend fun cancel(id: Long): ReminderItem? = error("Not used by this test.")
        override suspend fun getById(id: Long): ReminderItem? = error("Not used by this test.")
        override suspend fun getByUuid(uuid: String): ReminderItem? = error("Not used by this test.")

        override suspend fun getAll(
            status: String?,
            priority: String?,
            limit: Int,
        ): List<ReminderItem> {
            listStatus = status
            listPriority = priority
            return emptyList()
        }

        override suspend fun getUpcoming(
            fromMs: Long,
            toMs: Long,
            limit: Int,
        ): List<ReminderItem> = error("Not used by this test.")

        override suspend fun search(
            query: String?,
            status: String?,
            limit: Int,
        ): List<ReminderItem> {
            searchQuery = query
            searchStatus = status
            return emptyList()
        }

        override suspend fun count(): Long = 0L
        override suspend fun clearAll(): Int = error("Not used by this test.")
        override fun observeAll() = MutableStateFlow(emptyList<ReminderItem>())
    }
}
