package com.naira.assistant.domain

import android.content.Context

enum class AssistantVoice(
    val label: String,
    val geminiVoiceName: String,
) {
    FEMALE("Female", "Leda"),
    MALE("Male", "Puck");

    companion object {
        /** Case-insensitive so cloud rows written in any casing resolve; unknown falls back to [FEMALE]. */
        fun fromStorageValue(value: String?): AssistantVoice =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: FEMALE
    }
}

/** Holds the currently loaded cloud speech engine in memory. */
class SpeechSettingsStorage(context: Context) {
    private val assistantSettingsStorage = AssistantSettingsStorage(context.applicationContext)

    fun getEngine(): String = engine

    fun setEngine(engine: String) {
        SpeechSettingsStorage.engine = engine
    }

    fun getVoice(): AssistantVoice = assistantSettingsStorage.getVoice()

    fun setVoice(voice: AssistantVoice) {
        assistantSettingsStorage.setVoice(voice)
    }

    fun getCharacter(): AssistantCharacter = assistantSettingsStorage.getCharacter()

    private companion object {
        const val SYSTEM_ENGINE = "SYSTEM"

        @Volatile
        var engine: String = SYSTEM_ENGINE
    }
}
