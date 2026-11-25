package com.naira.assistant.domain.settings

import android.content.Context


enum class Gender(val id: String, val label: String) {
    MALE("male", "Male"),
    FEMALE("female", "Female"),
    UNSPECIFIED("unspecified", "Unspecified");

    companion object {
        fun fromId(raw: String?): Gender =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) || it.label.equals(raw, ignoreCase = true) }
                ?: UNSPECIFIED
    }
}

data class LanguageManager(
    val id: String,
    val label: String,
) {
    companion object {
        val ENGLISH = LanguageManager(id = "Eng", label = "English")
        val BANGLA = LanguageManager(id = "Ban", label = "Bangla")

        /**
         * Resolves a language id written by this app ("Eng"/"Ban"), a locale
         * code ("en"/"bn") or a human name ("english"/"bangla") —
         * case-insensitively, on the first 3 characters.
         *
         * Null, blank or unknown input falls back to [BANGLA] (the app
         * default) instead of throwing: the settings sync runs this for every
         * downloaded row, and one unsupported value must degrade that single
         * setting, not abort the whole sync.
         */
        fun fromId(raw: String?): LanguageManager {
            if (raw.isNullOrBlank()) return BANGLA
            return when (raw.trim().lowercase().take(3)) {
                "eng", "en" -> ENGLISH
                else -> BANGLA
            }
        }
    }
}

/** Languages the assistant can reply in. */
enum class AssistantLanguage(val code: String, val language: LanguageManager) {
    ENGLISH("en", LanguageManager.ENGLISH),
    BANGLA("bn", LanguageManager.BANGLA);

    val label: String
        get() = language.label

    companion object {
        fun fromCode(raw: String?): AssistantLanguage =
            entries.firstOrNull { it.code == raw } ?: BANGLA

        fun fromDatabaseId(raw: String?): AssistantLanguage =
            entries.firstOrNull { it.language == LanguageManager.fromId(raw) } ?: BANGLA
    }
}

/** BCP-47 speech tag for an [AssistantLanguage] (region disambiguates Bengali vs English STT/TTS voices). */
fun AssistantLanguage.sttLanguageTag(): String = when (this) {
    AssistantLanguage.BANGLA -> "bn-BD"
    AssistantLanguage.ENGLISH -> "en-US"
}

/** Keeps the currently loaded cloud profile in memory for app consumers. */
class AccountSettingsStorage(context: Context) {
    fun getUserName(): String = username
    fun setUserName(value: String) { username = value }

    fun getGender(): Gender = gender
    fun setGender(value: Gender) { gender = value }

    fun getPhoneNumber(): String = phoneNumber
    fun setPhoneNumber(value: String) { phoneNumber = value }

    fun getEmail(): String = email
    fun setEmail(value: String) { email = value }

    private companion object {
        @Volatile
        var username: String = ""

        @Volatile
        var gender: Gender = Gender.UNSPECIFIED

        @Volatile
        var phoneNumber: String = ""

        @Volatile
        var email: String = ""

    }
}
