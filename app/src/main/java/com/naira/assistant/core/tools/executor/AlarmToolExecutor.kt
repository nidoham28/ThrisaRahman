
package com.naira.assistant.core.tools.executor

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.naira.assistant.core.database.alarm.AlarmItem
import com.naira.assistant.core.database.alarm.AlarmRepository
import com.naira.assistant.domain.FunctionCall
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.util.Calendar
import java.util.Locale

object AlarmToolExecutor {

    // ------------------------------------------------------------
    // CREATE ALARM
    // ------------------------------------------------------------

    suspend fun executeCreateAlarm(
        call: FunctionCall,
        context: Context,
        repo: AlarmRepository,
    ): JsonObject {
        val hour = extractIntArg(call, "hour")
            ?: return error("'hour' is required and must be an integer.")

        val minute = extractIntArg(call, "minute")
            ?: return error("'minute' is required and must be an integer.")

        if (hour !in 0..23) {
            return error("'hour' must be between 0 and 23.")
        }

        if (minute !in 0..59) {
            return error("'minute' must be between 0 and 59.")
        }

        val label = if (call.args.containsKey("label")) {
            extractStringArg(call, "label")
                ?: return error("'label' must be a string.")
        } else ""

        val days = if (call.args.containsKey("days")) {
            parseDaysArg(call, "days")
                ?: return error(
                    "Invalid 'days'. Use everyday, weekdays, weekends, " +
                            "day names, or day numbers from 1 to 7."
                )
        } else emptyList()

        val vibrate = extractBooleanArg(call, "vibrate") ?: true
        val skipUi = extractBooleanArg(call, "skip_ui") ?: true

        // Save locally first.
        val alarm = try {
            repo.insert(
                hour = hour,
                minute = minute,
                label = label,
                days = days,
                enabled = true,
                vibrate = vibrate,
            )
        } catch (e: Exception) {
            return error("Failed to save alarm: ${e.message ?: "Database error."}")
        }

        // Request scheduling through Android Clock.
        val deviceScheduled = triggerDeviceAlarm(
            context = context,
            hour = hour,
            minute = minute,
            label = label,
            days = days,
            vibrate = vibrate,
            skipUi = skipUi,
        )

        val time = formatTime(hour, minute)

        return buildJsonObject {
            put("success", true)
            put("action", "created")
            put("id", alarm.id)
            put("time", time)
            put("hour", hour)
            put("minute", minute)
            put("label", label)
            put("repeat", days.isNotEmpty())
            put("days", days.toJsonArray())
            put("vibrate", vibrate)
            put("local_saved", true)
            put("device_scheduled", deviceScheduled)
            put("alarm", alarm.toJsonObject())

            put(
                "message",
                if (deviceScheduled) {
                    "Alarm set for $time" +
                            if (label.isNotBlank()) " ($label)." else "."
                } else {
                    "Alarm saved locally for $time, but Android Clock " +
                            "could not be opened. Device scheduling is unconfirmed."
                }
            )
        }
    }

    // ------------------------------------------------------------
    // LIST ALARMS
    // ------------------------------------------------------------

    suspend fun executeListAlarms(
        call: FunctionCall,
        repo: AlarmRepository,
    ): JsonObject {
        val enabledOnly = extractBooleanArg(call, "enabled_only") ?: false
        val limit = (extractIntArg(call, "limit") ?: 20).coerceIn(1, 100)

        val alarms = try {
            repo.getAll(
                enabledOnly = enabledOnly,
                limit = limit,
            )
        } catch (e: Exception) {
            return error("Failed to retrieve alarms: ${e.message ?: "Database error."}")
        }

        val total = try {
            repo.count()
        } catch (e: Exception) {
            return error("Failed to count alarms: ${e.message ?: "Database error."}")
        }

        return buildJsonObject {
            put("success", true)
            put("action", "list_alarms")
            put("total_stored", total)
            put("count", alarms.size)
            put("enabled_only", enabledOnly)
            put("alarms", buildJsonArray {
                alarms.forEach {
                    add(it.toJsonObject())
                }
            })
        }
    }

