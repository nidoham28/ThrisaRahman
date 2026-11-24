@file:Suppress("SpellCheckingInspection")

package com.naira.assistant.presentation.settings.page.category

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VpnKey
import com.naira.assistant.core.theme.Aqua
import com.naira.assistant.domain.settings.AssistantProvider
import com.naira.assistant.domain.settings.AssistantThinkingMode
import com.naira.assistant.presentation.settings.component.Success
import com.naira.assistant.presentation.settings.component.TrailingBadge
import com.naira.assistant.presentation.settings.component.TrailingSwitch
import com.naira.assistant.presentation.settings.model.EditField
import com.naira.assistant.presentation.settings.model.SettingsNode
import com.naira.assistant.presentation.settings.page.CategoryNameUi
import com.naira.assistant.presentation.settings.page.SettingsPageContext

internal object AiProvidersSettingsPage : CategoryNameUi() {
    override fun build(context: SettingsPageContext): SettingsNode.Category = with(context) {
        val state = this.state
        SettingsNode.Category(
            id = "providers",
            title = "Providers",
            subtitle = "Configure AI providers",
            icon = Icons.Outlined.VpnKey,
            children = AssistantProvider.entries.filter { it != AssistantProvider.CUSTOM }
                .map { provider ->
                    SettingsNode.Category(
                        id = "provider-${provider.name.lowercase()}",
                        title = provider.info.displayName,
                        subtitle = "Configure assistant API settings.",
                        icon = Icons.Outlined.VpnKey,
                        trailing = {
                            TrailingSwitch(
                                label = "Select ${provider.info.displayName}",
                                checked = state.selectedProvider == provider,
                                onCheckedChange = { checked ->
                                    if (checked) onSaveActiveProvider(provider)
                                },
                            )
                        },
                        children = buildList {
                            add(
                                SettingsNode.Item(
                                    id = "model-${provider.name.lowercase()}",
                                    title = "Model Name",
                                    subtitle = when {
                                        !provider.info.supportsCustomModel -> provider.info.defaultModelId
                                        else -> when (provider) {
                                            AssistantProvider.GEMINI -> state.geminiModelId
                                            AssistantProvider.OPENROUTER -> state.openRouterModelId
                                            AssistantProvider.TOKENS_BD -> provider.info.defaultModelId
                                            AssistantProvider.CUSTOM -> provider.info.defaultModelId
                                        }
                                    },
                                    icon = Icons.Outlined.AutoAwesome,
                                    onClick = if (provider.info.supportsCustomModel) {
                                        when (provider) {
                                            AssistantProvider.GEMINI -> ({ onEditField(EditField.GEMINI_MODEL) })
                                            AssistantProvider.OPENROUTER -> ({ onEditField(EditField.OPENROUTER_MODEL) })
                                            AssistantProvider.TOKENS_BD -> null
                                            AssistantProvider.CUSTOM -> null
                                        }
                                    } else {
                                        null
                                    },
                                )
                            )
                            add(
                                SettingsNode.Item(
                                    id = "reasoning-${provider.name.lowercase()}",
                                    title = "Reasoning Mode",
                                    subtitle = "Enable advanced reasoning capabilities.",
                                    icon = Icons.Outlined.AutoAwesome,
                                    onClick = {
                                        onSaveAssistantThinkingMode(
                                            if (state.assistantThinkingMode == AssistantThinkingMode.OFF) {
                                                AssistantThinkingMode.AUTO
                                            } else {
                                                AssistantThinkingMode.OFF
                                            },
                                        )
                                    },
                                    trailing = {
                                        TrailingSwitch(
                                            label = "Enable reasoning",
                                            checked = state.assistantThinkingMode != AssistantThinkingMode.OFF,
                                            onCheckedChange = { enabled ->
                                                onSaveAssistantThinkingMode(
                                                    if (enabled) AssistantThinkingMode.AUTO else AssistantThinkingMode.OFF,
                                                )
                                            },
                                        )
                                    },
                                )
                            )
                            add(
                                SettingsNode.Item(
                                    id = "key-${provider.name.lowercase()}",
                                    title = "API key",
                                    subtitle = if (state.keyFor(provider)
                                            .isBlank()
                                    ) "Not configured" else "Configured",
                                    icon = Icons.Outlined.Key,
                                    onClick = { onEditKey(provider) },
                                    trailing = {
                                        TrailingBadge(
                                            if (state.keyFor(provider)
                                                    .isBlank()
                                            ) "SET UP" else "SAVED",
                                            if (state.keyFor(provider).isBlank()) Aqua else Success,
                                        )
                                    })
                            )
                        })
                } + SettingsNode.Category(
                id = "Custom Provider",
                title = AssistantProvider.CUSTOM.info.displayName,
                subtitle = "Configure custom AI providers.",
                icon = Icons.Outlined.VpnKey,
                trailing = {
                    TrailingSwitch(
                        label = "Select ${AssistantProvider.CUSTOM.info.displayName}",
                        checked = state.selectedProvider == AssistantProvider.CUSTOM,
                        onCheckedChange = { checked ->
                            if (checked) onSaveActiveProvider(AssistantProvider.CUSTOM)
                        },
                    )
                },
                children = listOf(
                    SettingsNode.Item(
                        id = "custom-provider-model",
                        title = "Model Name",
                        subtitle = state.customProviderModelId.ifBlank { "Enter a model name." },
                        icon = Icons.Outlined.AutoAwesome,
                        onClick = { onEditField(EditField.CUSTOM_PROVIDER_MODEL) },
                    ),
                    SettingsNode.Item(
                        id = "custom-provider-url",
                        title = "API Endpoint",
                        subtitle = state.customProviderBaseUrl.ifBlank { "Set the API base URL." },
                        icon = Icons.Outlined.Settings,
                        onClick = { onEditField(EditField.CUSTOM_PROVIDER_BASE_URL) },
                    ),
                    SettingsNode.Item(
                        id = "custom-provider-key",
                        title = "API key",
                        subtitle = if (state.keyFor(AssistantProvider.CUSTOM)
                                .isBlank()
                        ) "Enter your API keys." else "Configured",
                        icon = Icons.Outlined.Key,
                        onClick = { onEditKey(AssistantProvider.CUSTOM) },
                        trailing = {
                            TrailingBadge(
                                if (state.keyFor(AssistantProvider.CUSTOM)
                                        .isBlank()
                                ) "SET UP" else "SAVED",
                                if (state.keyFor(AssistantProvider.CUSTOM)
                                        .isBlank()
                                ) Aqua else Success,
                            )
                        },
                    ),
                    SettingsNode.Item(
                        id = "custom-provider-reasoning",
                        title = "Reasoning Mode",
                        subtitle = "Enable advanced reasoning capabilities.",
                        icon = Icons.Outlined.AutoAwesome,
                        onClick = {
                            onSaveAssistantThinkingMode(
                                if (state.assistantThinkingMode == AssistantThinkingMode.OFF) AssistantThinkingMode.AUTO else AssistantThinkingMode.OFF,
                            )
                        },
                        trailing = {
                            TrailingSwitch(
                                label = "Enable reasoning",
                                checked = state.assistantThinkingMode != AssistantThinkingMode.OFF,
                                onCheckedChange = { enabled ->
                                    onSaveAssistantThinkingMode(if (enabled) AssistantThinkingMode.AUTO else AssistantThinkingMode.OFF)
                                },
                            )
                        },
                    ),
                ),
            )
        )
    }
}
