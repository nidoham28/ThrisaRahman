package com.nidoham.thrisa.core.tools.executor

import android.content.Context
import com.nidoham.thrisa.core.database.alarm.SqliteAlarmDatabase
import com.nidoham.thrisa.core.database.memory.MemoryRepository
import com.nidoham.thrisa.core.database.note.SqliteNoteDatabase
import com.nidoham.thrisa.core.database.reminder.ReminderRepository
import com.nidoham.thrisa.core.database.todo.SqliteTodoDatabase
import com.nidoham.thrisa.core.sync.SupabaseMemoryRepository
import com.nidoham.thrisa.core.sync.SupabaseReminderRepository
import com.nidoham.thrisa.core.tools.ToolRegistry
import com.nidoham.thrisa.core.tools.executor.AlarmToolExecutor.executeCreateAlarm
import com.nidoham.thrisa.core.tools.executor.AlarmToolExecutor.executeDeleteAlarm
import com.nidoham.thrisa.core.tools.executor.AlarmToolExecutor.executeGetAlarm
import com.nidoham.thrisa.core.tools.executor.AlarmToolExecutor.executeListAlarms
import com.nidoham.thrisa.core.tools.executor.AlarmToolExecutor.executeSetTimer
import com.nidoham.thrisa.core.tools.executor.AlarmToolExecutor.executeShowAlarms
import com.nidoham.thrisa.core.tools.executor.AlarmToolExecutor.executeUpdateAlarm
import com.nidoham.thrisa.core.tools.executor.AppToolExecutor.executeCheckAppInstalled
import com.nidoham.thrisa.core.tools.executor.AppToolExecutor.executeListInstalledApps
import com.nidoham.thrisa.core.tools.executor.AppToolExecutor.executeOpenApp
import com.nidoham.thrisa.core.tools.executor.CalendarToolExecutor.executeCreateCalendarEvent
import com.nidoham.thrisa.core.tools.executor.CalendarToolExecutor.executeDeleteCalendarEvent
import com.nidoham.thrisa.core.tools.executor.CalendarToolExecutor.executeGetCalendarEvent
import com.nidoham.thrisa.core.tools.executor.CalendarToolExecutor.executeListCalendarEvents
import com.nidoham.thrisa.core.tools.executor.CalendarToolExecutor.executeUpdateCalendarEvent
import com.nidoham.thrisa.core.tools.executor.CommunicationToolExecutor.executeComposeEmail
import com.nidoham.thrisa.core.tools.executor.CommunicationToolExecutor.executeComposeTextMessage
import com.nidoham.thrisa.core.tools.executor.CommunicationToolExecutor.executeShareText
import com.nidoham.thrisa.core.tools.executor.DeviceToggleToolExecutor.executeBluetoothControl
import com.nidoham.thrisa.core.tools.executor.DeviceToggleToolExecutor.executeFlashlightControl
import com.nidoham.thrisa.core.tools.executor.DeviceToggleToolExecutor.executeWifiControl
import com.nidoham.thrisa.core.tools.executor.DeviceToolExecutor.executeGetDeviceInfo
import com.nidoham.thrisa.core.tools.executor.MemoryToolExecutor.executeInsertMemory
import com.nidoham.thrisa.core.tools.executor.MemoryToolExecutor.executeListMemories
import com.nidoham.thrisa.core.tools.executor.MemoryToolExecutor.executeMergeMemories
import com.nidoham.thrisa.core.tools.executor.MemoryToolExecutor.executeRemoveMemory
import com.nidoham.thrisa.core.tools.executor.MemoryToolExecutor.executeSearchMemories
import com.nidoham.thrisa.core.tools.executor.MemoryToolExecutor.executeUpdateMemory
import com.nidoham.thrisa.core.tools.executor.MediaToolExecutor.executeGetMediaStatus
import com.nidoham.thrisa.core.tools.executor.MediaToolExecutor.executeMediaPlaybackControl
import com.nidoham.thrisa.core.tools.executor.MediaToolExecutor.executeMediaVolumeControl
import com.nidoham.thrisa.core.tools.executor.ImageToolExecutor.executeGenerateImage
import com.nidoham.thrisa.core.tools.executor.NoteToolExecutor.executeCreateNote
import com.nidoham.thrisa.core.tools.executor.NoteToolExecutor.executeDeleteNote
import com.nidoham.thrisa.core.tools.executor.NoteToolExecutor.executeListNotes
import com.nidoham.thrisa.core.tools.executor.NoteToolExecutor.executeSearchNotes
import com.nidoham.thrisa.core.tools.executor.NoteToolExecutor.executeUpdateNote
import com.nidoham.thrisa.core.tools.executor.ReminderToolExecutor.executeCreateReminder
import com.nidoham.thrisa.core.tools.executor.ReminderToolExecutor.executeDeleteReminder
import com.nidoham.thrisa.core.tools.executor.ReminderToolExecutor.executeListReminders
import com.nidoham.thrisa.core.tools.executor.ReminderToolExecutor.executeMarkReminderDone
import com.nidoham.thrisa.core.tools.executor.ReminderToolExecutor.executeSearchReminders
import com.nidoham.thrisa.core.tools.executor.ReminderToolExecutor.executeSnoozeReminder
import com.nidoham.thrisa.core.tools.executor.ReminderToolExecutor.executeUpdateReminder
import com.nidoham.thrisa.core.tools.executor.ScreenToolExecutor.executeCaptureScreen
import com.nidoham.thrisa.core.tools.executor.ScreenToolExecutor.executePerformScreenAction
import com.nidoham.thrisa.core.tools.executor.ScreenToolExecutor.executeReadScreenText
import com.nidoham.thrisa.core.tools.executor.TimeToolExecutor.executeGetCurrentTime
import com.nidoham.thrisa.core.tools.executor.TodoToolExecutor.executeCreateTodo
import com.nidoham.thrisa.core.tools.executor.TodoToolExecutor.executeDeleteTodo
import com.nidoham.thrisa.core.tools.executor.TodoToolExecutor.executeListTodos
import com.nidoham.thrisa.core.tools.executor.TodoToolExecutor.executeMarkTodoDone
import com.nidoham.thrisa.core.tools.executor.TodoToolExecutor.executeMarkTodoUndone
import com.nidoham.thrisa.core.tools.executor.TodoToolExecutor.executeSearchTodos
import com.nidoham.thrisa.core.tools.executor.TodoToolExecutor.executeUpdateTodo
import com.nidoham.thrisa.domain.FunctionCall
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object BuiltinToolExecutor {

    /** Safe argument extraction. */
    fun jsonStringArg(
        call: FunctionCall,
        key: String
    ): String? =
        runCatching {
            call.args[key]?.jsonPrimitive?.content
        }.getOrNull()?.takeIf {
            it.isNotBlank() && it != "null"
        }

    val definitions = ToolRegistry.definitions

    /**
     * Creates a tool executor that has access to an Android [Context],
     * required by device/battery, applications, media playback/volume, device toggles, calendar, alarms, notes, and todo tools.
     *
     * Memory and reminder tools are cloud-only: they read and write the
     * Supabase `user_memories` / `user_schedule` tables as the signed-in user.
     */
    fun executor(context: Context): suspend (FunctionCall) -> JsonObject {
        val memoryRepo: MemoryRepository = SupabaseMemoryRepository.getInstance()
        val reminderRepo: ReminderRepository = SupabaseReminderRepository.getInstance()

        val noteRepo = SqliteNoteDatabase.getInstance(context)
        val todoRepo = SqliteTodoDatabase.getInstance(context)
        val alarmRepo = SqliteAlarmDatabase.getInstance(context)
        val cricketToolExecutor = CricketToolExecutor(context)

        return { call ->
            try {
                when (call.name) {

                    // ── Time & Device ────────────────────────────────────────────
                    "get_current_time" ->
                        executeGetCurrentTime(call)

                    "get_device_info" ->
                        executeGetDeviceInfo(call, context)

                    "terminal_find_tool" ->
                        executeFindTerminalTool(call, context)

                    "terminal_calculate" ->
                        executeTerminalCalculation(call, context)

                    // ── Cricket ────────────────────────────────────────────────
                    "get_live_cricket_scores" ->
                        cricketToolExecutor.getLiveScores()

                    "get_cricket_matches" ->
                        cricketToolExecutor.getMatches(call)

                    "get_cricket_scorecard" ->
                        cricketToolExecutor.getScorecard(call)

                    // ── Applications ─────────────────────────────────────────────
                    "list_installed_apps" ->
                        executeListInstalledApps(call, context)

                    "check_app_installed" ->
                        executeCheckAppInstalled(call, context)

                    "open_app" ->
                        executeOpenApp(call, context)

                    // ── Communication drafts and sharing ──────────────────────
                    "compose_email" ->
                        executeComposeEmail(call, context)

                    "compose_text_message" ->
                        executeComposeTextMessage(call, context)

                    "share_text" ->
                        executeShareText(call, context)

                    "generate_image" ->
                        executeGenerateImage(call, context)

                    // ── Media playback & volume ─────────────────────────────────
                    "media_playback_control" ->
                        executeMediaPlaybackControl(call, context)

                    "media_volume_control" ->
                        executeMediaVolumeControl(call, context)

                    "get_media_status" ->
                        executeGetMediaStatus(context)

                    // ── Device toggles (flashlight, Bluetooth, Wi-Fi) ───────────
                    "control_flashlight" ->
                        executeFlashlightControl(call, context)

                    "control_bluetooth" ->
                        executeBluetoothControl(call, context)

                    "control_wifi" ->
                        executeWifiControl(call, context)

                    // ── Calendar ─────────────────────────────────────────────────
                    "create_calendar_event" ->
                        executeCreateCalendarEvent(call, context)

                    "list_calendar_events" ->
                        executeListCalendarEvents(call, context)

                    "get_calendar_event" ->
                        executeGetCalendarEvent(call, context)

                    "update_calendar_event" ->
                        executeUpdateCalendarEvent(call, context)

                    "delete_calendar_event" ->
                        executeDeleteCalendarEvent(call, context)

                    // ── Alarm & Timer ─────────────────────────────────────────────
                    "create_alarm" ->
                        executeCreateAlarm(call, context, alarmRepo)

                    "list_alarms" ->
                        executeListAlarms(call, alarmRepo)

                    "get_alarm" ->
                        executeGetAlarm(call, alarmRepo)

                    "update_alarm" ->
                        executeUpdateAlarm(call, context, alarmRepo)

                    "delete_alarm" ->
                        executeDeleteAlarm(call, alarmRepo)

                    "set_timer" ->
                        executeSetTimer(call, context)

                    "show_alarms" ->
                        executeShowAlarms(context)

                    // ── Long-term Memory ─────────────────────────────────────────
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

                    // ── Reminders ────────────────────────────────────────────────
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

                    // ── Notes ────────────────────────────────────────────────────
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

                    // ── To-do ────────────────────────────────────────────────────
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

                    // ── Screen access & automation (accessibility) ───────────────
                    "capture_screen" ->
                        executeCaptureScreen(call, context)

                    "read_screen_text" ->
                        executeReadScreenText(call, context)

                    "perform_screen_action" ->
                        executePerformScreenAction(call, context)

                    else -> buildJsonObject {
                        put(
                            "error",
                            "Unknown tool '${call.name}'. Available tools: " +
                                    definitions.joinToString { it.name } + "."
                        )
                    }
                }
            } catch (c: CancellationException) {
                // Never swallow coroutine cancellation — it must propagate so the
                // caller's Job can finish promptly.
                throw c
            } catch (t: Throwable) {
                buildJsonObject {
                    put(
                        "error",
                        "Tool '${call.name}' failed: " +
                                (t.message ?: t::class.simpleName.orEmpty())
                    )
                }
            }
        }
    }
}
