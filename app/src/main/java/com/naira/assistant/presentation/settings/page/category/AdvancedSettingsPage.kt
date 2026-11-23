@file:Suppress("SpellCheckingInspection")

package com.naira.assistant.presentation.settings.page.category

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsVoice
import androidx.compose.material.icons.outlined.Tune
import com.naira.assistant.presentation.settings.component.TrailingSwitch
import com.naira.assistant.presentation.settings.component.WakeWordSensitivityDropdown
import com.naira.assistant.presentation.settings.model.SettingsNode
import com.naira.assistant.presentation.settings.page.CategoryNameUi
import com.naira.assistant.presentation.settings.page.SettingsPageContext

internal object AdvancedSettingsPage : CategoryNameUi() {
    override fun build(context: SettingsPageContext): SettingsNode.Category = with(context) {
        val state = this.state
        SettingsNode.Category(
            id = "advanced-settings", title = "Advanced Settings",
            subtitle = "Configure advanced app settings.",
            icon = Icons.Outlined.Settings,
            children = listOf(
                SettingsNode.Item(
                    id = "wakeup-word-service",
                    title = "Wakeup Word Service",
                    subtitle = "Configure voice activation settings.",
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
        )
    }
}
