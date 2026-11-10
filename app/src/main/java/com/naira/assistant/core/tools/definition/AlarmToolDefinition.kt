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
        Set and schedule an alarm on the user's Android device.
        Use when the user says 'set an alarm for 7:00 AM', 'wake me up at 6:30', 
        'set alarm for 8 o'clock tomorrow', etc.
        Triggers the device's native clock alarm and tracks it locally.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("hour") {
                put("type", "integer")
                put("description", "Hour of the day in 24-hour format (0 to 23).")
            }
            putJsonObject("minute") {
                put("type", "integer")
                put("description", "Minute of the hour (0 to 59).")
            }
            putJsonObject("label") {
                put("type", "string")
                put(
                    "description",
                    "Optional alarm label or message (e.g. 'Morning wake up', 'Take medication', 'Workout')."
                )
            }
            putJsonObject("days") {
                put("type", "string")
                put(
                    "description",
                    "Optional repeating days. Comma-separated day numbers (1=Sunday, 2=Monday, ..., 7=Saturday), " +
                            "or keywords like 'everyday', 'weekdays', 'weekends'."
                )
            }
            putJsonObject("vibrate") {
                put("type", "boolean")
                put("description", "Whether the alarm should vibrate (default true).")
            }
            putJsonObject("skip_ui") {
                put("type", "boolean")
                put(
                    "description",
                    "Whether to set the alarm in the background without opening the clock app (default true)."
                )
            }
        }
        putJsonArray("required") {
            add("hour")
            add("minute")
        }
    },
)

fun listAlarmsTool() = ToolDefinition(
    name = "list_alarms",
    description = """
        List all saved or scheduled alarms on the device.
        Use when the user asks 'what alarms do I have?', 'show my alarms', 
        'is my 7 AM alarm on?', etc.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("enabled_only") {
                put("type", "boolean")
                put("description", "Filter only enabled/active alarms.")
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "Maximum number of alarms to return (default 20).")
            }
        }
        putJsonArray("required") {}
    },
)

fun getAlarmTool() = ToolDefinition(
    name = "get_alarm",
    description = """
        Fetch details of a specific alarm by its numeric ID.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("id") {
                put("type", "integer")
                put("description", "Numeric ID of the alarm.")
            }
        }
        putJsonArray("required") { add("id") }
    },
)

fun updateAlarmTool() = ToolDefinition(
    name = "update_alarm",
    description = """
        Update an existing alarm's time, label, repeat days, or active state by its numeric ID.
        Call `list_alarms` first if you don't know the alarm ID.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("id") {
                put("type", "integer")
                put("description", "Numeric ID of the alarm to update.")
            }
            putJsonObject("hour") {
                put("type", "integer")
                put("description", "New hour in 24-hour format (0 to 23).")
            }
            putJsonObject("minute") {
                put("type", "integer")
                put("description", "New minute (0 to 59).")
            }
            putJsonObject("label") {
                put("type", "string")
                put("description", "New label for the alarm.")
            }
            putJsonObject("days") {
                put("type", "string")
                put("description", "New repeat days (e.g. 'weekdays', 'everyday', '1,2,3').")
            }
            putJsonObject("enabled") {
                put("type", "boolean")
                put("description", "Enable (true) or disable (false) the alarm.")
            }
            putJsonObject("vibrate") {
                put("type", "boolean")
                put("description", "Enable or disable vibration.")
            }
        }
        putJsonArray("required") { add("id") }
    },
)

fun deleteAlarmTool() = ToolDefinition(
    name = "delete_alarm",
    description = """
        Permanently delete an alarm by its numeric ID.
        Call `list_alarms` first if you don't know the ID.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("id") {
                put("type", "integer")
                put("description", "Numeric ID of the alarm to delete.")
            }
        }
        putJsonArray("required") { add("id") }
    },
)

fun setTimerTool() = ToolDefinition(
    name = "set_timer",
    description = """
        Set and start a countdown timer on the user's Android device.
        Use when the user says 'set a timer for 10 minutes', 'timer for 30 seconds', 'start 5 min timer', etc.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("length_seconds") {
                put("type", "integer")
                put("description", "Length of the timer in seconds (e.g. 300 for 5 minutes).")
            }
            putJsonObject("label") {
                put("type", "string")
                put(
                    "description",
                    "Optional label for the timer (e.g. 'Tea', 'Boil eggs', 'Study')."
                )
            }
            putJsonObject("skip_ui") {
                put("type", "boolean")
                put(
                    "description",
                    "Whether to start the timer in background without opening the clock app (default true)."
                )
            }
        }
        putJsonArray("required") { add("length_seconds") }
    },
)

fun showAlarmsTool() = ToolDefinition(
    name = "show_alarms",
    description = """
        Open the device's native clock application to view alarms and timers.
        Use when the user asks to open their clock app or see their alarms on screen.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {}
        putJsonArray("required") {}
    },
)
