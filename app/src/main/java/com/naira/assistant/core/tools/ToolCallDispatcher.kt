package com.naira.assistant.core.tools

import android.content.Context
import com.naira.assistant.ai.AssistantToolResult
import com.naira.assistant.ai.DELEGATE_TOOL_NAME
import com.naira.assistant.core.database.alarm.SqliteAlarmDatabase
import com.naira.assistant.core.database.memory.MemoryRepository
import com.naira.assistant.core.database.note.SqliteNoteDatabase
import com.naira.assistant.core.database.reminder.ReminderRepository
import com.naira.assistant.core.database.todo.SqliteTodoDatabase
import com.naira.assistant.core.service.FirebaseAuthService
import com.naira.assistant.core.sync.FirebaseMemoryRepository
import com.naira.assistant.core.sync.FirebaseReminderRepository
import com.naira.assistant.core.tools.executor.AlarmToolExecutor.executeCreateAlarm
import com.naira.assistant.core.tools.executor.AlarmToolExecutor.executeDeleteAlarm
import com.naira.assistant.core.tools.executor.AlarmToolExecutor.executeGetAlarm
import com.naira.assistant.core.tools.executor.AlarmToolExecutor.executeListAlarms
import com.naira.assistant.core.tools.executor.AlarmToolExecutor.executeSetTimer
import com.naira.assistant.core.tools.executor.AlarmToolExecutor.executeShowAlarms
import com.naira.assistant.core.tools.executor.AlarmToolExecutor.executeUpdateAlarm
import com.naira.assistant.core.tools.executor.AppToolExecutor.executeCheckAppInstalled
import com.naira.assistant.core.tools.executor.AppToolExecutor.executeListInstalledApps
import com.naira.assistant.core.tools.executor.AppToolExecutor.executeOpenApp
import com.naira.assistant.core.tools.executor.CalendarToolExecutor.executeCreateCalendarEvent
import com.naira.assistant.core.tools.executor.CalendarToolExecutor.executeDeleteCalendarEvent
import com.naira.assistant.core.tools.executor.CalendarToolExecutor.executeGetCalendarEvent
import com.naira.assistant.core.tools.executor.CalendarToolExecutor.executeListCalendarEvents
import com.naira.assistant.core.tools.executor.CalendarToolExecutor.executeUpdateCalendarEvent
import com.naira.assistant.core.tools.executor.CommunicationToolExecutor.executeComposeEmail
import com.naira.assistant.core.tools.executor.CommunicationToolExecutor.executeFindEmailContact
import com.naira.assistant.core.tools.executor.CommunicationToolExecutor.executeComposeTextMessage
import com.naira.assistant.core.tools.executor.CommunicationToolExecutor.executeShareText
import com.naira.assistant.core.tools.executor.CricketToolExecutor
import com.naira.assistant.core.tools.executor.DeviceToggleToolExecutor.executeBluetoothControl
import com.naira.assistant.core.tools.executor.DeviceToggleToolExecutor.executeFlashlightControl
import com.naira.assistant.core.tools.executor.DeviceToggleToolExecutor.executeWifiControl
import com.naira.assistant.core.tools.executor.DeviceToolExecutor.executeGetDeviceInfo
import com.naira.assistant.core.tools.executor.executeFindTerminalTool
import com.naira.assistant.core.tools.executor.executeNairaDeveloperInfo
import com.naira.assistant.core.tools.executor.executeTerminalCalculation
import com.naira.assistant.core.tools.executor.RandomPictureResult
import com.naira.assistant.core.tools.executor.RandomToolExecutor
import com.naira.assistant.core.tools.executor.MediaToolExecutor.executeGetMediaStatus
import com.naira.assistant.core.tools.executor.MediaToolExecutor.executeMediaPlaybackControl
import com.naira.assistant.core.tools.executor.MediaToolExecutor.executeMediaVolumeControl
import com.naira.assistant.core.tools.executor.MediaToolExecutor.executePhoneAlertControl
import com.naira.assistant.core.tools.executor.MemoryToolExecutor.executeInsertMemory
import com.naira.assistant.core.tools.executor.MemoryToolExecutor.executeListMemories
import com.naira.assistant.core.tools.executor.MemoryToolExecutor.executeMergeMemories
import com.naira.assistant.core.tools.executor.MemoryToolExecutor.executeRemoveMemory
import com.naira.assistant.core.tools.executor.MemoryToolExecutor.executeSearchMemories
import com.naira.assistant.core.tools.executor.MemoryToolExecutor.executeUpdateMemory
import com.naira.assistant.core.tools.executor.NoteToolExecutor.executeCreateNote
import com.naira.assistant.core.tools.executor.NoteToolExecutor.executeDeleteNote
import com.naira.assistant.core.tools.executor.NoteToolExecutor.executeListNotes
import com.naira.assistant.core.tools.executor.NoteToolExecutor.executeSearchNotes
import com.naira.assistant.core.tools.executor.NoteToolExecutor.executeUpdateNote
import com.naira.assistant.core.tools.executor.ReminderToolExecutor.executeCreateReminder
import com.naira.assistant.core.tools.executor.ReminderToolExecutor.executeDeleteReminder
import com.naira.assistant.core.tools.executor.ReminderToolExecutor.executeListReminders
import com.naira.assistant.core.tools.executor.ReminderToolExecutor.executeMarkReminderDone
import com.naira.assistant.core.tools.executor.ReminderToolExecutor.executeSearchReminders
import com.naira.assistant.core.tools.executor.ReminderToolExecutor.executeSnoozeReminder
import com.naira.assistant.core.tools.executor.ReminderToolExecutor.executeUpdateReminder
import com.naira.assistant.core.tools.executor.ScreenToolExecutor
import com.naira.assistant.core.tools.executor.ScreenToolExecutor.executeCaptureScreen
import com.naira.assistant.core.tools.executor.ScreenToolExecutor.executePerformScreenAction
import com.naira.assistant.core.tools.executor.ScreenToolExecutor.executeReadScreenText
import com.naira.assistant.core.tools.executor.TimeToolExecutor.executeGetCurrentTime
import com.naira.assistant.core.tools.executor.TodoToolExecutor.executeCreateTodo
import com.naira.assistant.core.tools.executor.TodoToolExecutor.executeDeleteTodo
import com.naira.assistant.core.tools.executor.TodoToolExecutor.executeListTodos
import com.naira.assistant.core.tools.executor.TodoToolExecutor.executeMarkTodoDone
import com.naira.assistant.core.tools.executor.TodoToolExecutor.executeMarkTodoUndone
import com.naira.assistant.core.tools.executor.TodoToolExecutor.executeSearchTodos
import com.naira.assistant.core.tools.executor.TodoToolExecutor.executeUpdateTodo
import com.naira.assistant.core.tools.executor.executeWeatherReport
import com.naira.assistant.domain.FunctionCall
import com.naira.assistant.domain.settings.ApiKeyStorage
import com.naira.assistant.domain.settings.EmailContactStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put


