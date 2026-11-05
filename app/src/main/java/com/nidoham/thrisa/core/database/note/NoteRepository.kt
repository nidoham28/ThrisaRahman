package com.nidoham.thrisa.core.database.note

import kotlinx.coroutines.flow.StateFlow

interface NoteRepository {
    suspend fun insert(
        title: String,
        content: String = "",
        tags: List<String> = emptyList(),
        pinned: Boolean = false,
        color: String = "",
        source: String = "ai_tool",
    ): NoteItem

    suspend fun update(
        id: Long,
        title: String? = null,
        content: String? = null,
        tags: List<String>? = null,
        pinned: Boolean? = null,
        color: String? = null,
    ): NoteItem?

    suspend fun delete(id: Long): Boolean

    suspend fun getById(id: Long): NoteItem?

    suspend fun getAll(limit: Int = 50): List<NoteItem>

    suspend fun search(query: String?, tags: String? = null, limit: Int = 20): List<NoteItem>

    suspend fun count(): Long

    suspend fun clearAll(): Int

    fun observeAll(): StateFlow<List<NoteItem>>
}
