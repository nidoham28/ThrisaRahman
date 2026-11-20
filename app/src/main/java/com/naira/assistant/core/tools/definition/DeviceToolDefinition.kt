
package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.*

/**
 * Function declarations for time and device-information tools.
 */

fun timeTool() = ToolDefinition(
    name = "get_current_time",
    description = """
        Get the current date, local time, UTC offset, and timezone.

        Use this tool whenever an answer depends on the current date or time,
        including questions about today, tomorrow, deadlines, scheduling,
        time differences, reminders, alarms, and time-sensitive events.

        Timezone behavior:
        - If the user specifies a timezone, use that timezone.
        - If omitted, use Asia/Dhaka as the default.
        - Use the returned timestamp and timezone as the source of truth.
        - Never guess the current time or calculate it from conversation context.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("timezone") {
                put("type", "string")
                put(
                    "description",
                    "Optional IANA timezone identifier, such as Asia/Dhaka, " +
                            "Asia/Kolkata, Europe/London, or America/New_York. " +
                            "Defaults to Asia/Dhaka."
                )
            }
        }
    }
)

fun deviceInfoTool() = ToolDefinition(
    name = "get_device_info",
    description = """
        Retrieve information about the user's current Android device.

        Use this tool when the user asks about:
        - Device model, manufacturer, brand, or hardware information.
        - Android version, SDK version, or operating-system information.
        - Battery percentage, charging state, or battery health.

        Category selection:
        - device: Hardware and Android system information only.
        - battery: Battery information only.
        - all: Both device and battery information.
        - If the category is omitted, retrieve all available information.

        Important:
        - Report only information returned by the tool.
        - Never invent device specifications or battery details.
        - If a field is unavailable or unsupported, clearly say so.
        - Do not claim to know battery health unless the device actually
          provides a reliable battery-health value.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("category") {
                put("type", "string")
                put(
                    "description",
                    "Information category to retrieve. " +
                            "'device' = hardware and Android information, " +
                            "'battery' = battery information, " +
                            "'all' = both. Defaults to 'all'."
                )
                putJsonArray("enum") {
                    add("device")
                    add("battery")
                    add("all")
                }
            }
        }
    }
)
