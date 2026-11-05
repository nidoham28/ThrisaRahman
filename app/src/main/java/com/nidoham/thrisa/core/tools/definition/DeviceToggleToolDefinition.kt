package com.nidoham.thrisa.core.tools.definition

import com.nidoham.thrisa.core.tools.ToolDefinition
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Function declarations for hardware feature toggles: flashlight, Bluetooth and Wi-Fi. */

fun flashlightControlTool() = ToolDefinition(
    name = "control_flashlight",
    description = "Turn the device flashlight (camera torch) on or off, or check whether it is currently lit. " +
            "Use for requests like 'turn on the flashlight', 'turn off the torch' or 'is the flashlight on?'. " +
            "Not for screen brightness.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("description", "What to do with the flashlight.")
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
    },
)

fun bluetoothControlTool() = ToolDefinition(
    name = "control_bluetooth",
    description = "Turn the device Bluetooth on or off, or check whether it is currently enabled. " +
            "Use for requests like 'turn on Bluetooth' or 'is Bluetooth on?'. " +
            "Newer Android versions may not let apps switch Bluetooth directly; " +
            "in that case the tool opens the Bluetooth settings panel for the user to confirm.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("description", "What to do with Bluetooth.")
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
    },
)

fun wifiControlTool() = ToolDefinition(
    name = "control_wifi",
    description = "Turn the device Wi-Fi on or off, or check whether it is currently enabled. " +
            "Use for requests like 'turn off Wi-Fi' or 'is Wi-Fi on?'. " +
            "On Android 10 and newer, apps cannot switch Wi-Fi directly; " +
            "the tool then opens the Wi-Fi settings panel for the user to confirm.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("description", "What to do with Wi-Fi.")
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
    },
)
