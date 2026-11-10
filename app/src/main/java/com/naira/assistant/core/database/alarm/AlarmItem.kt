package com.naira.assistant.core.database.alarm

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class AlarmItem(
    val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val hour: Int,                // 0 - 23
    val minute: Int,              // 0 - 59
    val label: String = "",
    val days: List<Int> = emptyList(), // Calendar.SUNDAY(1) .. Calendar.SATURDAY(7)
    val enabled: Boolean = true,
    val vibrate: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val source: String = "ai_tool",
)
