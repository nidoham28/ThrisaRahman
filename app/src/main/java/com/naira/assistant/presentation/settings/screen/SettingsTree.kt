@file:Suppress("SpellCheckingInspection")

package com.naira.assistant.presentation.settings.screen

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsVoice
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VpnKey
import com.naira.assistant.core.sound.SpeechEngine
import com.naira.assistant.core.theme.Aqua
import com.naira.assistant.BuildConfig
import com.naira.assistant.domain.settings.AssistantCharacter
import com.naira.assistant.domain.settings.AssistantLanguage
import com.naira.assistant.domain.settings.AssistantProvider
import com.naira.assistant.domain.settings.AssistantThinkingMode
import com.naira.assistant.domain.settings.Gender
import com.naira.assistant.domain.settings.RelationType
import com.naira.assistant.presentation.settings.component.GenderDropdown
import com.naira.assistant.presentation.settings.component.LanguageDropdown
import com.naira.assistant.presentation.settings.component.RelationTypeDropdown
import com.naira.assistant.presentation.settings.component.Success
import com.naira.assistant.presentation.settings.component.TrailingBadge
import com.naira.assistant.presentation.settings.component.TrailingChevron
import com.naira.assistant.presentation.settings.component.TrailingSwitch
import com.naira.assistant.presentation.settings.component.TrailingValue
import com.naira.assistant.presentation.settings.component.WakeWordSensitivityDropdown
import com.naira.assistant.presentation.settings.component.ThemeDropdownTrailing
import com.naira.assistant.presentation.settings.component.accent
import com.naira.assistant.presentation.settings.component.icon
import com.naira.assistant.presentation.settings.model.EditField
import com.naira.assistant.presentation.settings.model.RowStyle
import com.naira.assistant.presentation.settings.model.SettingsNode
import com.naira.assistant.presentation.settings.model.rootCategoryOrder
import com.naira.assistant.presentation.settings.permission.PermissionActions
import com.naira.assistant.presentation.settings.permission.PermissionStatus
import com.naira.assistant.presentation.settings.page.SettingsPageContext


/* ------------------------------------------------------------------ */
/*  Tree builder                                                       */
/* ------------------------------------------------------------------ */

