package com.naira.assistant.presentation.settings.page

import com.naira.assistant.core.sound.SpeechEngine
import com.naira.assistant.domain.settings.AssistantLanguage
import com.naira.assistant.domain.settings.AssistantProvider
import com.naira.assistant.domain.settings.AssistantThinkingMode
import com.naira.assistant.domain.settings.EmailContact
import com.naira.assistant.domain.settings.Gender
import com.naira.assistant.domain.settings.RelationType
import com.naira.assistant.presentation.settings.model.EditField
import com.naira.assistant.presentation.settings.permission.PermissionActions
import com.naira.assistant.presentation.settings.permission.PermissionStatus
import com.naira.assistant.presentation.settings.screen.SettingsState

internal data class SettingsPageContext(
    val state: SettingsState,
    val emailAddress: String,
    val permissionStatus: PermissionStatus,
    val permissionActions: PermissionActions,
    val onSaveActiveProvider: (AssistantProvider) -> Unit,
    val onSaveModelId: (AssistantProvider, String) -> Unit,
    val onEditKey: (AssistantProvider) -> Unit,
    val onEditCricketApiKey: () -> Unit,
    val onEditWeatherApiKey: () -> Unit,
    val onEditCustomTtsApiKey: () -> Unit,
    val onDataManagementAction: (String) -> Unit,
    val onEditField: (EditField) -> Unit,
    val onEditEmailContact: (EmailContact?) -> Unit,
    val onSaveGender: (Gender) -> Unit,
    val onSaveAssistantName: (String) -> Unit,
    val onSaveRelationship: (RelationType) -> Unit,
    val onSaveLanguage: (AssistantLanguage) -> Unit,
    val onSaveAssistantThinkingMode: (AssistantThinkingMode) -> Unit,
    val onSaveSpeechEngine: (SpeechEngine) -> Unit,
    val onSaveTheme: (String) -> Unit,
    val onToggleWakeWordService: (Boolean) -> Unit,
    val onLogout: () -> Unit,
)
