
package com.naira.assistant.core.tools.executor

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import com.naira.assistant.core.tools.executor.CommandExecution.jsonStringArg
import com.naira.assistant.domain.FunctionCall
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.util.Calendar
import java.util.TimeZone

object CalendarToolExecutor {

    private const val DEFAULT_DURATION_MS = 60 * 60 * 1000L
    private const val DEFAULT_LIMIT = 20
    private const val MAX_LIMIT = 100
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    private val eventProjection = arrayOf(
        CalendarContract.Events._ID,
        CalendarContract.Events.TITLE,
        CalendarContract.Events.DESCRIPTION,
        CalendarContract.Events.EVENT_LOCATION,
        CalendarContract.Events.DTSTART,
        CalendarContract.Events.DTEND,
        CalendarContract.Events.ALL_DAY
    )

    // ------------------------------------------------------------------
    // CREATE
    // ------------------------------------------------------------------

    fun executeCreateCalendarEvent(
        call: FunctionCall,
        context: Context
    ): JsonObject {
        val title = jsonStringArg(call, "title")?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return error("Argument 'title' is required and must not be blank.")

        val startTimeMs = extractLongArg(call, "start_time_ms")
            ?: return error("Argument 'start_time_ms' is required and must be a valid integer.")

        if (startTimeMs <= 0L) {
            return error("Argument 'start_time_ms' must be a positive Unix timestamp in milliseconds.")
        }

        val endTimeMs = extractLongArg(call, "end_time_ms")
            ?: (startTimeMs + DEFAULT_DURATION_MS)

        if (endTimeMs <= startTimeMs) {
            return error("Event end time must be later than its start time.")
        }

        val allDay = extractBooleanArg(call, "all_day") ?: false
        val description = jsonStringArg(call, "description")?.trim().orEmpty()
        val location = jsonStringArg(call, "location")?.trim().orEmpty()

        return try {
            val calendarId = getPrimaryCalendarId(context)
                ?: return error(
                    "No writable calendar was found. Please configure a calendar account."
                )

            val timezone = if (allDay) {
                "UTC"
            } else {
                TimeZone.getDefault().id
            }

            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, description)
                put(CalendarContract.Events.EVENT_LOCATION, location)
                put(CalendarContract.Events.DTSTART, startTimeMs)
                put(CalendarContract.Events.DTEND, endTimeMs)
                put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
                put(CalendarContract.Events.EVENT_TIMEZONE, timezone)
            }

            val uri = context.contentResolver.insert(
                CalendarContract.Events.CONTENT_URI,
                values
            ) ?: return error("Calendar provider did not create the event.")

            val eventId = uri.lastPathSegment?.toLongOrNull()
                ?: return error("Event was created, but its ID could not be retrieved.")

