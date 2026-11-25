package com.naira.assistant.presentation.settings.model

import com.naira.assistant.domain.settings.AssistantProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelPresetsTest {

    @Test
    fun `every editable provider offers pre-added model names`() {
        listOf(AssistantProvider.GEMINI, AssistantProvider.OPENROUTER, AssistantProvider.CUSTOM)
            .forEach { provider ->
                val presets = ModelPresets.forProvider(provider)
                assertTrue("${provider.name} has no presets", presets.isNotEmpty())
                assertTrue("${provider.name} has blank presets", presets.none(String::isBlank))
                assertEquals("${provider.name} has duplicate presets", presets.toSet().size, presets.size)
            }
    }

    @Test
    fun `fixed providers offer no presets`() {
        assertEquals(emptyList<String>(), ModelPresets.forProvider(AssistantProvider.TOKENS_BD))
    }

    @Test
    fun `presets include each provider default model`() {
        listOf(AssistantProvider.GEMINI, AssistantProvider.OPENROUTER)
            .forEach { provider ->
                assertTrue(
                    "${provider.name} presets are missing ${provider.info.defaultModelId}",
                    provider.info.defaultModelId in ModelPresets.forProvider(provider),
                )
            }
    }
}
