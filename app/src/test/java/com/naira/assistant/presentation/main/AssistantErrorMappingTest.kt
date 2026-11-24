package com.naira.assistant.presentation.main

import com.naira.assistant.domain.settings.AssistantProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantErrorMappingTest {
    @Test
    fun `maps OpenRouter key rejection to actionable guidance`() {
        val text = assistantErrorText(
            RuntimeException("Missing Authentication header"),
            AssistantProvider.OPENROUTER,
        )

        assertTrue(text.startsWith("OpenRouter rejected your API key."))
        assertTrue(text.contains("Settings → Providers & API Keys → API Keys"))
    }

    @Test
    fun `maps unknown well-formed OpenRouter key the same way`() {
        val text = assistantErrorText(
            RuntimeException("User not found."),
            AssistantProvider.OPENROUTER,
        )

        assertTrue(text.startsWith("OpenRouter rejected your API key."))
    }

    @Test
    fun `keeps generic auth failure wording for other providers`() {
        val text = assistantErrorText(
            RuntimeException("Failed to get response: 401 nope"),
            AssistantProvider.GEMINI,
        )

        assertEquals(
            "Authentication failed. Check your Gemini API key.\nFailed to get response: 401 nope",
            text,
        )
    }

    @Test
    fun `passes unmatched provider messages through`() {
        val text = assistantErrorText(
            RuntimeException("Model not found"),
            AssistantProvider.OPENROUTER,
        )

        assertEquals("Model not found", text)
    }

    @Test
    fun `reports reaching the tool limit when no response text was produced`() {
        assertTrue(
            assistantResponseText(
                response = null,
                currentText = "",
                stepLimitReached = true,
            ).contains("tool-call limit"),
        )
    }

    @Test
    fun `reports the tool limit after a partial response`() {
        val text = assistantResponseText(
            response = null,
            currentText = "I found part of the answer.",
            stepLimitReached = true,
        )

        assertTrue(text.startsWith("I found part of the answer."))
        assertTrue(text.contains("tool-call limit"))
    }
}
