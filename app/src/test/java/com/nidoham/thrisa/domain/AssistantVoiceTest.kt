package com.nidoham.thrisa.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantVoiceTest {
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
