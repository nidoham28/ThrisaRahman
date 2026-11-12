package com.naira.assistant.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacySettingsCleanupTest {
    @Test
    fun `preserves API keys provider selection and OpenRouter model preferences`() {
        listOf(
            "key_OPENROUTER_user",
            "selected_provider",
            "selected_provider_user",
            "openrouter_model",
            "openrouter_model_user",
        ).forEach { key ->
            assertTrue("$key should be retained", isSupportedApiPreferenceKey(key))
        }
    }

    @Test
    fun `does not preserve unsupported preference keys`() {
        assertFalse(isSupportedApiPreferenceKey("old_setting"))
    }
}
