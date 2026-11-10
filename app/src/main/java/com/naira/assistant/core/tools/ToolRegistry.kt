package com.naira.assistant.core.tools

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
import com.naira.assistant.core.tools.definition.generateImageTool
import com.naira.assistant.core.tools.definition.insertMemoryTool
import com.naira.assistant.core.tools.definition.listAlarmsTool
import com.naira.assistant.core.tools.definition.listCalendarEventsTool
import com.naira.assistant.core.tools.definition.listInstalledAppsTool
import com.naira.assistant.core.tools.definition.listNotesTool
import com.naira.assistant.core.tools.definition.listMemoriesTool
import com.naira.assistant.core.tools.definition.listRemindersTool
import com.naira.assistant.core.tools.definition.listTodosTool
import com.naira.assistant.core.tools.definition.liveCricketScoresTool
import com.naira.assistant.core.tools.definition.markReminderDoneTool
import com.naira.assistant.core.tools.definition.markTodoDoneTool
import com.naira.assistant.core.tools.definition.markTodoUndoneTool
import com.naira.assistant.core.tools.definition.mediaPlaybackControlTool
import com.naira.assistant.core.tools.definition.mediaVolumeControlTool
import com.naira.assistant.core.tools.definition.mergeMemoriesTool
import com.naira.assistant.core.tools.definition.openAppTool
import com.naira.assistant.core.tools.definition.removeMemoryTool
import com.naira.assistant.core.tools.definition.searchMemoriesTool
import com.naira.assistant.core.tools.definition.searchNotesTool
import com.naira.assistant.core.tools.definition.searchRemindersTool
import com.naira.assistant.core.tools.definition.searchTodosTool
import com.naira.assistant.core.tools.definition.captureScreenTool
import com.naira.assistant.core.tools.definition.readScreenTextTool
import com.naira.assistant.core.tools.definition.performScreenActionTool
import com.naira.assistant.core.tools.definition.setTimerTool
import com.naira.assistant.core.tools.definition.showAlarmsTool
import com.naira.assistant.core.tools.definition.snoozeReminderTool
import com.naira.assistant.core.tools.definition.shareTextTool
import com.naira.assistant.core.tools.definition.timeTool
import com.naira.assistant.core.tools.definition.updateAlarmTool
import com.naira.assistant.core.tools.definition.updateCalendarEventTool
import com.naira.assistant.core.tools.definition.updateMemoryTool
import com.naira.assistant.core.tools.definition.updateNoteTool
import com.naira.assistant.core.tools.definition.wifiControlTool
import com.naira.assistant.core.tools.definition.updateReminderTool
import com.naira.assistant.core.tools.definition.updateTodoTool
import com.naira.assistant.core.tools.definition.findTerminalTool
import com.naira.assistant.core.tools.definition.terminalCalculationTool

/** Central registry of every tool (function declaration) exposed to the model. */
object ToolRegistry {

    val definitions: List<ToolDefinition> = listOf(
        // Time & device
        timeTool(),
        deviceInfoTool(),
        findTerminalTool(),
        terminalCalculationTool(),

        // Cricket
        liveCricketScoresTool(),
        cricketMatchesTool(),
        cricketScorecardTool(),

        // Applications
        listInstalledAppsTool(),
        checkAppInstalledTool(),
        openAppTool(),

        // Screen access & automation (accessibility)
        captureScreenTool(),
        readScreenTextTool(),
        performScreenActionTool(),

        // Communication drafts and sharing
        composeEmailTool(),
        composeTextMessageTool(),
        shareTextTool(),
        generateImageTool(),

        // Media playback & volume
        mediaPlaybackControlTool(),
        mediaVolumeControlTool(),
        getMediaStatusTool(),

        // Device toggles (flashlight, Bluetooth, Wi-Fi)
        flashlightControlTool(),
        bluetoothControlTool(),
        wifiControlTool(),

        // Calendar
        createCalendarEventTool(),
        listCalendarEventsTool(),
        getCalendarEventTool(),
        updateCalendarEventTool(),
        deleteCalendarEventTool(),

        // Alarm & Timer
        createAlarmTool(),
        listAlarmsTool(),
        getAlarmTool(),
        updateAlarmTool(),
        deleteAlarmTool(),
        setTimerTool(),
        showAlarmsTool(),

        // Long-term memory
        insertMemoryTool(),
        updateMemoryTool(),
        removeMemoryTool(),
        mergeMemoriesTool(),
        searchMemoriesTool(),
        listMemoriesTool(),

        // Reminders
        createReminderTool(),
        updateReminderTool(),
        deleteReminderTool(),
        listRemindersTool(),
        searchRemindersTool(),
        snoozeReminderTool(),
        markReminderDoneTool(),

        // Notes
        createNoteTool(),
        updateNoteTool(),
        deleteNoteTool(),
        listNotesTool(),
        searchNotesTool(),

        // To-do
        createTodoTool(),
        updateTodoTool(),
        deleteTodoTool(),
        listTodosTool(),
        searchTodosTool(),
        markTodoDoneTool(),
        markTodoUndoneTool(),
    )
}
