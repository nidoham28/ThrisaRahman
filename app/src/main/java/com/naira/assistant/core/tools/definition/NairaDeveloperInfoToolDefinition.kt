
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
    description = universalToolDescription(
        purpose = "Returns public information about Naira Assistant's identity, purpose, capabilities, or developer.",
        trigger = "The user asks who Naira is, what it is for/can do, or who developed it.",
        notFor = "Do not use for general technical support or disclose private/internal details, source code, credentials, or secrets.",
        prerequisite = "Select the matching declared `topic` enum value.",
    ),
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
