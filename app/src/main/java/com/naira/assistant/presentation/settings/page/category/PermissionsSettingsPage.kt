@file:Suppress("SpellCheckingInspection")

package com.naira.assistant.presentation.settings.page.category

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TouchApp
import com.naira.assistant.core.theme.Aqua
import com.naira.assistant.presentation.settings.component.Success
import com.naira.assistant.presentation.settings.component.TrailingBadge
import com.naira.assistant.presentation.settings.component.TrailingChevron
import com.naira.assistant.presentation.settings.component.TrailingSwitch
import com.naira.assistant.presentation.settings.model.SettingsNode
import com.naira.assistant.presentation.settings.page.CategoryNameUi
import com.naira.assistant.presentation.settings.page.SettingsPageContext

internal object PermissionsSettingsPage : CategoryNameUi() {
    override fun build(context: SettingsPageContext): SettingsNode.Category = with(context) {
        val state = this.state
        SettingsNode.Category(
            id = "privacy", title = "Permissions", subtitle = "Manage app access permissions.",
            icon = Icons.Outlined.Lock,
            accent = Aqua,
            children = listOf(
                SettingsNode.Item(
                    id = "sensitive-tool-access",
                    title = "Sensitive assistant actions",
                    subtitle = if (state.sensitiveToolAccessEnabled)
                        "Some sensitive device, screen, communication and data actions are allowed"
                    else "Some sensitive device, screen, communication and data actions are blocked",
                    icon = Icons.Outlined.Security,
                    onClick = { state.saveSensitiveToolAccess(!state.sensitiveToolAccessEnabled) },
                    trailing = {
                        TrailingSwitch(
                            label = "Sensitive assistant actions",
                            checked = state.sensitiveToolAccessEnabled,
                            onCheckedChange = state::saveSensitiveToolAccess,
                        )
                    },
                ),
                SettingsNode.Category(
                    id = "app-permissions",
                    title = "App permissions",
                    subtitle = if (permissionStatus.allGranted) "All permissions granted"
                    else "${permissionStatus.grantedCount} of ${permissionStatus.totalCount} granted",
                    icon = Icons.Outlined.Security,
                    children = listOfNotNull(
                        if (!permissionStatus.allGranted) {
                            SettingsNode.Item(
                                id = "grant-all-permissions", title = "Request all permissions",
                                subtitle = "Ask for all required permissions at once",
                                icon = Icons.Outlined.CheckCircle,
                                onClick = permissionActions.onRequestAll,
                                trailing = { TrailingBadge("REQUEST ALL", Aqua) }
                            )
                        } else null,
                        SettingsNode.Item(
                            id = "permission-mic", title = "Microphone",
                            subtitle = "Voice conversations & speech recognition",
                            icon = Icons.Outlined.Mic,
                            onClick = permissionActions.onRequestMic,
                            trailing = {
                                TrailingBadge(
                                    if (permissionStatus.hasMic) "ALLOWED" else "GRANT",
                                    if (permissionStatus.hasMic) Success else Aqua
                                )
                            }
                        ),
                        SettingsNode.Item(
                            id = "permission-physical-activity",
                            title = "Physical activity",
                            subtitle = "Optional step count and walking/running cues for virtual body motion; readings are sent with assistant requests",
                            icon = Icons.Outlined.Security,
                            onClick = permissionActions.onRequestActivityRecognition,
                            trailing = {
                                TrailingBadge(
                                    when {
                                        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> "NOT NEEDED"
                                        permissionStatus.hasActivityRecognition -> "ALLOWED"
                                        else -> "OPTIONAL"
                                    },
                                    if (
                                        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                                        permissionStatus.hasActivityRecognition
                                    ) Success else Aqua,
                                )
                            },
                        ),
                        SettingsNode.Item(
                            id = "permission-calendar", title = "Calendar",
                            subtitle = "Read & create calendar events",
                            icon = Icons.Outlined.CalendarMonth,
                            onClick = permissionActions.onRequestCalendar,
                            trailing = {
                                TrailingBadge(
                                    if (permissionStatus.hasCalendar) "ALLOWED" else "GRANT",
                                    if (permissionStatus.hasCalendar) Success else Aqua
                                )
                            }
                        ),
                        SettingsNode.Item(
                            id = "permission-notifications", title = "Notifications",
                            subtitle = "Reminders, alerts & assistant updates",
                            icon = Icons.Outlined.Notifications,
                            onClick = permissionActions.onRequestNotifications,
                            trailing = {
                                TrailingBadge(
                                    if (permissionStatus.hasNotifications) "ALLOWED" else "GRANT",
                                    if (permissionStatus.hasNotifications) Success else Aqua
                                )
                            }
                        ),
                        SettingsNode.Item(
                            id = "permission-exact-alarm", title = "Alarms & Reminders",
                            subtitle = "Schedule alarms and timers accurately on time",
                            icon = Icons.Outlined.Alarm,
                            onClick = permissionActions.onRequestExactAlarm,
                            trailing = {
                                TrailingBadge(
                                    if (permissionStatus.hasExactAlarm) "ALLOWED" else "GRANT",
                                    if (permissionStatus.hasExactAlarm) Success else Aqua
                                )
                            }
                        ),
                        SettingsNode.Item(
                            id = "permission-bluetooth", title = "Nearby devices (Bluetooth)",
                            subtitle = "Route voice audio to Bluetooth headsets",
                            icon = Icons.Outlined.Bluetooth,
                            onClick = permissionActions.onRequestBluetooth,
                            trailing = {
                                TrailingBadge(
                                    if (permissionStatus.hasBluetooth) "ALLOWED" else "GRANT",
                                    if (permissionStatus.hasBluetooth) Success else Aqua
                                )
                            }
                        ),
                        SettingsNode.Item(
                            id = "permission-media", title = "Photos, videos & audio",
                            subtitle = when {
                                permissionStatus.hasAllMedia -> "Full access to shared media"
                                permissionStatus.hasMedia -> "Selected media access is available"
                                else -> "Access media files for attachments and browsing"
                            },
                            icon = Icons.Outlined.GraphicEq,
                            onClick = permissionActions.onRequestMedia,
                            trailing = {
                                TrailingBadge(
                                    when {
                                        permissionStatus.hasAllMedia -> "ALLOWED"
                                        permissionStatus.hasMedia -> "SELECTED"
                                        else -> "GRANT"
                                    },
                                    if (permissionStatus.hasMedia) Success else Aqua
                                )
                            }
                        ),
                        SettingsNode.Item(
                            id = "permission-all-files", title = "All files access",
                            subtitle = "Manage shared files and folders on this device",
                            icon = Icons.Outlined.FolderOpen,
                            onClick = permissionActions.onRequestAllFiles,
                            trailing = {
                                TrailingBadge(
                                    when {
                                        Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> "NOT NEEDED"
                                        permissionStatus.hasAllFiles -> "ALLOWED"
                                        else -> "GRANT"
                                    },
                                    if (permissionStatus.hasAllFiles) Success else Aqua
                                )
                            }
                        ),
                        SettingsNode.Item(
                            id = "permission-modify-settings", title = "Modify system settings",
                            subtitle = "Allow Naira to change supported system settings",
                            icon = Icons.Outlined.Settings,
                            onClick = permissionActions.onRequestModifySystemSettings,
                            trailing = {
                                TrailingBadge(
                                    if (permissionStatus.hasModifySystemSettings) "ALLOWED" else "GRANT",
                                    if (permissionStatus.hasModifySystemSettings) Success else Aqua,
                                )
                            }
                        ),
                        SettingsNode.Item(
                            id = "permission-accessibility", title = "Screen access (Accessibility)",
                            subtitle = if (permissionStatus.hasAccessibility)
                                "Enabled in Android Accessibility settings"
                            else "Open Android Settings → Accessibility to enable screen reading and controls",
                            icon = Icons.Outlined.TouchApp,
                            onClick = permissionActions.onOpenAccessibility,
                            trailing = {
                                TrailingBadge(
                                    if (permissionStatus.hasAccessibility) "ALLOWED" else "GRANT",
                                    if (permissionStatus.hasAccessibility) Success else Aqua
                                )
                            }
                        ),
                        SettingsNode.Item(
                            id = "permission-app-settings", title = "System app settings",
                            subtitle = "Manage all permissions directly in Android Settings",
                            icon = Icons.Outlined.Settings,
                            onClick = permissionActions.onOpenAppSettings,
                            trailing = { TrailingChevron() }
                        )
                    )
                ),
            )
        )
    }
}
