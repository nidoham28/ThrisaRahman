package com.naira.assistant.presentation.settings

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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsVoice
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naira.assistant.core.service.screen.ThrisaAccessibilityService
import com.naira.assistant.core.service.CloudSettingsRepository
import com.naira.assistant.core.service.CloudSettingsPatch
import com.naira.assistant.core.service.CloudSettingsSyncResult
import com.naira.assistant.core.service.InMemoryCloudSettingsLocalStore
import com.naira.assistant.core.service.SupabaseCloudSettingsRemoteStore
import com.naira.assistant.core.service.SupabaseAuthService
import com.naira.assistant.core.sync.SupabaseApiKeyStore
import com.naira.assistant.core.sound.SpeechEngine
import com.naira.assistant.core.theme.Aqua
import com.naira.assistant.core.theme.CardStroke
import com.naira.assistant.core.theme.Lilac
import com.naira.assistant.core.theme.Night
import com.naira.assistant.core.theme.TextHigh
import com.naira.assistant.core.theme.TextSoft
import com.naira.assistant.domain.AccountSettingsStorage
import com.naira.assistant.domain.ApiKeyStorage
import com.naira.assistant.domain.AssistantProvider
import com.naira.assistant.domain.AssistantCharacter
import com.naira.assistant.domain.AssistantLanguage
import com.naira.assistant.domain.AssistantSettingsStorage
import com.naira.assistant.domain.Gender
import com.naira.assistant.domain.RelationType
import com.naira.assistant.domain.SpeechSettingsStorage
import com.naira.assistant.domain.UserSettingsStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/* ------------------------------------------------------------------ */
/*  Enums                                                              */
/* ------------------------------------------------------------------ */

enum class SettingsDestination {
    PERSONA, SUBSCRIPTION, APPEARANCE, PRIVACY, ABOUT
}

private enum class RowStyle { NORMAL, PREMIUM, DANGER }

/** Text fields the user can edit through a dialog. */
private enum class EditField(
    val title: String,
    val description: String,
    val placeholder: String,
    val isPhoneNumber: Boolean = false,
    val requiresNonBlank: Boolean = false,
) {
    USERNAME("Username", "How the assistant should address you.", "e.g. Rahim"),
    DISPLAY_NAME("Display name", "Name shown on your profile.", "e.g. Rahim"),
    PHONE("Phone number", "Your profile phone number.", "+880 1XXXXXXXXX", isPhoneNumber = true),
    OPENROUTER_MODEL(
        "OpenRouter model",
        "Enter an OpenRouter model ID. Use openrouter/auto for automatic routing.",
        AssistantProvider.OPENROUTER.defaultModelId,
        requiresNonBlank = true,
    ),
}

/* ------------------------------------------------------------------ */
/*  Node model                                                         */
/* ------------------------------------------------------------------ */

private sealed interface SettingsNode {
    val id: String
    val title: String

    data class Category(
        override val id: String,
        override val title: String,
        val subtitle: String? = null,
        val icon: ImageVector,
        val accent: Color = Aqua,
        val children: List<SettingsNode>
    ) : SettingsNode

    data class Item(
        override val id: String,
        override val title: String,
        val subtitle: String? = null,
        val icon: ImageVector? = null,
        val style: RowStyle = RowStyle.NORMAL,
        val enabled: Boolean = true,
        val onClick: (() -> Unit)? = null,
        val trailing: @Composable () -> Unit = { TrailingChevron() }
    ) : SettingsNode
}

/* ------------------------------------------------------------------ */
/*  Theme tokens                                                       */
/* ------------------------------------------------------------------ */

private val RowSurface = Color(0xFF0C1420)
private val SheetSurface = Color(0xFF0A111B)
private val OptionSurface = Color(0xFF091018)
private val Danger     = Color(0xFFFF6B6B)
private val Premium    = Color(0xFFFFC857)
private val Success    = Color(0xFF4ADE80)

/* ------------------------------------------------------------------ */
/*  Relation helpers                                                   */
/* ------------------------------------------------------------------ */

private fun RelationType.accent(): Color = when {
    isMature -> Danger
    isRomantic -> Lilac
    else -> Aqua
}

private fun RelationType.icon(): ImageVector = when (this) {
    RelationType.STRANGER -> Icons.Outlined.Person
    RelationType.FRIEND -> Icons.Outlined.People
    RelationType.CLOSE_FRIEND -> Icons.Outlined.ChatBubbleOutline
    RelationType.GIRLFRIEND -> Icons.Outlined.FavoriteBorder
    RelationType.WIFE -> Icons.Outlined.Favorite
}

private fun RelationType.badgeLabel(): String = when {
    isMature -> "18+"
    isRomantic -> "ROMANTIC"
    else -> "BOND"
}

/* ------------------------------------------------------------------ */
/*  State holder                                                       */
/* ------------------------------------------------------------------ */

