package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

fun terminalCalculationTool() = ToolDefinition(
    name = "terminal_calculate",
    description = """
        Evaluate a basic integer arithmetic expression using the app's Android shell.
        Use for calculations when no dedicated calculator tool is available. Supports
        parentheses and +, -, *, /, and %; division truncates toward zero. Do not use
        this tool to execute general shell commands.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("expression") {
                put("type", "string")
                put("description", "An integer arithmetic expression, such as (125 + 75) * 4 / 2.")
            }
        }
        putJsonArray("required") {
            add(kotlinx.serialization.json.JsonPrimitive("expression"))
        }
    },
)
