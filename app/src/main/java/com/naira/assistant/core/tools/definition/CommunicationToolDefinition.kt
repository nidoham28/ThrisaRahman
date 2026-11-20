
package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Tool definitions for composing emails, text messages,
 * and sharing prepared text through Android.
 */

fun composeEmailTool() = ToolDefinition(
    name = "compose_email",
    description = """
        Prepare an email draft in the user's installed email application.

        Use this tool when the user asks to write, draft, compose, or prepare
        an email.

        Arguments:
        - to: Recipient email address or comma-separated email addresses.
          Optional when the user wants to choose the recipient manually.
          Never invent an email address.
        - subject: Email subject. Use an empty string if no subject is
          provided or the user wants to fill it in later.
        - body: Complete email content, including greeting and sign-off
          when appropriate.

        Rules:
        - Preserve the user's requested tone, language, and key details.
        - Do not invent facts, commitments, or recipient information.
        - Do not ask for optional information that the user can fill in
          within the email application.
        - This tool only prepares a draft. The user must review and send it.
        - Never claim that an email was sent.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")

        putJsonObject("properties") {
            putJsonObject("to") {
                put("type", "string")
                put(
                    "description",
                    "Optional recipient email address or comma-separated " +
                            "addresses. Use an empty string when the user " +
                            "will select recipients manually. Never invent addresses."
                )
            }

            putJsonObject("subject") {
                put("type", "string")
                put(
                    "description",
                    "Email subject. Use an empty string if the user " +
                            "has not specified a subject."
                )
            }

            putJsonObject("body") {
                put("type", "string")
                put(
                    "description",
                    "Required complete email draft. Preserve the requested " +
                            "tone, language, and important details."
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
        Prepare an SMS or text message draft in the user's messaging
        application.

        Use this tool when the user asks to write, draft, compose,
        or prepare a text message or SMS.

        Arguments:
        - phone_number: Recipient phone number, including country code
          when known. Optional when the user will select the recipient
          manually. Never invent a phone number.
        - body: Complete text message content.

        Rules:
        - Keep the message natural and appropriate for the requested context.
        - Preserve the user's intended meaning, language, and tone.
        - Do not add unsupported information.
        - Do not ask for a recipient number if the user can select
          the recipient in the messaging application.
        - This tool only prepares a message draft. The user must review
          and send it.
        - Never claim that a message was sent.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")

        putJsonObject("properties") {
            putJsonObject("phone_number") {
                put("type", "string")
                put(
                    "description",
                    "Optional recipient phone number, including country " +
                            "code when known. Use an empty string if the user " +
                            "will select the recipient manually."
                )
            }

            putJsonObject("body") {
                put("type", "string")
                put(
                    "description",
                    "Required complete SMS or text message draft."
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
        Open Android's native Sharesheet with prepared text so the user
        can choose an application and recipient.

        Use this tool when the user wants to share, send through another app,
        or pass prepared text to an application such as WhatsApp, Messenger,
        Gmail, or another compatible sharing application.

        Arguments:
        - title: Optional short title displayed in the Android Sharesheet.
          Use an empty string if no title is necessary.
        - text: Required content to share.

        Rules:
        - Preserve the requested content and formatting.
        - Do not invent additional content.
        - Do not assume which application or recipient the user will choose.
        - Opening the Sharesheet does not mean the content was sent.
        - Never claim that the content was delivered or sent.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")

        putJsonObject("properties") {
            putJsonObject("title") {
                put("type", "string")
                put(
                    "description",
                    "Optional short title displayed in the Android Sharesheet."
                )
            }

            putJsonObject("text") {
                put("type", "string")
                put(
                    "description",
                    "Required text content to share through Android's Sharesheet."
                )
            }
        }

        putJsonArray("required") {
            add(JsonPrimitive("text"))
        }
    }
)
