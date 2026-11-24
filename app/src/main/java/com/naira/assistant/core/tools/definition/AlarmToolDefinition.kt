
package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

fun createAlarmTool() = ToolDefinition(
    name = "create_alarm",
    description = universalToolDescription(
        purpose = "Creates a clock-time alarm, optionally repeating on selected days.",
        trigger = "The user asks to set an alarm for a specific local clock time.",
        notFor = "Use `create_reminder` for date-specific alerts and `set_timer` for countdowns.",
        prerequisite = "Provide local time as 24-hour `hour` (0-23) and `minute` (0-59); set repeat days only when requested.",
    ),
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
    description = universalToolDescription(
        purpose = "Lists saved alarms and their IDs, optionally filtering to enabled alarms.",
        trigger = "The user asks to see saved alarms or their schedule.",
        notFor = "Use `show_alarms` to open the Clock UI or `get_alarm` for one alarm's details.",
        prerequisite = "Optionally provide the enabled filter and result limit; use returned IDs for later operations.",
    ),
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
    },
    parallelizable = true,
)

fun getAlarmTool() = ToolDefinition(
    name = "get_alarm",
    description = universalToolDescription(
        purpose = "Returns details for one alarm by ID.",
        trigger = "The user asks for information about a specific alarm.",
        notFor = "Use `list_alarms` to see all alarms or `show_alarms` to open the Clock UI.",
        prerequisite = "An exact alarm `id` is required; find an unknown ID with `list_alarms` first.",
    ),
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
    },
    parallelizable = true,
)

fun updateAlarmTool() = ToolDefinition(
    name = "update_alarm",
    description = universalToolDescription(
        purpose = "Updates only specified fields of an existing alarm and preserves the rest.",
        trigger = "The user asks to change an alarm's time, label, repeat schedule, enabled state, or vibration.",
        notFor = "Use `create_alarm` to create one; set `enabled=false` to disable rather than delete.",
        prerequisite = "An exact `id` is required; find an unknown ID with `list_alarms` and include only requested changes.",
    ),
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
    description = universalToolDescription(
        purpose = "Permanently deletes one identified alarm.",
        trigger = "The user explicitly asks to delete an alarm.",
        notFor = "Use `update_alarm` with `enabled=false` to disable it; never infer the target from its time or label.",
        prerequisite = "An exact `id` is required; identify an unknown alarm with `list_alarms` first.",
    ),
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
    description = universalToolDescription(
        purpose = "Starts a countdown timer for a specified duration.",
        trigger = "The user asks to set a timer for a duration.",
        notFor = "Use `create_alarm` for a clock-time alarm and `create_reminder` for a date-specific alert.",
        prerequisite = "Convert the requested duration without changing it and provide positive total `length_seconds`.",
    ),
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
    description = universalToolDescription(
        purpose = "Opens the device Clock app to display alarms and timers.",
        trigger = "The user asks to view alarms/timers in the Clock app.",
        notFor = "Use `list_alarms` to retrieve alarm data without opening the app.",
        prerequisite = "No arguments are needed; the request to open Clock must be explicit.",
    ),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {}
        putJsonArray("required") {}
    }
)
