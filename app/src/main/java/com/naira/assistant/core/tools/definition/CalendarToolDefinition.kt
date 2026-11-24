
package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

private fun calendarParameters(
    required: List<String> = emptyList(),
    properties: kotlinx.serialization.json.JsonObject
) = buildJsonObject {
    put("type", "object")
    put("additionalProperties", false)
    put("properties", properties)

    putJsonArray("required") {
        required.forEach { add(it) }
    }
}

private fun calendarStringProperty(
    description: String,
    maxLength: Int? = null
) = buildJsonObject {
    put("type", "string")
    put("description", description)
    maxLength?.let { put("maxLength", it) }
}

private fun calendarIntegerProperty(
    description: String,
    minimum: Long? = null,
    maximum: Int? = null,
    default: Int? = null,
) = buildJsonObject {
    put("type", "integer")
    put("description", description)
    minimum?.let { put("minimum", it) }
    maximum?.let { put("maximum", it) }
    default?.let { put("default", it) }
}

private fun calendarBooleanProperty(
    description: String,
    default: Boolean? = null,
) = buildJsonObject {
    put("type", "boolean")
    put("description", description)
    default?.let { put("default", it) }
}

// CREATE EVENT

fun createCalendarEventTool() = ToolDefinition(
    name = "create_calendar_event",
    description = universalToolDescription(
        purpose = "Creates a timed or all-day calendar event with start/end times in Unix epoch milliseconds.",
        trigger = "The user asks to add a meeting, appointment, or other event to the calendar.",
        notFor = "Use `create_todo` for tasks, `create_reminder` for date-specific alerts, or `create_alarm` for clock-time alarms.",
        prerequisite = "A `title` and `start_time_ms` are required; clarify ambiguous times. Use the contextual timestamp/timezone, or `get_current_time` if stale or another timezone is needed. End defaults to one hour later, or one day for all-day events.",
    ),
    parameters = calendarParameters(
        required = listOf("title", "start_time_ms"),
        properties = buildJsonObject {
            put(
                "title",
                calendarStringProperty(
                    "Event title or summary.",
                    maxLength = 200
                )
            )

            put(
                "start_time_ms",
                calendarIntegerProperty(
                    "Event start time as Unix epoch milliseconds. For all-day events, the date in the device's local timezone is used.",
                    minimum = 1
                )
            )

            put(
                "end_time_ms",
                calendarIntegerProperty(
                    "Optional end time in Unix epoch milliseconds; must follow start. For all-day events its device-local date is used. Defaults to one hour later for timed events and one day later for all-day events.",
                    minimum = 1
                )
            )

            put(
                "description",
                calendarStringProperty(
                    "Optional event description, agenda, or notes.",
                    maxLength = 5000
                )
            )

            put(
                "location",
                calendarStringProperty(
                    "Optional physical address, venue, or online meeting location.",
                    maxLength = 500
                )
            )

            put(
                "all_day",
                calendarBooleanProperty(
                    "Create an all-day event, normalizing its dates to UTC midnight as required by Android Calendar. Default: false.",
                    default = false,
                )
            )
        }
    ),
)

// LIST EVENTS

fun listCalendarEventsTool() = ToolDefinition(
    name = "list_calendar_events",
    description = universalToolDescription(
        purpose = "Lists calendar events and IDs within a specified or default time range.",
        trigger = "The user asks about their schedule, events in a period, or to find calendar events.",
        notFor = "Use `get_calendar_event` for details by known ID, `list_reminders` for reminders, or `list_todos` for tasks.",
        prerequisite = "Optionally provide a range or keyword; defaults to today through 30 days ahead and 20 results. Use the contextual timestamp, or `get_current_time` if stale.",
    ),
    parameters = calendarParameters(
        properties = buildJsonObject {
            put(
                "start_from_ms",
                calendarIntegerProperty(
                    "Optional inclusive range start as Unix epoch milliseconds. Defaults to the start of today.",
                    minimum = 0
                )
            )

            put(
                "end_to_ms",
                calendarIntegerProperty(
                    "Optional exclusive range end as Unix epoch milliseconds. Defaults to 30 days after the current time.",
                    minimum = 0
                )
            )

            put(
                "query",
                calendarStringProperty(
                    "Optional keyword filter for event title, description, or location.",
                    maxLength = 500
                )
            )

            put(
                "limit",
                buildJsonObject {
                    put("type", "integer")
                    put("description", "Maximum number of events to return. Defaults to 20.")
                    put("minimum", 1)
                    put("maximum", 100)
                    put("default", 20)
                }
            )
        }
    ),
    parallelizable = true,
)

