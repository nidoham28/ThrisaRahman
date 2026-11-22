package com.naira.assistant.core.prompt.character.util

data class Character(
    var avatar: String? = null,
    var name: String? = null,
    var type: CharacterType = CharacterType.ANIME,
    var gender: CharacterGender = CharacterGender.CUSTOM,
    var description: String? = null,
    var personality: List<String> = emptyList(),
    var interests: List<String> = emptyList()
)
