package com.naira.assistant.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantVoiceTest {
    @Test
    fun `assistant characters have distinct profiles and voice names`() {
        val characters = AssistantCharacter.entries

        assertEquals(
            listOf("Thrisa", "Naira", "Hridoy", "Raihan"),
            characters.map { it.displayName },
        )
        assertEquals(4, characters.map { it.voice.geminiVoiceName }.distinct().size)
        assertEquals(4, characters.map { it.gender }.distinct().size)
        assertTrue(characters.all { it.instructions.isNotBlank() })
    }

    @Test
    fun `assistant character is resolved by saved display name`() {
        assertEquals(AssistantCharacter.NAIRA, AssistantCharacter.fromName("naira"))
        assertEquals(AssistantCharacter.THRISA, AssistantCharacter.fromName("Assistant"))
    }

    @Test
    fun `maps selected voice to Gemini TTS voice names`() {
        assertEquals("Leda", AssistantVoice.FEMALE.geminiVoiceName)
        assertEquals("Puck", AssistantVoice.MALE.geminiVoiceName)
    }

    @Test
    fun `defaults missing saved voice to female`() {
        assertEquals(AssistantVoice.FEMALE, AssistantVoice.fromStorageValue(null))
    }
}
