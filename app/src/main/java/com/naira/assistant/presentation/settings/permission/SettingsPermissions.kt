@file:Suppress("SpellCheckingInspection")

package com.naira.assistant.presentation.settings.permission

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.core.content.ContextCompat
import com.naira.assistant.core.service.screen.ThrisaAccessibilityService


/* ------------------------------------------------------------------ */
/*  Permissions helpers                                               */
/* ------------------------------------------------------------------ */

internal data class PermissionStatus(
    val hasMic: Boolean,
    val hasCalendar: Boolean,
    val hasNotifications: Boolean,
    val hasExactAlarm: Boolean,
    val hasBluetooth: Boolean,
    val hasMedia: Boolean,
    val hasAllMedia: Boolean,
    val hasAllFiles: Boolean,
    val hasAccessibility: Boolean
) {
    val totalCount: Int = 8
    val grantedCount: Int
        get() = listOf(
            hasMic, hasCalendar, hasNotifications, hasExactAlarm,
            hasBluetooth, hasAllMedia, hasAllFiles, hasAccessibility
        ).count { it }
    val allGranted: Boolean
        get() = grantedCount == totalCount
}

internal class PermissionActions(
    val onRequestAll: () -> Unit,
    val onRequestMic: () -> Unit,
    val onRequestCalendar: () -> Unit,
    val onRequestNotifications: () -> Unit,
    val onRequestExactAlarm: () -> Unit,
    val onRequestBluetooth: () -> Unit,
    val onRequestMedia: () -> Unit,
    val onRequestAllFiles: () -> Unit,
    val onOpenAccessibility: () -> Unit,
    val onOpenAppSettings: () -> Unit,
    val onOpenPermissions: () -> Unit
)

internal fun checkPermissions(context: Context): PermissionStatus {
    val hasMic = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    val hasCalendar = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CALENDAR
    ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
        context, Manifest.permission.WRITE_CALENDAR
    ) == PackageManager.PERMISSION_GRANTED

    val hasNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.areNotificationsEnabled() != false
    }

    val hasExactAlarm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        am?.canScheduleExactAlarms() != false
    } else true

    val hasBluetooth = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ContextCompat.checkSelfPermission(
        context, Manifest.permission.BLUETOOTH_CONNECT
    ) == PackageManager.PERMISSION_GRANTED

    val mediaPermissions = mediaReadPermissions()
    val fullMediaPermissions = mediaPermissions.filterNot {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            it == Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        } else {
            TODO("VERSION.SDK_INT < UPSIDE_DOWN_CAKE")
        }
    }
    val grantedMediaCount = fullMediaPermissions.count {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    val hasAllMedia = fullMediaPermissions.isNotEmpty() && grantedMediaCount == fullMediaPermissions.size
    val hasSelectedVisualMedia = Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    ) == PackageManager.PERMISSION_GRANTED
    val hasMedia = hasAllMedia || hasSelectedVisualMedia || grantedMediaCount > 0
    val hasAllFiles = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
    val hasAccessibility = ThrisaAccessibilityService.isEnabledInSettings(context)

    return PermissionStatus(
        hasMic = hasMic,
        hasCalendar = hasCalendar,
        hasNotifications = hasNotifications,
        hasExactAlarm = hasExactAlarm,
        hasBluetooth = hasBluetooth,
        hasMedia = hasMedia,
        hasAllMedia = hasAllMedia,
        hasAllFiles = hasAllFiles,
        hasAccessibility = hasAccessibility
    )
}

internal fun mediaReadPermissions(): List<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> buildList {
        add(Manifest.permission.READ_MEDIA_IMAGES)
        add(Manifest.permission.READ_MEDIA_VIDEO)
        add(Manifest.permission.READ_MEDIA_AUDIO)
        if (Build.VERSION.SDK_INT >= 34) add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    }
    Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q -> listOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

internal fun openAppSettings(context: Context) {
    runCatching {
        val intent = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "Unable to open app settings.", Toast.LENGTH_SHORT).show()
    }
}

internal fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val intent = Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.recoverCatching { openAppSettings(context) }
    } else {
        Toast.makeText(context, "Exact alarm permission is granted by default on this version.", Toast.LENGTH_SHORT).show()
    }
}

internal fun openNotificationSettings(context: Context) {
    val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }.recoverCatching { openAppSettings(context) }
}

internal fun openAllFilesAccessSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    val appIntent = Intent(AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(appIntent) }.recoverCatching {
        val fallback = Intent(AndroidSettings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(fallback)
    }.onFailure {
        Toast.makeText(context, "Unable to open all-files access settings.", Toast.LENGTH_SHORT).show()
    }
}

internal fun openAccessibilitySettings(context: Context) {
    val intent = Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }.recoverCatching { openAppSettings(context) }
        .onFailure {
            Toast.makeText(context, "Unable to open accessibility settings.", Toast.LENGTH_SHORT).show()
        }
}
