
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
    description = universalToolDescription(
        purpose = "Creates a reminder with a title and future trigger time in Unix milliseconds; supports recurrence and priority.",
        trigger = "The user asks to set a reminder for a date/time or recurring schedule.",
        notFor = "Use `create_alarm` for a clock-time alarm and `create_calendar_event` for a calendar event.",
        prerequisite = "A `title` and future `trigger_at_ms` are required; clarify ambiguous times. Use contextual timestamp/local timezone, or `get_current_time` if stale.",
    ),
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
    description = universalToolDescription(
        purpose = "Updates specified fields of an existing reminder and preserves omitted fields.",
        trigger = "The user asks to change a reminder's title, time, recurrence, priority, or tags.",
        notFor = "Use `create_reminder` to add one, `snooze_reminder` to postpone it, and `mark_reminder_done` to complete it.",
        prerequisite = "An exact reminder `id` is required; find an unknown ID with `list_reminders` or `search_reminders`. Empty `description`/`tags` clears that field.",
    ),
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
    description = universalToolDescription(
        purpose = "Permanently deletes one reminder.",
        trigger = "The user explicitly asks to delete a reminder.",
        notFor = "Use `snooze_reminder` to postpone, `mark_reminder_done` to complete, or `update_reminder` to change its details.",
        prerequisite = "An exact `id` is required; identify an unknown reminder with `list_reminders`/`search_reminders`.",
    ),
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
    description = universalToolDescription(
        purpose = "Lists reminders with optional status, priority, and result-limit filters.",
        trigger = "The user asks for an overview or a list filtered by reminder status/priority.",
        notFor = "Use `search_reminders` for topic/keyword lookup; use `update_reminder`, `snooze_reminder`, `mark_reminder_done`, or `delete_reminder` for changes.",
        prerequisite = "Filters are optional; the default status is `active`.",
    ),
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
    parallelizable = true,
)

// -----------------------------------------------------------------------------
// SEARCH REMINDERS
// -----------------------------------------------------------------------------

fun searchRemindersTool() = ToolDefinition(
    name = "search_reminders",
    description = universalToolDescription(
        purpose = "Searches reminder titles, descriptions, and tags for matching terms.",
        trigger = "The user asks to find a specific reminder or reminders about a topic.",
        notFor = "Use `list_reminders` to browse/filter all reminders; do not arbitrarily choose among multiple matches.",
        prerequisite = "A meaningful `query` is required; ask the user to identify the right item if several reminders match.",
    ),
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
    parallelizable = true,
)

// -----------------------------------------------------------------------------
// SNOOZE REMINDER
// -----------------------------------------------------------------------------

fun snoozeReminderTool() = ToolDefinition(
    name = "snooze_reminder",
    description = universalToolDescription(
        purpose = "Postpones a reminder without deleting/completing it or changing its recurrence.",
        trigger = "The user asks to snooze or be reminded later about a specific reminder.",
        notFor = "Use `update_reminder` to change its schedule, `mark_reminder_done` to complete it, or `delete_reminder` to remove it.",
        prerequisite = "An exact reminder `id` and future `snooze_until_ms` are required; use contextual timestamp/timezone for relative times.",
    ),
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
    description = universalToolDescription(
        purpose = "Marks a reminder complete; recurring reminders advance to their next occurrence.",
        trigger = "The user confirms a specific reminder is complete or asks to mark it done.",
        notFor = "Use `snooze_reminder` to postpone and `delete_reminder` to remove a reminder.",
        prerequisite = "An exact reminder `id` and the user's completion instruction/confirmation are required; find an unknown ID first.",
    ),
    parameters = objectTool(
        properties = buildJsonObject {
            put("id", integerProperty("ID of the reminder.", minimum = 1))
        },
        required = listOf("id"),
    ),
)
