package com.nidoham.thrisa.core.tools

import com.nidoham.thrisa.core.tools.definition.bluetoothControlTool
import com.nidoham.thrisa.core.tools.definition.checkAppInstalledTool
import com.nidoham.thrisa.core.tools.definition.createAlarmTool
import com.nidoham.thrisa.core.tools.definition.createCalendarEventTool
import com.nidoham.thrisa.core.tools.definition.createNoteTool
import com.nidoham.thrisa.core.tools.definition.flashlightControlTool
import com.nidoham.thrisa.core.tools.definition.createReminderTool
import com.nidoham.thrisa.core.tools.definition.createTodoTool
import com.nidoham.thrisa.core.tools.definition.composeEmailTool
import com.nidoham.thrisa.core.tools.definition.composeTextMessageTool
import com.nidoham.thrisa.core.tools.definition.deleteAlarmTool
import com.nidoham.thrisa.core.tools.definition.deleteCalendarEventTool
import com.nidoham.thrisa.core.tools.definition.deleteNoteTool
import com.nidoham.thrisa.core.tools.definition.deleteReminderTool
import com.nidoham.thrisa.core.tools.definition.deleteTodoTool
import com.nidoham.thrisa.core.tools.definition.deviceInfoTool
import com.nidoham.thrisa.core.tools.definition.getAlarmTool
import com.nidoham.thrisa.core.tools.definition.getCalendarEventTool
import com.nidoham.thrisa.core.tools.definition.getMediaStatusTool
import com.nidoham.thrisa.core.tools.definition.generateImageTool
import com.nidoham.thrisa.core.tools.definition.insertMemoryTool
import com.nidoham.thrisa.core.tools.definition.listAlarmsTool
import com.nidoham.thrisa.core.tools.definition.listCalendarEventsTool
import com.nidoham.thrisa.core.tools.definition.listInstalledAppsTool
import com.nidoham.thrisa.core.tools.definition.listNotesTool
import com.nidoham.thrisa.core.tools.definition.listMemoriesTool
import com.nidoham.thrisa.core.tools.definition.listRemindersTool
import com.nidoham.thrisa.core.tools.definition.listTodosTool
import com.nidoham.thrisa.core.tools.definition.markReminderDoneTool
import com.nidoham.thrisa.core.tools.definition.markTodoDoneTool
import com.nidoham.thrisa.core.tools.definition.markTodoUndoneTool
import com.nidoham.thrisa.core.tools.definition.mediaPlaybackControlTool
import com.nidoham.thrisa.core.tools.definition.mediaVolumeControlTool
import com.nidoham.thrisa.core.tools.definition.mergeMemoriesTool
import com.nidoham.thrisa.core.tools.definition.openAppTool
import com.nidoham.thrisa.core.tools.definition.removeMemoryTool
import com.nidoham.thrisa.core.tools.definition.searchMemoriesTool
import com.nidoham.thrisa.core.tools.definition.searchNotesTool
import com.nidoham.thrisa.core.tools.definition.searchRemindersTool
import com.nidoham.thrisa.core.tools.definition.searchTodosTool
import com.nidoham.thrisa.core.tools.definition.captureScreenTool
import com.nidoham.thrisa.core.tools.definition.readScreenTextTool
import com.nidoham.thrisa.core.tools.definition.performScreenActionTool
import com.nidoham.thrisa.core.tools.definition.setTimerTool
import com.nidoham.thrisa.core.tools.definition.showAlarmsTool
import com.nidoham.thrisa.core.tools.definition.snoozeReminderTool
import com.nidoham.thrisa.core.tools.definition.shareTextTool
import com.nidoham.thrisa.core.tools.definition.timeTool
import com.nidoham.thrisa.core.tools.definition.updateAlarmTool
import com.nidoham.thrisa.core.tools.definition.updateCalendarEventTool
import com.nidoham.thrisa.core.tools.definition.updateMemoryTool
import com.nidoham.thrisa.core.tools.definition.updateNoteTool
import com.nidoham.thrisa.core.tools.definition.wifiControlTool
import com.nidoham.thrisa.core.tools.definition.updateReminderTool
import com.nidoham.thrisa.core.tools.definition.updateTodoTool
import com.nidoham.thrisa.core.tools.definition.findTerminalTool
import com.nidoham.thrisa.core.tools.definition.terminalCalculationTool

/** Central registry of every tool (function declaration) exposed to the model. */
object ToolRegistry {

    val definitions: List<ToolDefinition> = listOf(
        // Time & device
        timeTool(),
        deviceInfoTool(),
        findTerminalTool(),
        terminalCalculationTool(),

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
