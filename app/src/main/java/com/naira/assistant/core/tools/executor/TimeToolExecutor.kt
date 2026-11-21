
package com.naira.assistant.core.tools.executor

import com.naira.assistant.core.tools.CommandExecution.jsonStringArg
import com.naira.assistant.domain.FunctionCall
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.IllegalTimeZoneException
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock

object TimeToolExecutor {

    private const val DEFAULT_TIMEZONE = "Asia/Dhaka"

    /**
     * Executes get_current_time.
     *
     * Returns the current date, time, timezone, UTC offset,
     * and Unix timestamps for reliable time calculations.
     */
    fun executeGetCurrentTime(call: FunctionCall): JsonObject {

        val requested = jsonStringArg(call, "timezone")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val zone = try {
            TimeZone.of(requested ?: DEFAULT_TIMEZONE)
        } catch (_: IllegalTimeZoneException) {
            return buildJsonObject {
                put("success", false)
                put("error", "Unknown timezone '$requested'.")
                put(
                    "hint",
                    "Use a valid IANA timezone such as Asia/Dhaka, " +
                            "Asia/Kolkata, Europe/London, or America/New_York."
                )
            }
        }

        // Capture the current instant exactly once.
        val now = Clock.System.now()

        val local = now.toLocalDateTime(zone)
        val offset = zone.offsetAt(now)

        return buildJsonObject {
            put("success", true)

            put("timezone", zone.id)
            put("datetime", isoOffsetDateTime(local, offset))
            put("date", local.date.toString())
            put("time", formatClockTime(local))
            put("weekday", local.dayOfWeek.displayName())
            put("utc_offset", formatUtcOffset(offset))

            put("epoch_seconds", now.epochSeconds)
            put("epoch_milliseconds", now.toEpochMilliseconds())
        }
    }

    // ---------------------------------------------------------
    // Date and time formatting
    // ---------------------------------------------------------

    /**
     * ISO-8601 offset datetime.
     * Example: 2026-11-20T15:30:00+06:00
     */
    private fun isoOffsetDateTime(
        local: LocalDateTime,
        offset: UtcOffset
    ): String {
        return "${local.date}T${formatClockTime(local)}${formatUtcOffset(offset)}"
    }

    /**
     * Returns HH:mm:ss.
     */
    private fun formatClockTime(local: LocalDateTime): String {
        val hours = local.hour.toString().padStart(2, '0')
        val minutes = local.minute.toString().padStart(2, '0')
        val seconds = local.second.toString().padStart(2, '0')

        return "$hours:$minutes:$seconds"
    }

    /**
     * Returns Z for UTC or +/-HH:MM for other offsets.
     */
    private fun formatUtcOffset(offset: UtcOffset): String {
        val totalSeconds = offset.totalSeconds

        if (totalSeconds == 0) return "Z"

        val sign = if (totalSeconds < 0) "-" else "+"
        val absoluteSeconds = kotlin.math.abs(totalSeconds.toLong())

        val hours = (absoluteSeconds / 3600)
            .toString()
            .padStart(2, '0')

        val minutes = ((absoluteSeconds % 3600) / 60)
            .toString()
            .padStart(2, '0')

        return "$sign$hours:$minutes"
    }

    // ---------------------------------------------------------
    // Weekday formatting
    // ---------------------------------------------------------

    private fun DayOfWeek.displayName(): String = when (this) {
        DayOfWeek.MONDAY -> "Monday"
        DayOfWeek.TUESDAY -> "Tuesday"
        DayOfWeek.WEDNESDAY -> "Wednesday"
        DayOfWeek.THURSDAY -> "Thursday"
        DayOfWeek.FRIDAY -> "Friday"
        DayOfWeek.SATURDAY -> "Saturday"
        DayOfWeek.SUNDAY -> "Sunday"
    }
}
