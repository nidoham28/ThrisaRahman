package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

fun createCalendarEventTool() = ToolDefinition(
    name = "create_calendar_event",
    description = """
        Create a new event in the user's device calendar.
        Use when the user says 'schedule a meeting', 'add an event to my calendar', 
        'put this on my calendar', 'calendar event for tomorrow at 3pm', etc.
        Call `get_current_time` first when computing relative times.
        Always confirm event details back to the user after creation.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("title") {
                put("type", "string")
                put(
                    "description",
                    "Event summary or title (e.g. 'Team Sync', 'Doctor appointment')."
                )
            }
            putJsonObject("start_time_ms") {
                put("type", "integer")
                put("description", "Start time of the event as Unix epoch milliseconds.")
            }
            putJsonObject("end_time_ms") {
                put("type", "integer")
                put(
                    "description",
                    "Optional end time as Unix epoch milliseconds. Defaults to 1 hour after start if omitted."
                )
            }
            putJsonObject("description") {
                put("type", "string")
                put("description", "Optional event description or notes.")
            }
            putJsonObject("location") {
                put("type", "string")
                put(
                    "description",
                    "Optional event location (e.g. 'Office Room 3', 'Online Google Meet')."
                )
            }
            putJsonObject("all_day") {
                put("type", "boolean")
                put("description", "Whether this is an all-day event (default false).")
            }
        }
        putJsonArray("required") {
            add("title")
            add("start_time_ms")
        }
    },
)

fun listCalendarEventsTool() = ToolDefinition(
    name = "list_calendar_events",
    description = """
        List or search events from the user's device calendar.
        Use when the user asks 'what is on my calendar?', 'check my schedule for today', 
        'do I have any events this week?', or to find an event's ID before updating or deleting it.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("start_from_ms") {
                put("type", "integer")
                put(
                    "description",
                    "Unix epoch ms from which to search events (defaults to start of current day)."
                )
            }
            putJsonObject("end_to_ms") {
                put("type", "integer")
                put(
                    "description",
                    "Unix epoch ms up to which to search events (defaults to 30 days ahead)."
                )
            }
            putJsonObject("query") {
                put("type", "string")
                put(
                    "description",
                    "Optional keyword search in event title, description, or location."
                )
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "Maximum number of events to return (1-100, default 20).")
            }
        }
        putJsonArray("required") {}
    },
)

fun getCalendarEventTool() = ToolDefinition(
    name = "get_calendar_event",
    description = """
        Fetch detailed information about a single calendar event by its numeric ID.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("event_id") {
                put("type", "integer")
                put("description", "Numeric ID of the calendar event.")
            }
        }
        putJsonArray("required") { add("event_id") }
    },
)

fun updateCalendarEventTool() = ToolDefinition(
    name = "update_calendar_event",
    description = """
        Update an existing event in the user's device calendar by its numeric ID.
        Call `list_calendar_events` first if you don't know the event ID.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("event_id") {
                put("type", "integer")
                put("description", "Numeric ID of the calendar event to update.")
            }
            putJsonObject("title") {
                put("type", "string")
                put("description", "New title for the event.")
            }
            putJsonObject("start_time_ms") {
                put("type", "integer")
                put("description", "New start time in Unix epoch milliseconds.")
            }
            putJsonObject("end_time_ms") {
                put("type", "integer")
                put("description", "New end time in Unix epoch milliseconds.")
            }
            putJsonObject("description") {
                put("type", "string")
                put("description", "New description/notes.")
            }
            putJsonObject("location") {
                put("type", "string")
                put("description", "New location.")
            }
            putJsonObject("all_day") {
                put("type", "boolean")
                put("description", "Set all-day flag.")
            }
        }
        putJsonArray("required") { add("event_id") }
    },
)

fun deleteCalendarEventTool() = ToolDefinition(
    name = "delete_calendar_event",
    description = """
        Permanently delete an event from the user's device calendar by its numeric ID.
        Call `list_calendar_events` first if you don't know the event ID.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("event_id") {
                put("type", "integer")
                put("description", "Numeric ID of the event to delete.")
            }
        }
        putJsonArray("required") { add("event_id") }
    },
)
