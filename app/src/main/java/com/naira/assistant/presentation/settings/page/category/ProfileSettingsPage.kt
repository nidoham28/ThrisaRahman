@file:Suppress("SpellCheckingInspection")

package com.naira.assistant.presentation.settings.page.category

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import com.naira.assistant.presentation.settings.component.GenderDropdown
import com.naira.assistant.presentation.settings.model.EditField
import com.naira.assistant.presentation.settings.model.SettingsNode
import com.naira.assistant.presentation.settings.page.CategoryNameUi
import com.naira.assistant.presentation.settings.page.SettingsPageContext

internal object ProfileSettingsPage : CategoryNameUi() {
    override fun build(context: SettingsPageContext): SettingsNode.Category = with(context) {
        val state = this.state
        SettingsNode.Category(
            id = "profile", title = "Profile", subtitle = "Manage your account information.",
            icon = Icons.Outlined.Person,
            children = listOf(
                SettingsNode.Item(
                    id = "username", title = "Username",
                    subtitle = state.username.ifBlank { "Set your username" },
                    icon = Icons.Outlined.Person,
                    onClick = { onEditField(EditField.USERNAME) },
                ),
                SettingsNode.Item(
                    id = "email", title = "Email",
                    subtitle = state.email.ifBlank { "Add your email" },
                    icon = Icons.Outlined.Email,
                    onClick = { onEditField(EditField.EMAIL) },
                ),
                SettingsNode.Item(
                    id = "phone", title = "Phone number",
                    subtitle = state.phone.ifBlank { "Not set" },
                    icon = Icons.Outlined.Phone,
                    onClick = { onEditField(EditField.PHONE) },
                ),
                SettingsNode.Item(
                    id = "gender", title = "Gender", subtitle = "Select your gender.",
                    icon = Icons.Outlined.Person,
                    trailing = { GenderDropdown(state.gender, onSaveGender) },
                ),
            )
        )
    }
}
