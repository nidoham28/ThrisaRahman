package com.nidoham.thrisa.presentation.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nidoham.thrisa.core.database.reminder.ReminderStatus
import com.nidoham.thrisa.core.database.reminder.SqliteReminderDatabase
import com.nidoham.thrisa.core.theme.Night
import com.nidoham.thrisa.core.util.GreetingProvider
import com.nidoham.thrisa.domain.AccountSettingsStorage
import com.nidoham.thrisa.domain.sttLanguageTag
import com.nidoham.thrisa.presentation.main.component.AppDrawer
import com.nidoham.thrisa.presentation.main.component.AssistantNavigation
import com.nidoham.thrisa.presentation.main.component.DrawerAction
import com.nidoham.thrisa.presentation.main.component.Header
import com.nidoham.thrisa.presentation.main.component.ReminderInboxDialog
import com.nidoham.thrisa.presentation.main.pages.ChatPage
import com.nidoham.thrisa.presentation.main.pages.HomePage
import com.nidoham.thrisa.presentation.main.pages.MemoriesPage
import com.nidoham.thrisa.presentation.main.pages.ProfilePage
import kotlinx.coroutines.launch
import androidx.core.net.toUri

/** Tab indexes used by [AssistantNavigation]. Index 2 is the center mic slot, not a page. */
private object Tab {
    const val HOME = 0
    const val CHAT = 1
    const val MEMORIES = 3
    const val PROFILE = 4
}

@Composable
fun MainScreen(
    context: Context = LocalContext.current,
    // Activity-scoped, so chat history survives rotation. onCleared() closes the API client.
    viewModel: AssistantViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AssistantViewModel(context.applicationContext) }
        }
    ),
    onOpenSettings: () -> Unit = {},
    onOpenSchedule: () -> Unit = {},
    onDrawerAction: (DrawerAction) -> Unit = {}
) {
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    // --- UI state -------------------------------------------------------------
    var selectedTab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var showReminderInbox by rememberSaveable { mutableStateOf(false) }

    // --- Account settings (re-read whenever the screen resumes) ---------------
    val accountStorage = remember { AccountSettingsStorage(appContext) }
    var displayName by remember { mutableStateOf(accountStorage.getUserName().ifBlank { "User" }) }
    var voiceLanguage by remember { mutableStateOf(accountStorage.getLanguage().sttLanguageTag()) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        // Returning from Settings: reload API key, prompt settings, name and voice language.
        viewModel.refreshSettings()
        displayName = accountStorage.getUserName().ifBlank { "User" }
        voiceLanguage = accountStorage.getLanguage().sttLanguageTag()
    }

    // --- Voice + chat ---------------------------------------------------------
    // STT/TTS follow the assistant language and are re-created when it changes.
    val voice = rememberVoiceSession(viewModel, voiceLanguage)
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val chatBusyStatus by viewModel.busyStatus.collectAsStateWithLifecycle()

    // --- Reminders ------------------------------------------------------------
    val reminderRepository = remember { SqliteReminderDatabase.getInstance(appContext) }
    val reminders by reminderRepository.observeAll().collectAsStateWithLifecycle()
    val upcomingReminders = remember(reminders) {
        reminders
            .filter { it.status == ReminderStatus.ACTIVE || it.status == ReminderStatus.SNOOZED }
            .sortedBy {
                if (it.status == ReminderStatus.SNOOZED && it.snoozedUntilMs > 0) it.snoozedUntilMs
                else it.nextTriggerMs
            }
    }

    val greetingText = remember { GreetingProvider().currentMessage() }

    // Back from any other tab returns to Home before leaving the app.
    BackHandler(enabled = selectedTab != Tab.HOME) { selectedTab = Tab.HOME }

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
        ) { innerPadding ->
            Column(Modifier.fillMaxSize().padding(innerPadding)) {
                Header(
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onNotificationsClick = { showReminderInbox = true },
                    notificationCount = upcomingReminders.size
                )
                when (selectedTab) {
                    Tab.CHAT -> ChatPage(
                        messages = messages,
                        busyStatus = chatBusyStatus,
                        onSend = { text, imageUri -> viewModel.sendMessageStream(text, imageUri) },
                        onOpenImage = { imageUri -> openImage(context, imageUri) },
                        onStop = viewModel::cancelCurrent
                    )
                    Tab.MEMORIES -> MemoriesPage()
                    Tab.PROFILE -> ProfilePage()
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

/** Opens an image in the user's viewer app, with feedback if none can handle it. */
private fun openImage(context: Context, imageUri: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(imageUri.toUri(), "image/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Couldn't open this image.", Toast.LENGTH_SHORT).show()
    }
}