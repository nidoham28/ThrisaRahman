package com.naira.assistant.domain

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
}
