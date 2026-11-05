package com.nidoham.thrisa.presentation.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nidoham.thrisa.core.theme.Night
import com.nidoham.thrisa.core.util.GreetingProvider
import com.nidoham.thrisa.core.database.reminder.ReminderStatus
import com.nidoham.thrisa.core.database.reminder.SqliteReminderDatabase
import com.nidoham.thrisa.domain.AccountSettingsStorage
import com.nidoham.thrisa.domain.sttLanguageTag
import kotlinx.coroutines.launch
import com.nidoham.thrisa.presentation.main.component.AppDrawer
import com.nidoham.thrisa.presentation.main.component.AssistantNavigation
import com.nidoham.thrisa.presentation.main.component.DrawerAction
import com.nidoham.thrisa.presentation.main.component.Header
import com.nidoham.thrisa.presentation.main.component.ReminderInboxDialog
import presentation.main.pages.ChatPage
import presentation.main.pages.HomePage
import presentation.main.pages.MemoriesPage
import presentation.main.pages.ProfilePage

@Composable
fun MainScreen(
    context: Context = LocalContext.current,
    viewModel: AssistantViewModel = remember { AssistantViewModel(context) },
    onOpenSettings: () -> Unit = {},
    onOpenSchedule: () -> Unit = {},
    onDrawerAction: (DrawerAction) -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showReminderInbox by remember { mutableStateOf(false) }
    // Load saved username and compute time-aware greeting
    val accountStorage = remember { AccountSettingsStorage(context.applicationContext) }
    var displayName by remember { mutableStateOf(accountStorage.getUserName().ifBlank { "User" }) }
    // Voice STT/TTS follow the assistant language setting; re-created when it changes.
    var voiceLanguage by remember { mutableStateOf(accountStorage.getLanguage().sttLanguageTag()) }
    val voice = rememberVoiceSession(viewModel, voiceLanguage)
    val messages by viewModel.messages.collectAsState()
    val chatBusyStatus by viewModel.busyStatus.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val greetingText = remember { GreetingProvider().currentMessage() }
    val reminderRepository = remember { SqliteReminderDatabase.getInstance(context.applicationContext) }
    val reminders by reminderRepository.observeAll().collectAsState()
    val upcomingReminders = reminders
        .filter { it.status == ReminderStatus.ACTIVE || it.status == ReminderStatus.SNOOZED }
        .sortedBy { if (it.status == ReminderStatus.SNOOZED && it.snoozedUntilMs > 0) it.snoozedUntilMs else it.nextTriggerMs }

    DisposableEffect(viewModel) { onDispose { viewModel.dispose() } }

    // 🔑 Reload API key + all prompt-affecting settings every time the screen
    // resumes (e.g. after the user returns from Settings).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSettings()
                // Refresh display name and voice language in case they were changed in Settings
                displayName = accountStorage.getUserName().ifBlank { "User" }
                voiceLanguage = accountStorage.getLanguage().sttLanguageTag()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                displayname = displayName,
                greeting = greetingText,
                onAction = { action ->
                    scope.launch { drawerState.close() }
                    when (action) {
                        DrawerAction.SETTINGS -> onOpenSettings()
                        DrawerAction.SCHEDULE -> onOpenSchedule()
                        else -> onDrawerAction(action)
                    }
                }
            )
        }
    ) {
        Scaffold(
            containerColor = Night,
            bottomBar = {
                AssistantNavigation(
                    selectedTab,
                    voice.state,
                    onSelect = { selectedTab = it },
                    onMicClick = { voice.onMicClick() }
                )
            }
        ) { insets ->
            Column(Modifier.fillMaxSize().padding(insets)) {
                Header(
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onNotificationsClick = { showReminderInbox = true },
                    notificationCount = upcomingReminders.size
                )
                when (selectedTab) {
                    0 -> HomePage(voice.state, displayName)
                    1 -> ChatPage(
                        messages = messages,
                        busyStatus = chatBusyStatus,
                        onSend = { text, imageUri -> viewModel.sendMessageStream(text, imageUri) },
                        onOpenImage = { imageUri ->
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(Uri.parse(imageUri), "image/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            }
                        }
                    )
                    3 -> MemoriesPage()
                    4 -> ProfilePage()
                    else -> HomePage(voice.state, displayName)
                }
            }
        }
        if (showReminderInbox) {
            ReminderInboxDialog(
                reminders = upcomingReminders,
                onDismiss = { showReminderInbox = false },
                onOpenSchedule = {
                    showReminderInbox = false
                    onOpenSchedule()
                }
            )
        }
    }
}
