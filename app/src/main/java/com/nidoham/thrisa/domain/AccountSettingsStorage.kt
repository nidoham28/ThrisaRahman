package com.nidoham.thrisa.domain

import android.content.Context
import androidx.core.content.edit

/** Gender options for the account profile. */
enum class Gender(val label: String) {
    MALE("Male"),
    FEMALE("Female"),
    OTHER("Other"),
    PREFER_NOT_TO_SAY("Prefer not to say");

    companion object {
        fun fromLabel(raw: String?): Gender =
            entries.firstOrNull { it.label == raw } ?: PREFER_NOT_TO_SAY
    }
}

/** Languages the assistant can reply in. */
enum class AssistantLanguage(val code: String, val label: String) {
    ENGLISH("en", "English"),
    BANGLA("bn", "Bangla");

    companion object {
        fun fromCode(raw: String?): AssistantLanguage =
            entries.firstOrNull { it.code == raw } ?: BANGLA
    }
}

/** BCP-47 speech tag for an [AssistantLanguage] (region disambiguates Bengali vs English STT/TTS voices). */
fun AssistantLanguage.sttLanguageTag(): String = when (this) {
    AssistantLanguage.BANGLA -> "bn-BD"
    AssistantLanguage.ENGLISH -> "en-US"
}

/** Stores the account profile and assistant identity preferences on this device. */
class AccountSettingsStorage(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getUserName(): String = prefs.getString(KEY_USERNAME, "").orEmpty()

    fun setUserName(value: String) {
        prefs.edit { putString(KEY_USERNAME, value.trim()) }
    }

    fun getGender(): Gender = Gender.fromLabel(prefs.getString(KEY_GENDER, null))

    fun setGender(value: Gender) {
        prefs.edit { putString(KEY_GENDER, value.label) }
    }

    fun getPhoneNumber(): String = prefs.getString(KEY_PHONE, "").orEmpty()

    fun setPhoneNumber(value: String) {
        prefs.edit { putString(KEY_PHONE, value.trim()) }
    }

    fun getAssistantName(): String =
        prefs.getString(KEY_ASSISTANT_NAME, DEFAULT_ASSISTANT_NAME)
            ?.trim()?.takeUnless { it.isEmpty() } ?: DEFAULT_ASSISTANT_NAME

    fun setAssistantName(value: String) {
        val name = value.trim()
        prefs.edit { putString(KEY_ASSISTANT_NAME, if (name.isEmpty()) DEFAULT_ASSISTANT_NAME else name) }
    }

    fun getLanguage(): AssistantLanguage = AssistantLanguage.fromCode(prefs.getString(KEY_LANGUAGE, null))

    fun setLanguage(value: AssistantLanguage) {
        prefs.edit { putString(KEY_LANGUAGE, value.code) }
    }

    private companion object {
        const val PREFS_NAME = "account_settings"
        const val KEY_USERNAME = "username"
        const val KEY_GENDER = "gender"
        const val KEY_PHONE = "phone_number"
        const val KEY_ASSISTANT_NAME = "assistant_name"
        const val KEY_LANGUAGE = "assistant_language"
        const val DEFAULT_ASSISTANT_NAME = "Thrisa"
    }
}
