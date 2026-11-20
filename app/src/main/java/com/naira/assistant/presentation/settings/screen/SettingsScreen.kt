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
import com.naira.assistant.core.service.CloudSettingsPatch
import com.naira.assistant.core.service.CloudSettingsRepository
import com.naira.assistant.core.service.CloudSettingsSyncResult
import com.naira.assistant.core.service.FirebaseAuthService
import com.naira.assistant.core.service.FirebaseCloudSettingsRemoteStore
import com.naira.assistant.core.service.SharedCloudSettingsLocalStore
import com.naira.assistant.core.service.screen.ThrisaAccessibilityService
import com.naira.assistant.core.sync.FirebaseApiKeyStore
import com.naira.assistant.core.theme.Night
import com.naira.assistant.domain.settings.ApiKeyStorage
import com.naira.assistant.domain.settings.AsrProvider
import com.naira.assistant.domain.settings.AssistantProvider
import com.naira.assistant.presentation.settings.component.ApiKeyDialog
import com.naira.assistant.presentation.settings.component.AsrApiKeyDialog
import com.naira.assistant.presentation.settings.component.EditFieldDialog
import com.naira.assistant.presentation.settings.component.NodeCard
import com.naira.assistant.presentation.settings.component.RootList
import com.naira.assistant.presentation.settings.component.SettingsAsrStorageErrorCard
import com.naira.assistant.presentation.settings.component.SettingsCloudSyncErrorCard
import com.naira.assistant.presentation.settings.component.TopBar
import com.naira.assistant.presentation.settings.model.EditField
import com.naira.assistant.presentation.settings.model.SettingsDestination
import com.naira.assistant.presentation.settings.permission.PermissionActions
import com.naira.assistant.presentation.settings.permission.checkPermissions
import com.naira.assistant.presentation.settings.permission.mediaReadPermissions
import com.naira.assistant.presentation.settings.permission.openAccessibilitySettings
import com.naira.assistant.presentation.settings.permission.openAllFilesAccessSettings
import com.naira.assistant.presentation.settings.permission.openAppSettings
import com.naira.assistant.presentation.settings.permission.openExactAlarmSettings
import com.naira.assistant.presentation.settings.permission.openNotificationSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch


/* ------------------------------------------------------------------ */
/*  Entry                                                              */
/* ------------------------------------------------------------------ */

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onNavigate: (SettingsDestination) -> Unit = {},
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
    var editingAsrApiKey by rememberSaveable { mutableStateOf(false) }
    var editingFieldName by rememberSaveable { mutableStateOf<String?>(null) }
    val editingField = editingFieldName?.let { name ->
        EditField.entries.firstOrNull { it.name == name }
    }

    var awaitingAccessibilityGrant by remember { mutableStateOf(false) }
    var showAccessibilityHelp by remember { mutableStateOf(false) }

    var refreshPermissionsTrigger by remember { mutableIntStateOf(0) }
    val permissionStatus = remember(refreshPermissionsTrigger) { checkPermissions(context) }
    val userId = remember(refreshPermissionsTrigger) { authService.currentUser()?.uid }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermissionsTrigger++
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

    fun saveProviderCredential(provider: AssistantProvider, key: String) {
        val uid = userId
        if (uid == null) {
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
        if (provider == AssistantProvider.GROQ) {
            Toast.makeText(
                context,
                "Groq API key changes are saved on this device only; cloud sync needs backend support.",
                Toast.LENGTH_LONG,
            ).show()
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

    fun saveCricketCredential(key: String) {
        if (userId == null) {
            Toast.makeText(context, "Sign in before saving an API key.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            state.saveCricketApiKey(key)
            editingCricketApiKey = false
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: "Couldn't save the CricAPI key.", Toast.LENGTH_LONG).show()
        }
    }

    fun saveAsrCredential(key: String) {
        val provider = state.asrProvider
        if (provider == AsrProvider.SYSTEM) return
        try {
            state.saveAsrApiKey(provider, key)
            editingAsrApiKey = false
            Toast.makeText(context, "Speech key saved encrypted on this device.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: "Couldn't save the speech provider key.", Toast.LENGTH_LONG).show()
        }
    }

    fun removeAsrCredential() {
        val provider = state.asrProvider
        if (provider == AsrProvider.SYSTEM) return
        try {
            state.saveAsrApiKey(provider, "")
            editingAsrApiKey = false
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: "Couldn't remove the speech provider key.", Toast.LENGTH_LONG).show()
        }
    }

    fun chooseActiveProvider(provider: AssistantProvider) {
        state.saveProvider(provider, userId)
        if (provider == AssistantProvider.GROQ) {
            Toast.makeText(
                context,
                "Groq selection is saved on this device; cross-device sync needs backend support.",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        cloudSettingsRepository.recordChange(CloudSettingsPatch(activeProvider = provider))
        synchronizeCloudSettings()
    }

    LaunchedEffect(userId) {
        if (userId == null) return@LaunchedEffect
        cloudSyncError = null
        val cloudKeys = try {
            apiKeySync.fetchAll()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cloudSyncError = e.message ?: "Couldn't load provider keys from Firebase."
            return@LaunchedEffect
        }
        cloudKeys.forEach { (provider, key) -> state.saveApiKey(provider, key) }
    }

    LaunchedEffect(refreshPermissionsTrigger) {
        if (refreshPermissionsTrigger > 0) synchronizeCloudSettings()
    }

    val requestMultipleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        refreshPermissionsTrigger++
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

    val root = buildTree(
        state = state,
        permissionStatus = permissionStatus,
        permissionActions = permissionActions,
        onNavigate = onNavigate,
        onSaveActiveProvider = ::chooseActiveProvider,
        onEditKey = { editingProviderName = it.name },
        onEditCricketApiKey = { editingCricketApiKey = true },
        onEditField = { editingFieldName = it.name },
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
        onSaveAssistantThinkingMode = state::saveAssistantThinkingMode,
        onSaveSpeechEngine = { value ->
            state.saveSpeechEngine(value)
            cloudSettingsRepository.recordChange(CloudSettingsPatch(speechEngine = value))
            synchronizeCloudSettings()
        },
        onSaveAsrProvider = { value ->
            try {
                state.saveAsrProvider(value)
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "Couldn't save the speech provider.", Toast.LENGTH_LONG).show()
            }
        },
        onEditAsrApiKey = { editingAsrApiKey = true },
    )
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
            state.asrSettingsError?.let { message ->
                SettingsAsrStorageErrorCard(
                    message = message,
                    onReset = {
                        try {
                            state.clearAsrSettings()
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                e.message ?: "Couldn't clear the saved speech settings.",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                )
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

    if (editingAsrApiKey && state.asrProvider != AsrProvider.SYSTEM) {
        AsrApiKeyDialog(
            provider = state.asrProvider,
            hasSavedKey = state.asrApiKeyConfigured,
            onSave = ::saveAsrCredential,
            onRemove = ::removeAsrCredential,
            onDismiss = { editingAsrApiKey = false },
        )
    }

    editingField?.let { field ->
        val initialValue = when (field) {
            EditField.USERNAME -> state.username
            EditField.PHONE -> state.phone
            EditField.OPENROUTER_MODEL -> state.openRouterModelId
        }
        EditFieldDialog(
            field = field,
            initialValue = initialValue,
            onSave = { value: String ->
                if (field == EditField.OPENROUTER_MODEL) {
                    state.saveOpenRouterModelId(value)
                } else {
                    state.saveProfileField(field, value)
                    val patch = when (field) {
                        EditField.USERNAME -> CloudSettingsPatch(username = state.username)
                        EditField.PHONE -> CloudSettingsPatch(phone = state.phone)
                        EditField.OPENROUTER_MODEL -> CloudSettingsPatch()
                    }
                    cloudSettingsRepository.recordChange(patch)
                    synchronizeCloudSettings()
                }
                editingFieldName = null
            },
            onDismiss = { editingFieldName = null },
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
}
