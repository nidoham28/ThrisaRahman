package com.nidoham.thrisa.core.tools.definition

import com.nidoham.thrisa.core.tools.ToolDefinition
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

fun listInstalledAppsTool() = ToolDefinition(
    name = "list_installed_apps",
    description = """
        List or search applications installed on the user's Android device.
        Use when the user asks 'what apps do I have?', 'show my apps', 'list my applications', 
        or when you need to discover the exact name or package name of an app on the phone.
        By default, returns launchable user-facing apps.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("query") {
                put("type", "string")
                put(
                    "description",
                    "Optional search query to filter apps by name or package (e.g. 'camera', 'google', 'music')."
                )
            }
            putJsonObject("include_system_apps") {
                put("type", "boolean")
                put(
                    "description",
                    "Whether to include background/system packages. Default is false (only launchable user apps)."
                )
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "Maximum number of apps to return (1-100, default 50).")
            }
        }
        putJsonArray("required") {}
    },
)

fun checkAppInstalledTool() = ToolDefinition(
    name = "check_app_installed",
    description = """
        Check whether a specific application is installed on the user's device.
        Use when the user asks 'do I have WhatsApp installed?', 'is Spotify on my phone?', 
        'is Netflix installed?', or to verify an app exists before attempting to open it.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("app_name") {
                put("type", "string")
                put(
                    "description",
                    "The common or display name of the app (e.g. 'WhatsApp', 'Spotify', 'Chrome', 'Camera')."
                )
            }
            putJsonObject("package_name") {
                put("type", "string")
                put(
                    "description",
                    "Optional exact package name (e.g. 'com.whatsapp', 'com.spotify.music')."
                )
            }
        }
        putJsonArray("required") {}
    },
)

fun openAppTool() = ToolDefinition(
    name = "open_app",
    description = """
        Launch and open an installed application on the user's device when the user asks you to.
        Open it directly; do not ask the user to launch it themselves or ask for confirmation again.
        Use when the user says 'open YouTube', 'launch WhatsApp', 'start Spotify', 
        'open calculator', 'open camera', or any similar request to open an app.
        You can pass the app name (e.g. 'YouTube') or exact package name if known.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("app_name") {
                put("type", "string")
                put(
                    "description",
                    "The display name of the app to open (e.g. 'YouTube', 'Chrome', 'Calculator', 'Camera')."
                )
            }
            putJsonObject("package_name") {
                put("type", "string")
                put(
                    "description",
                    "Optional exact package name to launch directly (e.g. 'com.google.android.youtube')."
                )
            }
        }
        putJsonArray("required") {}
    },
)
