package com.nidoham.thrisa.core.tools.executor

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.nidoham.thrisa.core.database.alarm.AlarmItem
import com.nidoham.thrisa.core.database.alarm.AlarmRepository
import com.nidoham.thrisa.core.tools.executor.BuiltinToolExecutor.jsonStringArg
import com.nidoham.thrisa.domain.FunctionCall
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.util.Calendar

object AlarmToolExecutor {

    /**
     * Executes `create_alarm` — schedules alarm in device clock app and saves it in database.
     */
    suspend fun executeCreateAlarm(
        call: FunctionCall,
        context: Context,
        repo: AlarmRepository,
    ): JsonObject {
        val hour = extractIntArg(call, "hour")
            ?: return error("Argument 'hour' (0-23) is required.")
        val minute = extractIntArg(call, "minute")
            ?: return error("Argument 'minute' (0-59) is required.")

        if (hour !in 0..23) return error("'hour' must be between 0 and 23.")
        if (minute !in 0..59) return error("'minute' must be between 0 and 59.")

        val label = jsonStringArg(call, "label") ?: ""
        val days = parseDaysArg(call, "days")
        val vibrate = extractBooleanArg(call, "vibrate") ?: true
        val skipUi = extractBooleanArg(call, "skip_ui") ?: true

        // 1. Persist alarm in local repository
        val item = repo.insert(
            hour = hour,
            minute = minute,
            label = label,
            days = days,
            enabled = true,
            vibrate = vibrate,
        )

        // 2. Trigger native Android Clock app intent
        val deviceScheduled = triggerDeviceAlarm(context, hour, minute, label, days, vibrate, skipUi)

        val formattedTime = formatTime(hour, minute)

        return buildJsonObject {
            put("success", true)
            put("action", "created")
            put("id", item.id)
            put("time", formattedTime)
            put("hour", hour)
            put("minute", minute)
            put("label", label)
            put("device_intent_fired", deviceScheduled)
            put("alarm", item.toJsonObject())
            put("message", "Alarm set for $formattedTime" + if (label.isNotBlank()) " ('$label')." else ".")
        }
    }

    /**
     * Executes `list_alarms` — lists all tracked alarms.
     */
    suspend fun executeListAlarms(
        call: FunctionCall,
        repo: AlarmRepository,
    ): JsonObject {
        val enabledOnly = extractBooleanArg(call, "enabled_only")
        val limit = (extractIntArg(call, "limit") ?: 20).coerceIn(1, 100)

        val alarms = repo.getAll(enabledOnly = enabledOnly, limit = limit)
        val total = repo.count()

        return buildJsonObject {
            put("success", true)
            put("action", "list_alarms")
            put("total_stored", total)
            put("count", alarms.size)
            put("alarms", buildJsonArray { alarms.forEach { add(it.toJsonObject()) } })
        }
    }

    /**
     * Executes `get_alarm` — fetch single alarm details by ID.
     */
    suspend fun executeGetAlarm(
        call: FunctionCall,
        repo: AlarmRepository,
    ): JsonObject {
        val id = extractLongArg(call, "id")
            ?: return error("Argument 'id' is required.")

        val alarm = repo.getById(id)
            ?: return error("Alarm with ID #$id was not found.")

        return buildJsonObject {
            put("success", true)
            put("alarm", alarm.toJsonObject())
        }
    }

    /**
     * Executes `update_alarm` — updates alarm time, label, days, or enabled status.
     */
    suspend fun executeUpdateAlarm(
        call: FunctionCall,
        context: Context,
        repo: AlarmRepository,
    ): JsonObject {
        val id = extractLongArg(call, "id")
            ?: return error("Argument 'id' is required.")

        val hour = extractIntArg(call, "hour")
        val minute = extractIntArg(call, "minute")
        val label = jsonStringArg(call, "label")
        val days = if (call.args.containsKey("days")) parseDaysArg(call, "days") else null
        val enabled = if (call.args.containsKey("enabled")) extractBooleanArg(call, "enabled") else null
        val vibrate = if (call.args.containsKey("vibrate")) extractBooleanArg(call, "vibrate") else null

        val updated = repo.update(
            id = id,
            hour = hour,
            minute = minute,
            label = label,
            days = days,
            enabled = enabled,
            vibrate = vibrate,
        ) ?: return error("Alarm with ID #$id was not found.")

        // If enabled and updated time/label, re-trigger device alarm intent
        if (updated.enabled && (hour != null || minute != null || label != null)) {
            triggerDeviceAlarm(context, updated.hour, updated.minute, updated.label, updated.days, updated.vibrate, skipUi = true)
        }

        return buildJsonObject {
            put("success", true)
            put("action", "updated")
            put("id", id)
            put("alarm", updated.toJsonObject())
            put("message", "Alarm #$id updated successfully.")
        }
    }

