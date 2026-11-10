package com.naira.assistant.core.database.reminder

import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for persistent AI-managed reminders.
 *
 * Mirrors the offline-first design of [com.naira.assistant.core.database.memory.MemoryRepository]:
 * all mutations are suspend functions, queries are suspend or Flow-based,
 * and the implementation holds a reactive [StateFlow] cache for Compose UI.
 */
interface ReminderRepository {

    /**
     * Inserts a new reminder.
     * @return The persisted [ReminderItem] with its assigned [ReminderItem.id].
     */
    suspend fun insert(
        title: String,
        description: String = "",
        triggerAtMs: Long,
        recurrence: RecurrenceType = RecurrenceType.NONE,
        recurrenceInterval: Int = 0,
        priority: ReminderPriority = ReminderPriority.NORMAL,
        tags: List<String> = emptyList(),
        source: String = "ai_tool",
    ): ReminderItem

    /**
     * Updates an existing reminder by [id].
     * Only non-null parameters are modified; all others keep their current values.
     * @return The updated [ReminderItem], or null if not found.
     */
    suspend fun update(
        id: Long,
        title: String? = null,
        description: String? = null,
        triggerAtMs: Long? = null,
        recurrence: RecurrenceType? = null,
        recurrenceInterval: Int? = null,
        status: ReminderStatus? = null,
        priority: ReminderPriority? = null,
        tags: List<String>? = null,
        snoozedUntilMs: Long? = null,
    ): ReminderItem?

    /**
     * Permanently deletes a reminder by [id].
     * @return true if deleted, false if not found.
     */
    suspend fun delete(id: Long): Boolean

    /**
     * Marks a reminder as [ReminderStatus.DONE] (completed) without deleting it.
     * Advances [ReminderItem.nextTriggerMs] for recurring reminders.
     * @return The updated item, or null if not found.
     */
    suspend fun markDone(id: Long): ReminderItem?

    /**
     * Snoozes a reminder until [until] ms, setting status to [ReminderStatus.SNOOZED].
     * @return The updated item, or null if not found.
     */
    suspend fun snooze(id: Long, until: Long): ReminderItem?

    /**
     * Cancels a reminder (sets status to [ReminderStatus.CANCELLED]) without deleting.
     * @return The updated item, or null if not found.
     */
    suspend fun cancel(id: Long): ReminderItem?

    /**
     * Fetches a single reminder by its local [id].
     */
    suspend fun getById(id: Long): ReminderItem?

    /**
     * Fetches a single reminder by its global [uuid].
     */
    suspend fun getByUuid(uuid: String): ReminderItem?

    /**
     * Lists reminders, optionally filtered by [status] and/or [priority].
     * Results are ordered by [ReminderItem.nextTriggerMs] ascending.
     */
    suspend fun getAll(
        status: String? = null,
        priority: String? = null,
        limit: Int = 50,
    ): List<ReminderItem>

    /**
     * Returns reminders whose [ReminderItem.nextTriggerMs] falls in the half-open
     * interval [[fromMs], [toMs]).
     */
    suspend fun getUpcoming(fromMs: Long, toMs: Long, limit: Int = 20): List<ReminderItem>

    /**
     * Searches reminders by keyword in title/description/tags, with optional status filter.
     */
    suspend fun search(
        query: String? = null,
        status: String? = null,
        limit: Int = 20,
    ): List<ReminderItem>

    /**
     * Total count of stored reminders.
     */
    suspend fun count(): Long

    /**
     * Deletes all reminders. Returns the number of rows deleted.
     */
    suspend fun clearAll(): Int

    /**
     * Real-time Compose-observable flow of all reminders, ordered by next trigger time.
     */
    fun observeAll(): StateFlow<List<ReminderItem>>
}
