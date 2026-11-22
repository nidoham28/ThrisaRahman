package com.naira.assistant.domain

import com.naira.assistant.domain.settings.AssistantCharacter
import com.naira.assistant.domain.settings.AssistantVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test





class AssistantVoiceTest {
    @Test
    fun `assistant characters have distinct profiles and voice names`() {
        val characters = AssistantCharacter.entries

        assertEquals(
            listOf("Naira", "Venom", "Jervis"),
            characters.map { it.displayName },
        )
        assertEquals(3, characters.map { it.voice.geminiVoiceName }.distinct().size)
        assertEquals(2, characters.map { it.gender }.distinct().size)
        assertTrue(characters.all { it.persona.isNotBlank() })
    }

    @Test
    fun `assistant character is resolved by saved display name`() {
        assertEquals(AssistantCharacter.NAIRA, AssistantCharacter.fromName("naira"))
        assertEquals(AssistantCharacter.NAIRA, AssistantCharacter.fromName("Assistant"))
        assertEquals(AssistantCharacter.NAIRA, AssistantCharacter.fromName("Thrisa"))
        assertEquals(AssistantCharacter.JERVIS, AssistantCharacter.fromName("Hridoy"))
        assertEquals(AssistantCharacter.JERVIS, AssistantCharacter.fromName("Raihan"))
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
