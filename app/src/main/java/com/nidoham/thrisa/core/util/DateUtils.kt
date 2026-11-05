package com.nidoham.thrisa.core.util

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

data class CurrentDateInfo(
    val date: String,       // "2026-11-02"
    val dayName: String,    // "Wed"
    val dayNumber: Int,     // 2 (or use String if you want leading zeros like "02")
    val month: String       // "Nov"
) {
    override fun toString(): String = "$dayName, $month $dayNumber ($date)"
}

/**
 * বর্তমান তারিখের তথ্য দেয় (দিনের নাম ৩ অক্ষর, মাসের নাম ৩ অক্ষর, এবং তারিখের সংখ্যা)।
 */
fun currentDateInfo(
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): CurrentDateInfo {
    val now = Clock.System.now().toLocalDateTime(timeZone)
    return now.date.toDateInfo()
}

/**
 * নির্দিষ্ট তারিখ থেকে তথ্য বানানোর জন্য।
 */
fun LocalDate.toDateInfo(): CurrentDateInfo = CurrentDateInfo(
    date      = toString(),                       // ISO yyyy-MM-dd
    dayName   = dayOfWeek.shortEnglishName(),     // Wed
    dayNumber = day,                              // 2
    month     = month.shortEnglishName()          // Nov
)

// "WEDNESDAY" -> "Wed"
private fun DayOfWeek.shortEnglishName(): String =
    name.lowercase().replaceFirstChar { it.uppercase() }.take(3)

// "NOVEMBER" -> "Nov"
private fun Month.shortEnglishName(): String =
    name.lowercase().replaceFirstChar { it.uppercase() }.take(3)