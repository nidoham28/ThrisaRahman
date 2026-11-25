
package com.naira.assistant.core.tools.executor

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import com.naira.assistant.core.tools.CommandExecution.jsonStringArg
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

        val allDay = if (call.args.containsKey("all_day")) {
            extractBooleanArg(call, "all_day")
                ?: return error("Argument 'all_day' must be a boolean.")
        } else {
            false
        }
        val localStartTimeMs = if (allDay) {
            normalizeAllDayTimestamp(startTimeMs)
        } else {
            startTimeMs
        }
        val start = localStartTimeMs
        val endTimeMs = if (call.args.containsKey("end_time_ms")) {
            val requestedEnd = extractLongArg(call, "end_time_ms")
                ?: return error("Argument 'end_time_ms' must be a valid integer.")
            if (allDay) normalizeAllDayTimestamp(requestedEnd) else requestedEnd
        } else {
            defaultEndTimeMs(start, allDay)
                ?: return error("The default event end time exceeds the supported date range.")
        }

        if (endTimeMs <= start) {
            return error("Event end time must be later than its start time.")
        }

        val description = if (call.args.containsKey("description")) {
            rawStringArg(call, "description")
                ?: return error("Argument 'description' must be a string.")
        } else {
            ""
        }.trim()
        val location = if (call.args.containsKey("location")) {
            rawStringArg(call, "location")
                ?: return error("Argument 'location' must be a string.")
        } else {
            ""
        }.trim()

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
                put(CalendarContract.Events.DTSTART, start)
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
                put("start_time_ms", start)
                put("end_time_ms", endTimeMs)
                put("description", description)
                put("location", location)
                put("all_day", allDay)
                put("message", "Calendar event '$title' created successfully with ID #$eventId.")
            }
        } catch (_: SecurityException) {
            permissionError()
        } catch (_: Exception) {
            error("Failed to create the calendar event. Verify calendar access and the event details, then retry.")
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

        val startFromMs = if (call.args.containsKey("start_from_ms")) {
            extractLongArg(call, "start_from_ms")
                ?: return error("Argument 'start_from_ms' must be an integer.")
        } else {
            startOfToday()
        }

        val endToMs = if (call.args.containsKey("end_to_ms")) {
            extractLongArg(call, "end_to_ms")
                ?: return error("Argument 'end_to_ms' must be an integer.")
        } else {
            now + 30L * DAY_MS
        }

        if (startFromMs < 0L || endToMs <= startFromMs) {
            return error("Invalid date range. The end time must be later than the start time.")
        }

        val query = if (call.args.containsKey("query")) {
            rawStringArg(call, "query")
                ?: return error("Argument 'query' must be a string.")
        } else {
            null
        }?.trim()
        val limit = if (call.args.containsKey("limit")) {
            extractIntArg(call, "limit")
                ?: return error("Argument 'limit' must be an integer.")
        } else {
            DEFAULT_LIMIT
        }
        if (limit !in 1..MAX_LIMIT) {
            return error("Argument 'limit' must be between 1 and $MAX_LIMIT.")
        }

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
                    ${CalendarContract.Events.TITLE} LIKE ? ESCAPE '\'
                    OR ${CalendarContract.Events.DESCRIPTION} LIKE ? ESCAPE '\'
                    OR ${CalendarContract.Events.EVENT_LOCATION} LIKE ? ESCAPE '\'
                )
            """.trimIndent()

            // Escape SQL LIKE wildcards so literal text matches literally.
            val searchPattern = "%${escapeLikePattern(query)}%"
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
        } catch (_: Exception) {
            error("Failed to read calendar events. Verify calendar access, then retry.")
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
        } catch (_: Exception) {
            error("Failed to read calendar event #$eventId. Verify calendar access, then retry.")
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

        if (call.args.containsKey("title")) {
            val title = rawStringArg(call, "title")
                ?: return error("Argument 'title' must be a string.")
            if (title.isBlank()) return error("Event title cannot be blank.")
            values.put(CalendarContract.Events.TITLE, title.trim())
        }

        if (call.args.containsKey("description")) {
            val description = rawStringArg(call, "description")
                ?: return error("Argument 'description' must be a string.")
            values.put(CalendarContract.Events.DESCRIPTION, description.trim())
        }

        if (call.args.containsKey("location")) {
            val location = rawStringArg(call, "location")
                ?: return error("Argument 'location' must be a string.")
            values.put(CalendarContract.Events.EVENT_LOCATION, location.trim())
        }

        val newStart = if (call.args.containsKey("start_time_ms")) {
            extractLongArg(call, "start_time_ms")
                ?: return error("Argument 'start_time_ms' must be an integer.")
        } else {
            null
        }
        val newEnd = if (call.args.containsKey("end_time_ms")) {
            extractLongArg(call, "end_time_ms")
                ?: return error("Argument 'end_time_ms' must be an integer.")
        } else {
            null
        }
        val newAllDay = if (call.args.containsKey("all_day")) {
            extractBooleanArg(call, "all_day")
                ?: return error("Argument 'all_day' must be a boolean.")
        } else {
            null
        }

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
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.ALL_DAY,
                ),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    Triple(
                        cursor.getLong(0),
                        cursor.getLong(1),
                        cursor.getInt(2) == 1,
                    )
                } else {
                    null
                }
            } ?: return error("Calendar event with ID #$eventId was not found.")

            val isAllDay = newAllDay ?: existingTiming.third
            var adjustedStart = newStart ?: existingTiming.first
            var adjustedEnd = newEnd ?: existingTiming.second

            if (isAllDay) {
                if (existingTiming.third) {
                    if (newStart != null) {
                        adjustedStart = normalizeAllDayTimestamp(newStart)
                    }
                    if (newEnd != null) {
                        adjustedEnd = normalizeAllDayTimestamp(newEnd)
                    }
                } else {
                    adjustedStart = normalizeAllDayTimestamp(adjustedStart)
                    adjustedEnd = normalizeAllDayTimestamp(adjustedEnd)
                }

                if (adjustedEnd <= adjustedStart && newEnd == null) {
                    adjustedEnd = defaultEndTimeMs(adjustedStart, allDay = true)
                        ?: return error("The default event end time exceeds the supported date range.")
                }
                if (!existingTiming.third || newStart != null || newEnd != null) {
                    values.put(CalendarContract.Events.DTSTART, adjustedStart)
                    values.put(CalendarContract.Events.DTEND, adjustedEnd)
                }
            } else if (existingTiming.third && newAllDay == false) {
                if (newStart == null) {
                    adjustedStart = normalizeUtcAllDayTimestampToLocalMidnight(adjustedStart)
                }
                if (newEnd == null) {
                    adjustedEnd = normalizeUtcAllDayTimestampToLocalMidnight(adjustedEnd)
                }
                values.put(CalendarContract.Events.DTSTART, adjustedStart)
                values.put(CalendarContract.Events.DTEND, adjustedEnd)
            }

            if (adjustedEnd <= adjustedStart) {
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
                    put("start_time_ms", adjustedStart)
                    put("end_time_ms", adjustedEnd)
                    put("message", "Calendar event #$eventId updated successfully.")
                }
            } else {
                error("Calendar event with ID #$eventId was not found or could not be updated.")
            }
        } catch (_: SecurityException) {
            permissionError()
        } catch (_: Exception) {
            error("Failed to update calendar event #$eventId. Verify calendar access and the event details, then retry.")
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
        } catch (_: Exception) {
            error("Failed to delete calendar event #$eventId. Verify calendar access, then retry.")
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

    private fun rawStringArg(call: FunctionCall, key: String): String? {
        val value = call.args[key] as? kotlinx.serialization.json.JsonPrimitive
            ?: return null
        return value.content.takeIf { value.isString }
    }

    internal fun defaultEndTimeMs(startTimeMs: Long, allDay: Boolean): Long? {
        val duration = if (allDay) DAY_MS else DEFAULT_DURATION_MS
        return runCatching { Math.addExact(startTimeMs, duration) }.getOrNull()
    }

    internal fun normalizeAllDayTimestamp(
        timestampMs: Long,
        localTimeZone: TimeZone = TimeZone.getDefault(),
    ): Long {
        val localDate = Calendar.getInstance(localTimeZone).apply {
            timeInMillis = timestampMs
        }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(
                localDate.get(Calendar.YEAR),
                localDate.get(Calendar.MONTH),
                localDate.get(Calendar.DAY_OF_MONTH),
                0,
                0,
                0,
            )
        }.timeInMillis
    }

    private fun normalizeUtcAllDayTimestampToLocalMidnight(timestampMs: Long): Long {
        val utcDate = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = timestampMs
        }
        return Calendar.getInstance().apply {
            clear()
            set(
                utcDate.get(Calendar.YEAR),
                utcDate.get(Calendar.MONTH),
                utcDate.get(Calendar.DAY_OF_MONTH),
                0,
                0,
                0,
            )
        }.timeInMillis
    }

    // ------------------------------------------------------------------
    // DATE HELPERS
    // ------------------------------------------------------------------

    /** Escapes SQL LIKE wildcards so a search query matches literally. */
    private fun escapeLikePattern(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

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
