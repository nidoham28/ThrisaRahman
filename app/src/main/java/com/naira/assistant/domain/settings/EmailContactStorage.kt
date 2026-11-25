package com.naira.assistant.domain.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.Locale

data class EmailContact(
    val name: String,
    val email: String,
)

class EmailContactStorage(context: Context) {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getContacts(userId: String?): List<EmailContact> {
        val prefix = contactPrefix(userId)
        return preferences.all.entries
            .asSequence()
            .filter { (key, value) -> key.startsWith(prefix) && key.endsWith(NAME_SUFFIX) && value is String }
            .mapNotNull { (key, value) ->
                val canonicalEmail = key.removePrefix(prefix).removeSuffix(NAME_SUFFIX)
                val email = preferences.getString(emailKey(userId, canonicalEmail), null)
                    ?: return@mapNotNull null
                EmailContact(name = value as String, email = email)
            }
            .sortedWith(
                compareBy<EmailContact, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
                    .thenBy { it.email },
            )
            .toList()
    }

    fun findContacts(query: String, userId: String?): List<EmailContact> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return emptyList()

        return getContacts(userId).filter { contact ->
            contact.name.contains(normalizedQuery, ignoreCase = true) ||
                contact.email.contains(normalizedQuery, ignoreCase = true)
        }
    }

    @Synchronized
    fun saveContact(
        name: String,
        email: String,
        userId: String?,
        replacingEmail: String? = null,
    ): List<EmailContact> {
        val cleanedName = name.trim()
        val cleanedEmail = email.trim()
        validateEmailContactInput(cleanedName, cleanedEmail)?.let {
            throw IllegalStateException(it)
        }

        val canonicalEmail = canonicalizeEmail(cleanedEmail)
        val replacingCanonicalEmail = replacingEmail
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let(::canonicalizeEmail)

        synchronized(preferences) {
            val duplicate = getContacts(userId).firstOrNull {
                canonicalizeEmail(it.email) == canonicalEmail &&
                    canonicalEmail != replacingCanonicalEmail
            }
            if (duplicate != null) {
                throw IllegalStateException("That email address is already saved for ${duplicate.name}.")
            }

            preferences.edit {
                if (replacingCanonicalEmail != null && replacingCanonicalEmail != canonicalEmail) {
                    remove(nameKey(userId, replacingCanonicalEmail))
                    remove(emailKey(userId, replacingCanonicalEmail))
                }
                putString(nameKey(userId, canonicalEmail), cleanedName)
                putString(emailKey(userId, canonicalEmail), cleanedEmail)
            }
        }

        return getContacts(userId)
    }

    @Synchronized
    fun removeContact(email: String, userId: String?): List<EmailContact> {
        val canonicalEmail = canonicalizeEmail(email)
        synchronized(preferences) {
            preferences.edit {
                remove(nameKey(userId, canonicalEmail))
                remove(emailKey(userId, canonicalEmail))
            }
        }
        return getContacts(userId)
    }

    private fun contactPrefix(userId: String?): String =
        "$CONTACT_PREFIX${userId?.takeIf(String::isNotBlank) ?: LOCAL_USER_KEY}_"

    private fun nameKey(userId: String?, canonicalEmail: String): String =
        "${contactPrefix(userId)}$canonicalEmail$NAME_SUFFIX"

    private fun emailKey(userId: String?, canonicalEmail: String): String =
        "${contactPrefix(userId)}$canonicalEmail$EMAIL_SUFFIX"

    private fun canonicalizeEmail(email: String): String = email.trim().lowercase(Locale.ROOT)

    private companion object {
        const val PREFERENCES_NAME = "saved_email_contacts"
        const val CONTACT_PREFIX = "contact_"
        const val LOCAL_USER_KEY = "local"
        const val NAME_SUFFIX = "_name"
        const val EMAIL_SUFFIX = "_email"
    }
}

private val EMAIL_CONTACT_REGEX = Regex(
    """^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?(?:\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$""",
    RegexOption.IGNORE_CASE,
)

fun validateEmailContactInput(name: String, email: String): String? = when {
    name.isBlank() -> "Enter the contact name."
    email.isBlank() -> "Enter an email address."
    !isValidEmail(email) -> "Enter a valid email address."
    else -> null
}

/** Checks the shape of an email address without requiring a contact name. */
fun isValidEmail(email: String): Boolean = EMAIL_CONTACT_REGEX.matches(email.trim())
