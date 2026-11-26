package com.naira.assistant.domain.settings

import android.content.Context


enum class AssistantVoice(
    val label: String,
    val geminiVoiceName: String,
    val geminiVoiceNames: List<String>,
) {
    FEMALE("Female", "Leda", listOf(
        "Achernar", "Aoede", "Autonoe", "Callirrhoe", "Despina", "Erinome", "Gacrux",
        "Kore", "Laomedeia", "Leda", "Pulcherrima", "Sulafat", "Vindemiatrix", "Zephyr",
    )),
    MALE("Male", "Puck", listOf(
        "Achird", "Algenib", "Algieba", "Alnilam", "Charon", "Enceladus", "Fenrir",
        "Iapetus", "Orus", "Puck", "Rasalgethi", "Sadachbia", "Sadaltager", "Schedar",
        "Umbriel", "Zubenelgenubi",
    ));

    companion object {
        /** Case-insensitive so cloud rows written in any casing resolve; unknown falls back to [FEMALE]. */
        fun fromStorageValue(value: String?): AssistantVoice =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: FEMALE
    }
}

/** Holds the currently loaded cloud speech engine in memory. */
class SpeechSettingsStorage(context: Context) {
    private val assistantSettingsStorage = AssistantSettingsStorage(context.applicationContext)
    private val preferences = context.applicationContext.getSharedPreferences("speech_settings", Context.MODE_PRIVATE)

    fun getEngine(): String = preferences.getString(KEY_ENGINE, engine) ?: engine

    fun setEngine(engine: String) {
        SpeechSettingsStorage.engine = engine
        preferences.edit().putString(KEY_ENGINE, engine).apply()
    }

    fun getCharacter(): AssistantCharacter = assistantSettingsStorage.getCharacter()

    fun getGeminiTtsModel(): String = preferences.getString(KEY_GEMINI_TTS_MODEL, DEFAULT_GEMINI_TTS_MODEL)
        ?: DEFAULT_GEMINI_TTS_MODEL

    fun setGeminiTtsModel(value: String) {
        preferences.edit().putString(KEY_GEMINI_TTS_MODEL, value.trim()).apply()
    }

    fun getGeminiTtsVoice(): String = preferences.getString(KEY_GEMINI_TTS_VOICE, DEFAULT_GEMINI_TTS_VOICE)
        ?: DEFAULT_GEMINI_TTS_VOICE

    fun setGeminiTtsVoice(value: String) {
        preferences.edit().putString(KEY_GEMINI_TTS_VOICE, value.trim()).apply()
    }

    fun getGeminiTtsVoice(character: AssistantCharacter): String =
        getGeminiTtsVoice().takeIf { saved ->
            character.voice.localVoiceGender.geminiVoiceNames.any { it.equals(saved, ignoreCase = true) }
        } ?: character.voice.geminiVoiceName

    fun getCustomTtsVoice(): String = preferences.getString(KEY_CUSTOM_TTS_VOICE, DEFAULT_CUSTOM_TTS_VOICE)
        ?: DEFAULT_CUSTOM_TTS_VOICE

    fun setCustomTtsVoice(value: String) {
        preferences.edit().putString(KEY_CUSTOM_TTS_VOICE, value.trim()).apply()
    }

    fun getCustomTtsModel(): String = preferences.getString(KEY_CUSTOM_TTS_MODEL, DEFAULT_CUSTOM_TTS_MODEL)
        ?: DEFAULT_CUSTOM_TTS_MODEL

    fun setCustomTtsModel(value: String) {
        preferences.edit().putString(KEY_CUSTOM_TTS_MODEL, value.trim()).apply()
    }

    fun getCustomTtsBaseUrl(): String = preferences.getString(KEY_CUSTOM_TTS_BASE_URL, "").orEmpty()

    fun setCustomTtsBaseUrl(value: String) {
        preferences.edit().putString(KEY_CUSTOM_TTS_BASE_URL, value.trim().trimEnd('/')).apply()
    }

    private companion object {
        const val KEY_GEMINI_TTS_MODEL = "gemini_tts_model"
        const val KEY_GEMINI_TTS_VOICE = "gemini_tts_voice"
        const val KEY_CUSTOM_TTS_VOICE = "custom_tts_voice"
        const val KEY_CUSTOM_TTS_MODEL = "custom_tts_model"
        const val KEY_CUSTOM_TTS_BASE_URL = "custom_tts_base_url"
        const val KEY_ENGINE = "speech_engine"
        const val DEFAULT_GEMINI_TTS_MODEL = "gemini-2.5-flash-preview-tts"
        const val DEFAULT_GEMINI_TTS_VOICE = "Leda"
        const val DEFAULT_CUSTOM_TTS_VOICE = "alloy"
        const val DEFAULT_CUSTOM_TTS_MODEL = "gpt-4o-mini-tts"
        const val SYSTEM_ENGINE = "SYSTEM"

        @Volatile
        var engine: String = SYSTEM_ENGINE
    }
}
