package com.naira.assistant.core.tools

import com.naira.assistant.core.tools.definition.MemoryTools.insertMemoryTool
import com.naira.assistant.core.tools.definition.MemoryTools.listMemoriesTool
import com.naira.assistant.core.tools.definition.MemoryTools.mergeMemoriesTool
import com.naira.assistant.core.tools.definition.MemoryTools.removeMemoryTool
import com.naira.assistant.core.tools.definition.MemoryTools.searchMemoriesTool
import com.naira.assistant.core.tools.definition.MemoryTools.updateMemoryTool
import com.naira.assistant.core.tools.definition.bluetoothControlTool
import com.naira.assistant.core.tools.definition.checkAppInstalledTool
import com.naira.assistant.core.tools.definition.createAlarmTool
import com.naira.assistant.core.tools.definition.cricketMatchesTool
import com.naira.assistant.core.tools.definition.cricketScorecardTool
import com.naira.assistant.core.tools.definition.createCalendarEventTool
import com.naira.assistant.core.tools.definition.createNoteTool
import com.naira.assistant.core.tools.definition.flashlightControlTool
import com.naira.assistant.core.tools.definition.createReminderTool
import com.naira.assistant.core.tools.definition.createTodoTool
import com.naira.assistant.core.tools.definition.composeEmailTool
import com.naira.assistant.core.tools.definition.composeTextMessageTool
import com.naira.assistant.core.tools.definition.deleteAlarmTool
import com.naira.assistant.core.tools.definition.deleteCalendarEventTool
import com.naira.assistant.core.tools.definition.deleteNoteTool
import com.naira.assistant.core.tools.definition.deleteReminderTool
import com.naira.assistant.core.tools.definition.deleteTodoTool
import com.naira.assistant.core.tools.definition.deviceInfoTool
import com.naira.assistant.core.tools.definition.getAlarmTool
import com.naira.assistant.core.tools.definition.getCalendarEventTool
import com.naira.assistant.core.tools.definition.getMediaStatusTool
import com.naira.assistant.core.tools.definition.listAlarmsTool
import com.naira.assistant.core.tools.definition.listCalendarEventsTool
import com.naira.assistant.core.tools.definition.listInstalledAppsTool
import com.naira.assistant.core.tools.definition.listNotesTool
import com.naira.assistant.core.tools.definition.listRemindersTool
import com.naira.assistant.core.tools.definition.listTodosTool
import com.naira.assistant.core.tools.definition.liveCricketScoresTool
import com.naira.assistant.core.tools.definition.markReminderDoneTool
import com.naira.assistant.core.tools.definition.markTodoDoneTool
import com.naira.assistant.core.tools.definition.markTodoUndoneTool
import com.naira.assistant.core.tools.definition.mediaPlaybackControlTool
import com.naira.assistant.core.tools.definition.mediaVolumeControlTool
import com.naira.assistant.core.tools.definition.phoneAlertControlTool
import com.naira.assistant.core.tools.definition.openAppTool
import com.naira.assistant.core.tools.definition.searchNotesTool
import com.naira.assistant.core.tools.definition.searchRemindersTool
import com.naira.assistant.core.tools.definition.searchTodosTool
import com.naira.assistant.core.tools.definition.captureScreenTool
import com.naira.assistant.core.tools.definition.findTerminalTool
import com.naira.assistant.core.tools.definition.readScreenTextTool
import com.naira.assistant.core.tools.definition.performScreenActionTool
import com.naira.assistant.core.tools.definition.setTimerTool
import com.naira.assistant.core.tools.definition.showAlarmsTool
import com.naira.assistant.core.tools.definition.snoozeReminderTool
import com.naira.assistant.core.tools.definition.shareTextTool
import com.naira.assistant.core.tools.definition.terminalCalculationTool
import com.naira.assistant.core.tools.definition.timeTool
import com.naira.assistant.core.tools.definition.updateAlarmTool
import com.naira.assistant.core.tools.definition.updateCalendarEventTool
import com.naira.assistant.core.tools.definition.updateNoteTool
import com.naira.assistant.core.tools.definition.wifiControlTool
import com.naira.assistant.core.tools.definition.updateReminderTool
import com.naira.assistant.core.tools.definition.updateTodoTool
import com.naira.assistant.core.tools.definition.nairaDeveloperInfoTool
import com.naira.assistant.core.tools.definition.getWeatherReportTool

