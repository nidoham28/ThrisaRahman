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
        )
    }
}
