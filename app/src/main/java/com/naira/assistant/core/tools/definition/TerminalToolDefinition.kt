
package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

fun findTerminalTool() = ToolDefinition(
    name = "terminal_find_tool",
    description = """
        Locate one existing Android-shell executable only when no dedicated tool fits. This does
        not execute commands or change files. Pass a single executable name, never arguments or
        shell syntax.
    """.trimIndent(),
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
