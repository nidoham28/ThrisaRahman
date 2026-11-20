
package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.*

// -----------------------------------------------------------------------------
// Reminder tools
// -----------------------------------------------------------------------------

private val recurrenceValues = listOf(
    "none", "daily", "weekly", "monthly",
    "yearly", "weekdays", "weekends", "custom"
)

private val priorityValues = listOf(
    "low", "normal", "high", "urgent"
)

private val reminderStatusValues = listOf(
    "active", "done", "snoozed", "cancelled", "all"
)

private fun enumProperty(
    description: String,
    values: List<String>,
) = buildJsonObject {
    put("type", "string")
    put("description", description)
    putJsonArray("enum") {
        values.forEach { add(it) }
    }
}

private fun stringProperty(description: String) = buildJsonObject {
    put("type", "string")
    put("description", description)
}

private fun integerProperty(description: String) = buildJsonObject {
    put("type", "integer")
    put("description", description)
}

private fun objectTool(
    properties: JsonObject,
    required: List<String> = emptyList(),
) = buildJsonObject {
    put("type", "object")
    put("properties", properties)
    putJsonArray("required") {
        required.forEach { add(it) }
    }
}

// -----------------------------------------------------------------------------
// CREATE REMINDER
// -----------------------------------------------------------------------------

fun createReminderTool() = ToolDefinition(
    name = "create_reminder",
    description = """
        Create and schedule a new reminder for the user.

        Use for requests such as "remind me", "don't let me forget",
        "alert me", or "schedule a reminder".

        Rules:
        - Always provide a clear title and trigger time.
        - trigger_at_ms must be a Unix epoch timestamp in milliseconds.
        - Use the user's local timezone when interpreting dates and times.
        - Call get_current_time before calculating relative dates or times.
        - Never guess an ambiguous date or time.
        - Default recurrence to none and priority to normal.
        - Use recurrence_interval only with custom recurrence.
        - Do not invent recurrence or priority preferences.
        - Confirm the saved reminder details after successful creation.
    """.trimIndent(),
    parameters = objectTool(
        properties = buildJsonObject {
            put(
                "title",
                stringProperty("Required. Short, descriptive reminder title.")
            )
            put(
                "description",
                stringProperty("Optional additional details or notes.")
            )
            put(
                "trigger_at_ms",
                integerProperty(
                    "Required. Future trigger time as Unix epoch milliseconds."
                )
            )
            put(
                "recurrence",
                enumProperty(
                    "Repeat pattern. Defaults to none.",
                    recurrenceValues,
                )
            )
            put(
                "recurrence_interval",
                integerProperty(
                    "Repeat interval in minutes. Required for custom recurrence."
                )
            )
            put(
                "priority",
                enumProperty(
                    "Reminder priority. Defaults to normal.",
                    priorityValues,
                )
            )
            put(
                "tags",
                stringProperty(
                    "Optional comma-separated tags for categorization and search."
                )
            )
        },
        required = listOf("title", "trigger_at_ms"),
    ),
)

// -----------------------------------------------------------------------------
// UPDATE REMINDER
// -----------------------------------------------------------------------------

fun updateReminderTool() = ToolDefinition(
    name = "update_reminder",
    description = """
        Update selected fields of an existing reminder.

        Supported fields: title, description, trigger time, recurrence,
        recurrence interval, priority, and tags.

        Rules:
        - Always provide the reminder ID.
        - Only modify fields explicitly supplied.
        - Preserve all omitted fields.
        - An empty description or tags value may be used to clear it.
        - Validate the new trigger time and recurrence configuration.
        - Call list_reminders or search_reminders first if the ID is unknown.
        - Never create a new reminder when the user requests an update.
    """.trimIndent(),
    parameters = objectTool(
        properties = buildJsonObject {
            put("id", integerProperty("Required. ID of the existing reminder."))
            put("title", stringProperty("New reminder title."))
            put("description", stringProperty("New description or empty string to clear it."))
            put(
                "trigger_at_ms",
                integerProperty("New trigger time in Unix epoch milliseconds.")
            )
            put("recurrence", enumProperty("New repeat pattern.", recurrenceValues))
            put(
                "recurrence_interval",
                integerProperty("New custom repeat interval in minutes.")
            )
            put("priority", enumProperty("New priority level.", priorityValues))
            put("tags", stringProperty("New comma-separated tags or empty string to clear."))
        },
        required = listOf("id"),
    ),
)

// -----------------------------------------------------------------------------
// DELETE REMINDER
// -----------------------------------------------------------------------------

