
package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.*

fun timeTool() = ToolDefinition(
    name = "get_current_time",
    description = universalToolDescription(
        purpose = "Returns the current date and time for an IANA timezone; defaults to `Asia/Dhaka`.",
        trigger = "The user asks for the current time/date, a specific timezone is needed, or the available timestamp may be stale.",
        notFor = "Do not call again when the contextual timestamp is sufficient; create date-based items with `create_reminder`, `create_calendar_event`, or `create_todo`.",
        prerequisite = "Provide a valid IANA timezone when requested; otherwise `Asia/Dhaka` is used.",
    ),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("timezone") {
                put("type", "string")
                put(
                    "description",
                    "Optional IANA timezone (for example, Asia/Dhaka). Default: Asia/Dhaka."
                )
                put("default", "Asia/Dhaka")
            }
        }
    },
    parallelizable = true,
)

fun deviceInfoTool() = ToolDefinition(
    name = "get_device_info",
    description = universalToolDescription(
        purpose = "Returns device/Android or battery information reported by the device.",
        trigger = "The user asks for device, Android-version, or battery details.",
        notFor = "Use `control_flashlight`, `control_bluetooth`, or `control_wifi` to change those settings; never infer unavailable battery-health data.",
        prerequisite = "Choose `device`, `battery`, or `all` with `category`; the default is `all`.",
    ),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("category") {
                put("type", "string")
                put(
                    "description",
                    "device = hardware/Android; battery = battery; all = both. Default: all."
                )
                putJsonArray("enum") {
                    add("device")
                    add("battery")
                    add("all")
                }
                put("default", "all")
            }
        }
    },
    parallelizable = true,
)