/** Central registry of every tool (function declaration) exposed to the model. */
object ToolRegistry {
    val settingsPermissionRequiredNames = setOf(
        /**
         * Memories declarations
         */
        "insert_memory",
        "update_memory",
        "remove_memory",
        "merge_memories",

        /**
         * Notes declarations
         */
        "update_note",
        "delete_note",

        /**
         * Calendar declarations
         */
        "update_calendar_event",
        "delete_calendar_event",

        /**
         * TODO declarations
         */
        "update_todo",
        "delete_todo",

        /**
         * Alarm declarations
         */
        "update_alarm",
        "delete_alarm",

        /**
         * Reminders declarations
         */
        "update_reminder",
        "delete_reminder",

        /**
         * capture_screen declarations
         */
        "capture_screen",
        "read_screen_text",
        "perform_screen_action",

        /**
         * Device declarations
         */
        "get_device_info",
        "control_flashlight",
        "control_bluetooth",
        "control_wifi",


        /**
         * Software declarations
         */
        "list_installed_apps",
        "check_app_installed",
        "open_app",

        /**
         * Multimedia declarations
         */
        "phone_alert_control",
        "media_playback_control",
        "media_volume_control",
        "get_media_status",

        /**
         * Communication declarations
         */
        "compose_email",
        "compose_text_message",
        "share_text",

        /**
         * Cricket declarations
         */
        "get_live_cricket_scores",
        "get_cricket_matches",
        "get_cricket_scorecard",

        /**
         * Terminal declarations
         */
        "terminal_find_tool",
        "terminal_calculate",

    )

    val definitions: List<ToolDefinition> = listOf(

        /**
         * Memories declarations
         */
        insertMemoryTool(),
        updateMemoryTool(),
        removeMemoryTool(),
        mergeMemoriesTool(),
        searchMemoriesTool(),
        listMemoriesTool(),

        /**
         * Notes declarations
         */
        createNoteTool(),
        updateNoteTool(),
        deleteNoteTool(),
        listNotesTool(),
        searchNotesTool(),

        /**
         * Calendar declarations
         */
        createCalendarEventTool(),
        listCalendarEventsTool(),
        getCalendarEventTool(),
        updateCalendarEventTool(),
        deleteCalendarEventTool(),

        /**
         * TODO declarations
         */
        createTodoTool(),
        updateTodoTool(),
        deleteTodoTool(),
        listTodosTool(),
        searchTodosTool(),
        markTodoDoneTool(),
        markTodoUndoneTool(),

        /**
         * Alarm declarations
         */
        createAlarmTool(),
        listAlarmsTool(),
        getAlarmTool(),
        updateAlarmTool(),
        deleteAlarmTool(),
        setTimerTool(),
        showAlarmsTool(),

        /**
         * Reminders declarations
         */
        createReminderTool(),
        updateReminderTool(),
        deleteReminderTool(),
        listRemindersTool(),
        searchRemindersTool(),
        snoozeReminderTool(),
        markReminderDoneTool(),

        /**
         * capture_screen declarations
         */
        captureScreenTool(),
        readScreenTextTool(),
        performScreenActionTool(),

        /**
         * Device declarations
         */
        timeTool(),
        deviceInfoTool(),
        flashlightControlTool(),
        bluetoothControlTool(),
        wifiControlTool(),

        /**
         * Software declarations
         */
        listInstalledAppsTool(),
        checkAppInstalledTool(),
        openAppTool(),

        /**
         * Multimedia declarations
         */
        mediaPlaybackControlTool(),
        mediaVolumeControlTool(),
        getMediaStatusTool(),
        phoneAlertControlTool(),

        /**
         * Communication declarations
         */
        composeEmailTool(),
        composeTextMessageTool(),
        shareTextTool(),

        /**
         * Cricket declarations
         */
        liveCricketScoresTool(),
        cricketMatchesTool(),
        cricketScorecardTool(),

        /**
         * Terminal declarations
         */
        findTerminalTool(),
        terminalCalculationTool(),

        /**
         * Naira Assistant developer information
         */
        nairaDeveloperInfoTool(),
        getWeatherReportTool(),

    ).let { registered ->
        val duplicateNames = registered.groupingBy(ToolDefinition::name)
            .eachCount()
            .filterValues { it > 1 }
            .keys
        require(duplicateNames.isEmpty()) {
            "Tool names must be unique: ${duplicateNames.joinToString()}"
        }
        require(registered.all { it.name.isNotBlank() && it.description.isNotBlank() }) {
            "Every registered tool must have a name and a useful description."
        }
        check(settingsPermissionRequiredNames.all { required ->
            registered.any { it.name == required }
        }) {
            "A settings-permission tool is missing from the registry."
        }
        registered
    }

    private val definitionsByName: Map<String, ToolDefinition> by lazy {
        definitions.associateBy(ToolDefinition::name)
    }

    fun find(name: String): ToolDefinition? = definitionsByName[name]
}