@Stable
private class SettingsState(
    private val storage: ApiKeyStorage,
    context: Context,
    private val userId: String?,
) {
    var notifications by mutableStateOf(true)
    private val userSettingsStorage = UserSettingsStorage()
    var theme by mutableStateOf(userSettingsStorage.getTheme())
    var fontSize by mutableStateOf(userSettingsStorage.getFontSize())
    var requestTimeout by mutableIntStateOf(30)
    var selectedProvider by mutableStateOf(storage.getSelectedProvider(userId))
    private var geminiKey by mutableStateOf(
        userId?.let { storage.getApiKey(AssistantProvider.GEMINI.name, it) }.orEmpty()
    )
    private var openRouterKey by mutableStateOf(
        userId?.let { storage.getApiKey(AssistantProvider.OPENROUTER.name, it) }.orEmpty()
    )
    var cricketApiKey by mutableStateOf(
        userId?.let(storage::getCricApiKey).orEmpty()
    )
    var openRouterModelId by mutableStateOf(storage.getOpenRouterModelId(userId))
    private val speechStorage = SpeechSettingsStorage(context)
    private val accountStorage = AccountSettingsStorage(context)
    private val assistantStorage = AssistantSettingsStorage(context)
    var assistantLanguage by mutableStateOf(assistantStorage.getLanguage())
    var username by mutableStateOf(accountStorage.getUserName())
    var displayName by mutableStateOf(accountStorage.getDisplayName())
    var phone by mutableStateOf(accountStorage.getPhoneNumber())
    var gender by mutableStateOf(accountStorage.getGender())
    var assistantName by mutableStateOf(assistantStorage.getName())
    var relationship by mutableStateOf(assistantStorage.getRelationship())
    var speechEngine by mutableStateOf(
        runCatching { SpeechEngine.valueOf(speechStorage.getEngine()) }.getOrDefault(SpeechEngine.SYSTEM)
    )

    fun keyFor(provider: AssistantProvider): String = when (provider) {
        AssistantProvider.GEMINI -> geminiKey
        AssistantProvider.OPENROUTER -> openRouterKey
    }

    /** Saves the key directly (plaintext SharedPreferences) and updates the UI state. */
    fun saveApiKey(provider: AssistantProvider, value: String) {
        val cleaned = value.trim()
        when (provider) {
            AssistantProvider.GEMINI -> geminiKey = cleaned
            AssistantProvider.OPENROUTER -> openRouterKey = cleaned
        }
        userId?.let { storage.saveApiKey(provider.name, it, cleaned) }
    }

    fun saveCricketApiKey(value: String) {
        val cleaned = value.trim()
        cricketApiKey = cleaned
        userId?.let { storage.saveCricApiKey(it, cleaned) }
    }

    fun saveProvider(provider: AssistantProvider, userId: String?) {
        selectedProvider = provider
        storage.saveSelectedProvider(provider, userId)
    }

    fun saveOpenRouterModelId(value: String) {
        storage.saveOpenRouterModelId(value, userId)
        openRouterModelId = value.trim()
    }

    fun saveSpeechEngine(engine: SpeechEngine) {
        speechEngine = engine
        speechStorage.setEngine(engine.name)
    }

    fun saveAssistantLanguage(value: AssistantLanguage) {
        assistantLanguage = value
        assistantStorage.setLanguage(value)
    }

    fun saveProfileField(field: EditField, value: String) {
        when (field) {
            EditField.USERNAME -> {
                username = value
                accountStorage.setUserName(value)
            }
            EditField.DISPLAY_NAME -> {
                displayName = value
                accountStorage.setDisplayName(value)
            }
            EditField.PHONE -> {
                phone = value
                accountStorage.setPhoneNumber(value)
            }
            else -> Unit
        }
    }

    fun saveAssistantName(value: String) {
        assistantName = value
        assistantStorage.setName(value)
    }

    fun saveGender(value: Gender) {
        gender = value
        accountStorage.setGender(value)
    }

    fun saveRelationship(value: RelationType) {
        relationship = value
        assistantStorage.setRelationship(value)
    }

    fun refreshCloudBackedSettings() {
        assistantLanguage = assistantStorage.getLanguage()
        speechEngine = runCatching { SpeechEngine.valueOf(speechStorage.getEngine()) }
            .getOrDefault(SpeechEngine.SYSTEM)
        username = accountStorage.getUserName()
        displayName = accountStorage.getDisplayName()
        phone = accountStorage.getPhoneNumber()
        gender = accountStorage.getGender()
        assistantName = assistantStorage.getName()
        relationship = assistantStorage.getRelationship()
        theme = userSettingsStorage.getTheme()
        fontSize = userSettingsStorage.getFontSize()
    }
}

/* ------------------------------------------------------------------ */
/*  Entry                                                              */
/* ------------------------------------------------------------------ */

