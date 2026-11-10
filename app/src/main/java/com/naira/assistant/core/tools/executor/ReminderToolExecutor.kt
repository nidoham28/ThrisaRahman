package com.naira.assistant.core.tools.executor

import com.naira.assistant.core.database.reminder.RecurrenceType
import com.naira.assistant.core.database.reminder.ReminderItem
import com.naira.assistant.core.database.reminder.ReminderPriority
import com.naira.assistant.core.database.reminder.ReminderRepository
import com.naira.assistant.core.database.reminder.ReminderStatus
import com.naira.assistant.core.tools.executor.BuiltinToolExecutor.jsonStringArg
import com.naira.assistant.domain.FunctionCall
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Logic implementations for the AI reminder tools:
 *  - `create_reminder`
 *  - `update_reminder`
 *  - `delete_reminder`
 *  - `list_reminders`
 *  - `search_reminders`
 *  - `snooze_reminder`
 *  - `mark_reminder_done`
 */
object ReminderToolExecutor {

    // ------------------------------------------------------------------
    // Tool executors
    // ------------------------------------------------------------------

    /** Handles `create_reminder`. */
    suspend fun executeCreateReminder(
        call: FunctionCall,
        repo: ReminderRepository,
    ): JsonObject {
        val title = jsonStringArg(call, "title")
        if (title.isNullOrBlank()) {
            return error("Argument 'title' is required and must not be blank.")
        }

        val triggerAtMs = extractLongArg(call, "trigger_at_ms")
            ?: return error("Argument 'trigger_at_ms' (Unix epoch milliseconds) is required.")

        if (triggerAtMs <= 0) {
            return error("'trigger_at_ms' must be a positive Unix epoch in milliseconds.")
        }

        val description = jsonStringArg(call, "description") ?: ""
        val recurrence = RecurrenceType.fromId(jsonStringArg(call, "recurrence"))
        val recurrenceInterval = extractIntArg(call, "recurrence_interval") ?: 0
        val priority = ReminderPriority.fromId(jsonStringArg(call, "priority"))
        val tags = extractTagsArg(call, "tags")

        val inserted = repo.insert(
            title = title,
            description = description,
            triggerAtMs = triggerAtMs,
            recurrence = recurrence,
            recurrenceInterval = recurrenceInterval,
            priority = priority,
            tags = tags,
        )

        return buildJsonObject {
            put("success", true)
            put("action", "created")
            put("message", "Reminder #${inserted.id} '${inserted.title}' created successfully.")
            put("reminder", inserted.toJsonObject())
        }
    }

    /** Handles `update_reminder`. */
    suspend fun executeUpdateReminder(
        call: FunctionCall,
        repo: ReminderRepository,
    ): JsonObject {
        val id = extractLongArg(call, "id")
            ?: return error("A valid positive numeric 'id' is required to update a reminder.")

        if (id <= 0) return error("'id' must be a positive integer.")

        val title = jsonStringArg(call, "title")
        val description = jsonStringArg(call, "description")
        val triggerAtMs = extractLongArg(call, "trigger_at_ms")
        val recurrence = if (call.args.containsKey("recurrence"))
            RecurrenceType.fromId(jsonStringArg(call, "recurrence")) else null
        val recurrenceInterval = extractIntArg(call, "recurrence_interval")
        val priority = if (call.args.containsKey("priority"))
            ReminderPriority.fromId(jsonStringArg(call, "priority")) else null
        val tags = if (call.args.containsKey("tags")) extractTagsArg(call, "tags") else null

        val updated = repo.update(
            id = id,
            title = title,
            description = description,
            triggerAtMs = triggerAtMs,
            recurrence = recurrence,
            recurrenceInterval = recurrenceInterval,
            priority = priority,
            tags = tags,
        ) ?: return error("Reminder with id #$id was not found.")

        return buildJsonObject {
            put("success", true)
            put("action", "updated")
            put("message", "Reminder #$id updated successfully.")
            put("reminder", updated.toJsonObject())
        }
    }

    /** Handles `delete_reminder`. */
    suspend fun executeDeleteReminder(
        call: FunctionCall,
        repo: ReminderRepository,
    ): JsonObject {
        val id = extractLongArg(call, "id")
            ?: return error("A valid positive numeric 'id' is required to delete a reminder.")

        val deleted = repo.delete(id)
        return if (deleted) {
            buildJsonObject {
                put("success", true)
                put("action", "deleted")
                put("id", id)
                put("message", "Reminder #$id has been permanently deleted.")
            }
        } else {
            error("Reminder with id #$id was not found or has already been deleted.")
        }
    }

