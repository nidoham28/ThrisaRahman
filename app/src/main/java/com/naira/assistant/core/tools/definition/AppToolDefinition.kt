
package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

fun listInstalledAppsTool() = ToolDefinition(
    name = "list_installed_apps",
    description = "List or search installed apps. System/non-launchable apps are excluded unless " +
        "explicitly requested. Use returned names or package IDs; do not infer installation.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("query") {
                put("type", "string")
                put(
                    "description",
                    "Optional filter for app display name or package name."
                )
            }
            putJsonObject("include_system_apps") {
                put("type", "boolean")
                put(
                    "description",
                    "Include system and non-launchable packages. Default: false."
                )
                put("default", false)
            }
            putJsonObject("limit") {
                put("type", "integer")
                put(
                    "description",
                    "Maximum number of results, from 1 to 100. Default: 50."
                )
                put("minimum", 1)
                put("maximum", 100)
                put("default", 50)
            }
        }
        putJsonArray("required") {}
    }
)

fun checkAppInstalledTool() = ToolDefinition(
    name = "check_app_installed",
    description = "Check app installation by display name or exact package name. Provide either " +
        "or both; report only the device result.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("app_name") {
                put("type", "string")
                put("minLength", 1)
                put(
                    "description",
                    "Common or display name of the application, such as WhatsApp or Spotify."
                )
            }
            putJsonObject("package_name") {
                put("type", "string")
                put("minLength", 1)
                put(
                    "description",
                    "Exact Android package name, such as com.whatsapp. Optional when app_name is provided."
                )
            }
        }
    }
)

fun openAppTool() = ToolDefinition(
    name = "open_app",
    description = "Open an app only on explicit request. Supply its name or exact package ID; " +
        "resolve ambiguity first. Never substitute another app or claim an unconfirmed launch.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("app_name") {
                put("type", "string")
                put("minLength", 1)
                put(
                    "description",
                    "Display or common name of the application to launch."
                )
            }
            putJsonObject("package_name") {
                put("type", "string")
                put("minLength", 1)
                put(
                    "description",
                    "Exact Android package name. Prefer this when already known."
                )
            }
        }
    }
)
