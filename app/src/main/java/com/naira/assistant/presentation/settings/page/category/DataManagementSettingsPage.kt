@file:Suppress("SpellCheckingInspection")

package com.naira.assistant.presentation.settings.page.category

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import com.naira.assistant.presentation.settings.component.TrailingChevron
import com.naira.assistant.presentation.settings.model.SettingsNode
import com.naira.assistant.presentation.settings.page.CategoryNameUi
import com.naira.assistant.presentation.settings.page.SettingsPageContext

internal object DataManagementSettingsPage : CategoryNameUi() {
    override fun build(context: SettingsPageContext): SettingsNode.Category = with(context) {
        SettingsNode.Category(
            id = "data", title = "Data Backup", subtitle = "Backup and restore app data",
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
        )
    }
}
