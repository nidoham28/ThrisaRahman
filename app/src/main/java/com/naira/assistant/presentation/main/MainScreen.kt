package com.naira.assistant.presentation.main

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.naira.assistant.core.database.reminder.ReminderStatus
import com.naira.assistant.core.service.CloudSettingsRepository
import com.naira.assistant.core.service.CloudSettingsSyncResult
import com.naira.assistant.core.service.SharedCloudSettingsLocalStore
import com.naira.assistant.core.service.SupabaseAuthService
import com.naira.assistant.core.service.SupabaseCloudSettingsRemoteStore
import com.naira.assistant.core.sync.SupabaseReminderRepository
import com.naira.assistant.core.theme.Night
import com.naira.assistant.core.util.GreetingProvider
import com.naira.assistant.domain.AccountSettingsStorage
import com.naira.assistant.domain.AssistantSettingsStorage
import com.naira.assistant.domain.sttLanguageTag
import com.naira.assistant.presentation.main.component.AppDrawer
import com.naira.assistant.presentation.main.component.AssistantNavigation
import com.naira.assistant.presentation.main.component.DrawerAction
import com.naira.assistant.presentation.main.component.Header
import com.naira.assistant.presentation.main.component.ReminderInboxDialog
import com.naira.assistant.presentation.main.pages.ChatPage
import com.naira.assistant.presentation.main.pages.HomePage
import com.naira.assistant.presentation.main.pages.MemoriesPage
import com.naira.assistant.presentation.main.pages.ProfilePage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Tab indexes used by [AssistantNavigation]. Index 2 is the center mic slot, not a page. */
private object Tab {
    const val HOME = 0
    const val CHAT = 1
    const val MEMORIES = 3
    const val PROFILE = 4
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    context: Context = LocalContext.current,
    viewModel: AssistantViewModel = defaultAssistantViewModel(context),
    onOpenSettings: () -> Unit = {},
    onOpenSchedule: () -> Unit = {},
    onDrawerAction: (DrawerAction) -> Unit = {},
) {
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    // --- UI state -----------------------------------------------------------
    var selectedTab by rememberSaveable { mutableIntStateOf(Tab.HOME) }
    var showReminderInbox by rememberSaveable { mutableStateOf(false) }

    // --- Stable singletons --------------------------------------------------
    val accountStorage = remember(appContext) { AccountSettingsStorage(appContext) }
    val assistantSettingsStorage = remember(appContext) { AssistantSettingsStorage(appContext) }
    val authService = remember { SupabaseAuthService() }
    val reminderRepository = remember { SupabaseReminderRepository.getInstance() }
    val cloudSettingsRepository = remember(appContext, authService) {
        CloudSettingsRepository(
            local = SharedCloudSettingsLocalStore(appContext),
            remote = SupabaseCloudSettingsRemoteStore(),
            currentUserId = { authService.currentUser()?.id },
        )
    }

    // --- Local settings state -----------------------------------------------
    // Note: initial read is synchronous (SharedPreferences). Acceptable here
    // because MainScreen only mounts *after* Splash already did network I/O.
    // If cold-start profiling shows this is hot, migrate to `produceState`
    // with Dispatchers.IO + a placeholder.
    var displayName by remember(appContext) {
        mutableStateOf(loadDisplayName(accountStorage))
    }
    var voiceLanguage by remember(appContext) {
        mutableStateOf(loadVoiceLanguage(assistantSettingsStorage))
    }
    var greetingText by remember { mutableStateOf(GreetingProvider().currentMessage()) }

    // Re-read on every resume. Setting mutableState to the same value is a
    // no-op, so redundant reads at startup cost nothing visually.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        displayName = loadDisplayName(accountStorage)
        voiceLanguage = loadVoiceLanguage(assistantSettingsStorage)
        greetingText = GreetingProvider().currentMessage()
        viewModel.refreshSettings()
        // Reminders may have changed while backgrounded; pull fresh data.
        scope.launch { reminderRepository.refresh() }
    }

    // One-shot cloud sync. Failure must not crash the composition — local
    // settings are authoritative, remote is best-effort.
    LaunchedEffect(cloudSettingsRepository) {
        try {
            val result = cloudSettingsRepository.synchronize()
            if (result is CloudSettingsSyncResult.Synced) viewModel.refreshSettings()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // TODO: forward to Crashlytics once integrated.
        }
    }

    // --- Voice + chat -------------------------------------------------------
    val voice = rememberVoiceSession(viewModel, voiceLanguage)
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val chatBusyStatus by viewModel.busyStatus.collectAsStateWithLifecycle()
    val awaitingApproval by viewModel.awaitingApproval.collectAsStateWithLifecycle()
    val currentSessionMood by viewModel.currentSessionMood.collectAsStateWithLifecycle()

    // --- Reminders ----------------------------------------------------------
    val reminders by reminderRepository.observeAll().collectAsStateWithLifecycle()
    val upcomingReminders = remember(reminders) {
        reminders
            .filter { it.status == ReminderStatus.ACTIVE || it.status == ReminderStatus.SNOOZED }
            .sortedBy {
                if (it.status == ReminderStatus.SNOOZED && it.snoozedUntilMs > 0) it.snoozedUntilMs
                else it.nextTriggerMs
            }
    }

    // --- Back handling ------------------------------------------------------
    // Drawer takes precedence over tab pop. Both are more important than
    // letting the activity finish.
    BackHandler(enabled = drawerState.isOpen || selectedTab != Tab.HOME) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            selectedTab = Tab.HOME
        }
    }

    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = drawerState,
        // Edge-swipe to open only on Home (avoids conflicts on Chat).
        // Edge-swipe to close works whenever the drawer is open.
        gesturesEnabled = drawerState.isOpen || selectedTab == Tab.HOME,
        drawerContent = {
            AppDrawer(
                // NOTE: `AppDrawer` here uses the legacy `displayname` param.
                // If you applied the AppDrawer rename fix, change this to
                // `displayName = displayName`.
                displayName = displayName,
                greeting = greetingText,
                onAction = { action ->
                    scope.launch { drawerState.close() }
                    when (action) {
                        DrawerAction.SETTINGS -> onOpenSettings()
                        DrawerAction.SCHEDULE -> onOpenSchedule()
                        else -> onDrawerAction(action)
                    }
                },
            )
        },
    ) {
        Scaffold(
            containerColor = Night,
            bottomBar = {
                AssistantNavigation(
                    selectedTab,
                    voice.state,
                    onSelect = { selectedTab = it },
                    onMicClick = { voice.onMicClick() },
                )
            },
        ) { innerPadding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                Header(
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onNotificationsClick = { showReminderInbox = true },
                    notificationCount = upcomingReminders.size,
                )
                when (selectedTab) {
                    Tab.CHAT -> ChatPage(
                        messages = messages,
                        busyStatus = chatBusyStatus,
                        awaitingApproval = awaitingApproval,
                        onSend = { text, imageUri -> viewModel.sendMessageStream(text, imageUri) },
                        onOpenImage = { imageUri -> openImage(context, imageUri) },
                        onResolveTool = viewModel::resolveToolApproval,
                        onStop = viewModel::cancelCurrent,
                    )
                    Tab.MEMORIES -> MemoriesPage()
                    Tab.PROFILE -> ProfilePage()
                    // HOME, plus defensively any unexpected index (2 = mic slot).
                    else -> HomePage(
                        voice = voice.state,
                        displayName = displayName,
                        mood = currentSessionMood,
                    )
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
                },
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Helpers                                                            */
/* ------------------------------------------------------------------ */

@Composable
private fun defaultAssistantViewModel(context: Context): AssistantViewModel {
    val appContext = context.applicationContext
    // Remember the factory so it isn't reallocated on every recomposition.
    val factory = remember(appContext) {
        viewModelFactory {
            initializer { AssistantViewModel(appContext) }
        }
    }
    return viewModel(factory = factory)
}

private fun loadDisplayName(storage: AccountSettingsStorage): String =
    storage.getDisplayName().ifBlank { storage.getUserName() }.ifBlank { "User" }

private fun loadVoiceLanguage(storage: AssistantSettingsStorage): String =
    storage.getLanguage().sttLanguageTag()

/** Opens an image in the user's viewer app, with feedback if none can handle it. */
private fun openImage(context: Context, imageUri: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(imageUri.toUri(), "image/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // Deliberately no FLAG_ACTIVITY_NEW_TASK: `context` is the Activity,
        // and NEW_TASK on ACTION_VIEW opens the viewer in a separate task —
        // confusing when Back lands on the launcher instead of the app.
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Couldn't open this image.", Toast.LENGTH_SHORT).show()
    }
}