            buildJsonObject {
                put("success", true)
                put("action", "created")
                put("event_id", eventId)
                put("title", title)
                put("start_time_ms", startTimeMs)
                put("end_time_ms", endTimeMs)
                put("description", description)
                put("location", location)
                put("all_day", allDay)
                put("message", "Calendar event '$title' created successfully with ID #$eventId.")
            }
        } catch (_: SecurityException) {
            permissionError()
        } catch (e: Exception) {
            error("Failed to create calendar event: ${e.message ?: "Unknown error"}")
        }
    }

    // ------------------------------------------------------------------
    // LIST
    // ------------------------------------------------------------------

    fun executeListCalendarEvents(
        call: FunctionCall,
        context: Context
    ): JsonObject {
        val now = System.currentTimeMillis()

        val startFromMs = extractLongArg(call, "start_from_ms")
            ?: startOfToday()

        val endToMs = extractLongArg(call, "end_to_ms")
            ?: (now + 30L * DAY_MS)

        if (startFromMs < 0L || endToMs <= startFromMs) {
            return error("Invalid date range. The end time must be later than the start time.")
        }

        val query = jsonStringArg(call, "query")?.trim()
        val limit = (extractIntArg(call, "limit") ?: DEFAULT_LIMIT)
            .coerceIn(1, MAX_LIMIT)

        val selectionParts = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()

        // Include events that overlap the requested time range.
        selectionParts += "${CalendarContract.Events.DTSTART} < ?"
        selectionArgs += endToMs.toString()

        selectionParts += "${CalendarContract.Events.DTEND} > ?"
        selectionArgs += startFromMs.toString()

        if (!query.isNullOrBlank()) {
            selectionParts += """
                (
                    ${CalendarContract.Events.TITLE} LIKE ?
                    OR ${CalendarContract.Events.DESCRIPTION} LIKE ?
                    OR ${CalendarContract.Events.EVENT_LOCATION} LIKE ?
                )
            """.trimIndent()

            val searchPattern = "%$query%"
            repeat(3) {
                selectionArgs += searchPattern
            }
        }

        return try {
            val events = mutableListOf<JsonObject>()

            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                eventProjection,
                selectionParts.joinToString(" AND "),
                selectionArgs.toTypedArray(),
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext() && events.size < limit) {
                    events.add(cursor.toCalendarEventJson())
                }
            }

            buildJsonObject {
                put("success", true)
                put("action", "list_events")
                put("count", events.size)
                put("events", buildJsonArray {
                    events.forEach { add(it) }
                })
            }
        } catch (_: SecurityException) {
            permissionError()
        } catch (e: Exception) {
            error("Failed to read calendar events: ${e.message ?: "Unknown error"}")
        }
    }

    // ------------------------------------------------------------------
    // GET
    // ------------------------------------------------------------------

    fun executeGetCalendarEvent(
        call: FunctionCall,
        context: Context
    ): JsonObject {
        val eventId = extractLongArg(call, "event_id")
            ?.takeIf { it > 0L }
            ?: return error("Argument 'event_id' must be a positive integer.")

        val uri = ContentUris.withAppendedId(
            CalendarContract.Events.CONTENT_URI,
            eventId
        )

        return try {
            val event = context.contentResolver.query(
                uri,
                eventProjection,
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.toCalendarEventJson()
                } else {
                    null
                }
            }

            if (event == null) {
                error("Calendar event with ID #$eventId was not found.")
            } else {
                buildJsonObject {
                    put("success", true)
                    put("event", event)
                }
            }
        } catch (_: SecurityException) {
            permissionError()
        } catch (e: Exception) {
            error("Failed to get calendar event #$eventId: ${e.message ?: "Unknown error"}")
        }
    }

    // ------------------------------------------------------------------
    // UPDATE
    // ------------------------------------------------------------------

    fun executeUpdateCalendarEvent(
        call: FunctionCall,
        context: Context
    ): JsonObject {
        val eventId = extractLongArg(call, "event_id")
            ?.takeIf { it > 0L }
            ?: return error("Argument 'event_id' must be a positive integer.")

        val values = ContentValues()

        jsonStringArg(call, "title")?.let {
            if (it.isBlank()) {
                return error("Event title cannot be blank.")
            }
            values.put(CalendarContract.Events.TITLE, it.trim())
        }

        jsonStringArg(call, "description")?.let {
            values.put(CalendarContract.Events.DESCRIPTION, it.trim())
        }

        jsonStringArg(call, "location")?.let {
            values.put(CalendarContract.Events.EVENT_LOCATION, it.trim())
        }

        val newStart = extractLongArg(call, "start_time_ms")
        val newEnd = extractLongArg(call, "end_time_ms")
        val newAllDay = extractBooleanArg(call, "all_day")

        newStart?.let {
            if (it <= 0L) {
                return error("Argument 'start_time_ms' must be a positive timestamp.")
            }
            values.put(CalendarContract.Events.DTSTART, it)
        }

        newEnd?.let {
            if (it <= 0L) {
                return error("Argument 'end_time_ms' must be a positive timestamp.")
            }
            values.put(CalendarContract.Events.DTEND, it)
        }

        newAllDay?.let {
            values.put(CalendarContract.Events.ALL_DAY, if (it) 1 else 0)
            values.put(
                CalendarContract.Events.EVENT_TIMEZONE,
                if (it) "UTC" else TimeZone.getDefault().id
            )
        }

        if (values.size() == 0) {
            return error("No fields provided to update for event #$eventId.")
        }

        val uri = ContentUris.withAppendedId(
            CalendarContract.Events.CONTENT_URI,
            eventId
        )

        return try {
            // Read existing values so partial time updates can be validated.
            val existingTiming = context.contentResolver.query(
                uri,
                arrayOf(
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND
                ),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getLong(0) to cursor.getLong(1)
                } else {
                    null
                }
            } ?: return error("Calendar event with ID #$eventId was not found.")

            val finalStart = newStart ?: existingTiming.first
            val finalEnd = newEnd ?: existingTiming.second

            if (finalEnd <= finalStart) {
                return error("Event end time must be later than its start time.")
            }

            val rows = context.contentResolver.update(
                uri,
                values,
                null,
                null
            )

            if (rows > 0) {
                buildJsonObject {
                    put("success", true)
                    put("action", "updated")
                    put("event_id", eventId)
                    put("start_time_ms", finalStart)
                    put("end_time_ms", finalEnd)
                    put("message", "Calendar event #$eventId updated successfully.")
                }
            } else {
                error("Calendar event with ID #$eventId was not found or could not be updated.")
            }
        } catch (_: SecurityException) {
            permissionError()
        } catch (e: Exception) {
            error("Failed to update calendar event #$eventId: ${e.message ?: "Unknown error"}")
        }
    }

    // ------------------------------------------------------------------
    // DELETE
    // ------------------------------------------------------------------

    fun executeDeleteCalendarEvent(
        call: FunctionCall,
        context: Context
    ): JsonObject {
        val eventId = extractLongArg(call, "event_id")
            ?.takeIf { it > 0L }
            ?: return error("Argument 'event_id' must be a positive integer.")

        val uri = ContentUris.withAppendedId(
            CalendarContract.Events.CONTENT_URI,
            eventId
        )

        return try {
            val rows = context.contentResolver.delete(
                uri,
                null,
                null
            )

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
        } catch (e: Exception) {
            error("Failed to delete calendar event #$eventId: ${e.message ?: "Unknown error"}")
        }
    }

    // ------------------------------------------------------------------
    // CALENDAR SELECTION
    // ------------------------------------------------------------------

    private fun getPrimaryCalendarId(context: Context): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY
        )

        val selection =
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND " +
                    "${CalendarContract.Calendars.VISIBLE} = 1"

        val selectionArgs = arrayOf(
            CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()
        )

        return context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${CalendarContract.Calendars.IS_PRIMARY} DESC, " +
                    "${CalendarContract.Calendars._ID} ASC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getLong(
                    cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                )
            } else {
                null
            }
        }
    }

    // ------------------------------------------------------------------
    // CURSOR MAPPING
    // ------------------------------------------------------------------

    private fun Cursor.toCalendarEventJson(): JsonObject =
        buildJsonObject {
            put(
                "id",
                getLong(getColumnIndexOrThrow(CalendarContract.Events._ID))
            )

            put(
                "title",
                getString(getColumnIndexOrThrow(CalendarContract.Events.TITLE)).orEmpty()
            )

            put(
                "description",
                getString(getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)).orEmpty()
            )

            put(
                "location",
                getString(getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)).orEmpty()
            )

            put(
                "start_time_ms",
                getLong(getColumnIndexOrThrow(CalendarContract.Events.DTSTART))
            )

            put(
                "end_time_ms",
                getLong(getColumnIndexOrThrow(CalendarContract.Events.DTEND))
            )

            put(
                "all_day",
                getInt(getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)) == 1
            )
        }

    // ------------------------------------------------------------------
    // ARGUMENT HELPERS
    // ------------------------------------------------------------------

    private fun extractLongArg(
        call: FunctionCall,
        key: String
    ): Long? = runCatching {
        val element = call.args[key] ?: return null
        element.jsonPrimitive.longOrNull
            ?: element.jsonPrimitive.content.trim().toLongOrNull()
    }.getOrNull()

    private fun extractIntArg(
        call: FunctionCall,
        key: String
    ): Int? = runCatching {
        val element = call.args[key] ?: return null
        element.jsonPrimitive.intOrNull
            ?: element.jsonPrimitive.content.trim().toIntOrNull()
    }.getOrNull()

    private fun extractBooleanArg(
        call: FunctionCall,
        key: String
    ): Boolean? = runCatching {
        val element = call.args[key] ?: return null
        element.jsonPrimitive.booleanOrNull
            ?: element.jsonPrimitive.content.trim().toBooleanStrictOrNull()
    }.getOrNull()

    // ------------------------------------------------------------------
    // DATE HELPERS
    // ------------------------------------------------------------------

    private fun startOfToday(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    // ------------------------------------------------------------------
    // RESPONSE HELPERS
    // ------------------------------------------------------------------

    private fun permissionError(): JsonObject =
        buildJsonObject {
            put("success", false)
            put("permission_required", true)
            put(
                "error",
                "Calendar permission denied. Please grant Calendar permission to Naira in device Settings."
            )
        }

    private fun error(message: String): JsonObject =
        buildJsonObject {
            put("success", false)
            put("error", message)
        }
}
