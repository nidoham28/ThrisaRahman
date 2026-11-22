@file:Suppress("SpellCheckingInspection")

package com.naira.assistant.presentation.settings.screen

import android.os.Build
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GraphicEq
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
import com.naira.assistant.domain.settings.AsrProvider
import com.naira.assistant.domain.settings.AssistantCharacter
import com.naira.assistant.domain.settings.AssistantLanguage
import com.naira.assistant.domain.settings.AssistantProvider
import com.naira.assistant.domain.settings.AssistantThinkingMode
import com.naira.assistant.domain.settings.Gender
import com.naira.assistant.domain.settings.RelationType
import com.naira.assistant.presentation.settings.component.AsrProviderDropdown
import com.naira.assistant.presentation.settings.component.AssistantNameDropdown
import com.naira.assistant.presentation.settings.component.AssistantThinkingDropdown
import com.naira.assistant.presentation.settings.component.GenderDropdown
import com.naira.assistant.presentation.settings.component.LanguageDropdown
import com.naira.assistant.presentation.settings.component.ProviderDropdownTrailing
import com.naira.assistant.presentation.settings.component.RelationTypeDropdown
import com.naira.assistant.presentation.settings.component.SpeechEngineDropdown
import com.naira.assistant.presentation.settings.component.Success
import com.naira.assistant.presentation.settings.component.TrailingBadge
import com.naira.assistant.presentation.settings.component.TrailingChevron
import com.naira.assistant.presentation.settings.component.TrailingValue
import com.naira.assistant.presentation.settings.component.accent
import com.naira.assistant.presentation.settings.component.icon
import com.naira.assistant.presentation.settings.model.EditField
import com.naira.assistant.presentation.settings.model.SettingsDestination
import com.naira.assistant.presentation.settings.model.SettingsNode
import com.naira.assistant.presentation.settings.model.rootCategoryOrder
import com.naira.assistant.presentation.settings.permission.PermissionActions
import com.naira.assistant.presentation.settings.permission.PermissionStatus


/* ------------------------------------------------------------------ */
/*  Tree builder                                                       */
/* ------------------------------------------------------------------ */