    /**
     * Executes `delete_alarm` — deletes alarm from tracking.
     */
    suspend fun executeDeleteAlarm(
        call: FunctionCall,
        repo: AlarmRepository,
    ): JsonObject {
        val id = extractLongArg(call, "id")
            ?: return error("Argument 'id' is required.")

        val deleted = repo.delete(id)
        return if (deleted) {
            buildJsonObject {
                put("success", true)
                put("action", "deleted")
                put("id", id)
                put("message", "Alarm #$id was deleted successfully.")
            }
        } else {
            error("Alarm with ID #$id was not found or already deleted.")
        }
    }

    /**
     * Executes `set_timer` — sets countdown timer using Android AlarmClock API.
     */
    fun executeSetTimer(
        call: FunctionCall,
        context: Context,
    ): JsonObject {
        val lengthSeconds = extractIntArg(call, "length_seconds")
            ?: return error("Argument 'length_seconds' is required.")

        if (lengthSeconds <= 0) return error("'length_seconds' must be greater than 0.")

        val label = jsonStringArg(call, "label") ?: ""
        val skipUi = extractBooleanArg(call, "skip_ui") ?: true

        val triggered = try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, lengthSeconds)
                if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Throwable) {
            false
        }

        val minutes = lengthSeconds / 60
        val seconds = lengthSeconds % 60
        val durationText = buildString {
            if (minutes > 0) append("$minutes min ")
            if (seconds > 0 || minutes == 0) append("$seconds sec")
        }.trim()

        return buildJsonObject {
            put("success", triggered)
            put("action", "timer_set")
            put("length_seconds", lengthSeconds)
            put("duration", durationText)
            put("label", label)
            put("message", "Timer for $durationText" + if (label.isNotBlank()) " ('$label')" else "" + " started.")
        }
    }

    /**
     * Executes `show_alarms` — opens device clock app to alarms view.
     */
    fun executeShowAlarms(
        context: Context,
    ): JsonObject {
        val opened = try {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Throwable) {
            false
        }

        return buildJsonObject {
            put("success", opened)
            put("action", "show_alarms")
            put("message", if (opened) "Clock app opened to alarms screen." else "Could not open clock app.")
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

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
                if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
                if (days.isNotEmpty()) {
                    putExtra(AlarmClock.EXTRA_DAYS, ArrayList(days))
                }
                putExtra(AlarmClock.EXTRA_VIBRATE, vibrate)
                putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun formatTime(hour: Int, minute: Int): String {
        val amPm = if (hour < 12) "AM" else "PM"
        val hour12 = when (hour) {
            0 -> 12
            in 1..12 -> hour
            else -> hour - 12
        }
        val minStr = if (minute < 10) "0$minute" else "$minute"
        return "$hour12:$minStr $amPm"
    }

    fun parseDaysArg(call: FunctionCall, key: String): List<Int> {
        val element = call.args[key] ?: return emptyList()
        val text = element.jsonPrimitive.content.trim().lowercase()
        return when {
            text == "everyday" || text == "daily" ->
                listOf(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY)
            text == "weekdays" ->
                listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
            text == "weekends" ->
                listOf(Calendar.SATURDAY, Calendar.SUNDAY)
            else -> {
                text.split(",").mapNotNull { part ->
                    val clean = part.trim()
                    when {
                        clean.toIntOrNull() in 1..7 -> clean.toInt()
                        clean.startsWith("sun") -> Calendar.SUNDAY
                        clean.startsWith("mon") -> Calendar.MONDAY
                        clean.startsWith("tue") -> Calendar.TUESDAY
                        clean.startsWith("wed") -> Calendar.WEDNESDAY
                        clean.startsWith("thu") -> Calendar.THURSDAY
                        clean.startsWith("fri") -> Calendar.FRIDAY
                        clean.startsWith("sat") -> Calendar.SATURDAY
                        else -> null
                    }
                }.distinct().sorted()
            }
        }
    }

    private fun AlarmItem.toJsonObject(): JsonObject = buildJsonObject {
        put("id", id)
        put("uuid", uuid)
        put("hour", hour)
        put("minute", minute)
        put("formatted_time", formatTime(hour, minute))
        put("label", label)
        put("days", buildJsonArray { days.forEach { add(JsonPrimitive(it)) } })
        put("enabled", enabled)
        put("vibrate", vibrate)
        put("created_at_ms", createdAt)
        put("updated_at_ms", updatedAt)
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