/** Applies app-level tool policy and routes a model tool request to its handler. */
class ToolCallDispatcher(
    context: Context,
    private val sensitiveActionsEnabled: Boolean,
    private val executeRegisteredTool: suspend (FunctionCall) -> JsonObject,
    private val executeSpecialists: suspend (FunctionCall) -> AssistantToolResult,
) {
    private val appContext = context.applicationContext

    suspend fun execute(call: FunctionCall): AssistantToolResult {
        if (call.name == DELEGATE_TOOL_NAME) return executeSpecialists(call)

        if (ToolRegistry.find(call.name) == null) {
            return AssistantToolResult(error("Unknown tool '${call.name}'."))
        }

        if (!sensitiveActionsEnabled && call.name in ToolRegistry.settingsPermissionRequiredNames) {
            return AssistantToolResult(
                error("This action is blocked. Enable Sensitive assistant actions in Settings → Permissions to allow it."),
            )
        }

        return when (call.name) {
            "capture_screen" -> executeScreenTool(call) { ScreenToolExecutor.executeCaptureScreen(call, appContext) }
            "perform_screen_action" -> executeScreenTool(call) { ScreenToolExecutor.executePerformScreenAction(call, appContext) }
            "random_picture" -> executeRandomPictureTool(call)
            else -> AssistantToolResult(
                if (call.name in ioBoundToolNames) {
                    withContext(Dispatchers.IO) { executeRegisteredTool(call) }
                } else {
                    executeRegisteredTool(call)
                }
            )
        }
    }

    /**
     * Runs a screen tool inside the same failure contract as registered tools:
     * an executor exception becomes a structured error JSON instead of escaping
     * [execute] uncaught, and the last capture is still released to the caller.
     */
    private suspend fun executeScreenTool(
        call: FunctionCall,
        block: suspend () -> JsonObject,
    ): AssistantToolResult {
        val response = try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            toolFailure(call.name, exception)
        }
        return AssistantToolResult(
            response = response,
            image = ScreenToolExecutor.consumeLastCapture(),
        )
    }

    /**
     * Runs the random-picture tool with the same failure contract as screen
     * tools: exceptions become structured error JSON, and the picked photo is
     * attached to the result for display in the conversation.
     */
    private suspend fun executeRandomPictureTool(
        call: FunctionCall,
    ): AssistantToolResult {
        val outcome = try {
            withContext(Dispatchers.IO) { RandomToolExecutor.executeRandomPicture(call, appContext) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            RandomPictureResult(toolFailure(call.name, exception), null)
        }
        return AssistantToolResult(
            response = outcome.response,
            image = outcome.image,
            imageMimeType = "image/jpeg",
        )
    }

    private companion object {
        val ioBoundToolNames = setOf(
            "list_installed_apps",
            "check_app_installed",
            "open_app",
            "control_flashlight",
            "create_calendar_event",
            "list_calendar_events",
            "get_calendar_event",
            "update_calendar_event",
            "delete_calendar_event",
        )
    }

    private fun error(message: String) = buildJsonObject {
        put("error", message)
    }
}

