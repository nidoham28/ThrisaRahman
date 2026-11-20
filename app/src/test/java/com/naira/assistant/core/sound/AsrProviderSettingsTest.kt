package com.naira.assistant.core.sound

import com.naira.assistant.domain.AsrProvider
import com.naira.assistant.domain.AsrProviderSettings
import me.rerere.asr.ASRProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AsrProviderSettingsTest {

    @Test
    fun `maps each configured provider to its speech module setting`() {
        val settings = AsrProvider.entries
            .filterNot { it == AsrProvider.SYSTEM }
            .map { provider ->
                AsrProviderSettings(
                    provider = provider,
                    apiKeys = mapOf(provider to "provider-key"),
                ).toModuleProvider()
            }

        assertTrue(settings[0] is ASRProviderSetting.OpenAIRealtime)
        assertTrue(settings[1] is ASRProviderSetting.DashScope)
        assertTrue(settings[2] is ASRProviderSetting.Volcengine)
        assertTrue(settings[3] is ASRProviderSetting.MiMo)
        assertTrue(settings[4] is ASRProviderSetting.Step)
        assertTrue(settings[5] is ASRProviderSetting.DeepSeek)
        assertTrue(settings.all { setting ->
            when (setting) {
                is ASRProviderSetting.OpenAIRealtime -> setting.apiKey == "provider-key"
                is ASRProviderSetting.DashScope -> setting.apiKey == "provider-key"
                is ASRProviderSetting.Volcengine -> setting.apiKey == "provider-key"
                is ASRProviderSetting.MiMo -> setting.apiKey == "provider-key"
                is ASRProviderSetting.Step -> setting.apiKey == "provider-key"
                is ASRProviderSetting.DeepSeek -> setting.apiKey == "provider-key"
            }
        })
    }

    @Test
    fun `manual end of turn is required only for batch providers`() {
        assertEquals(false, AsrProvider.SYSTEM.requiresManualTurnEnd)
        assertEquals(false, AsrProvider.OPENAI_REALTIME.requiresManualTurnEnd)
        assertEquals(false, AsrProvider.DASHSCOPE.requiresManualTurnEnd)
        assertEquals(false, AsrProvider.VOLCENGINE.requiresManualTurnEnd)
        assertEquals(true, AsrProvider.MIMO.requiresManualTurnEnd)
        assertEquals(true, AsrProvider.STEP.requiresManualTurnEnd)
        assertEquals(true, AsrProvider.DEEPSEEK.requiresManualTurnEnd)
    }
}
