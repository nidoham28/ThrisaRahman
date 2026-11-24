package com.naira.assistant.domain

import com.naira.assistant.domain.settings.AssistantProvider
import com.naira.assistant.domain.settings.resolveSelectedProvider
import org.junit.Assert.assertEquals
import org.junit.Test




class ApiKeyStorageTest {
    @Test
    fun `account provider takes precedence over device fallback`() {
        assertEquals(
            AssistantProvider.OPENROUTER,
            resolveSelectedProvider("OPENROUTER", "GEMINI"),
        )
    }

    @Test
    fun `device provider restores selection when account value is not available yet`() {
        assertEquals(
            AssistantProvider.OPENROUTER,
            resolveSelectedProvider(null, "openrouter"),
        )
    }

    @Test
    fun `falls back to Gemini when a removed provider is stored`() {
        assertEquals(
            AssistantProvider.GEMINI,
            resolveSelectedProvider("groq", "openrouter"),
        )
    }

    @Test
    fun `restores Tokens BD provider from saved selection`() {
        assertEquals(
            AssistantProvider.TOKENS_BD,
            resolveSelectedProvider("tokens_bd", "gemini"),
        )
    }
}
