@file:Suppress("SpellCheckingInspection")

package com.naira.assistant.presentation.settings.screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.naira.assistant.core.service.CloudSettingsPatch
import com.naira.assistant.core.service.CloudSettingsRepository
import com.naira.assistant.core.service.CloudSettingsSyncResult
import com.naira.assistant.core.service.FirebaseAuthService
import com.naira.assistant.core.service.FirebaseCloudSettingsRemoteStore
import com.naira.assistant.core.service.SharedCloudSettingsLocalStore
import com.naira.assistant.core.service.screen.ThrisaAccessibilityService
import com.naira.assistant.core.sync.FirebaseApiKeyStore
import com.naira.assistant.core.sync.FirebasePaths
import com.naira.assistant.core.theme.Night
import com.naira.assistant.domain.settings.ApiKeyStorage
import com.naira.assistant.domain.settings.AssistantProvider
import com.naira.assistant.domain.settings.EmailContact
import com.naira.assistant.presentation.settings.component.ApiKeyDialog
import com.naira.assistant.presentation.settings.component.EditFieldDialog
import com.naira.assistant.presentation.settings.component.EmailContactDialog
import com.naira.assistant.presentation.settings.component.NodeCard
import com.naira.assistant.presentation.settings.component.RootList
import com.naira.assistant.presentation.settings.component.SettingsCloudSyncErrorCard
import com.naira.assistant.presentation.settings.component.TopBar
import com.naira.assistant.presentation.settings.model.EditField
import com.naira.assistant.presentation.settings.permission.PermissionActions
import com.naira.assistant.presentation.settings.permission.checkPermissions
import com.naira.assistant.presentation.settings.permission.mediaReadPermissions
import com.naira.assistant.presentation.settings.permission.openAccessibilitySettings
import com.naira.assistant.presentation.settings.permission.openAllFilesAccessSettings
import com.naira.assistant.presentation.settings.permission.openAppSettings
import com.naira.assistant.presentation.settings.permission.openExactAlarmSettings
import com.naira.assistant.presentation.settings.permission.openNotificationSettings
import com.naira.assistant.presentation.settings.permission.openModifySystemSettings
import com.naira.assistant.presentation.main.WakeWordForegroundService
import com.naira.assistant.presentation.settings.page.SettingsPageContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch


/* ------------------------------------------------------------------ */
/*  Entry                                                              */
/* ------------------------------------------------------------------ */

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onLogout: () -> Unit = {},
) {
    val context = LocalContext.current
    val storage = remember { ApiKeyStorage(context.applicationContext) }
    val authService = remember { FirebaseAuthService() }
    val state = remember(storage) {
        SettingsState(
            storage = storage,
            context = context.applicationContext,
            currentUserId = { authService.currentUser()?.uid },
        )
    }
    val cloudSettingsRepository = remember(context) {
        CloudSettingsRepository(
            local = SharedCloudSettingsLocalStore(context.applicationContext),
            remote = FirebaseCloudSettingsRemoteStore(),
            currentUserId = { authService.currentUser()?.uid },
        )
    }
    val coroutineScope = rememberCoroutineScope()
    val apiKeySync = remember(context) { FirebaseApiKeyStore() }
    var cloudSyncError by remember { mutableStateOf<String?>(null) }

    var pathIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var editingProviderName by rememberSaveable { mutableStateOf<String?>(null) }
    val editingProvider = editingProviderName?.let { name ->
        AssistantProvider.entries.firstOrNull { it.name == name }
    }
    var editingCricketApiKey by rememberSaveable { mutableStateOf(false) }
    var editingCustomTtsApiKey by rememberSaveable { mutableStateOf(false) }
    var editingWeatherApiKey by rememberSaveable { mutableStateOf(false) }
    var editingFieldName by rememberSaveable { mutableStateOf<String?>(null) }
    val editingField = editingFieldName?.let { name ->
        EditField.entries.firstOrNull { it.name == name }
    }
    var showEmailContactDialog by rememberSaveable { mutableStateOf(false) }
    var emailContactBeingEdited by remember { mutableStateOf<EmailContact?>(null) }

    var awaitingAccessibilityGrant by remember { mutableStateOf(false) }
    var showAccessibilityHelp by remember { mutableStateOf(false) }
    var showLogoutConfirmation by rememberSaveable { mutableStateOf(false) }
    var dataManagementAction by rememberSaveable { mutableStateOf<String?>(null) }

    var refreshPermissionsTrigger by remember { mutableIntStateOf(0) }
    var enableWakeWordAfterPermission by remember { mutableStateOf(false) }
    val permissionStatus = remember(refreshPermissionsTrigger) { checkPermissions(context) }
    val userId = remember(refreshPermissionsTrigger) { authService.currentUser()?.uid }
    val emailAddress = remember(refreshPermissionsTrigger) { authService.currentUser()?.email.orEmpty() }

    LaunchedEffect(userId) {
        state.refreshEmailContacts()
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissionsTrigger++
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(refreshPermissionsTrigger) {
        if (!awaitingAccessibilityGrant) return@LaunchedEffect
        awaitingAccessibilityGrant = false
        when {
            ThrisaAccessibilityService.isEnabledInSettings(context) ->
                Toast.makeText(context, "Screen access enabled.", Toast.LENGTH_SHORT).show()
            Build.VERSION.SDK_INT >= 33 -> showAccessibilityHelp = true
            else -> Toast.makeText(
                context,
                "Screen access is still off. Enable \"Thrisa AI Screen Access\" in Settings → Accessibility.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun synchronizeCloudSettings() {
        coroutineScope.launch {
            cloudSyncError = null
            val result = try {
                cloudSettingsRepository.synchronize()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                cloudSyncError = e.message ?: "Couldn't sync settings."
                return@launch
            }
            when (result) {
                is CloudSettingsSyncResult.Synced -> state.refreshCloudBackedSettings()
                CloudSettingsSyncResult.SignInRequired -> Unit
                is CloudSettingsSyncResult.Failed -> cloudSyncError = result.message
            }
        }
    }

    fun editEmailContact(contact: EmailContact?) {
        emailContactBeingEdited = contact
        showEmailContactDialog = true
    }

    fun saveProviderCredential(provider: AssistantProvider, key: String) {
        if (userId == null) {
            Toast.makeText(context, "Sign in before saving provider keys.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            state.saveApiKey(provider, key)
            editingProviderName = null
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: "Couldn't save the provider key.", Toast.LENGTH_LONG).show()
            return
        }
        coroutineScope.launch {
            val synced = try {
                if (key.isBlank()) apiKeySync.delete(provider)
                else apiKeySync.push(provider, key)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (!synced) {
                Toast.makeText(
                    context,
                    "Key saved on this device, but cloud sync failed.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    fun syncServiceApiKey(service: String, key: String) {
        coroutineScope.launch {
            val synced = try {
                if (key.isBlank()) apiKeySync.deleteServiceKey(service)
                else apiKeySync.pushServiceKey(service, key)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (!synced) Toast.makeText(
                context,
                "Key saved on this device, but cloud sync failed.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun saveCricketCredential(key: String) {
        if (userId == null) {
            Toast.makeText(context, "Sign in before saving an API key.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            state.saveCricketApiKey(key)
            editingCricketApiKey = false
            syncServiceApiKey("cricapi", key)
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: "Couldn't save the CricAPI key.", Toast.LENGTH_LONG).show()
        }
    }

    fun saveWeatherCredential(key: String) {
        if (userId == null) {
            Toast.makeText(context, "Sign in before saving an API key.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            state.saveWeatherApiKey(key)
            editingWeatherApiKey = false
            syncServiceApiKey("weatherapi", key)
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: "Couldn't save the WeatherAPI key.", Toast.LENGTH_LONG).show()
        }
    }

    fun saveCustomTtsCredential(key: String) {
        if (userId == null) {
            Toast.makeText(context, "Sign in before saving an API key.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            state.saveCustomTtsApiKey(key)
            editingCustomTtsApiKey = false
            syncServiceApiKey("custom_tts", key)
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: "Couldn't save the Custom TTS key.", Toast.LENGTH_LONG).show()
        }
    }

    fun chooseActiveProvider(provider: AssistantProvider) {
        state.saveProvider(provider, userId)
        cloudSettingsRepository.recordChange(CloudSettingsPatch(activeProvider = provider))
        synchronizeCloudSettings()
    }

    LaunchedEffect(refreshPermissionsTrigger) {
        if (refreshPermissionsTrigger > 0) synchronizeCloudSettings()
    }

    DisposableEffect(userId, cloudSettingsRepository) {
        if (userId == null) {
            return@DisposableEffect onDispose { }
        }
        val database = FirebaseDatabase.getInstance(
            "https://naira-assistant-default-rtdb.asia-southeast1.firebasedatabase.app",
        )
        val settingsRefs = listOf(
            FirebasePaths.user(database, userId),
            FirebasePaths.assistants(database, userId),
            FirebasePaths.cloudSettings(database, userId),
        )
        val settingsListeners = settingsRefs.map { reference ->
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    synchronizeCloudSettings()
                }
                override fun onCancelled(error: DatabaseError) {
                    cloudSyncError = error.message
                }
            }
            reference.addValueEventListener(listener)
            reference to listener
        }
        val keysReference = FirebasePaths.providerKeys(database, userId)
        val keysListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                coroutineScope.launch {
                    try {
                        val remoteKeys = apiKeySync.fetchAll()
                        AssistantProvider.entries.forEach { provider ->
                            state.saveApiKey(provider, remoteKeys[provider].orEmpty())
                        }
                        val serviceKeys = apiKeySync.fetchServiceKeys()
                        serviceKeys["cricapi"]?.let(state::saveCricketApiKey)
                        serviceKeys["weatherapi"]?.let(state::saveWeatherApiKey)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        cloudSyncError = e.message ?: "Couldn't load provider keys from Firebase."
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                cloudSyncError = error.message
            }
        }
        keysReference.addValueEventListener(keysListener)
        onDispose {
            settingsListeners.forEach { (reference, listener) ->
                reference.removeEventListener(listener)
            }
            keysReference.removeEventListener(keysListener)
        }
    }

    val requestMultipleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        refreshPermissionsTrigger++
        if (enableWakeWordAfterPermission) {
            enableWakeWordAfterPermission = false
            val updatedPermissions = checkPermissions(context)
            if (updatedPermissions.hasMic && updatedPermissions.hasNotifications) {
                runCatching {
                    state.saveWakeWordServiceEnabled(true)
                    WakeWordForegroundService.start(context)
                }.onFailure {
                    state.saveWakeWordServiceEnabled(false)
                    Toast.makeText(context, "Couldn't start the wakeup word service.", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(context, "Microphone and notification access are needed for the wakeup word service.", Toast.LENGTH_LONG).show()
            }
            return@rememberLauncherForActivityResult
        }
        val allGranted = results.values.isNotEmpty() && results.values.all { it }
        if (allGranted) {
            Toast.makeText(context, "Permissions granted successfully.", Toast.LENGTH_SHORT).show()
        } else if (results.values.any { !it }) {
            Toast.makeText(context, "Some permissions were not granted.", Toast.LENGTH_SHORT).show()
        }
    }

    val requestSingleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        refreshPermissionsTrigger++
        if (isGranted) Toast.makeText(context, "Permission granted.", Toast.LENGTH_SHORT).show()
        else Toast.makeText(context, "Permission not granted.", Toast.LENGTH_SHORT).show()
    }

    val permissionActions = remember(context, permissionStatus) {
        PermissionActions(
            onRequestAll = {
                val missing = mutableListOf<String>()
                if (!permissionStatus.hasMic) missing.add(Manifest.permission.RECORD_AUDIO)
                if (!permissionStatus.hasAllMedia) {
                    mediaReadPermissions().forEach { permission ->
                        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                            missing.add(permission)
                        }
                    }
                }
                if (!permissionStatus.hasCalendar) {
                    missing.add(Manifest.permission.READ_CALENDAR)
                    missing.add(Manifest.permission.WRITE_CALENDAR)
                }
                if (!permissionStatus.hasNotifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    missing.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (!permissionStatus.hasBluetooth && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    missing.add(Manifest.permission.BLUETOOTH_CONNECT)
                }
                if (missing.isNotEmpty()) {
                    requestMultipleLauncher.launch(missing.toTypedArray())
                } else if (!permissionStatus.hasExactAlarm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    openExactAlarmSettings(context)
                } else if (!permissionStatus.hasAllFiles) {
                    openAllFilesAccessSettings(context)
                } else if (!permissionStatus.hasModifySystemSettings) {
                    openModifySystemSettings(context)
                } else if (!permissionStatus.hasAccessibility) {
                    awaitingAccessibilityGrant = true
                    openAccessibilitySettings(context)
                } else {
                    Toast.makeText(context, "All permissions are already granted.", Toast.LENGTH_SHORT).show()
                }
            },
            onRequestMic = {
                if (permissionStatus.hasMic) Toast.makeText(context, "Microphone permission is already granted.", Toast.LENGTH_SHORT).show()
                else requestSingleLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onRequestCalendar = {
                if (permissionStatus.hasCalendar) Toast.makeText(context, "Calendar permissions are already granted.", Toast.LENGTH_SHORT).show()
                else requestMultipleLauncher.launch(
                    arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                )
            },
            onRequestNotifications = {
                when {
                    permissionStatus.hasNotifications ->
                        Toast.makeText(context, "Notification permission is already granted.", Toast.LENGTH_SHORT).show()
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                        requestSingleLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else -> openNotificationSettings(context)
                }
            },
            onRequestExactAlarm = {
                if (permissionStatus.hasExactAlarm) Toast.makeText(context, "Exact alarm permission is already granted.", Toast.LENGTH_SHORT).show()
                else openExactAlarmSettings(context)
            },
            onRequestBluetooth = {
                when {
                    permissionStatus.hasBluetooth ->
                        Toast.makeText(context, "Bluetooth permission is already granted.", Toast.LENGTH_SHORT).show()
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                        requestSingleLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    else -> Toast.makeText(context, "Bluetooth permission is granted by default on this version.", Toast.LENGTH_SHORT).show()
                }
            },
            onRequestMedia = {
                if (permissionStatus.hasAllMedia) Toast.makeText(context, "Media access is already granted.", Toast.LENGTH_SHORT).show()
                else {
                    val missing = mediaReadPermissions().filter {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }
                    requestMultipleLauncher.launch(missing.toTypedArray())
                }
            },
            onRequestAllFiles = {
                if (permissionStatus.hasAllFiles) Toast.makeText(context, "All-files access is already granted.", Toast.LENGTH_SHORT).show()
                else openAllFilesAccessSettings(context)
            },
            onRequestModifySystemSettings = {
                if (permissionStatus.hasModifySystemSettings) {
                    Toast.makeText(context, "Modify-system-settings access is already granted.", Toast.LENGTH_SHORT).show()
                } else openModifySystemSettings(context)
            },
            onOpenAccessibility = {
                if (permissionStatus.hasAccessibility) Toast.makeText(context, "Screen access is already granted.", Toast.LENGTH_SHORT).show()
                else {
                    awaitingAccessibilityGrant = true
                    openAccessibilitySettings(context)
                }
            },
            onOpenAppSettings = { openAppSettings(context) },
            onOpenPermissions = { pathIds = listOf("permissions") },
        )
    }

    val pageContext = SettingsPageContext(
        state = state,
        emailAddress = emailAddress,
        permissionStatus = permissionStatus,
        permissionActions = permissionActions,
        onSaveActiveProvider = ::chooseActiveProvider,
        onEditKey = { editingProviderName = it.name },
        onEditCricketApiKey = { editingCricketApiKey = true },
        onEditWeatherApiKey = { editingWeatherApiKey = true },
        onEditCustomTtsApiKey = { editingCustomTtsApiKey = true },
        onDataManagementAction = { dataManagementAction = it },
        onEditField = { editingFieldName = it.name },
        onEditEmailContact = ::editEmailContact,
        onSaveGender = { value ->
            state.saveGender(value)
            cloudSettingsRepository.recordChange(CloudSettingsPatch(gender = value))
            synchronizeCloudSettings()
        },
        onSaveAssistantName = { value ->
            state.saveAssistantName(value)
            cloudSettingsRepository.recordChange(
                CloudSettingsPatch(assistantName = state.assistantName)
            )
            synchronizeCloudSettings()
        },
        onSaveRelationship = { value ->
            state.saveRelationship(value)
            cloudSettingsRepository.recordChange(CloudSettingsPatch(relationship = value))
            synchronizeCloudSettings()
        },
        onSaveLanguage = { value ->
            state.saveAssistantLanguage(value)
            cloudSettingsRepository.recordChange(CloudSettingsPatch(assistantLanguage = value))
            synchronizeCloudSettings()
        },
        onSaveAssistantThinkingMode = { value ->
            state.saveAssistantThinkingMode(value)
            cloudSettingsRepository.recordChange(
                CloudSettingsPatch(assistantThinkingMode = value)
            )
            synchronizeCloudSettings()
        },
        onSaveSpeechEngine = { value ->
            state.saveSpeechEngine(value)
            cloudSettingsRepository.recordChange(CloudSettingsPatch(speechEngine = value))
            synchronizeCloudSettings()
        },
        onSaveTheme = { value ->
            state.saveTheme(value)
            cloudSettingsRepository.recordChange(CloudSettingsPatch(theme = value))
            synchronizeCloudSettings()
        },
        onToggleWakeWordService = { enabled ->
            if (!enabled) {
                state.saveWakeWordServiceEnabled(false)
                WakeWordForegroundService.stop(context)
            } else {
                val missing = buildList {
                    if (!permissionStatus.hasMic) add(Manifest.permission.RECORD_AUDIO)
                    if (!permissionStatus.hasNotifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                if (missing.isEmpty()) {
                    runCatching {
                        state.saveWakeWordServiceEnabled(true)
                        WakeWordForegroundService.start(context)
                    }.onFailure {
                        state.saveWakeWordServiceEnabled(false)
                        Toast.makeText(context, "Couldn't start the wakeup word service.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    enableWakeWordAfterPermission = true
                    requestMultipleLauncher.launch(missing.toTypedArray())
                }
            }
        },
        onLogout = { showLogoutConfirmation = true },
    )
    val root = buildTree(pageContext)
    val current = resolveCategory(root, pathIds)
    val isRoot = pathIds.isEmpty()

    val handleBack: () -> Unit = {
        if (isRoot) onBack() else pathIds = pathIds.dropLast(1)
    }

    Column(modifier.fillMaxSize().background(Night).statusBarsPadding()) {
        TopBar(title = current.title, onBack = handleBack)
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            cloudSyncError?.let { message ->
                SettingsCloudSyncErrorCard(message, ::synchronizeCloudSettings)
            }
            if (isRoot) {
                RootList(current.children) { id -> pathIds = pathIds + id }
            } else {
                NodeCard(current.children) { id -> pathIds = pathIds + id }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    editingProvider?.let { provider ->
        ApiKeyDialog(
            title = "${provider.displayName} API Key",
            placeholder = provider.placeholder,
            initialKey = state.keyFor(provider),
            onSave = { newKey: String -> saveProviderCredential(provider, newKey.trim()) },
            onRemove = { saveProviderCredential(provider, "") },
            onDismiss = { editingProviderName = null }
        )
    }

    if (editingCricketApiKey) {
        ApiKeyDialog(
            title = "CricAPI Key",
            placeholder = "Enter your CricAPI key",
            description = "Get a free API key at cricapi.com. It is stored on this device for your signed-in account.",
            initialKey = state.cricketApiKey,
            onSave = ::saveCricketCredential,
            onRemove = { saveCricketCredential("") },
            onDismiss = { editingCricketApiKey = false },
        )
    }

    if (editingWeatherApiKey) {
        ApiKeyDialog(
            title = "WeatherAPI Key",
            placeholder = "Enter your free WeatherAPI key",
            description = "Get a free key at weatherapi.com. Stored on this device for your signed-in account and not synced.",
            initialKey = state.weatherApiKey,
            onSave = ::saveWeatherCredential,
            onRemove = { saveWeatherCredential("") },
            onDismiss = { editingWeatherApiKey = false },
        )
    }

    if (editingCustomTtsApiKey) {
        ApiKeyDialog(
            title = "Custom TTS API Key",
            placeholder = "Enter your TTS provider API key",
            description = "Stored on this device for your signed-in account and synced with your service keys.",
            initialKey = state.customTtsApiKey,
            onSave = ::saveCustomTtsCredential,
            onRemove = { saveCustomTtsCredential("") },
            onDismiss = { editingCustomTtsApiKey = false },
        )
    }

    editingField?.let { field ->
        val initialValue = when (field) {
            EditField.USERNAME -> state.username
            EditField.PHONE -> state.phone
            EditField.CUSTOM_USER_AGENT -> state.customUserAgent
            EditField.GEMINI_MODEL -> state.geminiModelId
            EditField.GEMINI_TTS_MODEL -> state.geminiTtsModel
            EditField.GEMINI_TTS_VOICE -> state.geminiTtsVoice
            EditField.CUSTOM_TTS_VOICE -> state.customTtsVoice
            EditField.CUSTOM_TTS_MODEL -> state.customTtsModel
            EditField.CUSTOM_TTS_BASE_URL -> state.customTtsBaseUrl
            EditField.OPENROUTER_MODEL -> state.openRouterModelId
            EditField.OPENROUTER_BASE_URL -> state.openRouterBaseUrl
            EditField.CUSTOM_PROVIDER_MODEL -> state.customProviderModelId
            EditField.CUSTOM_PROVIDER_BASE_URL -> state.customProviderBaseUrl
        }
        EditFieldDialog(
            field = field,
            initialValue = initialValue,
            onSave = { value: String ->
                when (field) {
                    EditField.GEMINI_MODEL -> {
                        state.saveGeminiModelId(value)
                    }
                    EditField.GEMINI_TTS_MODEL -> {
                        state.saveGeminiTtsModel(value)
                    }
                    EditField.GEMINI_TTS_VOICE -> {
                        state.saveGeminiTtsVoice(value)
                    }
                    EditField.CUSTOM_TTS_VOICE -> {
                        state.saveCustomTtsVoice(value)
                    }
                    EditField.CUSTOM_TTS_MODEL -> {
                        state.saveCustomTtsModel(value)
                    }
                    EditField.CUSTOM_TTS_BASE_URL -> {
                        state.saveCustomTtsBaseUrl(value)
                    }
                    EditField.OPENROUTER_MODEL -> {
                        state.saveOpenRouterModelId(value)
                        cloudSettingsRepository.recordChange(
                            CloudSettingsPatch(openRouterModelId = state.openRouterModelId)
                        )
                        synchronizeCloudSettings()
                    }
                    EditField.OPENROUTER_BASE_URL -> {
                        state.saveOpenRouterBaseUrl(value)
                    }
                    EditField.CUSTOM_USER_AGENT -> {
                        state.saveCustomUserAgent(value)
                    }
                    EditField.CUSTOM_PROVIDER_MODEL -> {
                        state.saveCustomProviderModelId(value)
                    }
                    EditField.CUSTOM_PROVIDER_BASE_URL -> {
                        state.saveCustomProviderBaseUrl(value)
                    }
                    else -> {
                        state.saveProfileField(field, value)
                        val patch = when (field) {
                            EditField.USERNAME -> CloudSettingsPatch(username = state.username)
                            EditField.PHONE -> CloudSettingsPatch(phone = state.phone)
                            EditField.CUSTOM_USER_AGENT -> CloudSettingsPatch()
                            EditField.GEMINI_MODEL -> CloudSettingsPatch()
                            EditField.GEMINI_TTS_MODEL -> CloudSettingsPatch()
                            EditField.GEMINI_TTS_VOICE -> CloudSettingsPatch()
                            EditField.CUSTOM_TTS_VOICE -> CloudSettingsPatch()
                            EditField.CUSTOM_TTS_MODEL -> CloudSettingsPatch()
                            EditField.CUSTOM_TTS_BASE_URL -> CloudSettingsPatch()
                            EditField.OPENROUTER_MODEL -> CloudSettingsPatch()
                            EditField.OPENROUTER_BASE_URL -> CloudSettingsPatch()
                            EditField.CUSTOM_PROVIDER_MODEL -> CloudSettingsPatch()
                            EditField.CUSTOM_PROVIDER_BASE_URL -> CloudSettingsPatch()
                        }
                        cloudSettingsRepository.recordChange(patch)
                        synchronizeCloudSettings()
                    }
                }
                editingFieldName = null
            },
            onDismiss = { editingFieldName = null },
        )
    }

    if (showEmailContactDialog) {
        EmailContactDialog(
            initialContact = emailContactBeingEdited,
            onSave = { name, email, replacingEmail ->
                try {
                    state.saveEmailContact(name, email, replacingEmail)
                    null
                } catch (error: IllegalStateException) {
                    error.message ?: "Saved email contacts could not be updated."
                }
            },
            onDelete = { email ->
                try {
                    state.removeEmailContact(email)
                    null
                } catch (error: IllegalStateException) {
                    error.message ?: "Saved email contacts could not be updated."
                }
            },
            onDismiss = {
                showEmailContactDialog = false
                emailContactBeingEdited = null
            },
        )
    }

    dataManagementAction?.let { action ->
        AlertDialog(
            onDismissRequest = { dataManagementAction = null },
            title = { Text(action) },
            text = { Text("Backup export and restore are not available yet.") },
            confirmButton = {
                TextButton(onClick = { dataManagementAction = null }) { Text("OK") }
            },
        )
    }

    if (showAccessibilityHelp) {
        AlertDialog(
            onDismissRequest = { showAccessibilityHelp = false },
            title = { Text("Screen access is still off") },
            text = {
                Text(
                    "If the \"Thrisa AI Screen Access\" toggle was greyed out with a " +
                            "\"Restricted setting\" message, Android 13 and newer block sideloaded " +
                            "apps from enabling accessibility services. To unblock it:\n\n" +
                            "1. Open Thrisa's App info page\n" +
                            "2. Tap the ⋮ menu in the top-right corner\n" +
                            "3. Tap \"Allow restricted settings\"\n" +
                            "4. Come back and grant Screen access again\n\n" +
                            "Otherwise, enable \"Thrisa AI Screen Access\" directly in " +
                            "Settings → Accessibility."
                )
            },
            confirmButton = {
                TextButton(onClick = { showAccessibilityHelp = false; openAppSettings(context) }) {
                    Text("Open app info")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityHelp = false }) { Text("Later") }
            }
        )
    }

    if (showLogoutConfirmation) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmation = false },
            title = { Text("Log out?") },
            text = { Text("You will need to sign in again to access your account.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirmation = false
                        coroutineScope.launch {
                            authService.logout().fold(
                                onSuccess = { onLogout() },
                                onFailure = { error ->
                                    Toast.makeText(
                                        context,
                                        error.message ?: "Couldn't log out. Please try again.",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                },
                            )
                        }
                    },
                ) {
                    Text("Log out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
