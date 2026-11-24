
package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.*

fun timeTool() = ToolDefinition(
    name = "get_current_time",
    description = """
        Get a fresh date/time in a timezone. Use the timestamp already in context for ordinary
        date calculations; call this only when the user asks for the current time, the timestamp
        may be stale, or another timezone is needed. Default timezone: Asia/Dhaka.
    """.trimIndent(),
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
    }
)

fun deviceInfoTool() = ToolDefinition(
    name = "get_device_info",
    description = """
        Get device/Android or battery details when requested. Select only the needed category;
        report returned values and disclose unavailable fields. Never infer battery health.
    """.trimIndent(),
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
    }
)
