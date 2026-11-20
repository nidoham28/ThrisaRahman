
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
        Control the device camera flashlight (torch).

        Supported actions:
        - turn_on: Turn on the flashlight.
        - turn_off: Turn off the flashlight.
        - check_status: Check whether the flashlight is currently on.

        Use for requests involving the camera torch or flashlight.
        Do not use for screen brightness or camera photo capture.

        Important:
        - Report success only after the flashlight state has been confirmed.
        - If the device has no torch or camera access is unavailable,
          report the actual error.
        - Do not claim the flashlight is on merely because an operation
          was requested.
    """
)

fun bluetoothControlTool() = hardwareControlTool(
    name = "control_bluetooth",
    feature = "Bluetooth",
    description = """
        Control or inspect the device Bluetooth adapter.

        Supported actions:
        - turn_on: Request Bluetooth activation.
        - turn_off: Request Bluetooth deactivation.
        - check_status: Check whether Bluetooth is enabled.

        Android compatibility:
        - Android 12 and newer restrict Bluetooth operations through
          runtime permissions and system policies.
        - Android 13 and newer do not allow ordinary applications
          to enable or disable Bluetooth using BluetoothAdapter.enable()
          or BluetoothAdapter.disable().
        - When direct control is unavailable, open the appropriate
          system Bluetooth settings interface for user confirmation.

        Important:
        - Distinguish between an action being requested and completed.
        - If system settings are opened, tell the user that confirmation
          is required.
        - Never claim Bluetooth changed state until verified.
        - If permission is denied or Bluetooth is unsupported,
          report that accurately.
    """
)

fun wifiControlTool() = hardwareControlTool(
    name = "control_wifi",
    feature = "Wi-Fi",
    description = """
        Control or inspect the device Wi-Fi adapter.

        Supported actions:
        - turn_on: Request Wi-Fi activation.
        - turn_off: Request Wi-Fi deactivation.
        - check_status: Check whether Wi-Fi is enabled.

        Android compatibility:
        - Android 10 (API 29) and newer generally prevent ordinary
          applications from enabling or disabling Wi-Fi directly.
        - Use the system Wi-Fi settings panel when direct control
          is unavailable.
        - Do not confuse opening the settings panel with successfully
          changing the Wi-Fi state.

        Important:
        - Check the actual Wi-Fi state before reporting its status.
        - If the settings panel opens, explain that the user must
          complete the change.
        - Never claim Wi-Fi was enabled or disabled without verification.
        - Report permission failures and unsupported operations accurately.
    """
)