    // ------------------------------------------------------------
    // GET ALARM
    // ------------------------------------------------------------

    suspend fun executeGetAlarm(
        call: FunctionCall,
        repo: AlarmRepository,
    ): JsonObject {
        val id = extractLongArg(call, "id")
            ?: return error("'id' is required and must be an integer.")

        if (id <= 0) return error("'id' must be greater than zero.")

        val alarm = try {
            repo.getById(id)
        } catch (e: Exception) {
            return error("Failed to retrieve alarm: ${e.message ?: "Database error."}")
        } ?: return error("Alarm with ID #$id was not found.")

        return buildJsonObject {
            put("success", true)
            put("action", "get_alarm")
            put("alarm", alarm.toJsonObject())
        }
    }

    // ------------------------------------------------------------
    // UPDATE ALARM
    // ------------------------------------------------------------

    suspend fun executeUpdateAlarm(
        call: FunctionCall,
        context: Context,
        repo: AlarmRepository,
    ): JsonObject {
        val id = extractLongArg(call, "id")
            ?: return error("'id' is required and must be an integer.")

        if (id <= 0) return error("'id' must be greater than zero.")

        // Validate explicitly supplied fields.
        val hour = if (call.args.containsKey("hour")) {
            extractIntArg(call, "hour")
                ?: return error("'hour' must be an integer.")
        } else null

        val minute = if (call.args.containsKey("minute")) {
            extractIntArg(call, "minute")
                ?: return error("'minute' must be an integer.")
        } else null

        if (hour != null && hour !in 0..23) {
            return error("'hour' must be between 0 and 23.")
        }

        if (minute != null && minute !in 0..59) {
            return error("'minute' must be between 0 and 59.")
        }

        val label = if (call.args.containsKey("label")) {
            extractStringArg(call, "label")
                ?: return error("'label' must be a string.")
        } else null

        val days = if (call.args.containsKey("days")) {
            parseDaysArg(call, "days")
                ?: return error("Invalid repeat schedule in 'days'.")
        } else null

        val enabled = if (call.args.containsKey("enabled")) {
            extractBooleanArg(call, "enabled")
                ?: return error("'enabled' must be a boolean.")
        } else null

        val vibrate = if (call.args.containsKey("vibrate")) {
            extractBooleanArg(call, "vibrate")
                ?: return error("'vibrate' must be a boolean.")
        } else null

        if (
            hour == null &&
            minute == null &&
            label == null &&
            days == null &&
            enabled == null &&
            vibrate == null
        ) {
            return error("No valid fields provided to update.")
        }

        // Fetch original state to determine whether native scheduling
        // might need another request.
        val previous = try {
            repo.getById(id)
        } catch (e: Exception) {
            return error("Failed to retrieve alarm: ${e.message ?: "Database error."}")
        } ?: return error("Alarm with ID #$id was not found.")

        val updated = try {
            repo.update(
                id = id,
                hour = hour,
                minute = minute,
                label = label,
                days = days,
                enabled = enabled,
                vibrate = vibrate,
            )
        } catch (e: Exception) {
            return error("Failed to update alarm: ${e.message ?: "Database error."}")
        } ?: return error("Alarm with ID #$id was not found.")

        // Android's standard ACTION_SET_ALARM creates alarms.
        // It does not update an existing native alarm by database ID.
        val scheduleChanged =
            hour != null || minute != null || days != null

        val needsNewNativeAlarm =
            updated.enabled && scheduleChanged

        val nativeSchedulingRequested = if (needsNewNativeAlarm) {
            triggerDeviceAlarm(
                context = context,
                hour = updated.hour,
                minute = updated.minute,
                label = updated.label,
                days = updated.days,
                vibrate = updated.vibrate,
                skipUi = true,
            )
        } else false

        val nativeWarning = when {
            !updated.enabled ->
                "The local alarm is disabled. An existing native Clock alarm " +
                        "may still be active."

            needsNewNativeAlarm && nativeSchedulingRequested ->
                "A new native alarm was requested. The previous native alarm " +
                        "may still exist, so duplicates are possible."

            needsNewNativeAlarm ->
                "Local alarm updated, but native Clock scheduling failed."

            enabled == false ->
                "The local alarm is disabled. Native Clock state was not changed."

            else ->
                "Local alarm updated. Native Clock state was not changed."
        }

        return buildJsonObject {
            put("success", true)
            put("action", "updated")
            put("id", id)
            put("alarm", updated.toJsonObject())
            put("native_scheduling_requested", nativeSchedulingRequested)
            put("native_sync_warning", nativeWarning)
            put("message", "Alarm #$id updated locally.")
        }
    }

