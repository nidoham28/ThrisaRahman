@file:Suppress("SpellCheckingInspection")

package com.naira.assistant.presentation.settings.screen

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import com.naira.assistant.presentation.settings.model.SettingsNode
import com.naira.assistant.presentation.settings.model.rootCategoryOrder
import com.naira.assistant.presentation.settings.page.AboutUi
import com.naira.assistant.presentation.settings.page.AdvancedSettingsUi
import com.naira.assistant.presentation.settings.page.AssistantUi
import com.naira.assistant.presentation.settings.page.DataUi
import com.naira.assistant.presentation.settings.page.ExtensionsUi
import com.naira.assistant.presentation.settings.page.IntegrationsUi
import com.naira.assistant.presentation.settings.page.PermissionsUi
import com.naira.assistant.presentation.settings.page.PreferencesUi
import com.naira.assistant.presentation.settings.page.ProfileUi
import com.naira.assistant.presentation.settings.page.ProvidersUi
import com.naira.assistant.presentation.settings.page.VoiceUi



internal fun buildTree(context: SettingsPageContext): SettingsNode.Category = SettingsNode.Category(
    id = "root",
    title = "Settings",
    icon = Icons.Outlined.Tune,
    children = listOf(
        PreferencesUi(context),
        ProfileUi(context),
        AssistantUi(context),
        VoiceUi(context),
        ExtensionsUi(context),
        ProvidersUi(context),
        IntegrationsUi(context),
        DataUi(context),
        AdvancedSettingsUi(context),
        PermissionsUi(context),
        AboutUi(context),
    ).sortedBy { category ->
        rootCategoryOrder.indexOf(category.id).let { index ->
            if (index < 0) rootCategoryOrder.size else index
        }
    },
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
