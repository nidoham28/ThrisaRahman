package com.naira.assistant.core.tools.executor

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.naira.assistant.core.tools.CommandExecution.jsonStringArg
import com.naira.assistant.domain.FunctionCall
import com.naira.assistant.domain.settings.EmailContact
import com.naira.assistant.domain.settings.EmailContactStorage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.Locale

/**
 * Opens user-controlled Android composers with assistant-prepared content.
 *
 * Supported tools:
 * - compose_email
 * - find_email_contact
 * - compose_text_message
 * - share_text
 *
 * These tools never send messages automatically.
 * The user must review and confirm the action in the target application.
 */
object CommunicationToolExecutor {

    fun executeFindEmailContact(
        call: FunctionCall,
        storage: EmailContactStorage,
        userId: String?,
    ): JsonObject {
        val argument = call.args["query"] as? JsonPrimitive
            ?: return error("'query' must be a contact name or email address.")
        if (!argument.isString) return error("'query' must be a contact name or email address.")

        val query = argument.content.trim()
        if (query.isBlank()) return error("Provide a contact name or email address to search for.")

        val matches = storage.findContacts(query, userId)
        return buildJsonObject {
            put("success", true)
            put("query", query)
            put("count", matches.size)
            put("message", if (matches.isEmpty()) "No saved email contacts matched that query." else "Saved email contacts matching the query.")
            putJsonArray("matches") {
                matches.forEach { contact ->
                    add(
                        buildJsonObject {
                            put("name", contact.name)
                            put("email", contact.email)
                        },
                    )
                }
            }
        }
    }

    fun executeComposeEmail(
        call: FunctionCall,
        context: Context,
        contacts: List<EmailContact> = emptyList(),
    ): JsonObject {
        val body = stringArgument(call, "body")
            ?: return error("Provide the email body before opening the mail composer.")
        if (body.isBlank()) return error("The email body cannot be empty.")

        val toValue = optionalStringArgument(call, "to")
        if (call.args.containsKey("to") && toValue == null) {
            return error("'to' must be a string.")
        }
        val subject = optionalStringArgument(call, "subject")
        if (call.args.containsKey("subject") && subject == null) {
            return error("'subject' must be a string.")
        }

        val requestedRecipients = toValue.orEmpty()
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
        val resolved = resolveEmailRecipients(requestedRecipients, contacts)
        if (resolved is EmailRecipientResolution.Failed) return error(resolved.message)
        val recipients = (resolved as EmailRecipientResolution.Resolved).recipients

        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                if (recipients.isNotEmpty()) {
                    putExtra(Intent.EXTRA_EMAIL, recipients.toTypedArray())
                }
                putExtra(Intent.EXTRA_SUBJECT, subject.orEmpty().trim())
                putExtra(Intent.EXTRA_TEXT, body)
            }
            openChooser(context, intent, "Choose an email app")
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
            error("Could not open the email composer: ${e.message ?: "Unknown error."}")
        }
    }

    fun executeComposeTextMessage(
        call: FunctionCall,
        context: Context,
    ): JsonObject {
        val body = jsonStringArg(call, "body")
            ?: return error("Provide the text message before opening the messaging app.")
        if (body.isBlank()) return error("The text message cannot be empty.")

        if (call.args["phone_number"] != null && optionalStringArgument(call, "phone_number") == null) {
            return error("'phone_number' must be a string.")
        }

        val phone = optionalStringArgument(call, "phone_number")?.trim().orEmpty()
        if (phone.isNotEmpty() && !isValidPhoneNumber(phone)) {
            return error(
                "The phone number is invalid. Use digits and optionally " +
                    "a leading '+', spaces, parentheses, or hyphens.",
            )
        }

        return try {
            val smsUri = if (phone.isBlank()) Uri.parse("smsto:") else Uri.parse("smsto:${Uri.encode(phone)}")
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = smsUri
                putExtra("sms_body", body)
            }
            openChooser(context, intent, "Choose a messaging app")
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
            error("Could not open the messaging composer: ${e.message ?: "Unknown error."}")
        }
    }

    fun executeShareText(
        call: FunctionCall,
        context: Context,
    ): JsonObject {
        val text = jsonStringArg(call, "text")
            ?: return error("Provide text to share.")
        if (text.isBlank()) return error("The text to share cannot be empty.")

        if (call.args["title"] != null && optionalStringArgument(call, "title") == null) {
            return error("'title' must be a string.")
        }

        val title = optionalStringArgument(call, "title")
            ?.trim()
            ?.ifBlank { DEFAULT_SHARE_TITLE }
            ?: DEFAULT_SHARE_TITLE

        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            openChooser(context, intent, title)
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
            error("Could not open Android's share sheet: ${e.message ?: "Unknown error."}")
        }
    }

    private fun optionalStringArgument(call: FunctionCall, key: String): String? {
        val value = call.args[key] ?: return null
        val primitive = value as? JsonPrimitive ?: return null
        if (!primitive.isString) return null
        return primitive.content
    }

    private fun stringArgument(call: FunctionCall, key: String): String? {
        val value = call.args[key] as? JsonPrimitive ?: return null
        if (!value.isString) return null
        return value.content
    }

    private fun openChooser(context: Context, intent: Intent, title: String) {
        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.applicationContext.startActivity(chooser)
    }

    private fun isValidPhoneNumber(phone: String): Boolean {
        if (!PHONE_REGEX.matches(phone)) return false
        return phone.count(Char::isDigit) in 3..20
    }

    private fun error(message: String): JsonObject = buildJsonObject {
        put("success", false)
        put("error", message)
    }

    private const val DEFAULT_SHARE_TITLE = "Share with…"

    private val PHONE_REGEX = Regex("""^\+?[0-9][0-9(). -]{1,24}$""")
}

internal sealed interface EmailRecipientResolution {
    data class Resolved(val recipients: List<String>) : EmailRecipientResolution
    data class Failed(val message: String) : EmailRecipientResolution
}

internal fun resolveEmailRecipients(
    requestedRecipients: List<String>,
    contacts: List<EmailContact>,
): EmailRecipientResolution {
    val uniqueRecipients = linkedMapOf<String, String>()

    for (requested in requestedRecipients) {
        if (EMAIL_REGEX.matches(requested)) {
            uniqueRecipients.putIfAbsent(requested.lowercase(Locale.ROOT), requested)
            continue
        }

        val exactMatches = contacts.filter { it.name.equals(requested, ignoreCase = true) }
        val matchingContacts = (exactMatches.ifEmpty {
            contacts.filter { it.name.contains(requested, ignoreCase = true) }
        }).distinctBy { it.email.lowercase(Locale.ROOT) }

        when (matchingContacts.size) {
            0 -> return EmailRecipientResolution.Failed(
                "No saved email contact matches '$requested'. Search by name or email, or provide an email address.",
            )
            1 -> {
                val email = matchingContacts.single().email.trim()
                if (!EMAIL_REGEX.matches(email)) {
                    return EmailRecipientResolution.Failed("The saved email address for '$requested' is invalid.")
                }
                uniqueRecipients.putIfAbsent(email.lowercase(Locale.ROOT), email)
            }
            else -> return EmailRecipientResolution.Failed(
                "More than one saved email contact matches '$requested'. Use find_email_contact to choose the right person.",
            )
        }
    }

    return EmailRecipientResolution.Resolved(uniqueRecipients.values.toList())
}

private val EMAIL_REGEX = Regex(
    """^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?(?:\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$""",
    RegexOption.IGNORE_CASE,
)
