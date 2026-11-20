
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
        Check whether a single command-line executable is available in the app's
        Android shell and return its absolute path if found.

        Use this only when no dedicated assistant tool supports the requested
        capability and checking for an existing local utility is necessary.

        This tool only locates an executable. It does NOT execute commands,
        run scripts, install packages, or modify files.

        Provide only the executable name, such as grep, find, sqlite3, or curl.
        Never provide shell commands, arguments, paths, pipes, operators,
        command substitutions, or multiple executable names.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")

        putJsonObject("properties") {
            putJsonObject("tool_name") {
                put("type", "string")
                put(
                    "description",
                    "A single executable name only, such as grep, find, sqlite3, or curl. No arguments or shell syntax."
                )
            }
        }

        putJsonArray("required") {
            add(JsonPrimitive("tool_name"))
        }

        put("additionalProperties", false)
    }
)
