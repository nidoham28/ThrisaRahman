
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
        Use for exact/nontrivial integer arithmetic when direct calculation is unreliable.
        Supports +, -, *, /, %, and parentheses; division truncates toward zero. No decimals,
        variables, functions, commands, or general shell execution.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")

        putJsonObject("properties") {
            putJsonObject("expression") {
                put("type", "string")
                put(
                    "description",
                    "Integer arithmetic expression, maximum 128 characters."
                )
                put("minLength", 1)
                put("maxLength", 128)
            }
        }

        putJsonArray("required") {
            add(JsonPrimitive("expression"))
        }

        put("additionalProperties", false)
    }
)
