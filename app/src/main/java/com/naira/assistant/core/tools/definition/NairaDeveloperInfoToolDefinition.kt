
package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private val nairaInfoTopics = listOf(
    "about_naira",
    "developer",
    "purpose",
    "capabilities",
    "identity"
)

fun nairaDeveloperInfoTool() = ToolDefinition(
    name = "get_naira_info",
    description = "Get public identity, purpose, or capability information about Naira and its " +
        "developer. Never reveal source/internal details, private data, credentials, or secrets; " +
        "do not use for general technical support.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("topic") {
                put("type", "string")
                put("description", "Public information topic.")
                putJsonArray("enum") {
                    nairaInfoTopics.forEach {
                        add(JsonPrimitive(it))
                    }
                }
            }
        }
        putJsonArray("required") {
            add(JsonPrimitive("topic"))
        }
        put("additionalProperties", false)
    }
)
