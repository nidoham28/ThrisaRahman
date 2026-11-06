package com.nidoham.thrisa.domain

import android.content.Context
import androidx.core.content.edit

enum class AssistantVoice(
    val label: String,
    val geminiVoiceName: String,
) {
    FEMALE("Female", "Leda"),
    MALE("Male", "Puck");

    companion object {
        fun fromStorageValue(value: String?): AssistantVoice =
            entries.firstOrNull { it.name == value } ?: FEMALE
    }
}

/** Stores speech preferences locally on this device. */
class SpeechSettingsStorage(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getEngine(): String = prefs.getString(KEY_ENGINE, SYSTEM_ENGINE) ?: SYSTEM_ENGINE

    fun setEngine(engine: String) {
        prefs.edit { putString(KEY_ENGINE, engine) }
    }

    fun getVoice(): AssistantVoice =
        AssistantVoice.fromStorageValue(prefs.getString(KEY_VOICE, null))

    fun setVoice(voice: AssistantVoice) {
        prefs.edit { putString(KEY_VOICE, voice.name) }
    }

    private companion object {
        const val PREFS_NAME = "speech_settings"
        const val KEY_ENGINE = "tts_engine"
        const val KEY_VOICE = "assistant_voice"
        const val SYSTEM_ENGINE = "SYSTEM"
    }
}
