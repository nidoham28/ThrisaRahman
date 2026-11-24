
package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

fun findTerminalTool() = ToolDefinition(
    name = "terminal_find_tool",
    description = universalToolDescription(
        purpose = "Locates one existing Android-shell executable; it does not run commands or modify files.",
        trigger = "The user asks to locate a specific executable and no dedicated tool fits.",
        notFor = "Do not use to execute commands, edit files, or run a general shell; use `terminal_calculate` for supported arithmetic.",
        prerequisite = "Provide one executable name only, without arguments or shell syntax.",
    ),
    parameters = buildJsonObject {
        put("type", "object")

        putJsonObject("properties") {
            putJsonObject("tool_name") {
                put("type", "string")
                put(
                    "description",
                    "One executable name only; no arguments or shell syntax."
                )
                put("minLength", 1)
                put("maxLength", 128)
                put("pattern", "^[A-Za-z0-9_][A-Za-z0-9._+\\-]{0,127}$")
            }
        }

        putJsonArray("required") {
            add(JsonPrimitive("tool_name"))
        }

        put("additionalProperties", false)
    }
)
