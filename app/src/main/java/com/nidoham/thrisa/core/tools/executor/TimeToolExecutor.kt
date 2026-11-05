package com.nidoham.thrisa.core.tools.executor

import com.nidoham.thrisa.core.tools.executor.BuiltinToolExecutor.jsonStringArg
import com.nidoham.thrisa.domain.FunctionCall
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
import kotlin.math.abs
import kotlin.time.Clock

object TimeToolExecutor {
    /**
     * Implementation of the `get_current_time` tool.
     *
     * Resolves the optional `timezone` argument (IANA identifier such as
     * `"America/New_York"`) via [TimeZone.of]. When absent, uses
     * [TimeZone.currentSystemDefault] — the device's local zone on Android, the
     * browser's zone on JS, the OS zone on desktop / native. When present but
     * unrecognised, [TimeZone.of] throws [IllegalTimeZoneException] and we return a
     * structured error rather than silently falling back to UTC, so the model can
     * correct itself on retry.
     *
     * The response deliberately over-describes the instant: `datetime` for precise
     * parsing, plus `date` / `time` / `weekday` / `utc_offset` for easy reasoning,
     * plus `epoch_seconds` for arithmetic. Models are unreliable at recomputing one
     * of these from another, so it's cheaper to send all of them than to induce a
     * follow-up tool call.
     */
    fun executeGetCurrentTime(call: FunctionCall): JsonObject {
        val requested = jsonStringArg(call, "timezone")

        val zone: TimeZone = try {
            if (requested == null) TimeZone.currentSystemDefault() else TimeZone.of(requested)
        } catch (_: IllegalTimeZoneException) {
            return buildJsonObject {
                put(
                    "error",
                    "Unknown timezone '$requested'. Use an IANA identifier such as " +
                            "'America/New_York', 'Europe/London' or 'Asia/Tokyo'.",
                )
            }
        }

        val now = Clock.System.now()
        val local = now.toLocalDateTime(zone)
        val offset = zone.offsetAt(now)

        return buildJsonObject {
            put("timezone", zone.id)
            put("datetime", isoOffsetDateTime(local, offset))
            put("date", local.date.toString())
            put("time", formatClockTime(local))
            put("weekday", local.dayOfWeek.displayName())
            put("utc_offset", formatUtcOffset(offset))
            put("epoch_seconds", now.epochSeconds)
        }
    }

    /** Renders `2024-01-15T14:30:00+09:00` — the same shape `ISO_OFFSET_DATE_TIME` gives. */
    fun isoOffsetDateTime(local: LocalDateTime, offset: UtcOffset): String =
        "${local.date}T${formatClockTime(local)}${formatUtcOffset(offset)}"

    /** `HH:mm:ss`, zero-padded. */
    fun formatClockTime(local: LocalDateTime): String {
        val h = local.hour.toString().padStart(2, '0')
        val m = local.minute.toString().padStart(2, '0')
        val s = local.second.toString().padStart(2, '0')
        return "$h:$m:$s"
    }

    /** `Z` for UTC, otherwise `+HH:MM` / `-HH:MM`. */
    fun formatUtcOffset(offset: UtcOffset): String {
        val total = offset.totalSeconds
        if (total == 0) return "Z"
        val sign = if (total < 0) "-" else "+"
        val absSec = abs(total)
        val h = (absSec / 3600).toString().padStart(2, '0')
        val m = ((absSec % 3600) / 60).toString().padStart(2, '0')
        return "$sign$h:$m"
    }

    /** English weekday name. Models reason about prose better than enum names. */
    fun DayOfWeek.displayName(): String = when (this) {
        DayOfWeek.MONDAY -> "Monday"
        DayOfWeek.TUESDAY -> "Tuesday"
        DayOfWeek.WEDNESDAY -> "Wednesday"
        DayOfWeek.THURSDAY -> "Thursday"
        DayOfWeek.FRIDAY -> "Friday"
        DayOfWeek.SATURDAY -> "Saturday"
        DayOfWeek.SUNDAY -> "Sunday"
    }
}