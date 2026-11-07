package com.nidoham.thrisa.presentation.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantMoodTest {

    @Test
    fun `decodes the two-field response and normalizes mood casing`() {
        assertEquals(
            AssistantResponse("Hello, friend!", "Happy"),
            decodeAssistantResponse(
                """{"response":"Hello, friend!","mood":"happy"}""",
                isFinal = true,
            ),
        )
    }

    @Test
    fun `extracts partial response text for chat and voice while streaming`() {
        assertEquals(
            AssistantResponse("She said \"hi", null),
            decodeAssistantResponse(
                "{\"response\":\"She said \\\"hi",
                isFinal = false,
            ),
        )
        assertNull(decodeAssistantResponse("""{"res""", isFinal = false))
    }

    @Test
    fun `rejects invalid moods extra fields and non-string values`() {
        listOf(
            """{"response":"Hi","mood":"Worried"}""",
            """{"response":"Hi","mood":"Happy","details":"extra"}""",
            """{"response":42,"mood":"Happy"}""",
        ).forEach { raw ->
            assertThrows(IllegalStateException::class.java) {
                decodeAssistantResponse(raw, isFinal = true)
            }
        }
    }

    @Test
    fun `session mood uses the most frequent label and latest mood breaks ties`() {
        assertEquals(
            "Sad",
            mostFrequentAssistantMood(listOf("Happy", "Sad", "Happy", "Sad")),
        )
        assertEquals("Calm", mostFrequentAssistantMood(listOf("Happy", "Calm")))
        assertNull(mostFrequentAssistantMood(emptyList()))
    }

    @Test
    fun `home mood captions contain two or three words`() {
        (assistantMoods + null).forEach { mood ->
            val words = assistantMoodCaption(mood).split(' ')
            assertTrue(words.size in 2..3)
        }
    }
}
