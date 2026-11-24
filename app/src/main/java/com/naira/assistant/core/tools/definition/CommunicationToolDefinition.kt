
package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

fun composeEmailTool() = ToolDefinition(
    name = "compose_email",
    description = """
        Prepare an email draft only; the user reviews and sends it. Preserve requested tone,
        language, and facts. Never invent a recipient or claim it was sent.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")

        putJsonObject("properties") {
            putJsonObject("to") {
                put("type", "string")
                put(
                    "description",
                    "Optional recipient(s); omit or use empty string for manual selection. Never invent."
                )
            }

            putJsonObject("subject") {
                put("type", "string")
                put(
                    "description",
                    "Optional subject; use empty string if unspecified."
                )
            }

            putJsonObject("body") {
                put("type", "string")
                put(
                    "description",
                    "Complete email draft."
                )
            }
        }

        putJsonArray("required") {
            add(JsonPrimitive("body"))
        }
    }
)

fun composeTextMessageTool() = ToolDefinition(
    name = "compose_text_message",
    description = """
        Prepare an SMS/text draft only; the user reviews and sends it. Preserve intent, tone,
        and language. Never invent a phone number or claim it was sent.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")

        putJsonObject("properties") {
            putJsonObject("phone_number") {
                put("type", "string")
                put(
                    "description",
                    "Optional number; omit or use empty string for manual selection. Never invent."
                )
            }

            putJsonObject("body") {
                put("type", "string")
                put(
                    "description",
                    "Complete SMS/text draft."
                )
            }
        }

        putJsonArray("required") {
            add(JsonPrimitive("body"))
        }
    }
)

fun shareTextTool() = ToolDefinition(
    name = "share_text",
    description = """
        Open Android's Sharesheet only when the user asks to share, so they choose the app and recipient.
        Preserve the supplied text; opening the sheet does not mean it was sent.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")

        putJsonObject("properties") {
            putJsonObject("title") {
                put("type", "string")
                put(
                    "description",
                    "Optional Sharesheet title."
                )
            }

            putJsonObject("text") {
                put("type", "string")
                put(
                    "description",
                    "Text to share."
                )
            }
        }

        putJsonArray("required") {
            add(JsonPrimitive("text"))
        }
    }
)