@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onNavigate: (SettingsDestination) -> Unit = {}
) {
    val context = LocalContext.current
    val storage = remember { ApiKeyStorage(context.applicationContext) }
    val authService = remember { SupabaseAuthService() }
    val userId = authService.currentUser()?.id
    val state = remember(storage, userId) { SettingsState(storage, context.applicationContext, userId) }
    val cloudSettingsRepository = remember(context) {
        CloudSettingsRepository(
            local = InMemoryCloudSettingsLocalStore(context.applicationContext),
            remote = SupabaseCloudSettingsRemoteStore(),
            currentUserId = { authService.currentUser()?.id },
        )
    }
    val coroutineScope = rememberCoroutineScope()
    val apiKeySync = remember(context) { SupabaseApiKeyStore() }
    var cloudSyncError by remember { mutableStateOf<String?>(null) }

    var pathIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var editingProvider by remember { mutableStateOf<AssistantProvider?>(null) }
    var editingCricketApiKey by remember { mutableStateOf(false) }
    var editingField by remember { mutableStateOf<EditField?>(null) }

    // Set while the user is in the system accessibility settings granting
    // screen access; consumed on the next ON_RESUME to give feedback and,
    // on Android 13-15, to guide past the "Restricted setting" toggle block.
    var awaitingAccessibilityGrant by remember { mutableStateOf(false) }
    var showAccessibilityHelp by remember { mutableStateOf(false) }

    var refreshPermissionsTrigger by remember { mutableStateOf(0) }
    val permissionStatus = remember(refreshPermissionsTrigger) {
        checkPermissions(context)
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

    // When the user comes back from the accessibility settings without having
    // enabled the service, tell them what to do next: on Android 13-15 an
    // escape hatch for the "Restricted setting" toggle block that applies to
    // sideloaded installs, and on older versions a hint where to find it.
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
            when (val result = cloudSettingsRepository.synchronize()) {
                is CloudSettingsSyncResult.Synced -> state.refreshCloudBackedSettings()
                CloudSettingsSyncResult.SignInRequired -> Unit
                is CloudSettingsSyncResult.Failed ->
                    cloudSyncError = result.message
            }
        }
    }

    /** Saves the key locally and pushes it to the cloud for every device. */
    fun saveProviderCredential(provider: AssistantProvider, key: String) {
        val uid = userId
        if (uid == null) {
            Toast.makeText(context, "Sign in before saving provider keys.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            state.saveApiKey(provider, key)
            editingProvider = null
        } catch (e: Exception) {
            Toast.makeText(
                context,
                e.message ?: "Couldn't save the provider key.",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        coroutineScope.launch {
            val synced = if (key.isBlank()) apiKeySync.delete(provider) else apiKeySync.push(provider, key)
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
            Toast.makeText(
                context,
                e.message ?: "Couldn't save the CricAPI key.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun chooseActiveProvider(provider: AssistantProvider) {
        state.saveProvider(provider, userId)
    }

    LaunchedEffect(Unit) {
        synchronizeCloudSettings()
    }

    // Cloud is the source of truth for provider keys: pull on entry so keys
    // saved on another device (or a fresh install) appear here.
    LaunchedEffect(userId) {
        val uid = userId ?: return@LaunchedEffect
        val cloudKeys = apiKeySync.fetchAll()
        if (cloudKeys.isNotEmpty()) {
            cloudKeys.forEach { (provider, key) -> state.saveApiKey(provider, key) }
        }
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
        if (isGranted) {
            Toast.makeText(context, "Permission granted.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Permission not granted.", Toast.LENGTH_SHORT).show()
        }
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
                if (permissionStatus.hasMic) {
                    Toast.makeText(context, "Microphone permission is already granted.", Toast.LENGTH_SHORT).show()
                } else {
                    requestSingleLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onRequestCalendar = {
                if (permissionStatus.hasCalendar) {
                    Toast.makeText(context, "Calendar permissions are already granted.", Toast.LENGTH_SHORT).show()
                } else {
                    requestMultipleLauncher.launch(
                        arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                    )
                }
            },
            onRequestNotifications = {
                if (permissionStatus.hasNotifications) {
                    Toast.makeText(context, "Notification permission is already granted.", Toast.LENGTH_SHORT).show()
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestSingleLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openNotificationSettings(context)
                }
            },
            onRequestExactAlarm = {
                if (permissionStatus.hasExactAlarm) {
                    Toast.makeText(context, "Exact alarm permission is already granted.", Toast.LENGTH_SHORT).show()
                } else {
                    openExactAlarmSettings(context)
                }
            },
            onRequestBluetooth = {
                if (permissionStatus.hasBluetooth) {
                    Toast.makeText(context, "Bluetooth permission is already granted.", Toast.LENGTH_SHORT).show()
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    requestSingleLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                } else {
                    Toast.makeText(context, "Bluetooth permission is granted by default on this version.", Toast.LENGTH_SHORT).show()
                }
            },
            onRequestMedia = {
                if (permissionStatus.hasAllMedia) {
                    Toast.makeText(context, "Media access is already granted.", Toast.LENGTH_SHORT).show()
                } else {
                    val missing = mediaReadPermissions().filter {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }
                    requestMultipleLauncher.launch(missing.toTypedArray())
                }
            },
            onRequestAllFiles = {
                if (permissionStatus.hasAllFiles) {
                    Toast.makeText(context, "All-files access is already granted.", Toast.LENGTH_SHORT).show()
                } else {
                    openAllFilesAccessSettings(context)
                }
            },
            onOpenAccessibility = {
                if (permissionStatus.hasAccessibility) {
                    Toast.makeText(context, "Screen access is already granted.", Toast.LENGTH_SHORT).show()
                } else {
                    awaitingAccessibilityGrant = true
                    openAccessibilitySettings(context)
                }
            },
            onOpenAppSettings = {
                openAppSettings(context)
            },
            onOpenPermissions = {
                pathIds = listOf("permissions")
            }
        )
    }

    val root = buildTree(
        state = state,
        permissionStatus = permissionStatus,
        permissionActions = permissionActions,
        onNavigate = onNavigate,
        onSaveActiveProvider = ::chooseActiveProvider,
        onEditKey = { editingProvider = it },
        onEditCricketApiKey = { editingCricketApiKey = true },
        onEditField = { editingField = it },
        onSaveGender = { value ->
            state.saveGender(value)
            cloudSettingsRepository.recordChange(CloudSettingsPatch(gender = value))
            synchronizeCloudSettings()
        },
        onSaveAssistantName = { value ->
            state.saveAssistantName(value)
            cloudSettingsRepository.recordChange(CloudSettingsPatch(assistantName = value))
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
        onSaveSpeechEngine = { value ->
            state.saveSpeechEngine(value)
            cloudSettingsRepository.recordChange(CloudSettingsPatch(speechEngine = value))
            synchronizeCloudSettings()
        },
    )
    val current = resolveCategory(root, pathIds)
    val isRoot = pathIds.isEmpty()

    val handleBack: () -> Unit = {
        if (isRoot) onBack() else pathIds = pathIds.dropLast(1)
    }

    Column(
        Modifier.fillMaxSize().background(Night).statusBarsPadding()
    ) {
        TopBar(title = current.title, onBack = handleBack)
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
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
            onSave = { newKey -> saveProviderCredential(provider, newKey.trim()) },
            onRemove = {
                saveProviderCredential(provider, "")
            },
            onDismiss = { editingProvider = null }
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

    editingField?.let { field ->
        val initialValue = when (field) {
            EditField.USERNAME -> state.username
            EditField.DISPLAY_NAME -> state.displayName
            EditField.PHONE -> state.phone
            EditField.OPENROUTER_MODEL -> state.openRouterModelId
        }
        EditFieldDialog(
            field = field,
            initialValue = initialValue,
            onSave = { value ->
                if (field == EditField.OPENROUTER_MODEL) {
                    state.saveOpenRouterModelId(value)
                } else {
                    state.saveProfileField(field, value)
                    val patch = when (field) {
                        EditField.USERNAME -> CloudSettingsPatch(username = value.trim())
                        EditField.DISPLAY_NAME -> CloudSettingsPatch(displayName = value.trim())
                        EditField.PHONE -> CloudSettingsPatch(phone = value.trim())
                        EditField.OPENROUTER_MODEL -> CloudSettingsPatch()
                    }
                    cloudSettingsRepository.recordChange(patch)
                    synchronizeCloudSettings()
                }
                editingField = null
            },
            onDismiss = { editingField = null },
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
                TextButton(
                    onClick = {
                        showAccessibilityHelp = false
                        openAppSettings(context)
                    }
                ) { Text("Open app info") }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityHelp = false }) { Text("Later") }
            }
        )
    }
}

/* ------------------------------------------------------------------ */
/*  Permissions helpers                                               */
/* ------------------------------------------------------------------ */

private data class PermissionStatus(
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
        get() = listOf(hasMic, hasCalendar, hasNotifications, hasExactAlarm, hasBluetooth, hasAllMedia, hasAllFiles, hasAccessibility).count { it }
    val allGranted: Boolean
        get() = grantedCount == totalCount
}

private class PermissionActions(
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

private fun checkPermissions(context: Context): PermissionStatus {
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
    } else {
        true
    }

    val hasBluetooth = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ContextCompat.checkSelfPermission(
        context, Manifest.permission.BLUETOOTH_CONNECT
    ) == PackageManager.PERMISSION_GRANTED

    val mediaPermissions = mediaReadPermissions()
    val fullMediaPermissions = mediaPermissions.filterNot { it == Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED }
    val grantedMediaCount = fullMediaPermissions.count {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    val hasAllMedia = fullMediaPermissions.isNotEmpty() && grantedMediaCount == fullMediaPermissions.size
    val hasSelectedVisualMedia = Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    ) == PackageManager.PERMISSION_GRANTED
    val hasMedia = hasAllMedia || hasSelectedVisualMedia || grantedMediaCount > 0
    val hasAllFiles = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    // Special access: the AI screen-access accessibility service, enabled by the
    // user in the system accessibility settings (re-checked on every ON_RESUME).
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

private fun mediaReadPermissions(): List<String> = when {
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

private fun openAppSettings(context: Context) {
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

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val intent = Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(intent)
        }.recoverCatching {
            openAppSettings(context)
        }
    } else {
        Toast.makeText(context, "Exact alarm permission is granted by default on this version.", Toast.LENGTH_SHORT).show()
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(intent)
    }.recoverCatching {
        openAppSettings(context)
    }
}

private fun openAllFilesAccessSettings(context: Context) {
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

private fun openAccessibilitySettings(context: Context) {
    val intent = Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(intent)
    }.recoverCatching {
        openAppSettings(context)
    }.onFailure {
        Toast.makeText(context, "Unable to open accessibility settings.", Toast.LENGTH_SHORT).show()
    }
}

/* ------------------------------------------------------------------ */
/*  Tree builder                                                       */
/* ------------------------------------------------------------------ */

private fun buildTree(
    state: SettingsState,
    permissionStatus: PermissionStatus,
    permissionActions: PermissionActions,
    onNavigate: (SettingsDestination) -> Unit,
    onSaveActiveProvider: (AssistantProvider) -> Unit,
    onEditKey: (AssistantProvider) -> Unit,
    onEditCricketApiKey: () -> Unit,
    onEditField: (EditField) -> Unit,
    onSaveGender: (Gender) -> Unit,
    onSaveAssistantName: (String) -> Unit,
    onSaveRelationship: (RelationType) -> Unit,
    onSaveLanguage: (AssistantLanguage) -> Unit,
    onSaveSpeechEngine: (SpeechEngine) -> Unit,
): SettingsNode.Category = SettingsNode.Category(
    id = "root",
    title = "Settings",
    icon = Icons.Outlined.Tune,
    children = listOf(

        // "Persona & Roleplay" used to duplicate "Roleplay type" below — removed.
        SettingsNode.Category(
            id = "account",
            title = "Account",
            subtitle = "Profile, plan & sign in",
            icon = Icons.Outlined.Person,
            children = listOf(
                SettingsNode.Item(
                    id = "username",
                    title = "Username",
                    subtitle = state.username.ifBlank { "Set your username" },
                    icon = Icons.Outlined.Person,
                    onClick = { onEditField(EditField.USERNAME) },
                ),
                SettingsNode.Item(
                    id = "display-name",
                    title = "Display name",
                    subtitle = state.displayName.ifBlank { "Set your profile name" },
                    icon = Icons.Outlined.Person,
                    onClick = { onEditField(EditField.DISPLAY_NAME) },
                ),
                SettingsNode.Item(
                    id = "phone",
                    title = "Phone number",
                    subtitle = state.phone.ifBlank { "Not set" },
                    icon = Icons.Outlined.Phone,
                    onClick = { onEditField(EditField.PHONE) },
                ),
                SettingsNode.Item(
                    id = "gender",
                    title = "Gender",
                    subtitle = "Profile information",
                    icon = Icons.Outlined.Person,
                    trailing = { GenderDropdown(state.gender, onSaveGender) },
                ),
                SettingsNode.Item(
                    id = "subscription",
                    title = "Subscription",
                    subtitle = "Free plan",
                    icon = Icons.Outlined.WorkspacePremium,
                    style = RowStyle.PREMIUM,
                    onClick = { onNavigate(SettingsDestination.SUBSCRIPTION) },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TrailingBadge("PRO", Premium)
                            Spacer(Modifier.width(8.dp))
                            TrailingChevron()
                        }
                    }
                ),
            )
        ),

        SettingsNode.Category(
            id = "assistant",
            title = "Assistant",
            subtitle = "Language",
            icon = Icons.Outlined.AutoAwesome,
            children = listOf(
                SettingsNode.Item(
                    id = "assistant-name",
                    title = "Assistant character",
                    subtitle = AssistantCharacter.fromName(state.assistantName).let {
                        "${it.gender} · ${it.voice.geminiVoiceName} voice"
                    },
                    icon = Icons.Outlined.SmartToy,
                    trailing = {
                        AssistantNameDropdown(state.assistantName, onSaveAssistantName)
                    },
                ),
                SettingsNode.Item(
                    id = "assistant-language",
                    title = "Assistant language",
                    subtitle = "Language the assistant replies in",
                    icon = Icons.Outlined.Language,
                    trailing = {
                        LanguageDropdown(state.assistantLanguage, onSaveLanguage)
                    }
                ),
                SettingsNode.Item(
                    id = "assistant-relationship",
                    title = "Relationship",
                    subtitle = "Personalize the assistant's tone",
                    icon = Icons.Outlined.People,
                    trailing = { RelationTypeDropdown(state.relationship, onSaveRelationship) },
                ),
            )
        ),

        SettingsNode.Category(
            id = "engine",
            title = "Engine",
            subtitle = "Speech engine",
            icon = Icons.Outlined.SettingsVoice,
            children = listOf(
                SettingsNode.Item(
                    id = "tts-engine",
                    title = "Speech engine",
                    subtitle = if (state.speechEngine == SpeechEngine.SYSTEM)
                        "Local Device TTS · runs on this device"
                    else "Gemini TTS · uses your saved Gemini API key",
                    icon = Icons.Outlined.GraphicEq,
                    trailing = {
                        SpeechEngineDropdown(state.speechEngine, onSaveSpeechEngine)
                    }
                ),
            )
        ),

        SettingsNode.Category(
            id = "ai",
            title = "AI Providers",
            subtitle = "Connect model providers",
            icon = Icons.Outlined.VpnKey,
            children = listOf(
                SettingsNode.Item(
                    id = "provider",
                    title = "Provider",
                    subtitle = "Choose your default",
                    icon = Icons.Outlined.VpnKey,
                    trailing = {
                        ProviderDropdownTrailing(
                            selected = state.selectedProvider,
                            onSelect = onSaveActiveProvider,
                        )
                    }
                ),
                SettingsNode.Category(
                    id = "credentials",
                    title = "Credentials",
                    subtitle = "API keys per provider",
                    icon = Icons.Outlined.Key,
                    children = buildList {
                        AssistantProvider.entries.forEach { provider ->
                            add(
                                SettingsNode.Item(
                                    id = "key-${provider.name.lowercase()}",
                                    title = "${provider.displayName} API Key",
                                    subtitle = when {
                                        state.keyFor(provider).isBlank() -> "Not configured"
                                        else -> "Configured"
                                    },
                                    icon = Icons.Outlined.Key,
                                    onClick = { onEditKey(provider) },
                                    trailing = {
                                        if (state.keyFor(provider).isBlank()) {
                                            TrailingBadge("SET UP", Aqua)
                                        } else {
                                            TrailingBadge("SAVED", Success)
                                        }
                                    }
                                )
                            )
                        }
                    }
                ),
            )
        ),

        SettingsNode.Category(
            id = "cricket",
            title = "Cricket",
            subtitle = "Live scores and match details",
            icon = Icons.Outlined.Info,
            children = listOf(
                SettingsNode.Item(
                    id = "cricapi-key",
                    title = "CricAPI Key",
                    subtitle = if (state.cricketApiKey.isBlank()) "Not configured" else "Configured",
                    icon = Icons.Outlined.Key,
                    onClick = onEditCricketApiKey,
                    trailing = {
                        TrailingBadge(
                            if (state.cricketApiKey.isBlank()) "SET UP" else "SAVED",
                            if (state.cricketApiKey.isBlank()) Aqua else Success,
                        )
                    },
                ),
            ),
        ),

        SettingsNode.Category(
            id = "appearance",
            title = "Appearance",
            subtitle = "Theme, text & language",
            icon = Icons.Outlined.DarkMode,
            children = listOf(
                SettingsNode.Item(
                    id = "theme", title = "Theme",
                    icon = Icons.Outlined.DarkMode,
                    onClick = { onNavigate(SettingsDestination.APPEARANCE) },
                    trailing = { TrailingValue(state.theme.replaceFirstChar { it.uppercase() }) }
                ),
                SettingsNode.Item(
                    id = "textsize", title = "Text size",
                    icon = Icons.Outlined.FormatSize,
                    onClick = { onNavigate(SettingsDestination.APPEARANCE) },
                    trailing = { TrailingValue(state.fontSize.replaceFirstChar { it.uppercase() }) }
                ),
                SettingsNode.Item(
                    id = "language", title = "Language",
                    icon = Icons.Outlined.Language,
                    onClick = { onNavigate(SettingsDestination.APPEARANCE) },
                    trailing = { TrailingValue("English") }
                )
            )
        ),

        SettingsNode.Category(
            id = "permissions",
            title = "Permissions",
            subtitle = if (permissionStatus.allGranted) "All permissions granted"
            else "${permissionStatus.grantedCount} of ${permissionStatus.totalCount} granted · Tap to manage",
            icon = Icons.Outlined.Security,
            accent = Aqua,
            children = listOfNotNull(
                if (!permissionStatus.allGranted) {
                    SettingsNode.Item(
                        id = "grant-all-permissions",
                        title = "Request all permissions",
                        subtitle = "Ask for all required permissions at once",
                        icon = Icons.Outlined.CheckCircle,
                        onClick = permissionActions.onRequestAll,
                        trailing = {
                            TrailingBadge("REQUEST ALL", Aqua)
                        }
                    )
                } else null,
                SettingsNode.Item(
                    id = "permission-mic",
                    title = "Microphone",
                    subtitle = "Voice conversations & speech recognition",
                    icon = Icons.Outlined.Mic,
                    onClick = permissionActions.onRequestMic,
                    trailing = {
                        TrailingBadge(
                            text = if (permissionStatus.hasMic) "ALLOWED" else "GRANT",
                            accent = if (permissionStatus.hasMic) Success else Aqua
                        )
                    }
                ),
                SettingsNode.Item(
                    id = "permission-calendar",
                    title = "Calendar",
                    subtitle = "Read & create calendar events",
                    icon = Icons.Outlined.CalendarMonth,
                    onClick = permissionActions.onRequestCalendar,
                    trailing = {
                        TrailingBadge(
                            text = if (permissionStatus.hasCalendar) "ALLOWED" else "GRANT",
                            accent = if (permissionStatus.hasCalendar) Success else Aqua
                        )
                    }
                ),
                SettingsNode.Item(
                    id = "permission-notifications",
                    title = "Notifications",
                    subtitle = "Reminders, alerts & assistant updates",
                    icon = Icons.Outlined.Notifications,
                    onClick = permissionActions.onRequestNotifications,
                    trailing = {
                        TrailingBadge(
                            text = if (permissionStatus.hasNotifications) "ALLOWED" else "GRANT",
                            accent = if (permissionStatus.hasNotifications) Success else Aqua
                        )
                    }
                ),
                SettingsNode.Item(
                    id = "permission-exact-alarm",
                    title = "Alarms & Reminders",
                    subtitle = "Schedule alarms and timers accurately on time",
                    icon = Icons.Outlined.Alarm,
                    onClick = permissionActions.onRequestExactAlarm,
                    trailing = {
                        TrailingBadge(
                            text = if (permissionStatus.hasExactAlarm) "ALLOWED" else "GRANT",
                            accent = if (permissionStatus.hasExactAlarm) Success else Aqua
                        )
                    }
                ),
                SettingsNode.Item(
                    id = "permission-bluetooth",
                    title = "Nearby devices (Bluetooth)",
                    subtitle = "Route voice audio to Bluetooth headsets",
                    icon = Icons.Outlined.Bluetooth,
                    onClick = permissionActions.onRequestBluetooth,
                    trailing = {
                        TrailingBadge(
                            text = if (permissionStatus.hasBluetooth) "ALLOWED" else "GRANT",
                            accent = if (permissionStatus.hasBluetooth) Success else Aqua
                        )
                    }
                ),
                SettingsNode.Item(
                    id = "permission-media",
                    title = "Photos, videos & audio",
                    subtitle = when {
                        permissionStatus.hasAllMedia -> "Full access to shared media"
                        permissionStatus.hasMedia -> "Selected media access is available"
                        else -> "Access media files for attachments and browsing"
                    },
                    icon = Icons.Outlined.GraphicEq,
                    onClick = permissionActions.onRequestMedia,
                    trailing = {
                        TrailingBadge(
                            text = when {
                                permissionStatus.hasAllMedia -> "ALLOWED"
                                permissionStatus.hasMedia -> "SELECTED"
                                else -> "GRANT"
                            },
                            accent = if (permissionStatus.hasMedia) Success else Aqua
                        )
                    }
                ),
                SettingsNode.Item(
                    id = "permission-all-files",
                    title = "All files access",
                    subtitle = "Manage shared files and folders on this device",
                    icon = Icons.Outlined.FolderOpen,
                    onClick = permissionActions.onRequestAllFiles,
                    trailing = {
                        TrailingBadge(
                            text = when {
                                Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> "NOT NEEDED"
                                permissionStatus.hasAllFiles -> "ALLOWED"
                                else -> "GRANT"
                            },
                            accent = if (permissionStatus.hasAllFiles) Success else Aqua
                        )
                    }
                ),
                SettingsNode.Item(
                    id = "permission-accessibility",
                    title = "Screen access (Accessibility)",
                    subtitle = "Let the AI see your screen and execute tasks for you",
                    icon = Icons.Outlined.TouchApp,
                    onClick = permissionActions.onOpenAccessibility,
                    trailing = {
                        TrailingBadge(
                            text = if (permissionStatus.hasAccessibility) "ALLOWED" else "GRANT",
                            accent = if (permissionStatus.hasAccessibility) Success else Aqua
                        )
                    }
                ),
                SettingsNode.Item(
                    id = "permission-app-settings",
                    title = "System app settings",
                    subtitle = "Manage all permissions directly in Android Settings",
                    icon = Icons.Outlined.Settings,
                    onClick = permissionActions.onOpenAppSettings,
                    trailing = {
                        TrailingChevron()
                    }
                )
            )
        ),

        SettingsNode.Category(
            id = "data",
            title = "Data & Privacy",
            subtitle = "Your data, your control",
            icon = Icons.Outlined.Lock,
            children = listOf(
                SettingsNode.Item(
                    id = "privacy-permissions",
                    title = "App permissions",
                    subtitle = if (permissionStatus.allGranted) "All permissions granted"
                    else "${permissionStatus.grantedCount} of ${permissionStatus.totalCount} granted",
                    icon = Icons.Outlined.Security,
                    onClick = permissionActions.onOpenPermissions,
                    trailing = {
                        TrailingBadge(
                            text = if (permissionStatus.allGranted) "ALLOWED" else "MANAGE",
                            accent = if (permissionStatus.allGranted) Success else Aqua
                        )
                    }
                ),
            )
        ),

        SettingsNode.Category(
            id = "about",
            title = "About",
            subtitle = "Version & legal",
            icon = Icons.Outlined.Info,
            children = listOf(
                SettingsNode.Item(
                    id = "terms", title = "Terms & Privacy",
                    icon = Icons.Outlined.Description,
                    onClick = { onNavigate(SettingsDestination.ABOUT) }
                )
            )
        )
    )
)

private fun resolveCategory(
    root: SettingsNode.Category,
    pathIds: List<String>
): SettingsNode.Category {
    var current = root
    for (id in pathIds) {
        val next = current.children
            .filterIsInstance<SettingsNode.Category>()
            .firstOrNull { it.id == id } ?: break
        current = next
    }
    return current
}

/* ------------------------------------------------------------------ */
/*  Top bar                                                            */
/* ------------------------------------------------------------------ */

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = TextHigh,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(title, color = TextHigh, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
    }
}

