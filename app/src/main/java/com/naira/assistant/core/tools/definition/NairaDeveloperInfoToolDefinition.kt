
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
    description = "Retrieve official identity and general information about Naira Assistant and its developer. " +
            "Use this tool when users ask what Naira Assistant is, who created it, " +
            "why it exists, what it is designed to do, or what its general capabilities are. " +
            "This tool provides only public-facing identity information. " +
            "It must never reveal source code, internal implementation details, " +
            "private developer information, credentials, secrets, project configuration, " +
            "or confidential information. Do not use it for general Android, Kotlin, " +
            "programming, or technical support questions.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("topic") {
                put("type", "string")
                put("description", "The Naira Assistant information topic to retrieve.")
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
