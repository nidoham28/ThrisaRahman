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
    fun `uses neutral when the mood is missing or unrecognized`() {
        listOf(
            """{"response":"Hi","mood":"NotARealMood"}""",
            """{"response":"Hi"}""",
        ).forEach { raw ->
            assertEquals(
                AssistantResponse("Hi", "Neutral"),
                decodeAssistantResponse(raw, isFinal = true),
            )
        }
    }

    @Test
    fun `decodes fenced json and ignores unrelated fields`() {
        assertEquals(
            AssistantResponse("Hello!", "Happy"),
            decodeAssistantResponse(
                "```json\n{\"response\":\"Hello!\",\"mood\":\"Happy\",\"extra\":\"ignored\"}\n```",
                isFinal = true,
            ),
        )
    }

    @Test
    fun `rejects missing responses and non-string response values`() {
        listOf(
            """{"response":42,"mood":"Happy"}""",
            """{"mood":"Happy"}""",
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

    @Test
    fun `accepts the full companion emotion vocabulary`() {
        assistantMoods.forEach { mood ->
            assertEquals(
                AssistantResponse("A natural reply.", mood),
                decodeAssistantResponse(
                    """{"response":"A natural reply.","mood":"$mood"}""",
                    isFinal = true,
                ),
            )
        }
    }

    @Test
    fun `includes requested emotional and roleplay states`() {
        assertTrue(
            assistantMoods.containsAll(
                listOf("Jealous", "Unwell", "Naughty", "Pain", "Hungry", "Horrified"),
            ),
        )
    }
}
