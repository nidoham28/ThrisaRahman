
package com.naira.assistant.core.tools.executor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.naira.assistant.core.tools.CommandExecution.jsonStringArg
import com.naira.assistant.domain.FunctionCall
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale

/**
 * Executes the get_device_info tool.
 *
 * Retrieves Android device information and battery status.
 * The Context is supplied by the application, never by the AI model.
 */
object DeviceToolExecutor {

    private val validCategories = setOf("device", "battery", "all")

    fun executeGetDeviceInfo(
        call: FunctionCall,
        context: Context
    ): JsonObject {
        // An explicit JSON null means "use the schema default", not an error.
        val category = if (call.args.containsKey("category") && call.args["category"] !is JsonNull) {
            jsonStringArg(call, "category")
                ?.trim()
                ?.lowercase(Locale.ROOT)
                ?: return buildJsonObject {
                    put("success", false)
                    put("error", "'category' must be a non-empty string.")
                }
        } else {
            "all"
        }

        if (category !in validCategories) {
            return buildJsonObject {
                put("success", false)
                put(
                    "error",
                    "Invalid category. Allowed values: device, battery, all."
                )
            }
        }

        return buildJsonObject {
            put("success", true)
            put("category", category)

            if (category == "device" || category == "all") {
                putDeviceInfo()
            }

            if (category == "battery" || category == "all") {
                putBatteryInfo(context.applicationContext)
            }
        }
    }

    // ---------------------------------------------------------
    // Device information
    // ---------------------------------------------------------

    private fun JsonObjectBuilder.putDeviceInfo() {
        put("manufacturer", Build.MANUFACTURER.capitalise())
        put("brand", Build.BRAND.capitalise())
        put("model", Build.MODEL)
        put("device", Build.DEVICE)
        put("product", Build.PRODUCT)
        put("hardware", Build.HARDWARE)
        put("board", Build.BOARD)

        put("android_version", Build.VERSION.RELEASE)
        put("sdk_level", Build.VERSION.SDK_INT)
        put("security_patch", Build.VERSION.SECURITY_PATCH)

        put("display_id", Build.DISPLAY)

        put(
            "supported_abis",
            Build.SUPPORTED_ABIS.joinToString(", ")
        )
    }

    // ---------------------------------------------------------
    // Battery information
    // ---------------------------------------------------------

    private fun JsonObjectBuilder.putBatteryInfo(context: Context) {
        val batteryStatus = try {
            context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
        } catch (_: Exception) {
            null
        }

        if (batteryStatus == null) {
            put("battery_available", false)
            put("battery_error", "Unable to read battery status.")
            return
        }

        put("battery_available", true)

        putBatteryLevel(batteryStatus)
        putBatteryStatus(batteryStatus)
        putChargingSource(batteryStatus)
        putBatteryHealth(batteryStatus)
        putBatteryTemperature(batteryStatus)
        putBatteryVoltage(batteryStatus)
        putBatteryTechnology(batteryStatus)
    }

    private fun JsonObjectBuilder.putBatteryLevel(intent: Intent) {
        val level = intent.getIntExtra(
            BatteryManager.EXTRA_LEVEL,
            -1
        )

        val scale = intent.getIntExtra(
            BatteryManager.EXTRA_SCALE,
            -1
        )

        if (level >= 0 && scale > 0) {
            val percentage = (level * 100f / scale).toInt()
                .coerceIn(0, 100)

            put("battery_percentage", percentage)
        }
    }

    private fun JsonObjectBuilder.putBatteryStatus(intent: Intent) {
        val status = intent.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        )

        val statusText = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
            else -> "Unknown"
        }

        put("battery_status", statusText)

        put(
            "is_charging",
            status == BatteryManager.BATTERY_STATUS_CHARGING
        )
    }

    private fun JsonObjectBuilder.putChargingSource(intent: Intent) {
        val plugged = intent.getIntExtra(
            BatteryManager.EXTRA_PLUGGED,
            -1
        )

        val source = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            0 -> "Not plugged in"
            else -> "Unknown"
        }

        put("charging_source", source)
    }

    private fun JsonObjectBuilder.putBatteryHealth(intent: Intent) {
        val health = intent.getIntExtra(
            BatteryManager.EXTRA_HEALTH,
            BatteryManager.BATTERY_HEALTH_UNKNOWN
        )

        val healthText = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE ->
                "Unspecified failure"
            else -> "Unknown"
        }

        put("battery_health", healthText)

        // This is Android's reported battery health state,
        // NOT battery capacity or long-term degradation.
    }

    private fun JsonObjectBuilder.putBatteryTemperature(intent: Intent) {
        val temperature = intent.getIntExtra(
            BatteryManager.EXTRA_TEMPERATURE,
            -1
        )

        if (temperature >= 0) {
            put(
                "battery_temperature_celsius",
                temperature / 10.0
            )
        }
    }

    private fun JsonObjectBuilder.putBatteryVoltage(intent: Intent) {
        val voltage = intent.getIntExtra(
            BatteryManager.EXTRA_VOLTAGE,
            -1
        )

        if (voltage > 0) {
            put(
                "battery_voltage_volts",
                voltage / 1000.0
            )
        }
    }

    private fun JsonObjectBuilder.putBatteryTechnology(intent: Intent) {
        val technology = intent.getStringExtra(
            BatteryManager.EXTRA_TECHNOLOGY
        )

        if (!technology.isNullOrBlank()) {
            put("battery_technology", technology)
        }
    }

    // ---------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------

    private fun String.capitalise(): String =
        replaceFirstChar {
            if (it.isLowerCase()) {
                it.titlecase(Locale.ROOT)
            } else {
                it.toString()
            }
        }
}