fun deleteReminderTool() = ToolDefinition(
    name = "delete_reminder",
    description = """
        Permanently delete an existing reminder.

        Use only when the user explicitly requests permanent removal.

        Rules:
        - Always provide the exact reminder ID.
        - Call list_reminders or search_reminders if the ID is unknown.
        - Do not use for snoozing, completing, or cancelling a reminder.
        - Do not delete an ambiguous reminder.
    """.trimIndent(),
    parameters = objectTool(
        properties = buildJsonObject {
            put("id", integerProperty("Required. ID of the reminder to delete."))
        },
        required = listOf("id"),
    ),
)

// -----------------------------------------------------------------------------
// LIST REMINDERS
// -----------------------------------------------------------------------------

fun listRemindersTool() = ToolDefinition(
    name = "list_reminders",
    description = """
        Retrieve the user's saved reminders.

        Use when the user asks to view, list, or check their reminders.

        Rules:
        - Use status to filter reminder states.
        - Use priority when the user requests a specific urgency level.
        - Use limit to control the number of returned reminders.
        - Use search_reminders when looking for a specific topic or keyword.
    """.trimIndent(),
    parameters = objectTool(
        properties = buildJsonObject {
            put(
                "status",
                enumProperty(
                    "Filter: active, done, snoozed, cancelled, or all. Defaults to active.",
                    reminderStatusValues,
                )
            )
            put(
                "priority",
                enumProperty(
                    "Optional priority filter. Use all for no priority filtering.",
                    priorityValues + "all",
                )
            )
            put(
                "limit",
                integerProperty("Maximum results (1-100). Defaults to 20.")
            )
        },
    ),
)

// -----------------------------------------------------------------------------
// SEARCH REMINDERS
// -----------------------------------------------------------------------------

fun searchRemindersTool() = ToolDefinition(
    name = "search_reminders",
    description = """
        Search reminders by keyword or phrase.

        Searches reminder titles, descriptions, and tags.

        Use when the user refers to a reminder by its topic or description,
        especially before updating, deleting, snoozing, or completing it.

        Rules:
        - Always provide a meaningful search query.
        - Use status to narrow the results when appropriate.
        - Inspect returned IDs before performing another operation.
        - If multiple reminders match, do not arbitrarily select one.
    """.trimIndent(),
    parameters = objectTool(
        properties = buildJsonObject {
            put(
                "query",
                stringProperty("Required. Keyword or phrase to search.")
            )
            put(
                "status",
                enumProperty(
                    "Optional status filter. Defaults to active.",
                    reminderStatusValues,
                )
            )
            put(
                "limit",
                integerProperty("Maximum results (1-50). Defaults to 10.")
            )
        },
        required = listOf("query"),
    ),
)

// -----------------------------------------------------------------------------
// SNOOZE REMINDER
// -----------------------------------------------------------------------------

fun snoozeReminderTool() = ToolDefinition(
    name = "snooze_reminder",
    description = """
        Temporarily postpone an existing reminder until a specified time.

        Use for requests such as "snooze this", "remind me later",
        or "postpone this reminder".

        Rules:
        - Always provide the reminder ID and snooze time.
        - snooze_until_ms must be a future Unix epoch timestamp in milliseconds.
        - Call get_current_time before calculating relative snooze times.
        - Preserve the original reminder and its recurrence.
        - Do not permanently delete or complete the reminder.
        - Confirm the new snooze time after success.
    """.trimIndent(),
    parameters = objectTool(
        properties = buildJsonObject {
            put("id", integerProperty("Required. ID of the reminder to snooze."))
            put(
                "snooze_until_ms",
                integerProperty("Required. Future snooze time in Unix epoch milliseconds.")
            )
        },
        required = listOf("id", "snooze_until_ms"),
    ),
)

// -----------------------------------------------------------------------------
// MARK REMINDER DONE
// -----------------------------------------------------------------------------

fun markReminderDoneTool() = ToolDefinition(
    name = "mark_reminder_done",
    description = """
        Mark an existing reminder as completed.

        Use when the user confirms that a reminder has been fulfilled,
        completed, or checked off.

        Rules:
        - Always provide the reminder ID.
        - One-time reminders become done.
        - Recurring reminders advance to their next occurrence according
          to the recurrence schedule.
        - Do not delete the reminder or its history.
        - Do not mark a reminder complete without user confirmation.
    """.trimIndent(),
    parameters = objectTool(
        properties = buildJsonObject {
            put("id", integerProperty("Required. ID of the completed reminder."))
        },
        required = listOf("id"),
    ),
)