    // ------------------------------------------------------------
    // DELETE ALARM
    // ------------------------------------------------------------

    suspend fun executeDeleteAlarm(
        call: FunctionCall,
        repo: AlarmRepository,
    ): JsonObject {
        val id = extractLongArg(call, "id")
            ?: return error("'id' is required and must be an integer.")

        if (id <= 0) return error("'id' must be greater than zero.")

        val deleted = try {
            repo.delete(id)
        } catch (e: Exception) {
            return error("Failed to delete alarm: ${e.message ?: "Database error."}")
        }

        if (!deleted) {
            return error("Alarm with ID #$id was not found or already deleted.")
        }

        return buildJsonObject {
            put("success", true)
            put("action", "deleted")
            put("id", id)
            put("local_deleted", true)
            put("native_deleted", false)
            put(
                "message",
                "Alarm #$id was deleted from local storage. " +
                        "Any previously created Android Clock alarm may still exist."
            )
        }
    }

    // ------------------------------------------------------------
    // SET TIMER
    // ------------------------------------------------------------

    fun executeSetTimer(
        call: FunctionCall,
        context: Context,
    ): JsonObject {
        val lengthSeconds = extractIntArg(call, "length_seconds")
            ?: return error("'length_seconds' is required and must be an integer.")

        if (lengthSeconds <= 0) {
            return error("'length_seconds' must be greater than zero.")
        }

        val label = if (call.args.containsKey("label")) {
            extractStringArg(call, "label")
                ?: return error("'label' must be a string.")
        } else ""

        val skipUi = extractBooleanArg(call, "skip_ui") ?: true

        val triggered = try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, lengthSeconds)

                if (label.isNotBlank()) {
                    putExtra(AlarmClock.EXTRA_MESSAGE, label)
                }

                putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }

        val duration = formatDuration(lengthSeconds)

        return buildJsonObject {
            put("success", triggered)
            put("action", "timer_set")
            put("length_seconds", lengthSeconds)
            put("duration", duration)
            put("label", label)
            put("device_scheduled", triggered)
            put(
                "message",
                if (triggered) {
                    "Timer for $duration" +
                            if (label.isNotBlank()) " ($label) started." else " started."
                } else {
                    "Could not start the timer. No compatible Clock activity was available."
                }
            )
        }
    }

    // ------------------------------------------------------------
    // SHOW CLOCK
    // ------------------------------------------------------------

    fun executeShowAlarms(
        context: Context,
    ): JsonObject {
        val opened = try {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }

        return buildJsonObject {
            put("success", opened)
            put("action", "show_alarms")
            put(
                "message",
                if (opened) {
                    "Clock app opened."
                } else {
                    "Could not open the device Clock app."
                }
            )
        }
    }

    // ------------------------------------------------------------
    // ANDROID CLOCK INTEGRATION
    // ------------------------------------------------------------

    private fun triggerDeviceAlarm(
        context: Context,
        hour: Int,
        minute: Int,
        label: String,
        days: List<Int>,
        vibrate: Boolean,
        skipUi: Boolean,
    ): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_VIBRATE, vibrate)
                putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)

                if (label.isNotBlank()) {
                    putExtra(AlarmClock.EXTRA_MESSAGE, label)
                }

                if (days.isNotEmpty()) {
                    putExtra(
                        AlarmClock.EXTRA_DAYS,
                        ArrayList(days)
                    )
                }

                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    // ------------------------------------------------------------
    // ARGUMENT PARSING
    // ------------------------------------------------------------

    private fun extractIntArg(
        call: FunctionCall,
        key: String,
    ): Int? {
        val value = call.args[key] as? JsonPrimitive ?: return null

        return value.intOrNull
            ?: value.content.toIntOrNull()
    }

    private fun extractLongArg(
        call: FunctionCall,
        key: String,
    ): Long? {
        val value = call.args[key] as? JsonPrimitive ?: return null

        return value.longOrNull
            ?: value.content.toLongOrNull()
    }

    private fun extractBooleanArg(
        call: FunctionCall,
        key: String,
    ): Boolean? {
        val value = call.args[key] as? JsonPrimitive ?: return null

        return value.booleanOrNull
            ?: value.content.toBooleanStrictOrNull()
    }

    private fun extractStringArg(
        call: FunctionCall,
        key: String,
    ): String? {
        val value = call.args[key] as? JsonPrimitive ?: return null

        return if (value.isString) value.content else null
    }

    // ------------------------------------------------------------
    // REPEAT DAYS
    // ------------------------------------------------------------

    private fun parseDaysArg(
        call: FunctionCall,
        key: String,
    ): List<Int>? {
        val element = call.args[key] ?: return emptyList()
        val primitive = element as? JsonPrimitive ?: return null

        if (!primitive.isString) return null

        val text = primitive.content.trim().lowercase(Locale.ROOT)

        if (text.isBlank()) return emptyList()

        val weekdays = listOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
        )

        val weekends = listOf(
            Calendar.SATURDAY,
            Calendar.SUNDAY,
        )

        val everyday = listOf(
            Calendar.SUNDAY,
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
            Calendar.SATURDAY,
        )

        return when (text) {
            "everyday", "daily" -> everyday
            "weekdays" -> weekdays
            "weekends" -> weekends

            else -> {
                val parts = text.split(",")

                if (parts.any { it.isBlank() }) return null

                val parsed = parts.map { part ->
                    val value = part.trim()

                    when {
                        value.toIntOrNull() in 1..7 ->
                            value.toInt()

                        value.startsWith("sun") -> Calendar.SUNDAY
                        value.startsWith("mon") -> Calendar.MONDAY
                        value.startsWith("tue") -> Calendar.TUESDAY
                        value.startsWith("wed") -> Calendar.WEDNESDAY
                        value.startsWith("thu") -> Calendar.THURSDAY
                        value.startsWith("fri") -> Calendar.FRIDAY
                        value.startsWith("sat") -> Calendar.SATURDAY

                        else -> return null
                    }
                }

                parsed.distinct().sorted()
            }
        }
    }

    // ------------------------------------------------------------
    // SERIALIZATION
    // ------------------------------------------------------------

    private fun AlarmItem.toJsonObject(): JsonObject = buildJsonObject {
        put("id", id)
        put("uuid", uuid)
        put("hour", hour)
        put("minute", minute)
        put("formatted_time", formatTime(hour, minute))
        put("label", label)
        put("days", days.toJsonArray())
        put("enabled", enabled)
        put("vibrate", vibrate)
        put("created_at_ms", createdAt)
        put("updated_at_ms", updatedAt)
    }

    private fun List<Int>.toJsonArray() = buildJsonArray {
        forEach { add(it) }
    }

    // ------------------------------------------------------------
    // FORMATTING
    // ------------------------------------------------------------

    private fun formatTime(
        hour: Int,
        minute: Int,
    ): String {
        val hour12 = when (hour) {
            0 -> 12
            in 1..12 -> hour
            else -> hour - 12
        }

        val amPm = if (hour < 12) "AM" else "PM"

        return String.format(
            Locale.getDefault(),
            "%d:%02d %s",
            hour12,
            minute,
            amPm,
        )
    }

    private fun formatDuration(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remainingSeconds = seconds % 60

        return buildList {
            if (hours > 0) add("$hours hr")
            if (minutes > 0) add("$minutes min")
            if (remainingSeconds > 0) add("$remainingSeconds sec")
        }.joinToString(" ").ifBlank { "0 sec" }
    }

    // ------------------------------------------------------------
    // ERROR RESPONSE
    // ------------------------------------------------------------

    private fun error(message: String): JsonObject = buildJsonObject {
        put("success", false)
        put("error", message)
    }
}
