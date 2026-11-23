@file:Suppress("SpellCheckingInspection")

package com.naira.assistant.presentation.settings.page.category

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import com.naira.assistant.BuildConfig
import com.naira.assistant.presentation.settings.model.RowStyle
import com.naira.assistant.presentation.settings.model.SettingsNode
import com.naira.assistant.presentation.settings.page.CategoryNameUi
import com.naira.assistant.presentation.settings.page.SettingsPageContext

internal object AboutSettingsPage : CategoryNameUi() {
    override fun build(context: SettingsPageContext): SettingsNode.Category = with(context) {
        SettingsNode.Category(
            id = "about", title = "About", subtitle = "App information",
            icon = Icons.Outlined.Info,
            children = listOf(
                SettingsNode.Item(
                    id = "app-version", title = "App version",
                    subtitle = BuildConfig.VERSION_NAME,
                    icon = Icons.Outlined.Info,
                ),
                SettingsNode.Item(
                    id = "system-information",
                    title = "System information",
                    subtitle = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · " +
                            "${Build.MANUFACTURER} ${Build.MODEL}",
                    icon = Icons.Outlined.Settings,
                ),
                SettingsNode.Item(
                    id = "logout",
                    title = "Log out",
                    subtitle = "Sign out of your account on this device",
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    style = RowStyle.DANGER,
                    onClick = onLogout,
                ),
            )
        )
    }
}
