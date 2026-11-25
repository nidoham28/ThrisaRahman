
package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

fun terminalCalculationTool() = ToolDefinition(
    name = "terminal_calculate",
    description = universalToolDescription(
        purpose = "Calculates exact integer arithmetic with `+`, `-`, `*`, `/`, `%`, and parentheses; division truncates toward zero.",
        trigger = "Any arithmetic beyond what you can state with certainty at a glance, multi-step arithmetic, or when the user asks to calculate.",
        notFor = "No decimals, variables, functions, commands, or general shell execution; for app or device actions use the matching dedicated tool instead.",
        prerequisite = "Provide a valid arithmetic `expression` of at most 128 characters.",
    ),
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
