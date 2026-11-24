package com.naira.assistant.presentation.main

import com.naira.assistant.ai.AssistantApiService
import com.naira.assistant.domain.settings.AssistantProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantModelSelectionTest {
    @Test
    fun `uses configured OpenRouter model`() {
        assertEquals(
            "anthropic/claude-3.7-sonnet",
            resolveAssistantModelId(
                AssistantProvider.OPENROUTER,
                " anthropic/claude-3.7-sonnet ",
            ),
        )
    }

    @Test
    fun `uses default OpenRouter model when no model is configured`() {
        assertEquals(
            AssistantApiService.DEFAULT_MODEL_OPENROUTER,
            resolveAssistantModelId(AssistantProvider.OPENROUTER, null),
        )
    }

    @Test
    fun `keeps Gemini model selection independent of OpenRouter`() {
        assertEquals(
            AssistantApiService.DEFAULT_MODEL_ID,
            resolveAssistantModelId(AssistantProvider.GEMINI, "anthropic/claude-3.7-sonnet"),
        )
    }

    @Test
    fun `uses the Tokens BD default model`() {
        assertEquals(
            "deepseek-ai/deepseek-v4.1-flash-1017",
            resolveAssistantModelId(AssistantProvider.TOKENS_BD, null),
        )
    }
}
