
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
    description = universalToolDescription(
        purpose = "Turns the camera torch on/off or checks its current state.",
        trigger = "The user explicitly asks to turn the flashlight on/off or check its status.",
        notFor = "Do not use for screen brightness or photo capture; this tool only controls the camera torch.",
        prerequisite = "`action` must be `turn_on`, `turn_off`, or `check_status`; report permission or hardware errors.",
    )
)

fun bluetoothControlTool() = hardwareControlTool(
    name = "control_bluetooth",
    feature = "Bluetooth",
    description = universalToolDescription(
        purpose = "Controls Bluetooth or checks its current state.",
        trigger = "The user explicitly asks to turn Bluetooth on/off or check its status.",
        notFor = "Do not use for Wi-Fi; use `control_wifi` instead.",
        prerequisite = "Set `action` to `turn_on`, `turn_off`, or `check_status`; if Android opens Settings, ask the user to confirm there and do not claim an unverified result.",
    )
)

fun wifiControlTool() = hardwareControlTool(
    name = "control_wifi",
    feature = "Wi-Fi",
    description = universalToolDescription(
        purpose = "Controls Wi-Fi or checks its current state.",
        trigger = "The user explicitly asks to turn Wi-Fi on/off or check its status.",
        notFor = "Do not use for Bluetooth or mobile data; use `control_bluetooth` for Bluetooth.",
        prerequisite = "Set `action` to `turn_on`, `turn_off`, or `check_status`; if Android opens Settings, ask the user to finish there and do not claim an unverified result.",
    )
)
