
package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

fun createAlarmTool() = ToolDefinition(
    name = "create_alarm",
    description = """
        Set a clock-time alarm. Convert the requested local time to 24-hour hour/minute;
        add repeat days only when requested. This tool cannot select a calendar date;
        use a reminder for date-specific alerts.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("hour") {
                put("type", "integer")
                put("description", "Required. Hour in 24-hour format (0-23).")
                put("minimum", 0)
                put("maximum", 23)
            }
            putJsonObject("minute") {
                put("type", "integer")
                put("description", "Required. Minute (0-59).")
                put("minimum", 0)
                put("maximum", 59)
            }
            putJsonObject("label") {
                put("type", "string")
                put("description", "Optional descriptive alarm label.")
            }
            putJsonObject("days") {
                put("type", "string")
                put(
                    "description",
                    "Optional repeat schedule: everyday, weekdays, weekends, or comma-separated " +
                        "day numbers (1=Sunday through 7=Saturday)."
                )
            }
            putJsonObject("vibrate") {
                put("type", "boolean")
                put("description", "Enable vibration. Defaults to true.")
                put("default", true)
            }
            putJsonObject("skip_ui") {
                put("type", "boolean")
                put(
                    "description",
                    "Set in the background without opening Clock. Defaults to true."
                )
                put("default", true)
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
    description = "List saved alarms; filter to enabled alarms when requested. Use returned IDs " +
        "for later operations.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("enabled_only") {
                put("type", "boolean")
                put("description", "Return only enabled alarms. Defaults to false.")
                put("default", false)
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "Maximum results. Default: 20.")
                put("minimum", 1)
                put("maximum", 100)
                put("default", 20)
            }
        }
        putJsonArray("required") {}
    }
)

fun getAlarmTool() = ToolDefinition(
    name = "get_alarm",
    description = "Get one alarm by ID. If unknown, find it with list_alarms first.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("id") {
                put("type", "integer")
                put("description", "Existing alarm ID.")
                put("minimum", 1)
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
        Update only specified fields of an existing alarm; preserve the rest.
        Find an unknown ID with list_alarms. Do not create a replacement alarm.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("id") {
                put("type", "integer")
                put("description", "Existing alarm ID.")
                put("minimum", 1)
            }
            putJsonObject("hour") {
                put("type", "integer")
                put("description", "New hour in 24-hour format (0-23).")
                put("minimum", 0)
                put("maximum", 23)
            }
            putJsonObject("minute") {
                put("type", "integer")
                put("description", "New minute (0-59).")
                put("minimum", 0)
                put("maximum", 59)
            }
            putJsonObject("label") {
                put("type", "string")
                put("description", "New alarm label.")
            }
            putJsonObject("days") {
                put("type", "string")
                put(
                    "description",
                    "New schedule: everyday, weekdays, weekends, or comma-separated day numbers " +
                        "(1=Sunday through 7=Saturday)."
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
        Permanently delete only the identified alarm. Find an unknown ID with list_alarms;
        do not infer a target from its time or label, or use this to disable it.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("id") {
                put("type", "integer")
                put("description", "ID of the alarm to delete.")
                put("minimum", 1)
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
        Start a countdown timer, not a clock-time alarm. Convert the requested duration to
        positive total seconds; do not change it.
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
                put("minimum", 1)
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
                put("default", true)
            }
        }
        putJsonArray("required") {
            add("length_seconds")
        }
    }
)

fun showAlarmsTool() = ToolDefinition(
    name = "show_alarms",
    description = "Open Clock to show alarms and timers; use list_alarms for data without opening the app.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {}
        putJsonArray("required") {}
    }
)
