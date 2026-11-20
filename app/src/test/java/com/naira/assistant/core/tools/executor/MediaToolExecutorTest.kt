package com.naira.assistant.core.tools.executor

import com.naira.assistant.core.tools.ToolRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class MediaToolExecutorTest {

    @Test
    fun phoneAlertDurationUsesDefaultAndRejectsOutOfRangeValues() {
        assertEquals(30, MediaToolExecutor.resolvePhoneAlertDuration(null))
        assertEquals(5, MediaToolExecutor.resolvePhoneAlertDuration(5))
        assertEquals(60, MediaToolExecutor.resolvePhoneAlertDuration(60))
        assertNull(MediaToolExecutor.resolvePhoneAlertDuration(4))
        assertNull(MediaToolExecutor.resolvePhoneAlertDuration(61))
    }

    @Test
    fun phoneAlertSoundDefaultsAndNormalizesSupportedPatterns() {
        assertEquals("beep", MediaToolExecutor.resolvePhoneAlertSound(null))
        assertEquals("ring", MediaToolExecutor.resolvePhoneAlertSound(" Ring "))
        assertEquals("urgent", MediaToolExecutor.resolvePhoneAlertSound("URGENT"))
        assertNull(MediaToolExecutor.resolvePhoneAlertSound("unknown"))
    }

    @Test
    fun phoneAlertControlIsExposedToTheAssistant() {
        assertNotNull(ToolRegistry.definitions.find { it.name == "phone_alert_control" })
    }
}
