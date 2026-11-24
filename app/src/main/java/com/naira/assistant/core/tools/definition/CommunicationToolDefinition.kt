
package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

fun composeEmailTool() = ToolDefinition(
    name = "compose_email",
    description = universalToolDescription(
        purpose = "Prepares an email draft for the user to review and send.",
        trigger = "The user asks to write or prepare an email.",
        notFor = "This tool does not send email; never claim it was sent. Use `share_text` to share plain text and `compose_text_message` for an SMS draft.",
        prerequisite = "Email `body` is required. Resolve an ambiguous recipient name with `find_email_contact`; preserve requested tone, language, and facts, and avoid repeating recipient addresses.",
    ),
    parameters = buildJsonObject {
        put("type", "object")

        putJsonObject("properties") {
            putJsonObject("to") {
                put("type", "string")
                put(
                    "description",
                    "Optional comma-separated contact name(s) or email address(es). Omit for manual selection. " +
                        "Use find_email_contact for ambiguous names."
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

fun findEmailContactTool() = ToolDefinition(
    name = "find_email_contact",
    description = universalToolDescription(
        purpose = "Searches saved email contacts by name/address and returns matching contact details.",
        trigger = "The user asks for a saved contact's email or wants an email drafted to someone identified by name.",
        notFor = "Do not use for general phone contacts or SMS numbers; never guess an email address.",
        prerequisite = "A `query` is required; ask the user to choose if multiple contacts match.",
    ),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("query") {
                put("type", "string")
                put("description", "A contact owner's name or email address to search for.")
            }
        }
        putJsonArray("required") {
            add(JsonPrimitive("query"))
        }
    },
)

fun composeTextMessageTool() = ToolDefinition(
    name = "compose_text_message",
    description = universalToolDescription(
        purpose = "Prepares an SMS/text draft for the user to review and send.",
        trigger = "The user asks to write or prepare a text message/SMS.",
        notFor = "This tool does not send messages; use `compose_email` for email or `share_text` to share text. Never guess a phone number.",
        prerequisite = "Message `body` is required; omit an unknown `phone_number` so the user can choose the recipient.",
    ),
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
    description = universalToolDescription(
        purpose = "Opens Android's Sharesheet so the user can choose an app and recipient for supplied text.",
        trigger = "The user asks to share specific text.",
        notFor = "Use `compose_email`/`compose_text_message` to draft email/SMS; opening the Sharesheet does not mean the text was sent.",
        prerequisite = "Share `text` is required; preserve the supplied text.",
    ),
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
