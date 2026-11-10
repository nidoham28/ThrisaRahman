package com.naira.assistant.core.tools.executor

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.naira.assistant.core.tools.executor.BuiltinToolExecutor.jsonStringArg
import com.naira.assistant.domain.FunctionCall
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Opens user-controlled Android composers with assistant-prepared draft content. */
object CommunicationToolExecutor {

    fun executeComposeEmail(call: FunctionCall, context: Context): JsonObject {
        val body = jsonStringArg(call, "body")?.trim()
            ?: return error("Provide the email body before opening the mail composer.")
        val recipients = jsonStringArg(call, "to")
            ?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
        if (recipients.any { !EMAIL_REGEX.matches(it) }) {
            return error("One or more recipient email addresses are invalid.")
        }
        val subject = jsonStringArg(call, "subject")?.trim().orEmpty()

        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            if (recipients.isNotEmpty()) putExtra(Intent.EXTRA_EMAIL, recipients.toTypedArray())
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        openChooser(context, intent, "Choose an email app")
        return buildJsonObject {
            put("success", true)
            put("action", "email_draft_opened")
            put("recipient_count", recipients.size)
            put("message", "Opened an email draft for review. It has not been sent.")
        }
    }

    fun executeComposeTextMessage(call: FunctionCall, context: Context): JsonObject {
        val body = jsonStringArg(call, "body")?.trim()
            ?: return error("Provide the text message before opening the messaging app.")
        val phone = jsonStringArg(call, "phone_number")?.trim().orEmpty()
        if (phone.isNotEmpty() && !PHONE_REGEX.matches(phone)) {
            return error("The phone number contains unsupported characters.")
        }

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse(if (phone.isBlank()) "smsto:" else "smsto:${Uri.encode(phone)}")
            putExtra("sms_body", body)
        }
        openChooser(context, intent, "Choose a messaging app")
        return buildJsonObject {
            put("success", true)
            put("action", "text_draft_opened")
            put("recipient_set", phone.isNotBlank())
            put("message", "Opened a text message draft for review. It has not been sent.")
        }
    }

    fun executeShareText(call: FunctionCall, context: Context): JsonObject {
        val text = jsonStringArg(call, "text")?.trim()
            ?: return error("Provide text to share.")
        val title = jsonStringArg(call, "title")?.trim().orEmpty().ifBlank { "Share with…" }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        openChooser(context, intent, title)
        return buildJsonObject {
            put("success", true)
            put("action", "share_sheet_opened")
            put("message", "Opened Android's share sheet with the prepared text. Nothing has been sent.")
        }
    }

    private fun openChooser(context: Context, intent: Intent, title: String) {
        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.applicationContext.startActivity(chooser)
    }

    private fun error(message: String): JsonObject = buildJsonObject {
        put("success", false)
        put("error", message)
    }

    private val EMAIL_REGEX = Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", RegexOption.IGNORE_CASE)
    private val PHONE_REGEX = Regex("^[0-9+().\\- ]{3,}$")
}
