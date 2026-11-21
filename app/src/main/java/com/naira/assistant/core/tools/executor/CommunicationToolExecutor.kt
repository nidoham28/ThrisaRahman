
package com.naira.assistant.core.tools.executor

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.naira.assistant.core.tools.CommandExecution.jsonStringArg
import com.naira.assistant.domain.FunctionCall
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Opens user-controlled Android composers with assistant-prepared content.
 *
 * Supported tools:
 * - compose_email
 * - compose_text_message
 * - share_text
 *
 * These tools never send messages automatically.
 * The user must review and confirm the action in the target application.
 */
object CommunicationToolExecutor {

    // ------------------------------------------------------------------
    // Email
    // ------------------------------------------------------------------

    fun executeComposeEmail(
        call: FunctionCall,
        context: Context
    ): JsonObject {
        val body = jsonStringArg(call, "body")
            ?: return error("Provide the email body before opening the mail composer.")

        if (body.isBlank()) {
            return error("The email body cannot be empty.")
        }

        if (
            call.args["to"] != null &&
            jsonStringArg(call, "to") == null
        ) {
            return error("'to' must be a string.")
        }

        if (
            call.args["subject"] != null &&
            jsonStringArg(call, "subject") == null
        ) {
            return error("'subject' must be a string.")
        }

        val recipients = jsonStringArg(call, "to")
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()

        if (recipients.any { !EMAIL_REGEX.matches(it) }) {
            return error("One or more recipient email addresses are invalid.")
        }

        val subject = jsonStringArg(call, "subject")
            .orEmpty()
            .trim()

        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")

                if (recipients.isNotEmpty()) {
                    putExtra(
                        Intent.EXTRA_EMAIL,
                        recipients.toTypedArray()
                    )
                }

                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }

            openChooser(
                context = context,
                intent = intent,
                title = "Choose an email app"
            )

            buildJsonObject {
                put("success", true)
                put("action", "email_draft_opened")
                put("recipient_count", recipients.size)
                put("message", "Opened an email draft for review. It has not been sent.")
            }
        } catch (e: ActivityNotFoundException) {
            error("No compatible email application was found on this device.")
        } catch (e: SecurityException) {
            error("Android denied access to the email application.")
        } catch (e: Exception) {
            error(
                "Could not open the email composer: " +
                        (e.message ?: "Unknown error.")
            )
        }
    }

    // ------------------------------------------------------------------
    // SMS / Text message
    // ------------------------------------------------------------------

    fun executeComposeTextMessage(
        call: FunctionCall,
        context: Context
    ): JsonObject {
        val body = jsonStringArg(call, "body")
            ?: return error("Provide the text message before opening the messaging app.")

        if (body.isBlank()) {
            return error("The text message cannot be empty.")
        }

        if (
            call.args["phone_number"] != null &&
            jsonStringArg(call, "phone_number") == null
        ) {
            return error("'phone_number' must be a string.")
        }

        val phone = jsonStringArg(call, "phone_number")
            ?.trim()
            .orEmpty()

        if (phone.isNotEmpty() && !isValidPhoneNumber(phone)) {
            return error(
                "The phone number is invalid. Use digits and optionally " +
                        "a leading '+', spaces, parentheses, or hyphens."
            )
        }

        return try {
            val smsUri = if (phone.isBlank()) {
                Uri.parse("smsto:")
            } else {
                Uri.parse("smsto:${Uri.encode(phone)}")
            }

            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = smsUri
                putExtra("sms_body", body)
            }

            openChooser(
                context = context,
                intent = intent,
                title = "Choose a messaging app"
            )

            buildJsonObject {
                put("success", true)
                put("action", "text_draft_opened")
                put("recipient_set", phone.isNotBlank())
                put("message", "Opened a text message draft for review. It has not been sent.")
            }
        } catch (e: ActivityNotFoundException) {
            error("No compatible messaging application was found on this device.")
        } catch (e: SecurityException) {
            error("Android denied access to the messaging application.")
        } catch (e: Exception) {
            error(
                "Could not open the messaging composer: " +
                        (e.message ?: "Unknown error.")
            )
        }
    }

    // ------------------------------------------------------------------
    // General Android Sharesheet
    // ------------------------------------------------------------------

    fun executeShareText(
        call: FunctionCall,
        context: Context
    ): JsonObject {
        val text = jsonStringArg(call, "text")
            ?: return error("Provide text to share.")

        if (text.isBlank()) {
            return error("The text to share cannot be empty.")
        }

        if (
            call.args["title"] != null &&
            jsonStringArg(call, "title") == null
        ) {
            return error("'title' must be a string.")
        }

        val title = jsonStringArg(call, "title")
            ?.trim()
            ?.ifBlank { DEFAULT_SHARE_TITLE }
            ?: DEFAULT_SHARE_TITLE

        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }

            openChooser(
                context = context,
                intent = intent,
                title = title
            )

            buildJsonObject {
                put("success", true)
                put("action", "share_sheet_opened")
                put("message", "Opened Android's share sheet with the prepared text. Nothing has been sent.")
            }
        } catch (e: ActivityNotFoundException) {
            error("Android could not find an application to handle text sharing.")
        } catch (e: SecurityException) {
            error("Android denied access to the sharing application.")
        } catch (e: Exception) {
            error(
                "Could not open Android's share sheet: " +
                        (e.message ?: "Unknown error.")
            )
        }
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    /**
     * Opens Android's chooser using the application context.
     *
     * If this method returns normally, Android accepted the activity
     * launch request. It does not mean the user selected an application
     * or completed sending/sharing the content.
     */
    private fun openChooser(
        context: Context,
        intent: Intent,
        title: String
    ) {
        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.applicationContext.startActivity(chooser)
    }

    /**
     * Basic practical email validation.
     * This is not intended to implement the entire email-address standard.
     */
    private fun isValidEmail(email: String): Boolean {
        return EMAIL_REGEX.matches(email)
    }

    /**
     * Accepts common international and local phone number formats.
     * Requires at least 3 digits and limits the total number of digits.
     */
    private fun isValidPhoneNumber(phone: String): Boolean {
        if (!PHONE_REGEX.matches(phone)) return false

        val digitCount = phone.count { it.isDigit() }

        return digitCount in 3..20
    }

    private fun error(message: String): JsonObject =
        buildJsonObject {
            put("success", false)
            put("error", message)
        }

    // ------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------

    private const val DEFAULT_SHARE_TITLE = "Share with…"

    private val EMAIL_REGEX = Regex(
        """^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?(?:\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$""",
        RegexOption.IGNORE_CASE
    )

    private val PHONE_REGEX = Regex(
        """^\+?[0-9][0-9(). -]{1,24}$"""
    )
}
