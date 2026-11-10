package com.naira.assistant.core.tools.executor

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import com.naira.assistant.core.tools.executor.BuiltinToolExecutor.jsonStringArg
import com.naira.assistant.domain.FunctionCall
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.util.TimeZone

object CalendarToolExecutor {

    /**
     * Executes the `create_calendar_event` tool.
     */
    fun executeCreateCalendarEvent(call: FunctionCall, context: Context): JsonObject {
        val title = jsonStringArg(call, "title")
        if (title.isNullOrBlank()) {
            return error("Argument 'title' is required and must not be blank.")
        }

        val startTimeMs = extractLongArg(call, "start_time_ms")
            ?: return error("Argument 'start_time_ms' (Unix epoch ms) is required.")

        val endTimeMs = extractLongArg(call, "end_time_ms") ?: (startTimeMs + 3600000L) // Default 1 hour
        val description = jsonStringArg(call, "description") ?: ""
        val location = jsonStringArg(call, "location") ?: ""
        val allDay = extractBooleanArg(call, "all_day") ?: false

        val calendarId = getPrimaryCalendarId(context) ?: 1L

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title.trim())
            put(CalendarContract.Events.DESCRIPTION, description.trim())
            put(CalendarContract.Events.EVENT_LOCATION, location.trim())
            put(CalendarContract.Events.DTSTART, startTimeMs)
            put(CalendarContract.Events.DTEND, endTimeMs)
            put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }

        return try {
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val eventId = uri?.lastPathSegment?.toLongOrNull() ?: 0L

            buildJsonObject {
                put("success", true)
                put("action", "created")
                put("event_id", eventId)
                put("title", title.trim())
                put("start_time_ms", startTimeMs)
                put("end_time_ms", endTimeMs)
                put("description", description)
                put("location", location)
                put("all_day", allDay)
                put("message", "Calendar event '$title' created successfully with ID #$eventId.")
            }
        } catch (_: SecurityException) {
            permissionError()
        } catch (t: Throwable) {
            error("Failed to create calendar event: ${t.message ?: t::class.simpleName}")
        }
    }

    /**
     * Executes the `list_calendar_events` tool.
     */
    fun executeListCalendarEvents(call: FunctionCall, context: Context): JsonObject {
        val startFromMs = extractLongArg(call, "start_from_ms") ?: (System.currentTimeMillis() - 86400000L)
        val endToMs = extractLongArg(call, "end_to_ms") ?: (System.currentTimeMillis() + 30L * 86400000L)
        val query = jsonStringArg(call, "query")?.trim()
        val limit = (extractIntArg(call, "limit") ?: 20).coerceIn(1, 100)

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
        )

        val selParts = mutableListOf<String>()
        val args = mutableListOf<String>()

        selParts += "${CalendarContract.Events.DTSTART} >= ?"
        args += startFromMs.toString()

        if (endToMs > 0L) {
            selParts += "${CalendarContract.Events.DTSTART} <= ?"
            args += endToMs.toString()
        }

        if (!query.isNullOrBlank()) {
            selParts += "(${CalendarContract.Events.TITLE} LIKE ? OR ${CalendarContract.Events.DESCRIPTION} LIKE ? OR ${CalendarContract.Events.EVENT_LOCATION} LIKE ?)"
            val q = "%$query%"
            args += q; args += q; args += q
        }

        return try {
            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selParts.joinToString(" AND "),
                args.toTypedArray(),
                "${CalendarContract.Events.DTSTART} ASC"
            )

            val events = mutableListOf<JsonObject>()
            cursor?.use { c ->
                var count = 0
                while (c.moveToNext() && count < limit) {
                    events.add(c.toCalendarEventJson())
                    count++
                }
            }

            buildJsonObject {
                put("success", true)
                put("action", "list_events")
                put("count", events.size)
                put("events", buildJsonArray { events.forEach { add(it) } })
            }
        } catch (_: SecurityException) {
            permissionError()
        } catch (t: Throwable) {
            error("Failed to read calendar events: ${t.message ?: t::class.simpleName}")
        }
    }

    /**
     * Executes the `get_calendar_event` tool.
     */
    fun executeGetCalendarEvent(call: FunctionCall, context: Context): JsonObject {
        val eventId = extractLongArg(call, "event_id")
            ?: return error("Argument 'event_id' is required.")

        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
        )

        return try {
            val cursor = context.contentResolver.query(uri, projection, null, null, null)
            val event = cursor?.use { c ->
                if (c.moveToFirst()) c.toCalendarEventJson() else null
            }

            if (event != null) {
                buildJsonObject {
                    put("success", true)
                    put("event", event)
                }
            } else {
                error("Calendar event with ID #$eventId was not found.")
            }
        } catch (_: SecurityException) {
            permissionError()
        } catch (t: Throwable) {
            error("Failed to get calendar event #$eventId: ${t.message ?: t::class.simpleName}")
        }
    }

    /**
     * Executes the `update_calendar_event` tool.
     */
    fun executeUpdateCalendarEvent(call: FunctionCall, context: Context): JsonObject {
        val eventId = extractLongArg(call, "event_id")
            ?: return error("Argument 'event_id' is required.")

        val values = ContentValues()
        jsonStringArg(call, "title")?.let { values.put(CalendarContract.Events.TITLE, it.trim()) }
        jsonStringArg(call, "description")?.let { values.put(CalendarContract.Events.DESCRIPTION, it.trim()) }
        jsonStringArg(call, "location")?.let { values.put(CalendarContract.Events.EVENT_LOCATION, it.trim()) }
        extractLongArg(call, "start_time_ms")?.let { values.put(CalendarContract.Events.DTSTART, it) }
        extractLongArg(call, "end_time_ms")?.let { values.put(CalendarContract.Events.DTEND, it) }
        extractBooleanArg(call, "all_day")?.let { values.put(CalendarContract.Events.ALL_DAY, if (it) 1 else 0) }

        if (values.size() == 0) {
            return error("No fields provided to update for event #$eventId.")
        }

        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)

        return try {
            val rows = context.contentResolver.update(uri, values, null, null)
            if (rows > 0) {
                buildJsonObject {
                    put("success", true)
                    put("action", "updated")
                    put("event_id", eventId)
                    put("message", "Calendar event #$eventId updated successfully.")
                }
            } else {
                error("Calendar event with ID #$eventId was not found.")
            }
        } catch (_: SecurityException) {
            permissionError()
        } catch (t: Throwable) {
            error("Failed to update calendar event #$eventId: ${t.message ?: t::class.simpleName}")
        }
    }

    /**
     * Executes the `delete_calendar_event` tool.
     */
    fun executeDeleteCalendarEvent(call: FunctionCall, context: Context): JsonObject {
        val eventId = extractLongArg(call, "event_id")
            ?: return error("Argument 'event_id' is required.")

        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)

        return try {
            val rows = context.contentResolver.delete(uri, null, null)
            if (rows > 0) {
                buildJsonObject {
                    put("success", true)
                    put("action", "deleted")
                    put("event_id", eventId)
                    put("message", "Calendar event #$eventId deleted successfully.")
                }
            } else {
                error("Calendar event with ID #$eventId was not found or already deleted.")
            }
        } catch (_: SecurityException) {
            permissionError()
        } catch (t: Throwable) {
            error("Failed to delete calendar event #$eventId: ${t.message ?: t::class.simpleName}")
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun getPrimaryCalendarId(context: Context): Long? {
        val uri = CalendarContract.Calendars.CONTENT_URI
        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY)
        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                var firstId: Long? = null
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID))
                    if (firstId == null) firstId = id
                    val isPrimaryIdx = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
                    if (isPrimaryIdx != -1 && cursor.getInt(isPrimaryIdx) == 1) {
                        return id
                    }
                }
                firstId
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun Cursor.toCalendarEventJson(): JsonObject = buildJsonObject {
        put("id", getLong(getColumnIndexOrThrow(CalendarContract.Events._ID)))
        put("title", getString(getColumnIndexOrThrow(CalendarContract.Events.TITLE)).orEmpty())
        put("description", getString(getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)).orEmpty())
        put("location", getString(getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)).orEmpty())
        put("start_time_ms", getLong(getColumnIndexOrThrow(CalendarContract.Events.DTSTART)))
        put("end_time_ms", getLong(getColumnIndexOrThrow(CalendarContract.Events.DTEND)))
        put("all_day", getInt(getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)) == 1)
    }

    private fun permissionError(): JsonObject = buildJsonObject {
        put("success", false)
        put("permission_required", true)
        put("error", "Calendar permission denied. Please grant Calendar permission to Thrisa in device Settings.")
    }

    private fun error(message: String): JsonObject = buildJsonObject {
        put("success", false)
        put("error", message)
    }

    private fun extractLongArg(call: FunctionCall, key: String): Long? =
        runCatching {
            val element = call.args[key] ?: return null
            element.jsonPrimitive.longOrNull
                ?: element.jsonPrimitive.content.trim().toLongOrNull()
        }.getOrNull()

    private fun extractIntArg(call: FunctionCall, key: String): Int? =
        runCatching {
            val element = call.args[key] ?: return null
            element.jsonPrimitive.intOrNull
                ?: element.jsonPrimitive.content.trim().toIntOrNull()
        }.getOrNull()

    private fun extractBooleanArg(call: FunctionCall, key: String): Boolean? =
        runCatching {
            val element = call.args[key] ?: return null
            element.jsonPrimitive.booleanOrNull
                ?: element.jsonPrimitive.content.trim().lowercase().toBooleanStrictOrNull()
        }.getOrNull()
}
