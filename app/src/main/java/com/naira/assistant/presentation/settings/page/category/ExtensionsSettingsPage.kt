@file:Suppress("SpellCheckingInspection")

package com.naira.assistant.presentation.settings.page.category

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import com.naira.assistant.presentation.settings.model.SettingsNode
import com.naira.assistant.presentation.settings.page.CategoryNameUi
import com.naira.assistant.presentation.settings.page.SettingsPageContext

internal object ExtensionsSettingsPage : CategoryNameUi() {
    override fun build(context: SettingsPageContext): SettingsNode.Category = with(context) {
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
        )
    }
}
