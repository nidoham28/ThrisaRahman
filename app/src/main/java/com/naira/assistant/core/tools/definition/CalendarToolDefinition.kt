
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
    description = """
        Create a calendar event using Unix epoch milliseconds. Use the timestamp and timezone
        in context for relative dates; call get_current_time only if a fresher time or another
        timezone is needed. End defaults to one hour after start.
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
                    minimum = 1
                )
            )

            put(
                "end_time_ms",
                calendarIntegerProperty(
                    "Optional end time in Unix epoch milliseconds; must follow start. Defaults to one hour later.",
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
                    "All-day event. Default: false.",
                    default = false,
                )
            )
        }
    )
)

// LIST EVENTS

fun listCalendarEventsTool() = ToolDefinition(
    name = "list_calendar_events",
    description = """
        List events in a time range (default: today through 30 days ahead, 20 results).
        Use returned IDs for details, updates, or deletion. Use the context timestamp unless
        it is stale or another timezone is needed.
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
                    put("default", 20)
                }
            )
        }
    )
)

// GET EVENT

fun getCalendarEventTool() = ToolDefinition(
    name = "get_calendar_event",
    description = "Get one event by ID. If unknown, find it with list_calendar_events first.",
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
        Update only supplied fields of an existing event; preserve omitted fields.
        Find unknown IDs with list_calendar_events. Use context time unless stale or another
        timezone is needed.
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
    description = """
        Delete an event only on explicit request. Find an unknown event ID with
        list_calendar_events first.
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
