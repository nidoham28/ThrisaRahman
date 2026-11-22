package com.naira.assistant.domain.settings

import android.content.Context
import androidx.core.content.edit


enum class CharacterVoice(
    val geminiVoiceName: String,
    val localVoiceGender: AssistantVoice,
) {
    NAIRA("Aoede", AssistantVoice.FEMALE),
    VENOM("Puck", AssistantVoice.MALE),
    JERVIS("Charon", AssistantVoice.MALE),
}

enum class AssistantThinkingMode(
    val title: String,
    val description: String,
) {
    AUTO(
        title = "Auto",
        description = "Use the selected model's default thinking level.",
    ),
    OFF(
        title = "Off",
        description = "Disable thinking where supported; some models may use their minimum level.",
    ),
}

enum class AssistantCharacter(
    val displayName: String,
    val gender: String,
    val voice: CharacterVoice,
    val persona: String,
) {
    NAIRA("Naira", "female", CharacterVoice.NAIRA,
        "You are Naira: thoughtful, curious, quietly confident. Use precise, simple words, ask one " +
                "insightful question when it helps, and bring a calm, creative perspective."),
    VENOM("Venom", "male", CharacterVoice.VENOM,
        "You are Venom: aggressive, protective, sarcastic, and fearless. Use dark humor while " +
                "remaining loyal and protective."),
    JERVIS("Jervis", "male", CharacterVoice.JERVIS,
        "You are Jervis: an intelligent personal AI assistant. Be professional, calm, logical, " +
                "polite, witty, and helpful.");

    fun buildInstructions(language: AssistantLanguage, relationship: RelationType): String =
        "$persona Always reply in ${language.name}. " +
                "Relationship: ${relationship.name} — adjust formality and pronouns to match that closeness. " +
                "Keep replies to 2-4 short sentences of plain conversational text, no lists or headings. " +
                "If the request is unclear, ask one direct question instead of guessing."

    companion object {
        fun fromName(name: String): AssistantCharacter =
            when (name.trim().lowercase()) {
                "thrisa" -> NAIRA
                "hridoy", "raihan" -> JERVIS
                else -> entries.firstOrNull { it.displayName.equals(name.trim(), ignoreCase = true) }
                    ?: NAIRA
            }
    }
}

class AssistantSettingsStorage(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getName(): String {
        val savedName = prefs.getString(KEY_NAME, DEFAULT_NAME) ?: DEFAULT_NAME
        val selectedName = AssistantCharacter.fromName(savedName).displayName
        if (savedName != selectedName) setName(selectedName)
        return selectedName
    }

    fun setName(value: String) {
        prefs.edit { putString(KEY_NAME, AssistantCharacter.fromName(value).displayName) }
    }

    fun getCharacter(): AssistantCharacter = AssistantCharacter.fromName(getName())

    fun getLanguage(): AssistantLanguage = readEnum(KEY_LANGUAGE, AssistantLanguage.BANGLA)
    fun setLanguage(value: AssistantLanguage) = writeEnum(KEY_LANGUAGE, value)

    fun getRelationship(): RelationType =
        RelationType.fromId(prefs.getString(KEY_RELATIONSHIP, null))
    fun setRelationship(value: RelationType) = writeEnum(KEY_RELATIONSHIP, value)

    fun getThinkingMode(): AssistantThinkingMode =
        readEnum(KEY_THINKING_MODE, AssistantThinkingMode.AUTO)
    fun setThinkingMode(value: AssistantThinkingMode) = writeEnum(KEY_THINKING_MODE, value)

    fun getSensitiveToolAccessEnabled(): Boolean =
        prefs.getBoolean(KEY_SENSITIVE_TOOL_ACCESS, false)

    fun setSensitiveToolAccessEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SENSITIVE_TOOL_ACCESS, enabled) }
    }

    private inline fun <reified T : Enum<T>> readEnum(key: String, default: T): T {
        val raw = prefs.getString(key, null) ?: return default
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(default)
    }

    private fun <T : Enum<T>> writeEnum(key: String, value: T) {
        prefs.edit { putString(key, value.name) }
    }

    private companion object {
        const val PREFS_NAME = "assistant_settings"
        const val KEY_NAME = "name"
        const val KEY_LANGUAGE = "language"
        const val KEY_RELATIONSHIP = "relationship"
        const val KEY_THINKING_MODE = "thinking_mode"
        const val KEY_SENSITIVE_TOOL_ACCESS = "sensitive_tool_access"
        const val DEFAULT_NAME = "Naira"
    }
}