object CommandExecution {

    /**
     * Safely extracts a string argument from a tool call.
     *
     * Rejects null, numbers, booleans, arrays, and objects.
     */
    internal fun jsonStringArg(
        call: FunctionCall,
        key: String,
    ): String? {
        val primitive = call.args[key] as? JsonPrimitive
            ?: return null

        if (!primitive.isString) return null

        return primitive.content.takeIf {
            it.isNotBlank() && it != "null"
        }
    }

    val definitions
        get() = ToolRegistry.definitions

    /**
     * Creates a tool executor with application-scoped dependencies.
     *
     * Local database repositories are initialized once per executor.
     * Firebase repositories are initialized lazily when required.
     */
    fun executor(
        context: Context,
    ): suspend (FunctionCall) -> JsonObject {

        val appContext = context.applicationContext
        val weatherApiKeyStorage = ApiKeyStorage(appContext)
        val emailContactStorage = EmailContactStorage(appContext)
        val authService = FirebaseAuthService()

        val noteRepo = SqliteNoteDatabase.getInstance(appContext)
        val todoRepo = SqliteTodoDatabase.getInstance(appContext)
        val alarmRepo = SqliteAlarmDatabase.getInstance(appContext)

        val cricketToolExecutor = CricketToolExecutor(appContext)

        val memoryRepo: MemoryRepository by lazy {
            FirebaseMemoryRepository.getInstance()
        }

        val reminderRepo: ReminderRepository by lazy {
            FirebaseReminderRepository.getInstance()
        }

        return { call ->

            try {
                when (call.name) {

                    // ── Time & Device ─────────────────────────────

                    "get_current_time" ->
                        executeGetCurrentTime(call)

                    "get_weather_report" ->
                        executeWeatherReport(
                            call,
                            authService.currentUser()?.uid
                                ?.let(weatherApiKeyStorage::getWeatherApiKey)
                                .orEmpty(),
                        )

                    "get_device_info" ->
                        executeGetDeviceInfo(call, appContext)

                    "random_number" ->
                        RandomToolExecutor.executeRandomNumber(call)

                    "random_boolean" ->
                        RandomToolExecutor.executeRandomBoolean(call)

                    // ── Terminal ──────────────────────────────────

                    "get_naira_info" ->
                        executeNairaDeveloperInfo(call)

                    "terminal_find_tool" ->
                        executeFindTerminalTool(call, appContext)

                    "terminal_calculate" ->
                        executeTerminalCalculation(call, appContext)

                    // ── Cricket ───────────────────────────────────

                    "get_live_cricket_scores" ->
                        cricketToolExecutor.getLiveScores()

                    "get_cricket_matches" ->
                        cricketToolExecutor.getMatches(call)

                    "get_cricket_scorecard" ->
                        cricketToolExecutor.getScorecard(call)

                    // ── Applications ──────────────────────────────

                    "list_installed_apps" ->
                        executeListInstalledApps(call, appContext)

                    "check_app_installed" ->
                        executeCheckAppInstalled(call, appContext)

                    "open_app" ->
                        executeOpenApp(call, appContext)

                    // ── Communication ─────────────────────────────

                    "find_email_contact" ->
                        executeFindEmailContact(
                            call,
                            emailContactStorage,
                            authService.currentUser()?.uid,
                        )

                    "compose_email" ->
                        executeComposeEmail(
                            call,
                            appContext,
                            emailContactStorage.getContacts(authService.currentUser()?.uid),
                        )

                    "compose_text_message" ->
                        executeComposeTextMessage(call, appContext)

                    "share_text" ->
                        executeShareText(call, appContext)

                    // ── Media ──────────────────────────────────────

                    "media_playback_control" ->
                        executeMediaPlaybackControl(call, appContext)

                    "media_volume_control" ->
                        executeMediaVolumeControl(call, appContext)

                    "get_media_status" ->
                        executeGetMediaStatus(appContext)

                    "phone_alert_control" ->
                        executePhoneAlertControl(call, appContext)

                    // ── Device Toggles ─────────────────────────────

                    "control_flashlight" ->
                        executeFlashlightControl(call, appContext)

                    "control_bluetooth" ->
                        executeBluetoothControl(call, appContext)

                    "control_wifi" ->
                        executeWifiControl(call, appContext)

                    // ── Calendar ───────────────────────────────────

                    "create_calendar_event" ->
                        executeCreateCalendarEvent(call, appContext)

                    "list_calendar_events" ->
                        executeListCalendarEvents(call, appContext)

                    "get_calendar_event" ->
                        executeGetCalendarEvent(call, appContext)

                    "update_calendar_event" ->
                        executeUpdateCalendarEvent(call, appContext)

                    "delete_calendar_event" ->
                        executeDeleteCalendarEvent(call, appContext)

                    // ── Alarms & Timers ────────────────────────────

                    "create_alarm" ->
                        executeCreateAlarm(call, appContext, alarmRepo)

                    "list_alarms" ->
                        executeListAlarms(call, alarmRepo)

                    "get_alarm" ->
                        executeGetAlarm(call, alarmRepo)

                    "update_alarm" ->
                        executeUpdateAlarm(call, appContext, alarmRepo)

                    "delete_alarm" ->
                        executeDeleteAlarm(call, alarmRepo)

                    "set_timer" ->
                        executeSetTimer(call, appContext)

                    "show_alarms" ->
                        executeShowAlarms(appContext)

                    // ── Long-term Memory ───────────────────────────

                    "insert_memory" ->
                        executeInsertMemory(call, memoryRepo)

                    "update_memory" ->
                        executeUpdateMemory(call, memoryRepo)

                    "remove_memory" ->
                        executeRemoveMemory(call, memoryRepo)

                    "merge_memories" ->
                        executeMergeMemories(call, memoryRepo)

                    "search_memories" ->
                        executeSearchMemories(call, memoryRepo)

                    "list_memories" ->
                        executeListMemories(call, memoryRepo)

                    // ── Reminders ──────────────────────────────────

                    "create_reminder" ->
                        executeCreateReminder(call, reminderRepo)

                    "update_reminder" ->
                        executeUpdateReminder(call, reminderRepo)

                    "delete_reminder" ->
                        executeDeleteReminder(call, reminderRepo)

                    "list_reminders" ->
                        executeListReminders(call, reminderRepo)

                    "search_reminders" ->
                        executeSearchReminders(call, reminderRepo)

                    "snooze_reminder" ->
                        executeSnoozeReminder(call, reminderRepo)

                    "mark_reminder_done" ->
                        executeMarkReminderDone(call, reminderRepo)

                    // ── Notes ──────────────────────────────────────

                    "create_note" ->
                        executeCreateNote(call, noteRepo)

                    "update_note" ->
                        executeUpdateNote(call, noteRepo)

                    "delete_note" ->
                        executeDeleteNote(call, noteRepo)

                    "list_notes" ->
                        executeListNotes(call, noteRepo)

                    "search_notes" ->
                        executeSearchNotes(call, noteRepo)

                    // ── To-do ──────────────────────────────────────

                    "create_todo" ->
                        executeCreateTodo(call, todoRepo)

                    "update_todo" ->
                        executeUpdateTodo(call, todoRepo)

                    "delete_todo" ->
                        executeDeleteTodo(call, todoRepo)

                    "list_todos" ->
                        executeListTodos(call, todoRepo)

                    "search_todos" ->
                        executeSearchTodos(call, todoRepo)

                    "mark_todo_done" ->
                        executeMarkTodoDone(call, todoRepo)

                    "mark_todo_undone" ->
                        executeMarkTodoUndone(call, todoRepo)

                    // ── Screen Access ──────────────────────────────

                    "capture_screen" ->
                        executeCaptureScreen(call, appContext)

                    "read_screen_text" ->
                        executeReadScreenText(call, appContext)

                    "perform_screen_action" ->
                        executePerformScreenAction(call, appContext)

                    // ── Unknown Tool ───────────────────────────────

                    else -> buildJsonObject {
                        put(
                            "error",
                            "Unknown tool '${call.name}'. Available tools: " +
                                    definitions.joinToString { it.name } + ".",
                        )
                    }
                }

            } catch (timeout: TimeoutCancellationException) {
                // A tool-side withTimeout is an execution failure, not a caller
                // cancellation: report it so the model gets a result. If the
                // enclosing coroutine is genuinely being cancelled, rethrow.
                if (!currentCoroutineContext().isActive) throw timeout
                buildJsonObject {
                    put("success", false)
                    put("error", "Tool '${call.name}' timed out. Try a narrower request.")
                }

            } catch (cancelled: CancellationException) {
                // Preserve structured concurrency.
                throw cancelled

            } catch (exception: Exception) {
                toolFailure(call.name, exception)
            }
        }
    }
}

/** Converts an executor exception into the structured tool-failure JSON returned to the model. */
private fun toolFailure(toolName: String, exception: Exception): JsonObject = buildJsonObject {
    put("success", false)
    put(
        "error",
        "Tool '$toolName' failed: " +
                (
                        exception.message
                            ?.takeIf { it.isNotBlank() }
                            ?: exception::class.simpleName.orEmpty()
                        ),
    )
}
