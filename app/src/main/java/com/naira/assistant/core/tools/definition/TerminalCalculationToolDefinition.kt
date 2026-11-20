
package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

fun terminalCalculationTool() = ToolDefinition(
    name = "terminal_calculate",
    description = """
        Evaluate a basic integer arithmetic expression using the app's Android shell.

        Use this tool for integer calculations when no dedicated calculator tool
        is available.

        Supported operators:
        - Addition: +
        - Subtraction: -
        - Multiplication: *
        - Division: /
        - Modulo: %
        - Parentheses: ()

        Division truncates toward zero.

        Only integer arithmetic is supported. Decimal numbers, variables,
        function calls, shell commands, and other shell syntax are not allowed.

        This tool evaluates arithmetic only. Never use it to execute general
        shell commands or scripts.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")

        putJsonObject("properties") {
            putJsonObject("expression") {
                put("type", "string")
                put(
                    "description",
                    "A valid integer arithmetic expression, such as (125 + 75) * 4 / 2. Supports +, -, *, /, %, and parentheses."
                )
            }
        }

        putJsonArray("required") {
            add(JsonPrimitive("expression"))
        }

        put("additionalProperties", false)
    }
)