internal fun buildTree(
    state: SettingsState,
    permissionStatus: PermissionStatus,
    permissionActions: PermissionActions,
    onNavigate: (SettingsDestination) -> Unit,
    onSaveActiveProvider: (AssistantProvider) -> Unit,
    onEditKey: (AssistantProvider) -> Unit,
    onEditCricketApiKey: () -> Unit,
    onEditWeatherApiKey: () -> Unit,
    onEditField: (EditField) -> Unit,
    onSaveGender: (Gender) -> Unit,
    onSaveAssistantName: (String) -> Unit,
    onSaveRelationship: (RelationType) -> Unit,
    onSaveLanguage: (AssistantLanguage) -> Unit,
    onSaveAssistantThinkingMode: (AssistantThinkingMode) -> Unit,
    onSaveSpeechEngine: (SpeechEngine) -> Unit,
    onSaveAsrProvider: (AsrProvider) -> Unit,
    onEditAsrApiKey: () -> Unit,
    backgroundVoiceEnabled: Boolean,
    onToggleBackgroundVoice: () -> Unit,
): SettingsNode.Category = SettingsNode.Category(
    id = "root",
    title = "Settings",
    icon = Icons.Outlined.Tune,
    children = listOf(
        SettingsNode.Category(
            id = "account", title = "Account", subtitle = "Profile, plan & sign in",
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
            id = "assistant", title = "Assistant", subtitle = "Language & thinking",
            icon = Icons.Outlined.AutoAwesome,
            children = listOf(
                SettingsNode.Item(
                    id = "assistant-name", title = "Assistant character",
                    subtitle = AssistantCharacter.fromName(state.assistantName).let {
                        "${it.gender} · ${it.voice.geminiVoiceName} voice"
                    },
                    icon = Icons.Outlined.SmartToy,
                    trailing = { AssistantNameDropdown(state.assistantName, onSaveAssistantName) },
                ),
                SettingsNode.Item(
                    id = "assistant-language", title = "Assistant language",
                    subtitle = "Language the assistant replies in",
                    icon = Icons.Outlined.Language,
                    trailing = { LanguageDropdown(state.assistantLanguage, onSaveLanguage) }
                ),
                SettingsNode.Item(
                    id = "assistant-relationship", title = "Relationship",
                    subtitle = "Personalize the assistant's tone",
                    icon = Icons.Outlined.People,
                    trailing = { RelationTypeDropdown(state.relationship, onSaveRelationship) },
                ),
                SettingsNode.Item(
                    id = "assistant-thinking",
                    title = "Model thinking",
                    subtitle = state.assistantThinkingMode.description,
                    icon = Icons.Outlined.AutoAwesome,
                    trailing = {
                        AssistantThinkingDropdown(
                            selected = state.assistantThinkingMode,
                            onSelect = onSaveAssistantThinkingMode,
                        )
                    },
                ),
            )
        ),
        SettingsNode.Category(
            id = "engine", title = "Engine", subtitle = "Speech engine",
            icon = Icons.Outlined.SettingsVoice,
            children = listOf(
                SettingsNode.Item(
                    id = "tts-engine", title = "Speech engine",
                    subtitle = if (state.speechEngine == SpeechEngine.SYSTEM)
                        "Local Device TTS · runs on this device"
                    else "Gemini TTS · uses your saved Gemini API key",
                    icon = Icons.Outlined.GraphicEq,
                    trailing = { SpeechEngineDropdown(state.speechEngine, onSaveSpeechEngine) }
                ),
                SettingsNode.Item(
                    id = "asr-provider", title = "Speech recognition",
                    subtitle = when {
                        state.asrProvider == AsrProvider.SYSTEM ->
                            "Android speech recognition · default"
                        state.asrApiKeyConfigured ->
                            "${state.asrProvider.displayName} · provider key saved on this device"
                        else ->
                            "${state.asrProvider.displayName} · API key required"
                    },
                    icon = Icons.Outlined.Mic,
                    trailing = { AsrProviderDropdown(state.asrProvider, onSaveAsrProvider) },
                ),
                SettingsNode.Item(
                    id = "asr-api-key",
                    title = "${state.asrProvider.displayName} API key",
                    subtitle = when {
                        state.asrProvider == AsrProvider.SYSTEM -> "No provider key required"
                        state.asrApiKeyConfigured -> "Encrypted on this device · not synced"
                        else -> "Required for ${state.asrProvider.displayName}"
                    },
                    icon = Icons.Outlined.Lock,
                    enabled = state.asrProvider != AsrProvider.SYSTEM,
                    onClick = onEditAsrApiKey.takeIf { state.asrProvider != AsrProvider.SYSTEM },
                ),
            )
        ),
        SettingsNode.Category(
            id = "ai", title = "Providers & API Keys", subtitle = "Manage providers and API keys",
            icon = Icons.Outlined.VpnKey,
            children = listOf(
                SettingsNode.Item(
                    id = "provider", title = "Provider", subtitle = "Choose your default",
                    icon = Icons.Outlined.VpnKey,
                    trailing = {
                        ProviderDropdownTrailing(
                            selected = state.selectedProvider,
                            onSelect = onSaveActiveProvider,
                        )
                    }
                ),
                SettingsNode.Item(
                    id = "openrouter-model",
                    title = "OpenRouter model",
                    subtitle = state.openRouterModelId,
                    icon = Icons.Outlined.AutoAwesome,
                    onClick = { onEditField(EditField.OPENROUTER_MODEL) },
                ),
                SettingsNode.Category(
                    id = "credentials", title = "API Keys", subtitle = "Provider and service credentials",
                    icon = Icons.Outlined.Key,
                    children = buildList {
                        AssistantProvider.entries.forEach { provider ->
                            add(
                                SettingsNode.Item(
                                    id = "key-${provider.name.lowercase()}",
                                    title = "${provider.displayName} API Key",
                                    subtitle = if (state.keyFor(provider).isBlank()) "Not configured" else "Configured",
                                    icon = Icons.Outlined.Key,
                                    onClick = { onEditKey(provider) },
                                    trailing = {
                                        if (state.keyFor(provider).isBlank()) {
                                            TrailingBadge("SET UP", Aqua)
                                        } else {
                                            TrailingBadge("SAVED", Success)
                                        }
                                    }
                                )
                            )
                        }
                        add(
                            SettingsNode.Item(
                                id = "cricapi-key",
                                title = "CricAPI Key",
                                subtitle = if (state.cricketApiKey.isBlank()) "Not configured" else "Configured",
                                icon = Icons.Outlined.Key,
                                onClick = onEditCricketApiKey,
                                trailing = {
                                    TrailingBadge(
                                        if (state.cricketApiKey.isBlank()) "SET UP" else "SAVED",
                                        if (state.cricketApiKey.isBlank()) Aqua else Success,
                                    )
                                },
                            )
                        )
                        add(
                            SettingsNode.Item(
                                id = "weatherapi-key",
                                title = "WeatherAPI Key",
                                subtitle = if (state.weatherApiKey.isBlank()) "Not configured" else "Configured · synced to your account",
                                icon = Icons.Outlined.Key,
                                onClick = onEditWeatherApiKey,
                                trailing = {
                                    TrailingBadge(
                                        if (state.weatherApiKey.isBlank()) "SET UP" else "SAVED",
                                        if (state.weatherApiKey.isBlank()) Aqua else Success,
                                    )
                                },
                            )
                        )
                    }
                ),
            )
        ),
        SettingsNode.Category(
            id = "appearance", title = "Appearance", subtitle = "Theme, text & language",
            icon = Icons.Outlined.DarkMode,
            children = listOf(
                SettingsNode.Item(
                    id = "language", title = "Language",
                    icon = Icons.Outlined.Language,
                    onClick = { onNavigate(SettingsDestination.APPEARANCE) },
                    trailing = { TrailingValue("English") }
                )
            )
        ),
        SettingsNode.Category(
            id = "permissions", title = "Permissions",
            subtitle = if (permissionStatus.allGranted) "All permissions granted"
            else "${permissionStatus.grantedCount} of ${permissionStatus.totalCount} granted · Tap to manage",
            icon = Icons.Outlined.Security,
            accent = Aqua,
            children = listOfNotNull(
                SettingsNode.Item(
                    id = "background-voice",
                    title = "Background wake word",
                    subtitle = if (backgroundVoiceEnabled) "Wake word listening is enabled" else "Wake word listening is paused",
                    icon = Icons.Outlined.SettingsVoice,
                    onClick = onToggleBackgroundVoice,
                    trailing = { TrailingBadge(if (backgroundVoiceEnabled) "ON · STOP" else "OFF · START", if (backgroundVoiceEnabled) Success else Aqua) },
                ),
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
                    id = "sensitive-tool-access",
                    title = "Sensitive assistant actions",
                    subtitle = if (state.sensitiveToolAccessEnabled)
                        "Screen access, edits and deletions are allowed"
                    else "Screen access, edits and deletions are blocked",
                    icon = Icons.Outlined.Security,
                    onClick = { state.saveSensitiveToolAccess(!state.sensitiveToolAccessEnabled) },
                    trailing = {
                        TrailingBadge(
                            if (state.sensitiveToolAccessEnabled) "ALLOWED" else "BLOCKED",
                            if (state.sensitiveToolAccessEnabled) Success else Aqua,
                        )
                    },
                ),
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
        )
    ).sortedBy { category ->
        rootCategoryOrder.indexOf(category.id).let { index ->
            if (index < 0) rootCategoryOrder.size else index
        }
    }
)

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
