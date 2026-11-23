@file:Suppress("SpellCheckingInspection")

package com.naira.assistant.presentation.settings.page.category

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.SmartToy
import com.naira.assistant.domain.settings.AssistantCharacter
import com.naira.assistant.presentation.settings.component.LanguageDropdown
import com.naira.assistant.presentation.settings.component.RelationTypeDropdown
import com.naira.assistant.presentation.settings.component.TrailingSwitch
import com.naira.assistant.presentation.settings.model.SettingsNode
import com.naira.assistant.presentation.settings.page.CategoryNameUi
import com.naira.assistant.presentation.settings.page.SettingsPageContext

internal object AssistantSettingsPage : CategoryNameUi() {
    override fun build(context: SettingsPageContext): SettingsNode.Category = with(context) {
        val state = this.state
        SettingsNode.Category(
            id = "assistant",
            title = "Assistants",
            subtitle = "${AssistantCharacter.fromName(state.assistantName).displayName} is active",
            icon = Icons.Outlined.AutoAwesome,
            children = buildList {
                AssistantCharacter.entries.forEach { character ->
                    val isActive = character.displayName.equals(state.assistantName, ignoreCase = true)
                    add(SettingsNode.Category(
                        id = "assistant-${character.name.lowercase()}",
                        title = character.displayName,
                        subtitle = "${character.gender} · ${character.voice.geminiVoiceName} voice",
                        icon = Icons.Outlined.SmartToy,
                        trailing = {
                            TrailingSwitch(
                                label = "Select ${character.displayName}",
                                checked = isActive,
                                onCheckedChange = { checked ->
                                    if (checked) onSaveAssistantName(character.displayName)
                                },
                            )
                        },
                        children = listOf(
                            SettingsNode.Item("${character.name.lowercase()}-language", "Language", state.assistantLanguage.name.lowercase().replaceFirstChar(Char::uppercase), Icons.Outlined.Language,
                                trailing = { LanguageDropdown(state.assistantLanguage, onSaveLanguage) }),
                            SettingsNode.Item("${character.name.lowercase()}-style", "Interaction style", state.relationship.name.lowercase().replaceFirstChar(Char::uppercase), Icons.Outlined.People,
                                trailing = { RelationTypeDropdown(state.relationship, onSaveRelationship) }),
                        ),
                    ))
                }
            }
        )
    }
}
