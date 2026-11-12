package com.naira.assistant.domain

import android.content.Context
import androidx.core.content.edit

enum class CharacterVoice(
    val geminiVoiceName: String,
    val localVoiceGender: AssistantVoice,
) {
    THRISA("Leda", AssistantVoice.FEMALE),
    NAIRA("Aoede", AssistantVoice.FEMALE),
    HRIDOY("Puck", AssistantVoice.MALE),
    RAIHAN("Charon", AssistantVoice.MALE),
}

enum class AssistantCharacter(
    val displayName: String,
    val gender: String,
    val voice: CharacterVoice,
    val persona: String,
) {
    THRISA("Thrisa", "female", CharacterVoice.THRISA,
        "You are Thrisa: warm, expressive, emotionally perceptive. Read the user's mood before " +
                "advising, use gentle humor and affectionate encouragement, and stay candid."),
    NAIRA("Naira", "female", CharacterVoice.NAIRA,
        "You are Naira: thoughtful, curious, quietly confident. Use precise, simple words, ask one " +
                "insightful question when it helps, and bring a calm, creative perspective."),
    HRIDOY("Hridoy", "male", CharacterVoice.HRIDOY,
        "You are Hridoy: upbeat, direct, resourceful. Give practical ideas in energetic, natural " +
                "language, with light humor and never pushy."),
    RAIHAN("Raihan", "male", CharacterVoice.RAIHAN,
        "You are Raihan: composed, observant, wryly funny. Be measured and reassuring, point out " +
                "useful details, and give clear perspective in a casual tone.");

    fun buildInstructions(language: AssistantLanguage, relationship: RelationType): String =
        "$persona Always reply in ${language.name}. " +
                "Relationship: ${relationship.name} — adjust formality and pronouns to match that closeness. " +
                "Keep replies to 2-4 short sentences of plain conversational text, no lists or headings. " +
                "If the request is unclear, ask one direct question instead of guessing."

    companion object {
        fun fromName(name: String): AssistantCharacter =
            entries.firstOrNull { it.displayName.equals(name.trim(), ignoreCase = true) } ?: THRISA
    }
}

class AssistantSettingsStorage(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getName(): String = prefs.getString(KEY_NAME, DEFAULT_NAME) ?: DEFAULT_NAME

    fun setName(value: String) {
        prefs.edit { putString(KEY_NAME, value.trim()) }
    }

    fun getCharacter(): AssistantCharacter = AssistantCharacter.fromName(getName())

    fun getLanguage(): AssistantLanguage = readEnum(KEY_LANGUAGE, AssistantLanguage.BANGLA)
    fun setLanguage(value: AssistantLanguage) = writeEnum(KEY_LANGUAGE, value)

    fun getRelationship(): RelationType = readEnum(KEY_RELATIONSHIP, RelationType.STRANGER)
    fun setRelationship(value: RelationType) = writeEnum(KEY_RELATIONSHIP, value)

    fun getVoice(): AssistantVoice = readEnum(KEY_VOICE, AssistantVoice.FEMALE)
    fun setVoice(value: AssistantVoice) = writeEnum(KEY_VOICE, value)

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
        const val KEY_VOICE = "voice"
        const val DEFAULT_NAME = "Assistant"
    }
}