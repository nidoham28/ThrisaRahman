
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
    minimum: Long? = null
) = buildJsonObject {
    put("type", "integer")
    put("description", description)
    minimum?.let { put("minimum", it) }
}

private fun calendarBooleanProperty(
    description: String
) = buildJsonObject {
    put("type", "boolean")
    put("description", description)
}

// CREATE EVENT

fun createCalendarEventTool() = ToolDefinition(
    name = "create_calendar_event",
    description = """
        Create an event in the user's device calendar.
        Use when the user asks to schedule, book, or add an event.
        Resolve relative dates and times using get_current_time first.
        Convert start and end times to Unix epoch milliseconds.
        If the end time is omitted, the event lasts one hour.
        Confirm the saved event details after successful creation.
    """.trimIndent(),
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
                    "Event start time as Unix epoch milliseconds.",
                    minimum = 0
                )
            )

            put(
                "end_time_ms",
                calendarIntegerProperty(
                    "Optional event end time in Unix epoch milliseconds. Defaults to one hour after the start.",
                    minimum = 0
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
                    "Whether this is an all-day event. Defaults to false."
                )
            )
        }
    )
)

// LIST EVENTS

fun listCalendarEventsTool() = ToolDefinition(
    name = "list_calendar_events",
    description = """
        Retrieve calendar events within a time range.
        Use when the user asks about their schedule, upcoming events,
        or events on a particular day or date range.
        Also use to find an event ID before updating or deleting it.
        Resolve relative date ranges using get_current_time first.
        Defaults to today through the next 30 days, with up to 20 results.
    """.trimIndent(),
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
                    "Optional inclusive range end as Unix epoch milliseconds. Defaults to 30 days after the current time.",
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
                }
            )
        }
    )
)

// GET EVENT

fun getCalendarEventTool() = ToolDefinition(
    name = "get_calendar_event",
    description = """
        Retrieve complete details for a single calendar event by its ID.
        Use when the user asks for details about a specific event.
        Find the ID with list_calendar_events if it is unknown.
    """.trimIndent(),
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
    )
)

// UPDATE EVENT

fun updateCalendarEventTool() = ToolDefinition(
    name = "update_calendar_event",
    description = """
        Update an existing device calendar event by ID.
        Find the event with list_calendar_events if its ID is unknown.
        Only include fields the user wants to change.
        Preserve all omitted fields.
        Resolve relative date and time changes using get_current_time.
        Confirm the updated event details after success.
    """.trimIndent(),
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
                    minimum = 0
                )
            )

            put(
                "end_time_ms",
                calendarIntegerProperty(
                    "Replacement end time as Unix epoch milliseconds.",
                    minimum = 0
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
                    "Whether the event should be an all-day event."
                )
            )
        }
    )
)

// DELETE EVENT

fun deleteCalendarEventTool() = ToolDefinition(
    name = "delete_calendar_event",
    description = """
        Delete an event from the user's device calendar by ID.
        Find the event with list_calendar_events if its ID is unknown.
        Use only when the user explicitly requests deletion.
    """.trimIndent(),
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
