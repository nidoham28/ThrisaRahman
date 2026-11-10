package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

fun composeEmailTool() = ToolDefinition(
    name = "compose_email",
    description = "Prepare an email draft in the user's installed mail app. Use this when asked to write or draft an email. " +
            "Never claim it was sent; the user reviews and sends it in the mail app. Ask for missing details when needed.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("to") {
                put("type", "string")
                put("description", "Recipient email address or comma-separated addresses. Optional if the user wants to fill it in later.")
            }
            putJsonObject("subject") {
                put("type", "string")
                put("description", "Email subject. Use an empty string if the user wants to add it later.")
            }
            putJsonObject("body") {
                put("type", "string")
                put("description", "The complete email draft, preserving the requested tone and key details.")
            }
        }
        putJsonArray("required") { add("body") }
    }
)

fun composeTextMessageTool() = ToolDefinition(
    name = "compose_text_message",
    description = "Prepare an SMS/text message draft in the user's messaging app. Use this when asked to write or draft a text. " +
            "Never claim it was sent; the user reviews and sends it in the messaging app. Ask for missing details when needed.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("phone_number") {
                put("type", "string")
                put("description", "Recipient phone number, including country code when known. Optional if the user will select a recipient in the app.")
            }
            putJsonObject("body") {
                put("type", "string")
                put("description", "The complete text message draft.")
            }
        }
        putJsonArray("required") { add("body") }
    }
)

fun shareTextTool() = ToolDefinition(
    name = "share_text",
    description = "Open Android's share sheet with prepared text so the user can choose an app and recipient. " +
            "Use for content the user wants to share in another app. Never claim the content was sent.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("title") {
                put("type", "string")
                put("description", "A short title shown in the share sheet.")
            }
            putJsonObject("text") {
                put("type", "string")
                put("description", "The content to share.")
            }
        }
        putJsonArray("required") { add("text") }
    }
)
