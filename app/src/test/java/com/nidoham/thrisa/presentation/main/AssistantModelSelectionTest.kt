package com.nidoham.thrisa.presentation.main

import com.nidoham.thrisa.ai.AssistantApiService
import com.nidoham.thrisa.domain.AssistantProvider
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
}
