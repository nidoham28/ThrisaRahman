
package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Alarm and timer tool definitions.
 *
 * These tools interact with the Android device's native Clock app
 * and maintain locally tracked alarm information.
 */

fun createAlarmTool() = ToolDefinition(
    name = "create_alarm",
    description = """
        Create and schedule a new alarm on the user's Android device.

        Use for requests such as setting an alarm, waking up at a specific
        time, or creating a recurring alarm.

        Rules:
        - Convert the requested time to 24-hour format.
        - Always provide hour and minute.
        - Use days only when repetition is requested.
        - Use an empty or omitted label when no label is specified.
        - Do not assume recurring days unless explicitly requested.
        - Respect AM/PM and relative dates when determining the time.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("hour") {
                put("type", "integer")
                put("description", "Required. Hour in 24-hour format (0-23).")
            }
            putJsonObject("minute") {
                put("type", "integer")
                put("description", "Required. Minute (0-59).")
            }
            putJsonObject("label") {
                put("type", "string")
                put("description", "Optional descriptive alarm label.")
            }
            putJsonObject("days") {
                put("type", "string")
                put(
                    "description",
                    "Optional repeat schedule: 'everyday', 'weekdays', " +
                            "'weekends', or comma-separated day numbers " +
                            "(1=Sunday, 2=Monday, ..., 7=Saturday)."
                )
            }
            putJsonObject("vibrate") {
                put("type", "boolean")
                put("description", "Enable vibration. Defaults to true.")
            }
            putJsonObject("skip_ui") {
                put("type", "boolean")
                put(
                    "description",
                    "Set in the background without opening Clock. Defaults to true."
                )
            }
        }
        putJsonArray("required") {
            add("hour")
            add("minute")
        }
    }
)

fun listAlarmsTool() = ToolDefinition(
    name = "list_alarms",
    description = """
        Retrieve alarms saved on the user's Android device.

        Use when the user asks to see, check, find, or identify their alarms.

        Rules:
        - Use enabled_only=true when only active alarms are requested.
        - Use the returned alarm IDs for subsequent operations.
        - Do not assume an alarm exists without checking the results.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("enabled_only") {
                put("type", "boolean")
                put("description", "Return only enabled alarms. Defaults to false.")
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "Maximum number of results. Defaults to 20.")
            }
        }
        putJsonArray("required") {}
    }
)

fun getAlarmTool() = ToolDefinition(
    name = "get_alarm",
    description = """
        Retrieve complete details of a specific alarm using its numeric ID.

        Use when the user asks about a particular alarm and its ID is known.
        If the ID is unknown, call list_alarms first.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("id") {
                put("type", "integer")
                put("description", "Required. Existing alarm ID.")
            }
        }
        putJsonArray("required") {
            add("id")
        }
    }
)

fun updateAlarmTool() = ToolDefinition(
    name = "update_alarm",
    description = """
        Modify an existing alarm on the user's Android device.

        Supported changes:
        - Alarm time (hour and minute).
        - Label.
        - Repeating days.
        - Enabled or disabled state.
        - Vibration.

        Rules:
        - Always provide the target alarm ID.
        - Only include fields the user wants to change.
        - Do not overwrite unspecified settings.
        - If the alarm ID is unknown, call list_alarms first.
        - Never create a new alarm when the user requests an update.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("id") {
                put("type", "integer")
                put("description", "Required. ID of the existing alarm.")
            }
            putJsonObject("hour") {
                put("type", "integer")
                put("description", "New hour in 24-hour format (0-23).")
            }
            putJsonObject("minute") {
                put("type", "integer")
                put("description", "New minute (0-59).")
            }
            putJsonObject("label") {
                put("type", "string")
                put("description", "New alarm label.")
            }
            putJsonObject("days") {
                put("type", "string")
                put(
                    "description",
                    "New repeat schedule: 'everyday', 'weekdays', " +
                            "'weekends', or comma-separated day numbers."
                )
            }
            putJsonObject("enabled") {
                put("type", "boolean")
                put("description", "Enable or disable the alarm.")
            }
            putJsonObject("vibrate") {
                put("type", "boolean")
                put("description", "Enable or disable alarm vibration.")
            }
        }
        putJsonArray("required") {
            add("id")
        }
    }
)

fun deleteAlarmTool() = ToolDefinition(
    name = "delete_alarm",
    description = """
        Permanently delete an existing alarm from the user's Android device.

        Rules:
        - Requires the exact alarm ID.
        - If the ID is unknown, call list_alarms first.
        - Never delete an alarm based on an ambiguous time or label.
        - Do not use this tool to temporarily disable an alarm.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("id") {
                put("type", "integer")
                put("description", "Required. ID of the alarm to delete.")
            }
        }
        putJsonArray("required") {
            add("id")
        }
    }
)

fun setTimerTool() = ToolDefinition(
    name = "set_timer",
    description = """
        Create and start a countdown timer on the user's Android device.

        Use for requests involving a duration, such as minutes, seconds,
        or hours.

        Rules:
        - Convert the requested duration to total seconds.
        - Always provide length_seconds.
        - Use a label only when specified or clearly implied.
        - Do not use this tool for alarms tied to a clock time.
        - Do not silently change the requested duration.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("length_seconds") {
                put("type", "integer")
                put(
                    "description",
                    "Required. Countdown duration in seconds. Must be greater than zero."
                )
            }
            putJsonObject("label") {
                put("type", "string")
                put("description", "Optional descriptive timer label.")
            }
            putJsonObject("skip_ui") {
                put("type", "boolean")
                put(
                    "description",
                    "Start in the background without opening Clock. Defaults to true."
                )
            }
        }
        putJsonArray("required") {
            add("length_seconds")
        }
    }
)

fun showAlarmsTool() = ToolDefinition(
    name = "show_alarms",
    description = """
        Open the Android Clock app and display the user's alarms and timers.

        Use when the user explicitly asks to open the Clock app,
        view alarms on screen, or access their clock interface.

        Do not use this tool merely to retrieve alarm information.
        Use list_alarms for that instead.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {}
        putJsonArray("required") {}
    }
)
