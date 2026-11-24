@file:Suppress("SpellCheckingInspection")

package com.naira.assistant.presentation.settings.screen

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import com.naira.assistant.presentation.settings.model.SettingsNode
import com.naira.assistant.presentation.settings.model.rootCategoryOrder
import com.naira.assistant.presentation.settings.page.CategoryNameUi
import com.naira.assistant.presentation.settings.page.SettingsPageContext
import com.naira.assistant.presentation.settings.page.category.AboutSettingsPage
import com.naira.assistant.presentation.settings.page.category.AdvancedSettingsPage
import com.naira.assistant.presentation.settings.page.category.AiProvidersSettingsPage
import com.naira.assistant.presentation.settings.page.category.ApiIntegrationsSettingsPage
import com.naira.assistant.presentation.settings.page.category.AssistantSettingsPage
import com.naira.assistant.presentation.settings.page.category.DataManagementSettingsPage
import com.naira.assistant.presentation.settings.page.category.ExtensionsSettingsPage
import com.naira.assistant.presentation.settings.page.category.EmailSettingsPage
import com.naira.assistant.presentation.settings.page.category.PermissionsSettingsPage
import com.naira.assistant.presentation.settings.page.category.PreferencesSettingsPage
import com.naira.assistant.presentation.settings.page.category.ProfileSettingsPage
import com.naira.assistant.presentation.settings.page.category.SpeechVoiceSettingsPage
import com.naira.assistant.presentation.settings.page.category.WhatsAppSettingsPage

private val settingsCategoryPages: List<CategoryNameUi> = listOf(
    PreferencesSettingsPage,
    ProfileSettingsPage,
    AssistantSettingsPage,
    SpeechVoiceSettingsPage,
    ExtensionsSettingsPage,
    AiProvidersSettingsPage,
    ApiIntegrationsSettingsPage,
    DataManagementSettingsPage,
    EmailSettingsPage,
    WhatsAppSettingsPage,
    AdvancedSettingsPage,
    PermissionsSettingsPage,
    AboutSettingsPage,
)

internal fun buildTree(context: SettingsPageContext): SettingsNode.Category = SettingsNode.Category(
    id = "root",
    title = "Settings",
    icon = Icons.Outlined.Tune,
    children = settingsCategoryPages.map { page -> page.build(context) }.sortedBy { category ->
        rootCategoryOrder.indexOf(category.id).let { index ->
            if (index < 0) rootCategoryOrder.size else index
        }
    },
)

internal fun resolveCategory(
    root: SettingsNode.Category,
    pathIds: List<String>,
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
