
package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

fun listInstalledAppsTool() = ToolDefinition(
    name = "list_installed_apps",
    description = """
        Discover applications installed on the user's Android device.

        Use when the user asks to list, show, find, or search installed apps,
        or when you need to identify an app's exact display name or package name.

        By default, return launchable user-facing apps only.
        Set include_system_apps=true only when the user explicitly asks for
        system apps or a complete package list.

        Use query to search by app name or package name.
        Do not claim an app is installed unless it appears in the results.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("query") {
                put("type", "string")
                put(
                    "description",
                    "Optional search term. Matches application names or package names."
                )
            }
            putJsonObject("include_system_apps") {
                put("type", "boolean")
                put(
                    "description",
                    "Include system and non-launchable packages. Default: false."
                )
            }
            putJsonObject("limit") {
                put("type", "integer")
                put(
                    "description",
                    "Maximum number of results, from 1 to 100. Default: 50."
                )
            }
        }
        putJsonArray("required") {}
    }
)

fun checkAppInstalledTool() = ToolDefinition(
    name = "check_app_installed",
    description = """
        Verify whether a specific application is installed on the user's device.

        Use when the user asks whether an app is installed or when you need
        to verify an app before opening it.

        Provide app_name for a common or display name, or package_name
        for an exact Android package identifier. Use both when available
        to improve identification.

        Do not infer installation from an app's popularity or availability
        on the Play Store. Report only the result returned by the device.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("app_name") {
                put("type", "string")
                put(
                    "description",
                    "Common or display name of the application, such as WhatsApp or Spotify."
                )
            }
            putJsonObject("package_name") {
                put("type", "string")
                put(
                    "description",
                    "Exact Android package name, such as com.whatsapp. Optional when app_name is provided."
                )
            }
        }
        putJsonArray("required") {
            add(JsonPrimitive("app_name"))
        }
    }
)

fun openAppTool() = ToolDefinition(
    name = "open_app",
    description = """
        Launch an installed application on the user's Android device.

        Use only when the user explicitly asks to open, launch, or start an app.
        Accept a recognizable app name or an exact package name.

        If the app name is ambiguous, use check_app_installed or
        list_installed_apps to identify the correct application first.

        Launch the requested app directly without asking for redundant
        confirmation. Do not claim it opened unless the launch succeeds.

        If the app is not installed, unavailable, or cannot be launched,
        report the actual result. Never substitute a different app silently.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("app_name") {
                put("type", "string")
                put(
                    "description",
                    "Display or common name of the application to launch."
                )
            }
            putJsonObject("package_name") {
                put("type", "string")
                put(
                    "description",
                    "Exact Android package name. Prefer this when already known."
                )
            }
        }
        putJsonArray("required") {
            add(JsonPrimitive("app_name"))
        }
    }
)
