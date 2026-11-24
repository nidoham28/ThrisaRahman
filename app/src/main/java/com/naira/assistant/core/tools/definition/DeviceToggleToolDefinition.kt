
package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.*

/**
 * Function declarations for hardware feature controls:
 * flashlight, Bluetooth, and Wi-Fi.
 */

private fun hardwareControlTool(
    name: String,
    feature: String,
    description: String
) = ToolDefinition(
    name = name,
    description = description.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")

        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("description", "Action to perform on $feature.")

                putJsonArray("enum") {
                    add("turn_on")
                    add("turn_off")
                    add("check_status")
                }
            }
        }

        putJsonArray("required") {
            add("action")
        }
    }
)

fun flashlightControlTool() = hardwareControlTool(
    name = "control_flashlight",
    feature = "the flashlight",
    description = """
        Control or check the camera torch only, not screen brightness or photo capture.
        Report only the confirmed state or the actual unsupported/permission error.
    """
)

fun bluetoothControlTool() = hardwareControlTool(
    name = "control_bluetooth",
    feature = "Bluetooth",
    description = """
        Control or check Bluetooth. Android 12+ restricts access; Android 13+ apps generally
        cannot toggle it directly. If system settings are opened, tell the user to confirm there
        and report state only after verification.
    """
)

fun wifiControlTool() = hardwareControlTool(
    name = "control_wifi",
    feature = "Wi-Fi",
    description = """
        Control or check Wi-Fi. Android 10+ apps generally cannot toggle it directly; use system
        settings when required and tell the user to finish there. Report only verified state or
        the actual permission/unsupported error.
    """
)
