@file:Suppress("SpellCheckingInspection")

package com.naira.assistant.presentation.settings.page.category

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import com.naira.assistant.presentation.settings.component.ThemeDropdownTrailing
import com.naira.assistant.presentation.settings.component.TrailingValue
import com.naira.assistant.presentation.settings.model.EditField
import com.naira.assistant.presentation.settings.model.SettingsNode
import com.naira.assistant.presentation.settings.page.CategoryNameUi
import com.naira.assistant.presentation.settings.page.SettingsPageContext

internal object PreferencesSettingsPage : CategoryNameUi() {
    override fun build(context: SettingsPageContext): SettingsNode.Category = with(context) {
        val state = this.state
        SettingsNode.Category(
            id = "preferences", title = "Preferences", subtitle = "Customize your app experience.",
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
                    id = "general", title = "General", subtitle = "Manage general app prefrences.",
                    icon = Icons.Outlined.Settings,
                    children = emptyList(),
                ),
                SettingsNode.Category(
                    id = "interface", title = "Interface", subtitle = "Customize app apperance and layout.",
                    icon = Icons.Outlined.Tune,
                    children = emptyList(),
                ),
                SettingsNode.Category(
                    id = "network", title = "Network", subtitle = "Manage network settings and connectivity.",
                    icon = Icons.Outlined.Settings,
                    children = listOf(
                        SettingsNode.Item(
                            id = "custom-user-agent",
                            title = "User Agent",
                            subtitle = state.customUserAgent.ifBlank { "Customize your browser identity." },
                            icon = Icons.Outlined.Settings,
                            onClick = { onEditField(EditField.CUSTOM_USER_AGENT) },
                            trailing = {
                                TrailingValue(if (state.customUserAgent.isBlank()) "Default" else "Custom")
                            },
                        ),
                    ),
                ),
            ),
        )
    }
}
