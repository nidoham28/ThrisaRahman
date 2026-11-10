package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

fun findTerminalTool() = ToolDefinition(
    name = "terminal_find_tool",
    description = """
        Check whether a command-line utility is available in the app's Android shell and return its path.
        Use this only when no dedicated assistant tool exists for the requested capability and you need
        to check for a local shell utility before deciding what to do. This only locates a command; it
        does not run the utility or accept shell commands.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("tool_name") {
                put("type", "string")
                put("description", "A single executable name, such as grep, find, or sqlite3.")
            }
        }
        putJsonArray("required") {
            add(kotlinx.serialization.json.JsonPrimitive("tool_name"))
        }
    },
)
