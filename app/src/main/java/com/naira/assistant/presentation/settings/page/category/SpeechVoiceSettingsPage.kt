@file:Suppress("SpellCheckingInspection")

package com.naira.assistant.presentation.settings.page.category

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsVoice
import com.naira.assistant.core.sound.SpeechEngine
import com.naira.assistant.domain.settings.AssistantProvider
import com.naira.assistant.presentation.settings.component.TrailingSwitch
import com.naira.assistant.presentation.settings.model.EditField
import com.naira.assistant.presentation.settings.model.SettingsNode
import com.naira.assistant.presentation.settings.page.CategoryNameUi
import com.naira.assistant.presentation.settings.page.SettingsPageContext

internal object SpeechVoiceSettingsPage : CategoryNameUi() {
    override fun build(context: SettingsPageContext): SettingsNode.Category = with(context) {
        val state = this.state
        SettingsNode.Category(
            id = "voice", title = "Speech Services", subtitle = "Manage speech and voice settings.",
            icon = Icons.Outlined.SettingsVoice,
            children = listOf(
                SettingsNode.Item(
                    id = "system-tts", title = "System text-to-speech",
                    subtitle = if (state.speechEngine == SpeechEngine.SYSTEM) "Enabled on this device" else "Disabled",
                    icon = Icons.Outlined.GraphicEq,
                    onClick = {
                        onSaveSpeechEngine(
                            if (state.speechEngine == SpeechEngine.SYSTEM) SpeechEngine.ADVANCED
                            else SpeechEngine.SYSTEM,
                        )
                    },
                    trailing = {
                        TrailingSwitch(
                            label = "System text-to-speech",
                            checked = state.speechEngine == SpeechEngine.SYSTEM,
                            onCheckedChange = { enabled ->
                                onSaveSpeechEngine(
                                    if (enabled) SpeechEngine.SYSTEM else SpeechEngine.ADVANCED,
                                )
                            },
                        )
                    },
                ),
                SettingsNode.Category(
                    id = "gemini-tts", title = "Google Gemini TTS",
                    subtitle = if (state.speechEngine == SpeechEngine.ADVANCED) "Enabled" else "Disabled",
                    icon = Icons.Outlined.GraphicEq,
                    trailing = {
                        TrailingSwitch(
                            label = "Google Gemini TTS",
                            checked = state.speechEngine == SpeechEngine.ADVANCED,
                            onCheckedChange = { enabled ->
                                onSaveSpeechEngine(
                                    if (enabled) SpeechEngine.ADVANCED else SpeechEngine.SYSTEM,
                                )
                            },
                        )
                    },
                    children = listOf(
                        SettingsNode.Item(
                            id = "gemini-tts-key", title = "API key",
                            subtitle = "Use the Gemini API key saved in Providers & API Keys",
                            icon = Icons.Outlined.Key,
                            onClick = { onEditKey(AssistantProvider.GEMINI) },
                        ),
                        SettingsNode.Item(
                            id = "gemini-tts-model", title = "Model selection",
                            subtitle = state.geminiTtsModel,
                            icon = Icons.Outlined.AutoAwesome,
                            onClick = { onEditField(EditField.GEMINI_TTS_MODEL) },
                        ),
                        SettingsNode.Item(
                            id = "gemini-tts-voice", title = "Voice selection",
                            subtitle = state.geminiTtsVoice,
                            icon = Icons.Outlined.GraphicEq,
                            onClick = { onEditField(EditField.GEMINI_TTS_VOICE) },
                        ),
                    ),
                ),
                SettingsNode.Category(
                    id = "custom-tts", title = "Custom TTS Provider",
                    subtitle = if (state.speechEngine == SpeechEngine.CUSTOM) "Enabled" else "Disabled",
                    icon = Icons.Outlined.GraphicEq,
                    trailing = {
                        TrailingSwitch(
                            label = "Custom TTS Provider",
                            checked = state.speechEngine == SpeechEngine.CUSTOM,
                            onCheckedChange = { enabled ->
                                onSaveSpeechEngine(if (enabled) SpeechEngine.CUSTOM else SpeechEngine.SYSTEM)
                            },
                        )
                    },
                    children = listOf(
                        SettingsNode.Item(
                            id = "custom-tts-url", title = "API base URL",
                            subtitle = state.customTtsBaseUrl.ifBlank { "Set your OpenAI-compatible API URL" },
                            icon = Icons.Outlined.Settings,
                            onClick = { onEditField(EditField.CUSTOM_TTS_BASE_URL) },
                        ),
                        SettingsNode.Item(
                            id = "custom-tts-key", title = "API key",
                            subtitle = if (state.customTtsApiKey.isBlank()) "Not configured" else "Configured",
                            icon = Icons.Outlined.Key,
                            onClick = onEditCustomTtsApiKey,
                        ),
                        SettingsNode.Item(
                            id = "custom-tts-model", title = "Model selection",
                            subtitle = state.customTtsModel.ifBlank { "Set your model ID" },
                            icon = Icons.Outlined.AutoAwesome,
                            onClick = { onEditField(EditField.CUSTOM_TTS_MODEL) },
                        ),
                        SettingsNode.Item(
                            id = "custom-tts-voice", title = "Voice selection",
                            subtitle = state.customTtsVoice,
                            icon = Icons.Outlined.GraphicEq,
                            onClick = { onEditField(EditField.CUSTOM_TTS_VOICE) },
                        ),
                    ),
                ),
            )
        )
    }
}