/* ------------------------------------------------------------------ */
/*  Root list — prominent category cards                               */
/* ------------------------------------------------------------------ */

@Composable
private fun SettingsCloudSyncErrorCard(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF171C2C))
            .border(1.dp, CardStroke, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = Color(0xFFFFB36B),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Settings couldn't sync",
                    color = TextHigh,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(3.dp))
                Text(message, color = TextSoft, fontSize = 12.sp)
            }
        }
        TextButton(
            onClick = onRetry,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text("Retry", color = Aqua)
        }
    }
}

@Composable
private fun RootList(nodes: List<SettingsNode>, onOpen: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        nodes.forEach { node ->
            when (node) {
                is SettingsNode.Category -> RootCategoryCard(node) { onOpen(node.id) }
                is SettingsNode.Item -> RootItemCard(node)
            }
        }
    }
}

@Composable
private fun RootCategoryCard(category: SettingsNode.Category, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RowSurface)
            .border(1.dp, CardStroke.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                .background(category.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(category.icon, contentDescription = null, tint = category.accent,
                modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(category.title, color = TextHigh, fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold)
            category.subtitle?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, color = TextSoft, fontSize = 12.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        TrailingChevron()
    }
}

@Composable
private fun RootItemCard(item: SettingsNode.Item) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(RowSurface)
            .border(1.dp, CardStroke.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
    ) {
        ItemRow(item, isLast = true)
    }
}

