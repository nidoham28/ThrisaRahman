
package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

fun listInstalledAppsTool() = ToolDefinition(
    name = "list_installed_apps",
    description = universalToolDescription(
        purpose = "Lists or searches installed launchable apps by display name or package ID; system apps are excluded by default.",
        trigger = "The user asks to list installed apps or find an app by name/package.",
        notFor = "For a single app's installation status use `check_app_installed`; to launch an app use `open_app`.",
        prerequisite = "An optional `query`; set `include_system_apps=true` only when system apps are requested.",
    ),
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
    },
    parallelizable = true,
)

fun checkAppInstalledTool() = ToolDefinition(
    name = "check_app_installed",
    description = universalToolDescription(
        purpose = "Checks whether a specific app is installed on the device.",
        trigger = "The user asks whether a particular app is installed.",
        notFor = "To find/list multiple apps use `list_installed_apps`; to launch one use `open_app`.",
        prerequisite = "Provide an app name or exact package name; resolve an ambiguous name with `list_installed_apps` first.",
    ),
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
    },
    parallelizable = true,
)

fun openAppTool() = ToolDefinition(
    name = "open_app",
    description = universalToolDescription(
        purpose = "Launches a specified app on the device.",
        trigger = "The user explicitly asks to open or launch an app.",
        notFor = "Use `check_app_installed` to check installation and `list_installed_apps` to search/list apps; never substitute another app.",
        prerequisite = "Provide the app name or exact package name; resolve multiple matches with `list_installed_apps` first.",
    ),
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
