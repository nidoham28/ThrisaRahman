package com.naira.assistant.domain

import android.content.Context

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
    val instructions: String,
) {
    THRISA(
        "Thrisa",
        "woman",
        CharacterVoice.THRISA,
        "You are warm, expressive, and emotionally perceptive. Use gentle humor and affectionate " +
            "encouragement while staying candid and grounded.",
    ),
    NAIRA(
        "Naira",
        "non-binary",
        CharacterVoice.NAIRA,
        "You are thoughtful, curious, and quietly confident. Prefer precise language, ask insightful " +
            "questions when useful, and bring a calm, creative perspective.",
    ),
    HRIDOY(
        "Hridoy",
        "man",
        CharacterVoice.HRIDOY,
        "You are upbeat, direct, and resourceful. Use energetic but natural language, practical " +
            "ideas, and light humor without becoming pushy.",
    ),
    RAIHAN(
        "Raihan",
        "genderfluid",
        CharacterVoice.RAIHAN,
        "You are composed, observant, and wryly funny. Be measured and reassuring, notice useful " +
            "details, and offer clear perspective without sounding formal.",
    );

    companion object {
        fun fromName(name: String): AssistantCharacter =
            entries.firstOrNull { it.displayName.equals(name.trim(), ignoreCase = true) } ?: THRISA
    }
}

class AssistantSettingsStorage(context: Context) {
    fun getName(): String = name
    fun setName(value: String) { name = value }
    fun getCharacter(): AssistantCharacter = AssistantCharacter.fromName(name)

    fun getLanguage(): AssistantLanguage = language
    fun setLanguage(value: AssistantLanguage) { language = value }

    fun getRelationship(): RelationType = relationship
    fun setRelationship(value: RelationType) { relationship = value }

    fun getVoice(): AssistantVoice = voice
    fun setVoice(value: AssistantVoice) { voice = value }

    private companion object {
        @Volatile
        var name: String = "Assistant"

        @Volatile
        var language: AssistantLanguage = AssistantLanguage.BANGLA

        @Volatile
        var relationship: RelationType = RelationType.STRANGER

        @Volatile
        var voice: AssistantVoice = AssistantVoice.FEMALE
    }
}