// GET EVENT

fun getCalendarEventTool() = ToolDefinition(
    name = "get_calendar_event",
    description = universalToolDescription(
        purpose = "Returns details for one calendar event by ID.",
        trigger = "The user asks for details about a specific calendar event.",
        notFor = "Use `list_calendar_events` to find or list events.",
        prerequisite = "An exact `event_id` is required; identify an unknown ID with `list_calendar_events` first.",
    ),
    parameters = calendarParameters(
        required = listOf("event_id"),
        properties = buildJsonObject {
            put(
                "event_id",
                calendarIntegerProperty(
                    "Numeric ID of the calendar event.",
                    minimum = 1
                )
            )
        }
    ),
    parallelizable = true,
)

// UPDATE EVENT

fun updateCalendarEventTool() = ToolDefinition(
    name = "update_calendar_event",
    description = universalToolDescription(
        purpose = "Updates only supplied fields of an existing event and preserves omitted fields.",
        trigger = "The user asks to edit an existing event's time, title, or other details.",
        notFor = "Use `create_calendar_event` to create an event and `delete_calendar_event` to remove one.",
        prerequisite = "An exact `event_id` is required; find an unknown ID with `list_calendar_events`. For new times, use contextual time or `get_current_time` if stale/another timezone is required.",
    ),
    parameters = calendarParameters(
        required = listOf("event_id"),
        properties = buildJsonObject {
            put(
                "event_id",
                calendarIntegerProperty(
                    "Numeric ID of the event to update.",
                    minimum = 1
                )
            )

            put(
                "title",
                calendarStringProperty(
                    "Replacement event title.",
                    maxLength = 200
                )
            )

            put(
                "start_time_ms",
                calendarIntegerProperty(
                    "Replacement start time as Unix epoch milliseconds.",
                    minimum = 1
                )
            )

            put(
                "end_time_ms",
                calendarIntegerProperty(
                    "Replacement end time as Unix epoch milliseconds.",
                    minimum = 1
                )
            )

            put(
                "description",
                calendarStringProperty(
                    "Replacement event description or notes.",
                    maxLength = 5000
                )
            )

            put(
                "location",
                calendarStringProperty(
                    "Replacement event location.",
                    maxLength = 500
                )
            )

            put(
                "all_day",
                calendarBooleanProperty(
                    "Set whether the event is all-day."
                )
            )
        }
    )
)

// DELETE EVENT

fun deleteCalendarEventTool() = ToolDefinition(
    name = "delete_calendar_event",
    description = universalToolDescription(
        purpose = "Permanently deletes an identified calendar event.",
        trigger = "The user explicitly asks to delete or cancel a calendar event.",
        notFor = "Use `update_calendar_event` to change event details, `delete_reminder` to remove a reminder, or `delete_todo` to remove a task.",
        prerequisite = "An exact `event_id` is required; identify an unknown event with `list_calendar_events` first.",
    ),
    parameters = calendarParameters(
        required = listOf("event_id"),
        properties = buildJsonObject {
            put(
                "event_id",
                calendarIntegerProperty(
                    "Numeric ID of the event to delete.",
                    minimum = 1
                )
            )
        }
    )
)

// ALL CALENDAR TOOLS

fun allCalendarTools() = listOf(
    createCalendarEventTool(),
    listCalendarEventsTool(),
    getCalendarEventTool(),
    updateCalendarEventTool(),
    deleteCalendarEventTool()
)
