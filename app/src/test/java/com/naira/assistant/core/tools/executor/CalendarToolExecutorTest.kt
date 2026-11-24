package com.naira.assistant.core.tools.executor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class CalendarToolExecutorTest {

    @Test
    fun `all day timestamps are normalized to UTC midnight and default to one day`() {
        val utc = TimeZone.getTimeZone("UTC")
        val timestamp = Calendar.getInstance(utc).apply {
            set(2026, Calendar.MAY, 12, 15, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val expectedStart = Calendar.getInstance(utc).apply {
            clear()
            set(2026, Calendar.MAY, 12, 0, 0, 0)
        }.timeInMillis

        val start = CalendarToolExecutor.normalizeAllDayTimestamp(timestamp, utc)

        assertEquals(expectedStart, start)
        assertEquals(
            24L * 60L * 60L * 1000L,
            CalendarToolExecutor.defaultEndTimeMs(start, allDay = true)!! - start,
        )
    }

    @Test
    fun `timed events retain the one hour default and overflow is rejected`() {
        assertEquals(
            60L * 60L * 1000L,
            CalendarToolExecutor.defaultEndTimeMs(1_000L, allDay = false)!! - 1_000L,
        )
        assertNull(CalendarToolExecutor.defaultEndTimeMs(Long.MAX_VALUE, allDay = true))
    }
}