    /** Handles `list_reminders`. */
    suspend fun executeListReminders(
        call: FunctionCall,
        repo: ReminderRepository,
    ): JsonObject {
        val status = jsonStringArg(call, "status") ?: "active"
        val priority = jsonStringArg(call, "priority")
        val limit = extractIntArg(call, "limit") ?: 20

        val statusFilter = if (status == "all") null else status
        val results = repo.getAll(status = statusFilter, priority = priority, limit = limit)
        val total = repo.count()

        return buildJsonObject {
            put("success", true)
            put("action", "list")
            put("status_filter", status)
            put("total_stored", total)
            put("returned_count", results.size)
            put("reminders", buildJsonArray { results.forEach { add(it.toJsonObject()) } })
        }
    }

    /** Handles `search_reminders`. */
    suspend fun executeSearchReminders(
        call: FunctionCall,
        repo: ReminderRepository,
    ): JsonObject {
        val query = jsonStringArg(call, "query")
        val status = jsonStringArg(call, "status")
        val limit = extractIntArg(call, "limit") ?: 10

        val results = repo.search(query = query, status = status, limit = limit)

        return buildJsonObject {
            put("success", true)
            put("action", "search")
            put("query", query ?: "")
            put("count", results.size)
            put("reminders", buildJsonArray { results.forEach { add(it.toJsonObject()) } })
        }
    }

    /** Handles `snooze_reminder`. */
    suspend fun executeSnoozeReminder(
        call: FunctionCall,
        repo: ReminderRepository,
    ): JsonObject {
        val id = extractLongArg(call, "id")
            ?: return error("A valid positive numeric 'id' is required.")
        val until = extractLongArg(call, "snooze_until_ms")
            ?: return error("'snooze_until_ms' (Unix epoch milliseconds) is required.")

        if (until <= System.currentTimeMillis()) {
            return error("'snooze_until_ms' must be a future timestamp.")
        }

        val updated = repo.snooze(id, until) ?: return error("Reminder with id #$id was not found.")

        return buildJsonObject {
            put("success", true)
            put("action", "snoozed")
            put("message", "Reminder #$id snoozed until epoch ms $until.")
            put("reminder", updated.toJsonObject())
        }
    }

    /** Handles `mark_reminder_done`. */
    suspend fun executeMarkReminderDone(
        call: FunctionCall,
        repo: ReminderRepository,
    ): JsonObject {
        val id = extractLongArg(call, "id")
            ?: return error("A valid positive numeric 'id' is required.")

        val updated = repo.markDone(id) ?: return error("Reminder with id #$id was not found.")

        val msg = if (updated.status == ReminderStatus.DONE) {
            "Reminder #$id marked as done."
        } else {
            "Reminder #$id marked done; next occurrence scheduled at epoch ms ${updated.nextTriggerMs}."
        }

        return buildJsonObject {
            put("success", true)
            put("action", "done")
            put("message", msg)
            put("reminder", updated.toJsonObject())
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun error(message: String): JsonObject = buildJsonObject { put("error", message) }

    private fun extractLongArg(call: FunctionCall, key: String): Long? =
        runCatching {
            val element = call.args[key] ?: return null
            element.jsonPrimitive.longOrNull
                ?: element.jsonPrimitive.content.trim().toLongOrNull()
        }.getOrNull()

    private fun extractIntArg(call: FunctionCall, key: String): Int? =
        runCatching {
            val element = call.args[key] ?: return null
            element.jsonPrimitive.content.trim().toIntOrNull()
        }.getOrNull()

    private fun extractTagsArg(call: FunctionCall, key: String): List<String> =
        runCatching {
            val element = call.args[key] ?: return emptyList()
            when (element) {
                is JsonArray -> element.mapNotNull { it.jsonPrimitive.content.takeIf(String::isNotBlank) }
                else -> {
                    val raw = element.jsonPrimitive.content
                    if (raw.isBlank()) emptyList()
                    else raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                }
            }
        }.getOrDefault(emptyList())

    /** Serialises a [ReminderItem] to a compact [JsonObject] for returning to the model. */
    private fun ReminderItem.toJsonObject(): JsonObject = buildJsonObject {
        put("id", id)
        put("uuid", uuid)
        put("title", title)
        put("description", description)
        put("trigger_at_ms", triggerAtMs)
        put("next_trigger_ms", nextTriggerMs)
        put("recurrence", recurrence.id)
        if (recurrence == RecurrenceType.CUSTOM) put("recurrence_interval_minutes", recurrenceInterval)
        put("status", status.id)
        put("priority", priority.id)
        put("tags", buildJsonArray { tags.forEach { add(JsonPrimitive(it)) } })
        if (snoozedUntilMs > 0) put("snoozed_until_ms", snoozedUntilMs)
        put("created_at_ms", createdAt)
        put("updated_at_ms", updatedAt)
    }
}
