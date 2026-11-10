package com.naira.assistant.core.tools.executor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.naira.assistant.core.tools.executor.BuiltinToolExecutor.jsonStringArg
import com.naira.assistant.domain.FunctionCall
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale

/**
 * Implementation of the `get_device_info` tool.
 *
 * Collects the user's device hardware details and current battery status so
 * the AI assistant can answer questions like "What phone am I using?",
 * "How much battery do I have left?", or "Is my phone charging?".
 *
 * The [context] is the Android application context required to read the
 * battery sticky broadcast. It is injected once when the tool executor is
 * built, NOT supplied by the model.
 */
object DeviceToolExecutor {

    /**
     * Executes the `get_device_info` tool.
     *
     * @param call   the function call from Gemini (currently has no args).
     * @param context Android application context for battery info.
     * @return a [JsonObject] with device and battery fields.
     */
    fun executeGetDeviceInfo(call: FunctionCall, context: Context): JsonObject {
        val category = jsonStringArg(call, "category")

        return buildJsonObject {
            if (category == null || category == "device" || category == "all") {
                putDeviceInfo()
            }
            if (category == null || category == "battery" || category == "all") {
                putBatteryInfo(context)
            }
        }
    }

    // ------------------------------------------------------------------
    // Device info
    // ------------------------------------------------------------------

    private fun JsonObjectBuilderScope.putDeviceInfo() {
        put("manufacturer", Build.MANUFACTURER.capitalise())
        put("brand", Build.BRAND.capitalise())
        put("model", Build.MODEL)
        put("device", Build.DEVICE)
        put("product", Build.PRODUCT)
        put("hardware", Build.HARDWARE)
        put("android_version", Build.VERSION.RELEASE)
        put("sdk_level", Build.VERSION.SDK_INT)
        put("security_patch", Build.VERSION.SECURITY_PATCH)
        put("board", Build.BOARD)
        put("display_id", Build.DISPLAY)
        put("build_fingerprint", Build.FINGERPRINT)
        put("supported_abis", Build.SUPPORTED_ABIS.joinToString())
    }

    // ------------------------------------------------------------------
    // Battery info
    // ------------------------------------------------------------------

    private fun JsonObjectBuilderScope.putBatteryInfo(context: Context) {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let {
            context.applicationContext.registerReceiver(null, it)
        }

        if (batteryStatus == null) {
            put("battery_error", "Unable to read battery status.")
            return
        }

        // Battery level as a percentage.
        val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val batteryPct = if (level >= 0 && scale > 0) {
            (level * 100 / scale)
        } else {
            -1
        }
        put("battery_percentage", batteryPct)

        // Charging status.
        val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        put("battery_status", when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
            else -> "Unknown"
        })

        // Charging source (plugged type).
        val plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        put("charging_source", when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            0 -> "Not plugged in"
            else -> "Unknown"
        })

        // Battery health.
        val health = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
        put("battery_health", when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified failure"
            else -> "Unknown"
        })

        // Battery temperature in °C (the extra is in tenths of a degree).
        val tempRaw = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        if (tempRaw > 0) {
            put("battery_temperature_celsius", tempRaw / 10.0)
        }

        // Battery voltage in volts (the extra is in millivolts).
        val voltageRaw = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        if (voltageRaw > 0) {
            put("battery_voltage_volts", voltageRaw / 1000.0)
        }

        // Battery technology (e.g. "Li-ion").
        batteryStatus.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.let {
            put("battery_technology", it)
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun String.capitalise(): String =
        replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }

    /**
     * Scoping alias so `put(...)` calls compile inside [buildJsonObject].
     * [kotlinx.serialization.json.JsonObjectBuilder] is the actual receiver
     * of [buildJsonObject]'s lambda, so this typealias just makes the
     * private extension functions' receiver clearer.
     */
    private typealias JsonObjectBuilderScope = JsonObjectBuilder
}
