package com.naira.assistant.presentation.settings.page.category

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Email
import com.naira.assistant.presentation.settings.model.SettingsNode
import com.naira.assistant.presentation.settings.page.CategoryNameUi
import com.naira.assistant.presentation.settings.page.SettingsPageContext

internal object EmailSettingsPage : CategoryNameUi() {
    override fun build(context: SettingsPageContext): SettingsNode.Category = with(context) {
        SettingsNode.Category(
            id = "messaging-email",
            title = "Email",
            subtitle = "Manage named email contacts for the assistant.",
            icon = Icons.Outlined.Email,
            children = buildList {
                state.emailContacts.forEach { contact ->
                    add(
                        SettingsNode.Item(
                            id = "email-contact-${contact.email.lowercase()}",
                            title = contact.name,
                            subtitle = contact.email,
                            icon = Icons.Outlined.Email,
                            onClick = { onEditEmailContact(contact) },
                        ),
                    )
                }
                add(
                    SettingsNode.Item(
                        id = "add-email-contact",
                        title = "Add New Email",
                        subtitle = "Save a contact name and email address.",
                        icon = Icons.Outlined.Add,
                        onClick = { onEditEmailContact(null) },
                    ),
                )
            },
        )
    }
}