internal fun buildTree(context: SettingsPageContext): SettingsNode.Category {
    val state = context.state
    val permissionStatus = context.permissionStatus
    val permissionActions = context.permissionActions
    val onSaveActiveProvider = context.onSaveActiveProvider
    val onEditKey = context.onEditKey
    val onEditCricketApiKey = context.onEditCricketApiKey
    val onEditWeatherApiKey = context.onEditWeatherApiKey
    val onEditCustomTtsApiKey = context.onEditCustomTtsApiKey
    val onDataManagementAction = context.onDataManagementAction
    val onEditField = context.onEditField
    val onSaveGender = context.onSaveGender
    val onSaveAssistantName = context.onSaveAssistantName
    val onSaveRelationship = context.onSaveRelationship
    val onSaveLanguage = context.onSaveLanguage
    val onSaveAssistantThinkingMode = context.onSaveAssistantThinkingMode
    val onSaveSpeechEngine = context.onSaveSpeechEngine
    val onSaveTheme = context.onSaveTheme
    val onLogout = context.onLogout
    return SettingsNode.Category(
    id = "root",
    title = "Settings",
    icon = Icons.Outlined.Tune,
    children = listOf(
        SettingsNode.Category(
            id = "preferences", title = "Preferences", subtitle = "Appearance and general behavior",
            icon = Icons.Outlined.Tune,
            children = listOf(
                SettingsNode.Category(
                    id = "appearance", title = "Appearance", subtitle = "Theme",
                    icon = Icons.Outlined.DarkMode,
                    children = listOf(
                        SettingsNode.Item(
                            id = "theme", title = "Theme",
                            subtitle = "Choose system, light, or dark appearance",
                            icon = Icons.Outlined.DarkMode,
                            trailing = { ThemeDropdownTrailing(state.theme, onSaveTheme) },
                        ),
                    ),
                ),
                SettingsNode.Category(
                    id = "general", title = "General", subtitle = "Assistant behavior",
                    icon = Icons.Outlined.Settings,
                    children = emptyList(),
                ),
                SettingsNode.Category(
                    id = "interface", title = "Interface", subtitle = "Chat display",
                    icon = Icons.Outlined.Tune,
                    children = emptyList(),
                ),
                SettingsNode.Category(
                    id = "network", title = "Network", subtitle = "Connection preferences",
                    icon = Icons.Outlined.Settings,
                    children = listOf(
                        SettingsNode.Item(
                            id = "custom-user-agent",
                            title = "Custom user-agent",
                            subtitle = state.customUserAgent.ifBlank { "Use the default browser identity" },
                            icon = Icons.Outlined.Settings,
                            onClick = { onEditField(EditField.CUSTOM_USER_AGENT) },
                            trailing = {
                                TrailingValue(if (state.customUserAgent.isBlank()) "Default" else "Custom")
                            },
                        ),
                    ),
                ),
            ),
        ),
        SettingsNode.Category(
            id = "profile", title = "Profile", subtitle = "Personal information",
            icon = Icons.Outlined.Person,
            children = listOf(
                SettingsNode.Item(
                    id = "username", title = "Username",
                    subtitle = state.username.ifBlank { "Set your username" },
                    icon = Icons.Outlined.Person,
                    onClick = { onEditField(EditField.USERNAME) },
                ),
                SettingsNode.Item(
                    id = "phone", title = "Phone number",
                    subtitle = state.phone.ifBlank { "Not set" },
                    icon = Icons.Outlined.Phone,
                    onClick = { onEditField(EditField.PHONE) },
                ),
                SettingsNode.Item(
                    id = "gender", title = "Gender", subtitle = "Profile information",
                    icon = Icons.Outlined.Person,
                    trailing = { GenderDropdown(state.gender, onSaveGender) },
                ),
            )
        ),
        SettingsNode.Category(
            id = "assistant",
            title = "Assistants",
            subtitle = "${AssistantCharacter.fromName(state.assistantName).displayName} is active",
            icon = Icons.Outlined.AutoAwesome,
            children = buildList {
                AssistantCharacter.entries.forEach { character ->
                    val isActive = character.displayName.equals(state.assistantName, ignoreCase = true)
                    add(SettingsNode.Category(
                        id = "assistant-${character.name.lowercase()}",
                        title = character.displayName,
                        subtitle = "${character.gender} · ${character.voice.geminiVoiceName} voice",
                        icon = Icons.Outlined.SmartToy,
                        trailing = {
                            TrailingSwitch(
                                label = "Select ${character.displayName}",
                                checked = isActive,
                                onCheckedChange = { checked ->
                                    if (checked) onSaveAssistantName(character.displayName)
                                },
                            )
                        },
                        children = listOf(
                            SettingsNode.Item("${character.name.lowercase()}-language", "Language", state.assistantLanguage.name.lowercase().replaceFirstChar(Char::uppercase), Icons.Outlined.Language,
                                trailing = { LanguageDropdown(state.assistantLanguage, onSaveLanguage) }),
                            SettingsNode.Item("${character.name.lowercase()}-style", "Interaction style", state.relationship.name.lowercase().replaceFirstChar(Char::uppercase), Icons.Outlined.People,
                                trailing = { RelationTypeDropdown(state.relationship, onSaveRelationship) }),
                        ),
                    ))
                }
            }
        ),
        SettingsNode.Category(
            id = "voice", title = "Speech & Voice", subtitle = "Speech input and output",
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
        ),
        SettingsNode.Category(
            id = "extensions", title = "Extensions", subtitle = "Skills and workspace",
            icon = Icons.Outlined.FolderOpen,
            children = listOf(
                SettingsNode.Category("agent-skills", "Agent Skills", "Installed skills and permissions", Icons.Outlined.AutoAwesome,
                    children = listOf(
                        SettingsNode.Item("installed-skills", "Installed Skills", "Not available yet", Icons.Outlined.CheckCircle),
                        SettingsNode.Item("skill-library", "Skill Library", "Not available yet", Icons.Outlined.FolderOpen),
                        SettingsNode.Item("skill-permissions", "Skill Permissions", "Not available yet", Icons.Outlined.Security),
                    )),
                SettingsNode.Category("workspace", "Workspace", "Configuration, files and permissions", Icons.Outlined.FolderOpen,
                    children = listOf(
                        SettingsNode.Item("workspace-configuration", "Workspace Configuration", "Not available yet", Icons.Outlined.Settings),
                        SettingsNode.Item("workspace-files", "Workspace Files", "Not available yet", Icons.Outlined.FolderOpen),
                        SettingsNode.Item("workspace-permissions", "Workspace Permissions", "Not available yet", Icons.Outlined.Security),
                    )),
            ),
        ),
        SettingsNode.Category(
            id = "providers", title = "AI Providers", subtitle = "Models and API keys",
            icon = Icons.Outlined.VpnKey,
            children = AssistantProvider.entries.filter { it != AssistantProvider.CUSTOM }.map { provider ->
                SettingsNode.Category(
                    id = "provider-${provider.name.lowercase()}",
                    title = provider.displayName,
                    subtitle = if (state.selectedProvider == provider) "Active provider" else "Provider settings",
                    icon = Icons.Outlined.VpnKey,
                    trailing = {
                        TrailingSwitch(
                            label = "Select ${provider.displayName}",
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
                                title = "Model selection",
                                subtitle = when (provider) {
                                    AssistantProvider.GEMINI -> state.geminiModelId
                                    AssistantProvider.OPENROUTER -> state.openRouterModelId
                                    AssistantProvider.GROQ -> state.groqModelId
                                    AssistantProvider.CUSTOM -> provider.defaultModelId
                                },
                                icon = Icons.Outlined.AutoAwesome,
                                onClick = when (provider) {
                                    AssistantProvider.GEMINI -> ({ onEditField(EditField.GEMINI_MODEL) })
                                    AssistantProvider.OPENROUTER -> ({ onEditField(EditField.OPENROUTER_MODEL) })
                                    AssistantProvider.GROQ -> ({ onEditField(EditField.GROQ_MODEL) })
                                    AssistantProvider.CUSTOM -> null
                                },
                            )
                        )
                        if (provider == AssistantProvider.OPENROUTER || provider == AssistantProvider.GROQ) {
                            val baseUrl = if (provider == AssistantProvider.OPENROUTER) {
                                state.openRouterBaseUrl
                            } else {
                                state.groqBaseUrl
                            }
                            add(
                                SettingsNode.Item(
                                    id = "api-url-${provider.name.lowercase()}",
                                    title = "API base URL",
                                    subtitle = baseUrl.ifBlank { "Default · tap to override" },
                                    icon = Icons.Outlined.Settings,
                                    onClick = {
                                        onEditField(
                                            if (provider == AssistantProvider.OPENROUTER) {
                                                EditField.OPENROUTER_BASE_URL
                                            } else {
                                                EditField.GROQ_BASE_URL
                                            },
                                        )
                                    },
                                ),
                            )
                        }
                        add(
                            SettingsNode.Item(
                                id = "reasoning-${provider.name.lowercase()}",
                                title = "Enable reasoning",
                                subtitle = if (state.assistantThinkingMode == AssistantThinkingMode.OFF) {
                                    "Disabled"
                                } else {
                                    "Enabled · shared across providers"
                                },
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
                                subtitle = if (state.keyFor(provider).isBlank()) "Not configured" else "Configured",
                                icon = Icons.Outlined.Key,
                                onClick = { onEditKey(provider) },
                                trailing = {
                                    TrailingBadge(
                                        if (state.keyFor(provider).isBlank()) "SET UP" else "SAVED",
                                        if (state.keyFor(provider).isBlank()) Aqua else Success,
                                    )
                                }
                            )
                        )
                    }
                )
            } + SettingsNode.Category(
                id = "provider-custom",
                title = AssistantProvider.CUSTOM.displayName,
                subtitle = if (state.selectedProvider == AssistantProvider.CUSTOM) "Active provider" else "Custom endpoint settings",
                icon = Icons.Outlined.VpnKey,
                trailing = {
                    TrailingSwitch(
                        label = "Select ${AssistantProvider.CUSTOM.displayName}",
                        checked = state.selectedProvider == AssistantProvider.CUSTOM,
                        onCheckedChange = { checked ->
                            if (checked) onSaveActiveProvider(AssistantProvider.CUSTOM)
                        },
                    )
                },
                children = listOf(
                    SettingsNode.Item(
                        id = "custom-provider-model",
                        title = "Model name",
                        subtitle = state.customProviderModelId.ifBlank { "Set your model ID" },
                        icon = Icons.Outlined.AutoAwesome,
                        onClick = { onEditField(EditField.CUSTOM_PROVIDER_MODEL) },
                    ),
                    SettingsNode.Item(
                        id = "custom-provider-url",
                        title = "API base URL",
                        subtitle = state.customProviderBaseUrl.ifBlank { "Set your HTTPS API URL" },
                        icon = Icons.Outlined.Settings,
                        onClick = { onEditField(EditField.CUSTOM_PROVIDER_BASE_URL) },
                    ),
                    SettingsNode.Item(
                        id = "custom-provider-key",
                        title = "API key",
                        subtitle = if (state.keyFor(AssistantProvider.CUSTOM).isBlank()) "Not configured" else "Configured",
                        icon = Icons.Outlined.Key,
                        onClick = { onEditKey(AssistantProvider.CUSTOM) },
                        trailing = {
                            TrailingBadge(
                                if (state.keyFor(AssistantProvider.CUSTOM).isBlank()) "SET UP" else "SAVED",
                                if (state.keyFor(AssistantProvider.CUSTOM).isBlank()) Aqua else Success,
                            )
                        },
                    ),
                    SettingsNode.Item(
                        id = "custom-provider-reasoning",
                        title = "Enable reasoning",
                        subtitle = if (state.assistantThinkingMode == AssistantThinkingMode.OFF) "Disabled" else "Enabled · shared across providers",
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
        ),
        SettingsNode.Category(
            id = "integrations", title = "API Integrations", subtitle = "Weather and cricket services",
            icon = Icons.Outlined.Key,
            children = listOf(
                SettingsNode.Item(
                    id = "weatherapi-key",
                    title = "Weather service API key",
                    subtitle = if (state.weatherApiKey.isBlank()) {
                        "Not configured"
                    } else {
                        "Configured · synced to your account"
                    },
                    icon = Icons.Outlined.Key,
                    onClick = onEditWeatherApiKey,
                    trailing = {
                        TrailingBadge(
                            if (state.weatherApiKey.isBlank()) "SET UP" else "SAVED",
                            if (state.weatherApiKey.isBlank()) Aqua else Success,
                        )
                    },
                ),
                SettingsNode.Item(
                    id = "cricapi-key",
                    title = "Cricket service API key",
                    subtitle = if (state.cricketApiKey.isBlank()) "Not configured" else "Configured",
                    icon = Icons.Outlined.Key,
                    onClick = onEditCricketApiKey,
                    trailing = {
                        TrailingBadge(
                            if (state.cricketApiKey.isBlank()) "SET UP" else "SAVED",
                            if (state.cricketApiKey.isBlank()) Aqua else Success,
                        )
                    },
                ),
            ),
        ),
        SettingsNode.Category(
            id = "data", title = "Data Management", subtitle = "Back up or restore your data",
            icon = Icons.Outlined.FolderOpen,
            children = listOf(
                SettingsNode.Item(
                    id = "export-data", title = "Export Data (Backup)",
                    subtitle = "Not available yet",
                    icon = Icons.Outlined.FolderOpen,
                    onClick = { onDataManagementAction("Export Data (Backup)") },
                    trailing = { TrailingChevron() },
                ),
                SettingsNode.Item(
                    id = "import-data", title = "Import Data (Restore)",
                    subtitle = "Not available yet",
                    icon = Icons.Outlined.FolderOpen,
                    onClick = { onDataManagementAction("Import Data (Restore)") },
                    trailing = { TrailingChevron() },
                ),
            ),
        ),
        SettingsNode.Category(
            id = "advanced-settings", title = "Advanced Settings",
            subtitle = "Background wake-up detection model",
            icon = Icons.Outlined.Settings,
            children = listOf(
                SettingsNode.Item(
                    id = "wakeup-word-service",
                    title = "Wakeup word service",
                    subtitle = "Listen for the wake word while the app is in the background",
                    icon = Icons.Outlined.Mic,
                    onClick = {
                        context.onToggleWakeWordService(!state.wakeWordServiceEnabled)
                    },
                    trailing = {
                        TrailingSwitch(
                            label = "Wakeup word service",
                            checked = state.wakeWordServiceEnabled,
                            onCheckedChange = context.onToggleWakeWordService,
                        )
                    },
                ),
                SettingsNode.Item(
                    id = "wake-word-model",
                    title = "Wake-word model",
                    subtitle = "Sherpa-ONNX Zipformer2 · runs on-device",
                    icon = Icons.Outlined.SettingsVoice,
                ),
                SettingsNode.Item(
                    id = "wake-word-phrases",
                    title = "Wake phrases",
                    subtitle = "Hey Assistant · Hello Assistant · Wakeup Assistant",
                    icon = Icons.Outlined.Mic,
                ),
                SettingsNode.Item(
                    id = "wake-word-sensitivity",
                    title = "Detection sensitivity",
                    subtitle = "Higher sensitivity may trigger more often in background noise",
                    icon = Icons.Outlined.Tune,
                    trailing = {
                        WakeWordSensitivityDropdown(
                            selectedThreshold = state.wakeWordDetectionThreshold,
                            onSelect = state::saveWakeWordDetectionThreshold,
                        )
                    },
                ),
            ),
        ),
        SettingsNode.Category(
            id = "privacy", title = "Permissions", subtitle = "App and assistant access",
            icon = Icons.Outlined.Lock,
            accent = Aqua,
            children = listOf(
                SettingsNode.Item(
                    id = "sensitive-tool-access",
                    title = "Sensitive assistant actions",
                    subtitle = if (state.sensitiveToolAccessEnabled)
                        "Screen access, edits and deletions are allowed"
                    else "Screen access, edits and deletions are blocked",
                    icon = Icons.Outlined.Security,
                    onClick = { state.saveSensitiveToolAccess(!state.sensitiveToolAccessEnabled) },
                    trailing = {
                        TrailingSwitch(
                            label = "Sensitive assistant actions",
                            checked = state.sensitiveToolAccessEnabled,
                            onCheckedChange = state::saveSensitiveToolAccess,
                        )
                    },
                ),
                SettingsNode.Category(
                    id = "app-permissions",
                    title = "App permissions",
                    subtitle = if (permissionStatus.allGranted) "All permissions granted"
                    else "${permissionStatus.grantedCount} of ${permissionStatus.totalCount} granted",
                    icon = Icons.Outlined.Security,
                    children = listOfNotNull(
                        if (!permissionStatus.allGranted) {
                            SettingsNode.Item(
                                id = "grant-all-permissions", title = "Request all permissions",
                                subtitle = "Ask for all required permissions at once",
                                icon = Icons.Outlined.CheckCircle,
                                onClick = permissionActions.onRequestAll,
                                trailing = { TrailingBadge("REQUEST ALL", Aqua) }
                            )
                        } else null,
                        SettingsNode.Item(
                            id = "permission-mic", title = "Microphone",
                            subtitle = "Voice conversations & speech recognition",
                            icon = Icons.Outlined.Mic,
                            onClick = permissionActions.onRequestMic,
                            trailing = {
                                TrailingBadge(
                                    if (permissionStatus.hasMic) "ALLOWED" else "GRANT",
                                    if (permissionStatus.hasMic) Success else Aqua
                                )
                            }
                        ),
                        SettingsNode.Item(
                            id = "permission-calendar", title = "Calendar",
                            subtitle = "Read & create calendar events",
                            icon = Icons.Outlined.CalendarMonth,
                            onClick = permissionActions.onRequestCalendar,
                            trailing = {
                                TrailingBadge(
                                    if (permissionStatus.hasCalendar) "ALLOWED" else "GRANT",
                                    if (permissionStatus.hasCalendar) Success else Aqua
                                )
                            }
                        ),
                        SettingsNode.Item(
                            id = "permission-notifications", title = "Notifications",
                            subtitle = "Reminders, alerts & assistant updates",
                            icon = Icons.Outlined.Notifications,
                            onClick = permissionActions.onRequestNotifications,
                            trailing = {
                                TrailingBadge(
                                    if (permissionStatus.hasNotifications) "ALLOWED" else "GRANT",
                                    if (permissionStatus.hasNotifications) Success else Aqua
                                )
                            }
                        ),
                        SettingsNode.Item(
                            id = "permission-exact-alarm", title = "Alarms & Reminders",
                            subtitle = "Schedule alarms and timers accurately on time",
                            icon = Icons.Outlined.Alarm,
                            onClick = permissionActions.onRequestExactAlarm,
                            trailing = {
                                TrailingBadge(
                                    if (permissionStatus.hasExactAlarm) "ALLOWED" else "GRANT",
                                    if (permissionStatus.hasExactAlarm) Success else Aqua
                                )
                            }
                        ),
                        SettingsNode.Item(
                            id = "permission-bluetooth", title = "Nearby devices (Bluetooth)",
                            subtitle = "Route voice audio to Bluetooth headsets",
                            icon = Icons.Outlined.Bluetooth,
                            onClick = permissionActions.onRequestBluetooth,
                            trailing = {
                                TrailingBadge(
                                    if (permissionStatus.hasBluetooth) "ALLOWED" else "GRANT",
                                    if (permissionStatus.hasBluetooth) Success else Aqua
                                )
                            }
                        ),
                        SettingsNode.Item(
                            id = "permission-media", title = "Photos, videos & audio",
                            subtitle = when {
                                permissionStatus.hasAllMedia -> "Full access to shared media"
                                permissionStatus.hasMedia -> "Selected media access is available"
                                else -> "Access media files for attachments and browsing"
                            },
                            icon = Icons.Outlined.GraphicEq,
                            onClick = permissionActions.onRequestMedia,
                            trailing = {
                                TrailingBadge(
                                    when {
                                        permissionStatus.hasAllMedia -> "ALLOWED"
                                        permissionStatus.hasMedia -> "SELECTED"
                                        else -> "GRANT"
                                    },
                                    if (permissionStatus.hasMedia) Success else Aqua
                                )
                            }
                        ),
                        SettingsNode.Item(
                            id = "permission-all-files", title = "All files access",
                            subtitle = "Manage shared files and folders on this device",
                            icon = Icons.Outlined.FolderOpen,
                            onClick = permissionActions.onRequestAllFiles,
                            trailing = {
                                TrailingBadge(
                                    when {
                                        Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> "NOT NEEDED"
                                        permissionStatus.hasAllFiles -> "ALLOWED"
                                        else -> "GRANT"
                                    },
                                    if (permissionStatus.hasAllFiles) Success else Aqua
                                )
                            }
                        ),
                        SettingsNode.Item(
                            id = "permission-modify-settings", title = "Modify system settings",
                            subtitle = "Allow Thrisa to change supported system settings",
                            icon = Icons.Outlined.Settings,
                            onClick = permissionActions.onRequestModifySystemSettings,
                            trailing = {
                                TrailingBadge(
                                    if (permissionStatus.hasModifySystemSettings) "ALLOWED" else "GRANT",
                                    if (permissionStatus.hasModifySystemSettings) Success else Aqua,
                                )
                            }
                        ),
                        SettingsNode.Item(
                            id = "permission-accessibility", title = "Screen access (Accessibility)",
                            subtitle = if (permissionStatus.hasAccessibility)
                                "Enabled in Android Accessibility settings"
                            else "Open Android Settings → Accessibility to enable screen reading and controls",
                            icon = Icons.Outlined.TouchApp,
                            onClick = permissionActions.onOpenAccessibility,
                            trailing = {
                                TrailingBadge(
                                    if (permissionStatus.hasAccessibility) "ALLOWED" else "GRANT",
                                    if (permissionStatus.hasAccessibility) Success else Aqua
                                )
                            }
                        ),
                        SettingsNode.Item(
                            id = "permission-app-settings", title = "System app settings",
                            subtitle = "Manage all permissions directly in Android Settings",
                            icon = Icons.Outlined.Settings,
                            onClick = permissionActions.onOpenAppSettings,
                            trailing = { TrailingChevron() }
                        )
                    )
                ),
            )
        ),
        SettingsNode.Category(
            id = "about", title = "About", subtitle = "App information",
            icon = Icons.Outlined.Info,
            children = listOf(
                SettingsNode.Item(
                    id = "app-version", title = "App version",
                    subtitle = BuildConfig.VERSION_NAME,
                    icon = Icons.Outlined.Info,
                ),
                SettingsNode.Item(
                    id = "system-information",
                    title = "System information",
                    subtitle = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · " +
                            "${Build.MANUFACTURER} ${Build.MODEL}",
                    icon = Icons.Outlined.Settings,
                ),
                SettingsNode.Item(
                    id = "logout",
                    title = "Log out",
                    subtitle = "Sign out of your account on this device",
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    style = RowStyle.DANGER,
                    onClick = onLogout,
                ),
            )
        ),
    ).sortedBy { category ->
        rootCategoryOrder.indexOf(category.id).let { index ->
            if (index < 0) rootCategoryOrder.size else index
        }
    }
    )
}

internal fun resolveCategory(
    root: SettingsNode.Category,
    pathIds: List<String>
): SettingsNode.Category {
    var current = root
    for (id in pathIds) {
        val next = current.children
            .filterIsInstance<SettingsNode.Category>()
            .firstOrNull { it.id == id } ?: break
        current = next
    }
    return current
}
