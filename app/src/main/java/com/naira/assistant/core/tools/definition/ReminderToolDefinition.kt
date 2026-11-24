
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
    default: String? = null,
) = buildJsonObject {
    put("type", "string")
    put("description", description)
    putJsonArray("enum") {
        values.forEach { add(it) }
    }
    default?.let { put("default", it) }
}

private fun stringProperty(
    description: String,
    minLength: Int? = null,
) = buildJsonObject {
    put("type", "string")
    put("description", description)
    minLength?.let { put("minLength", it) }
}

private fun integerProperty(
    description: String,
    minimum: Int? = null,
    maximum: Int? = null,
    default: Int? = null,
) = buildJsonObject {
    put("type", "integer")
    put("description", description)
    minimum?.let { put("minimum", it) }
    maximum?.let { put("maximum", it) }
    default?.let { put("default", it) }
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
        Create a requested reminder with a clear title and future Unix-millisecond trigger. Use
        context time/local timezone unless stale or another timezone is needed. Default recurrence
        to none and priority to normal; interval applies only to custom recurrence.
    """.trimIndent(),
    parameters = objectTool(
        properties = buildJsonObject {
            put(
                "title",
                stringProperty("Short reminder title.", minLength = 1)
            )
            put(
                "description",
                stringProperty("Optional additional details or notes.")
            )
            put(
                "trigger_at_ms",
                integerProperty(
                    "Future trigger time as Unix epoch milliseconds.",
                    minimum = 1,
                )
            )
            put(
                "recurrence",
                enumProperty(
                    "Repeat pattern. Default: none.",
                    recurrenceValues,
                    default = "none",
                )
            )
            put(
                "recurrence_interval",
                integerProperty(
                    "Positive interval in minutes; required for custom recurrence.",
                    minimum = 1,
                )
            )
            put(
                "priority",
                enumProperty(
                    "Priority. Default: normal.",
                    priorityValues,
                    default = "normal",
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
        Update only supplied fields of an existing reminder; preserve the rest. Find unknown IDs
        with list_reminders/search_reminders. Empty description or tags clears that field; do not
        create a new reminder instead.
    """.trimIndent(),
    parameters = objectTool(
        properties = buildJsonObject {
            put("id", integerProperty("ID of the existing reminder.", minimum = 1))
            put("title", stringProperty("New reminder title.", minLength = 1))
            put("description", stringProperty("New description or empty string to clear it."))
            put(
                "trigger_at_ms",
                integerProperty("New trigger time in Unix epoch milliseconds.", minimum = 1)
            )
            put("recurrence", enumProperty("New repeat pattern.", recurrenceValues))
            put(
                "recurrence_interval",
                integerProperty("New custom repeat interval in minutes.", minimum = 1)
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
        Permanently delete only on explicit request. Find the exact ID with list_reminders or
        search_reminders first if unknown; do not use to snooze, complete, or cancel.
    """.trimIndent(),
    parameters = objectTool(
        properties = buildJsonObject {
            put("id", integerProperty("ID of the reminder to delete.", minimum = 1))
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
        List reminders with optional status, priority, and result limit. Use search_reminders for
        topic/keyword lookup.
    """.trimIndent(),
    parameters = objectTool(
        properties = buildJsonObject {
            put(
                "status",
                enumProperty(
                    "Status filter. Default: active.",
                    reminderStatusValues,
                    default = "active",
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
                integerProperty("Maximum results. Default: 20.", minimum = 1, maximum = 100, default = 20)
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
        Search reminder titles, descriptions, and tags. Inspect returned IDs; clarify rather than
        choose arbitrarily when several reminders match.
    """.trimIndent(),
    parameters = objectTool(
        properties = buildJsonObject {
            put(
                "query",
                stringProperty("Search terms.", minLength = 1)
            )
            put(
                "status",
                enumProperty(
                    "Optional status filter.",
                    reminderStatusValues,
                )
            )
            put(
                "limit",
                integerProperty("Maximum results. Default: 10.", minimum = 1, maximum = 50, default = 10)
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
        Postpone a reminder without deleting/completing it or changing recurrence. Supply its ID
        and future Unix-millisecond time; use context time unless stale or another timezone is needed.
    """.trimIndent(),
    parameters = objectTool(
        properties = buildJsonObject {
            put("id", integerProperty("ID of the reminder to snooze.", minimum = 1))
            put(
                "snooze_until_ms",
                integerProperty("Future snooze time in Unix epoch milliseconds.", minimum = 1)
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
        Mark a reminder complete only after user confirmation. Recurring reminders advance to
        their next occurrence; keep the reminder and its history.
    """.trimIndent(),
    parameters = objectTool(
        properties = buildJsonObject {
            put("id", integerProperty("ID of the reminder.", minimum = 1))
        },
        required = listOf("id"),
    ),
)
