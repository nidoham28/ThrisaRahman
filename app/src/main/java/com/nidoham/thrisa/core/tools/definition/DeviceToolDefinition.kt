package com.nidoham.thrisa.core.tools.definition

import kotlinx.serialization.json.*
import com.nidoham.thrisa.core.tools.ToolDefinition

/** Function declarations for time and device-information tools. */

fun timeTool() = ToolDefinition(
    name = "get_current_time",
    description = "Get the current date, time and timezone. " +
            "Use for questions about now, today, deadlines, " +
            "scheduling and time-dependent events. Defaults to Asia/Dhaka.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("timezone") {
                put("type", "string")
                put(
                    "description",
                    "Optional IANA timezone, e.g. Asia/Dhaka. Defaults to Asia/Dhaka."
                )
            }
        }
        putJsonArray("required") {}
    },
)

fun deviceInfoTool() = ToolDefinition(
    name = "get_device_info",
    description = "Get the user's device hardware details and battery status. " +
            "Use when the user asks about their phone model, manufacturer, " +
            "Android version, battery level, charging status, battery health, " +
            "or any device-related information.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("category") {
                put("type", "string")
                put(
                    "description",
                    "Optional category filter: 'device' for hardware info only, " +
                            "'battery' for battery info only, or 'all' for everything. " +
                            "Defaults to all if omitted."
                )
                putJsonArray("enum") {
                    add("device")
                    add("battery")
                    add("all")
                }
            }
        }
        putJsonArray("required") {}
    },
)