/* ------------------------------------------------------------------ */
/*  Nested list — uniform card                                         */
/* ------------------------------------------------------------------ */

@Composable
private fun NodeCard(nodes: List<SettingsNode>, onOpenCategory: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(RowSurface)
            .border(1.dp, CardStroke.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
    ) {
        nodes.forEachIndexed { index, node ->
            val isLast = index == nodes.lastIndex
            when (node) {
                is SettingsNode.Category -> CategoryRow(node, isLast) { onOpenCategory(node.id) }
                is SettingsNode.Item -> ItemRow(node, isLast)
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: SettingsNode.Category,
    isLast: Boolean,
    onClick: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                    .background(category.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(category.icon, contentDescription = null, tint = category.accent,
                    modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(category.title, color = TextHigh, fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                category.subtitle?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, color = TextSoft, fontSize = 12.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(8.dp))
            TrailingChevron()
        }
        if (!isLast) Divider(startPadding = 58.dp)
    }
}

@Composable
private fun ItemRow(item: SettingsNode.Item, isLast: Boolean) {
    val titleColor = when (item.style) {
        RowStyle.DANGER -> Danger
        else -> TextHigh
    }.copy(alpha = if (item.enabled) 1f else 0.45f)

    val iconTint = when (item.style) {
        RowStyle.DANGER -> Danger
        RowStyle.PREMIUM -> Premium
        RowStyle.NORMAL -> TextSoft
    }.copy(alpha = if (item.enabled) 1f else 0.45f)

    val click = item.onClick

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .then(
                    if (click != null && item.enabled) Modifier.clickable { click() }
                    else Modifier
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.icon != null) {
                Icon(item.icon, contentDescription = null, tint = iconTint,
                    modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(item.title, color = titleColor, fontSize = 15.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                item.subtitle?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, color = TextSoft.copy(alpha = if (item.enabled) 1f else 0.45f),
                        fontSize = 12.sp, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(12.dp))
            item.trailing()
        }
        if (!isLast) Divider(startPadding = if (item.icon != null) 50.dp else 16.dp)
    }
}

@Composable
private fun Divider(startPadding: Dp) {
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.width(startPadding))
        Box(Modifier.weight(1f).height(1.dp)
            .background(CardStroke.copy(alpha = 0.4f)))
    }
}

/* ------------------------------------------------------------------ */
/*  Trailing components                                                */
/* ------------------------------------------------------------------ */

@Composable
private fun TrailingChevron() {
    Icon(
        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = TextSoft.copy(alpha = 0.6f),
        modifier = Modifier.size(20.dp)
    )
}

@Composable
private fun TrailingValue(text: String) {
    Text(text, color = TextSoft, fontSize = 13.sp)
}

@Composable
private fun TrailingBadge(text: String, accent: Color = Aqua) {
    Box(
        Modifier.clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.16f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(text, color = accent, fontSize = 9.sp, letterSpacing = 0.8.sp,
            fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TrailingSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Night,
            checkedTrackColor = Aqua,
            uncheckedThumbColor = TextSoft,
            uncheckedTrackColor = CardStroke
        )
    )
}

@Composable
private fun ProviderDropdownTrailing(
    selected: AssistantProvider,
    onSelect: (AssistantProvider) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Text(selected.displayName, color = Aqua, fontSize = 13.sp,
                fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null,
                tint = TextSoft, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(RowSurface)
                .border(1.dp, CardStroke.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
        ) {
            AssistantProvider.entries.forEach { provider ->
                val isSelected = provider == selected
                DropdownMenuItem(
                    text = {
                        Row(Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(provider.displayName,
                                color = TextHigh,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold
                                else FontWeight.Normal)
                            Spacer(Modifier.weight(1f))
                            Spacer(Modifier.width(12.dp))
                            when {
                                isSelected -> Icon(Icons.Outlined.Check,
                                    contentDescription = null, tint = Aqua,
                                    modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    onClick = { onSelect(provider); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun SpeechEngineDropdown(
    selected: SpeechEngine,
    onSelect: (SpeechEngine) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Text(selected.label, color = Aqua, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Choose speech engine",
                tint = TextSoft, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(RowSurface)
                .border(1.dp, CardStroke.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
        ) {
            SpeechEngine.entries.forEach { engine ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(engine.label, color = TextHigh, fontSize = 14.sp,
                                    fontWeight = if (engine == selected) FontWeight.SemiBold else FontWeight.Normal)
                                if (engine == selected) {
                                    Spacer(Modifier.width(10.dp))
                                    Icon(Icons.Outlined.Check, contentDescription = null,
                                        tint = Aqua, modifier = Modifier.size(16.dp))
                                }
                            }
                            Text(engine.description, color = TextSoft, fontSize = 11.sp)
                        }
                    },
                    onClick = { onSelect(engine); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun AssistantNameDropdown(
    selected: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Text(selected, color = Aqua, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Choose assistant name",
                tint = TextSoft, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(RowSurface)
                .border(1.dp, CardStroke.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
        ) {
            AssistantCharacter.entries.forEach { character ->
                val name = character.displayName
                val isSelected = name == selected
                DropdownMenuItem(
                    text = {
                        Column {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    name,
                                    color = TextHigh,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold
                                    else FontWeight.Normal,
                                )
                                Spacer(Modifier.weight(1f))
                                if (isSelected) {
                                    Icon(Icons.Outlined.Check, contentDescription = null,
                                        tint = Aqua, modifier = Modifier.size(16.dp))
                                }
                            }
                            Text(
                                "${character.gender} · ${character.voice.geminiVoiceName} voice",
                                color = TextSoft,
                                fontSize = 11.sp,
                            )
                        }
                    },
                    onClick = { onSelect(name); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun GenderDropdown(
    selected: Gender,
    onSelect: (Gender) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Text(selected.label, color = Aqua, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Choose gender",
                tint = TextSoft, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(RowSurface)
                .border(1.dp, CardStroke.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
        ) {
            Gender.entries.forEach { gender ->
                DropdownMenuItem(
                    text = { Text(gender.label, color = TextHigh, fontSize = 14.sp) },
                    onClick = { onSelect(gender); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun RelationTypeDropdown(
    selected: RelationType,
    onSelect: (RelationType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Text(selected.title, color = selected.accent(), fontSize = 13.sp,
                fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Choose relationship",
                tint = TextSoft, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(RowSurface)
                .border(1.dp, CardStroke.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
        ) {
            RelationType.entries.forEach { relationship ->
                DropdownMenuItem(
                    text = { Text(relationship.title, color = TextHigh, fontSize = 14.sp) },
                    onClick = { onSelect(relationship); expanded = false },
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  API key dialog                                                     */
/* ------------------------------------------------------------------ */

@Composable
private fun ApiKeyDialog(
    title: String,
    placeholder: String,
    description: String = "The key is stored on this device for your signed-in account.",
    initialKey: String,
    onSave: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    var key by remember(title) { mutableStateOf(initialKey) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RowSurface,
        title = {
            Text(title, color = TextHigh,
                fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column {
                Text(
                    description,
                    color = TextSoft, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    placeholder = { Text(placeholder, color = TextSoft.copy(alpha = 0.5f)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(key) }) {
                Text("Save", color = Aqua, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            Row {
                if (initialKey.isNotBlank()) {
                    TextButton(onClick = onRemove) {
                        Text("Remove", color = Color(0xFFFF6B6B))
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel", color = TextSoft) }
            }
        }
    )
}

/* ------------------------------------------------------------------ */
/*  Assistant language dropdown                                        */
/* ------------------------------------------------------------------ */

@Composable
private fun LanguageDropdown(
    selected: AssistantLanguage,
    onSelect: (AssistantLanguage) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Text(selected.label, color = Aqua, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Choose assistant language",
                tint = TextSoft, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(RowSurface)
                .border(1.dp, CardStroke.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
        ) {
            AssistantLanguage.entries.forEach { language ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(language.label, color = TextHigh, fontSize = 14.sp,
                                fontWeight = if (language == selected) FontWeight.SemiBold
                                else FontWeight.Normal)
                            Spacer(Modifier.weight(1f))
                            Spacer(Modifier.width(12.dp))
                            if (language == selected) {
                                Icon(Icons.Outlined.Check, contentDescription = null,
                                    tint = Aqua, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    onClick = { onSelect(language); expanded = false }
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Text edit dialog (username / phone / assistant name)               */
/* ------------------------------------------------------------------ */

@Composable
private fun EditFieldDialog(
    field: EditField,
    initialValue: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember(field) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RowSurface,
        title = {
            Text(field.title, color = TextHigh, fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column {
                Text(field.description, color = TextSoft, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    placeholder = { Text(field.placeholder, color = TextSoft.copy(alpha = 0.5f)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (field.isPhoneNumber) KeyboardType.Phone
                        else KeyboardType.Text
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(value) },
                enabled = !field.requiresNonBlank || value.isNotBlank(),
            ) {
                Text("Save", color = Aqua, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSoft) }
        }
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = TextSoft.copy(alpha = 0.8f),
        fontSize = 11.sp,
        letterSpacing = 1.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RelationTypeSheet(
    selected: RelationType,
    onApply: (RelationType) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf(selected) }
    var confirmMature by remember { mutableStateOf(false) }

    val platonic = RelationType.entries.filter { !it.isRomantic }
    val romantic = RelationType.entries.filter { it.isRomantic }

    fun applyAndClose(type: RelationType) {
        onApply(type)
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetSurface,
        contentColor = TextHigh
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                        .background(Lilac.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.People, contentDescription = null,
                        tint = Lilac, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Relationship", color = TextHigh, fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text("Who she is to you — from stranger to wife", color = TextSoft,
                        fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(16.dp))

            // Options
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                platonic.forEach { type ->
                    RelationOptionCard(type, isSelected = type == pending) { pending = type }
                }
                Spacer(Modifier.height(6.dp))
                SectionLabel("ROMANTIC BOND")
                romantic.forEach { type ->
                    RelationOptionCard(type, isSelected = type == pending) { pending = type }
                }
                Spacer(Modifier.height(4.dp))
            }

            Spacer(Modifier.height(12.dp))

            // Primary action — switching to Wife asks for an explicit 18+ confirmation.
            val changed = pending != selected
            Button(
                onClick = {
                    if (pending.isMature && !selected.isMature) confirmMature = true
                    else applyAndClose(pending)
                },
                enabled = changed,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = pending.accent(),
                    contentColor = Night,
                    disabledContainerColor = CardStroke.copy(alpha = 0.5f),
                    disabledContentColor = TextSoft
                )
            ) {
                Text(
                    if (changed) "Use ${pending.title}" else "Currently ${selected.title}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmMature) {
        AlertDialog(
            onDismissRequest = { confirmMature = false },
            containerColor = RowSurface,
            icon = {
                Icon(Icons.Outlined.LocalFireDepartment, contentDescription = null,
                    tint = Danger, modifier = Modifier.size(26.dp))
            },
            title = {
                Text("Enable 18+ content?", color = TextHigh, fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold)
            },
            text = {
                Text(
                    "“${pending.title}” may include explicit or intense content. " +
                            "Continue only if you are 18 or older. You can switch back at any time.",
                    color = TextSoft, fontSize = 13.sp, lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmMature = false
                    applyAndClose(pending)
                }) {
                    Text("I'm 18 or older", color = Danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmMature = false }) {
                    Text("Cancel", color = TextSoft)
                }
            }
        )
    }
}

@Composable
private fun RelationOptionCard(
    type: RelationType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val accent = type.accent()
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(14.dp)

    val borderColor by animateColorAsState(
        if (isSelected) accent else CardStroke.copy(alpha = 0.6f), label = "border"
    )
    val bgColor by animateColorAsState(
        if (isSelected) accent.copy(alpha = 0.09f) else OptionSurface, label = "bg"
    )

    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bgColor)
            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, shape)
            .selectable(
                selected = isSelected,
                role = Role.RadioButton,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
                .background(accent.copy(alpha = if (isSelected) 0.22f else 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(type.icon(), contentDescription = null, tint = accent,
                modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                type.title,
                color = TextHigh,
                fontSize = 15.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
            )
            Spacer(Modifier.height(3.dp))
            Text(
                type.description,
                color = TextSoft.copy(alpha = 0.9f),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }

        Spacer(Modifier.width(10.dp))
        RadioDot(selected = isSelected, accent = accent)
    }
}

@Composable
private fun RadioDot(selected: Boolean, accent: Color) {
    val ring by animateColorAsState(
        if (selected) accent else TextSoft.copy(alpha = 0.4f), label = "ring"
    )
    Box(
        Modifier.size(22.dp).clip(CircleShape)
            .border(1.5.dp, ring, CircleShape)
            .background(if (selected) accent.copy(alpha = 0.2f) else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = accent,
                modifier = Modifier.size(14.dp))
        }
    }
}
