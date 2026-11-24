package com.naira.assistant.presentation.settings.page.category

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Phone
import com.naira.assistant.presentation.settings.model.EditField
import com.naira.assistant.presentation.settings.model.SettingsNode
import com.naira.assistant.presentation.settings.page.CategoryNameUi
import com.naira.assistant.presentation.settings.page.SettingsPageContext

internal object WhatsAppSettingsPage : CategoryNameUi() {
    override fun build(context: SettingsPageContext): SettingsNode.Category = with(context) {
        SettingsNode.Category(
            id = "messaging-whatsapp",
            title = "WhatsApp",
            subtitle = "Manage the phone number associated with your profile.",
            icon = Icons.Outlined.Phone,
            children = listOf(
                SettingsNode.Item(
                    id = "messaging-whatsapp-phone",
                    title = "Phone number",
                    subtitle = state.phone.ifBlank { "Set your number in Profile." },
                    icon = Icons.Outlined.Phone,
                    onClick = { onEditField(EditField.PHONE) },
                ),
            ),
        )
    }
}
