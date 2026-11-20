package com.naira.assistant.core.tools.executor

import com.naira.assistant.core.database.reminder.RecurrenceType
import com.naira.assistant.core.database.reminder.ReminderItem
import com.naira.assistant.core.database.reminder.ReminderPriority
import com.naira.assistant.core.database.reminder.ReminderRepository
import com.naira.assistant.core.database.reminder.ReminderStatus
import com.naira.assistant.core.tools.executor.CommandExecution.jsonStringArg
import com.naira.assistant.domain.FunctionCall
import kotlinx.serialization.json.*

/**
 * Executes AI reminder tools.
 */
object ReminderToolExecutor {

    private val validStatuses = setOf(
        "active", "done", "snoozed", "cancelled"
    )

    private val validPriorities = setOf(
        "low", "normal", "high", "urgent"
    )

    // ----------------------------------------------------------------
    // Create
    // ----------------------------------------------------------------

    suspend fun executeCreateReminder(
        call: FunctionCall,
        repo: ReminderRepository,
    ): JsonObject {
        val title = jsonStringArg(call, "title")
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return error("'title' is required.")

        val triggerAtMs = extractLongArg(call, "trigger_at_ms")
            ?: return error("'trigger_at_ms' must be a valid Unix timestamp in milliseconds.")

        if (triggerAtMs <= System.currentTimeMillis()) {
            return error("The reminder time must be in the future.")
        }

        val recurrence = RecurrenceType.fromId(
            jsonStringArg(call, "recurrence")
        )

        val recurrenceInterval = extractIntArg(call, "recurrence_interval") ?: 0

        if (recurrence == RecurrenceType.CUSTOM && recurrenceInterval <= 0) {
            return error("A positive 'recurrence_interval' is required for custom recurrence.")
        }

        if (recurrenceInterval < 0) {
            return error("'recurrence_interval' cannot be negative.")
        }

        val priorityValue = jsonStringArg(call, "priority") ?: "normal"

        if (priorityValue !in validPriorities) {
            return error("Invalid priority. Allowed values: ${validPriorities.joinToString()}.")
        }

        val inserted = repo.insert(
            title = title,
            description = jsonStringArg(call, "description")?.trim().orEmpty(),
            triggerAtMs = triggerAtMs,
            recurrence = recurrence,
            recurrenceInterval = recurrenceInterval,
            priority = ReminderPriority.fromId(priorityValue),
            tags = extractTagsArg(call, "tags"),
        )

        return success(
            action = "created",
            message = "Reminder #${inserted.id} created successfully.",
            reminder = inserted,
        )
    }

    // ----------------------------------------------------------------
    // Update
    // ----------------------------------------------------------------

    suspend fun executeUpdateReminder(
        call: FunctionCall,
        repo: ReminderRepository,
    ): JsonObject {
        val id = extractLongArg(call, "id")
            ?.takeIf { it > 0 }
            ?: return error("A valid positive reminder 'id' is required.")

        val triggerAtMs = extractLongArg(call, "trigger_at_ms")

        if (call.args.containsKey("trigger_at_ms")) {
            if (triggerAtMs == null || triggerAtMs <= System.currentTimeMillis()) {
                return error("'trigger_at_ms' must be a valid future timestamp.")
            }
        }

        val recurrence = if (call.args.containsKey("recurrence")) {
            RecurrenceType.fromId(jsonStringArg(call, "recurrence"))
        } else {
            null
        }

        val recurrenceInterval = extractIntArg(call, "recurrence_interval")

        if (recurrenceInterval != null && recurrenceInterval <= 0) {
            return error("'recurrence_interval' must be positive.")
        }

        if (recurrence == RecurrenceType.CUSTOM && recurrenceInterval == null) {
            return error("'recurrence_interval' is required when setting custom recurrence.")
        }

        val priority = if (call.args.containsKey("priority")) {
            val value = jsonStringArg(call, "priority")
                ?: return error("'priority' cannot be empty.")

            if (value !in validPriorities) {
                return error("Invalid priority. Allowed values: ${validPriorities.joinToString()}.")
            }

            ReminderPriority.fromId(value)
        } else {
            null
        }

        val updated = repo.update(
            id = id,
            title = jsonStringArg(call, "title")?.trim()?.takeIf(String::isNotBlank),
            description = jsonStringArg(call, "description"),
            triggerAtMs = triggerAtMs,
            recurrence = recurrence,
            recurrenceInterval = recurrenceInterval,
            priority = priority,
            tags = if (call.args.containsKey("tags")) {
                extractTagsArg(call, "tags")
            } else {
                null
            },
        ) ?: return error("Reminder #$id was not found.")

        return success(
            action = "updated",
            message = "Reminder #$id updated successfully.",
            reminder = updated,
        )
    }

    // ----------------------------------------------------------------
    // Delete
    // ----------------------------------------------------------------

    suspend fun executeDeleteReminder(
        call: FunctionCall,
        repo: ReminderRepository,
    ): JsonObject {
        val id = extractLongArg(call, "id")
            ?.takeIf { it > 0 }
            ?: return error("A valid positive reminder 'id' is required.")

        if (!repo.delete(id)) {
            return error("Reminder #$id was not found.")
        }

        return buildJsonObject {
            put("success", true)
            put("action", "deleted")
            put("id", id)
            put("message", "Reminder #$id deleted successfully.")
        }
    }

    // ----------------------------------------------------------------
    // List
    // ----------------------------------------------------------------

