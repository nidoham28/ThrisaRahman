@file:Suppress("SpellCheckingInspection")

package com.naira.assistant.presentation.settings.page.category

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import com.naira.assistant.core.theme.Aqua
import com.naira.assistant.presentation.settings.component.Success
import com.naira.assistant.presentation.settings.component.TrailingBadge
import com.naira.assistant.presentation.settings.model.SettingsNode
import com.naira.assistant.presentation.settings.page.CategoryNameUi
import com.naira.assistant.presentation.settings.page.SettingsPageContext

internal object ApiIntegrationsSettingsPage : CategoryNameUi() {
    override fun build(context: SettingsPageContext): SettingsNode.Category = with(context) {
        val state = this.state
        SettingsNode.Category(
            id = "integrations", title = "API Integrations", subtitle = "Manage API keys for external services.",
            icon = Icons.Outlined.Key,
            children = listOf(
                SettingsNode.Item(
                    id = "weatherapi-key",
                    title = "Weather API Key",
                    subtitle = if (state.weatherApiKey.isBlank()) {
                        "Enter your weather API key."
                    } else {
                        "Configured"
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
                    title = "Cricket API Key",
                    subtitle = if (state.cricketApiKey.isBlank()) "Enter your cricket API key." else "Configured",
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
        )
    }
}
