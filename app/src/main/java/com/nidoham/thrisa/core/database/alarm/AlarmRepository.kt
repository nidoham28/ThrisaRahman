package com.nidoham.thrisa.core.database.alarm

import kotlinx.coroutines.flow.StateFlow

interface AlarmRepository {
    suspend fun insert(
        hour: Int,
        minute: Int,
        label: String = "",
        days: List<Int> = emptyList(),
        enabled: Boolean = true,
        vibrate: Boolean = true,
        source: String = "ai_tool",
    ): AlarmItem

    suspend fun update(
        id: Long,
        hour: Int? = null,
        minute: Int? = null,
        label: String? = null,
        days: List<Int>? = null,
        enabled: Boolean? = null,
        vibrate: Boolean? = null,
    ): AlarmItem?

    suspend fun delete(id: Long): Boolean

    suspend fun getById(id: Long): AlarmItem?

    suspend fun getAll(enabledOnly: Boolean? = null, limit: Int = 50): List<AlarmItem>

    suspend fun count(): Long

    suspend fun clearAll(): Int

    fun observeAll(): StateFlow<List<AlarmItem>>
}