    suspend fun executeListReminders(
        call: FunctionCall,
        repo: ReminderRepository,
    ): JsonObject {
        val status = jsonStringArg(call, "status") ?: "active"
        val priority = jsonStringArg(call, "priority")
        val limit = extractIntArg(call, "limit") ?: 20

        if (status != "all" && status !in validStatuses) {
            return error("Invalid status. Allowed values: all, ${validStatuses.joinToString()}.")
        }

        if (priority != null && priority !in validPriorities) {
            return error("Invalid priority. Allowed values: ${validPriorities.joinToString()}.")
        }

        if (limit !in 1..100) {
            return error("'limit' must be between 1 and 100.")
        }

        val results = repo.getAll(
            status = status.takeUnless { it == "all" },
            priority = priority,
            limit = limit,
        )

        return buildJsonObject {
            put("success", true)
            put("action", "list")
            put("status_filter", status)
            put("total_stored", repo.count())
            put("returned_count", results.size)
            put("reminders", results.toJsonArray())
        }
    }

    // ----------------------------------------------------------------
    // Search
    // ----------------------------------------------------------------

    suspend fun executeSearchReminders(
        call: FunctionCall,
        repo: ReminderRepository,
    ): JsonObject {
        val query = jsonStringArg(call, "query")
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return error("'query' is required and cannot be blank.")

        val status = jsonStringArg(call, "status")
        val limit = extractIntArg(call, "limit") ?: 10

        if (status != null && status !in validStatuses) {
            return error("Invalid status. Allowed values: ${validStatuses.joinToString()}.")
        }

        if (limit !in 1..50) {
            return error("'limit' must be between 1 and 50.")
        }

        val results = repo.search(
            query = query,
            status = status,
            limit = limit,
        )

        return buildJsonObject {
            put("success", true)
            put("action", "search")
            put("query", query)
            put("count", results.size)
            put("reminders", results.toJsonArray())
        }
    }

    // ----------------------------------------------------------------
    // Snooze
    // ----------------------------------------------------------------

    suspend fun executeSnoozeReminder(
        call: FunctionCall,
        repo: ReminderRepository,
    ): JsonObject {
        val id = extractLongArg(call, "id")
            ?.takeIf { it > 0 }
            ?: return error("A valid positive reminder 'id' is required.")

        val until = extractLongArg(call, "snooze_until_ms")
            ?: return error("'snooze_until_ms' must be a valid Unix timestamp.")

        if (until <= System.currentTimeMillis()) {
            return error("'snooze_until_ms' must be in the future.")
        }

        val updated = repo.snooze(id, until)
            ?: return error("Reminder #$id was not found.")

        return success(
            action = "snoozed",
            message = "Reminder #$id snoozed successfully.",
            reminder = updated,
        )
    }

    // ----------------------------------------------------------------
    // Mark done
    // ----------------------------------------------------------------

    suspend fun executeMarkReminderDone(
        call: FunctionCall,
        repo: ReminderRepository,
    ): JsonObject {
        val id = extractLongArg(call, "id")
            ?.takeIf { it > 0 }
            ?: return error("A valid positive reminder 'id' is required.")

        val updated = repo.markDone(id)
            ?: return error("Reminder #$id was not found.")

        val message = if (updated.status == ReminderStatus.DONE) {
            "Reminder #$id marked as done."
        } else {
            "Reminder #$id completed. Next occurrence: ${updated.nextTriggerMs}."
        }

        return success(
            action = "done",
            message = message,
            reminder = updated,
        )
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private fun error(message: String): JsonObject = buildJsonObject {
        put("success", false)
        put("error", message)
    }

    private fun success(
        action: String,
        message: String,
        reminder: ReminderItem,
    ): JsonObject = buildJsonObject {
        put("success", true)
        put("action", action)
        put("message", message)
        put("reminder", reminder.toJsonObject())
    }

    private fun extractLongArg(
        call: FunctionCall,
        key: String,
    ): Long? {
        val primitive = call.args[key] as? JsonPrimitive ?: return null
        if (primitive.isString && primitive.content.isBlank()) return null

        return primitive.longOrNull
            ?: primitive.content.trim().toLongOrNull()
    }

    private fun extractIntArg(
        call: FunctionCall,
        key: String,
    ): Int? {
        val primitive = call.args[key] as? JsonPrimitive ?: return null
        if (primitive.isString && primitive.content.isBlank()) return null

        return primitive.intOrNull
            ?: primitive.content.trim().toIntOrNull()
    }

    private fun extractTagsArg(
        call: FunctionCall,
        key: String,
    ): List<String> {
        val element = call.args[key] ?: return emptyList()

        val values = when (element) {
            is JsonArray -> element.mapNotNull { item ->
                (item as? JsonPrimitive)
                    ?.takeIf { it.isString }
                    ?.content
            }

            is JsonPrimitive -> {
                if (!element.isString) return emptyList()
                element.content.split(",")
            }

            else -> return emptyList()
        }

        return values
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
    }

    private fun List<ReminderItem>.toJsonArray(): JsonArray =
        buildJsonArray {
            forEach { add(it.toJsonObject()) }
        }

    private fun ReminderItem.toJsonObject(): JsonObject = buildJsonObject {
        put("id", id)
        put("uuid", uuid)
        put("title", title)
        put("description", description)
        put("trigger_at_ms", triggerAtMs)
        put("next_trigger_ms", nextTriggerMs)
        put("recurrence", recurrence.id)

        if (recurrence == RecurrenceType.CUSTOM) {
            put("recurrence_interval_minutes", recurrenceInterval)
        }

        put("status", status.id)
        put("priority", priority.id)

        put("tags", buildJsonArray {
            tags.forEach { add(JsonPrimitive(it)) }
        })

        if (snoozedUntilMs > 0) {
            put("snoozed_until_ms", snoozedUntilMs)
        }

        put("created_at_ms", createdAt)
        put("updated_at_ms", updatedAt)
    }
}