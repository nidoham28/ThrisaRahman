package com.naira.assistant.core.database.todo

import kotlinx.coroutines.flow.StateFlow

interface TodoRepository {
    suspend fun insert(
        title: String,
        notes: String = "",
        dueAtMs: Long = 0,
        priority: TodoPriority = TodoPriority.NORMAL,
        tags: List<String> = emptyList(),
        listName: String = "inbox",
        source: String = "ai_tool",
    ): TodoItem

    suspend fun update(
        id: Long,
        title: String? = null,
        notes: String? = null,
        done: Boolean? = null,
        dueAtMs: Long? = null,
        priority: TodoPriority? = null,
        tags: List<String>? = null,
        listName: String? = null,
    ): TodoItem?

    suspend fun delete(id: Long): Boolean

    suspend fun markDone(id: Long): TodoItem?

    suspend fun markUndone(id: Long): TodoItem?

    suspend fun getById(id: Long): TodoItem?

    suspend fun getAll(
        done: Boolean? = null,
        priority: String? = null,
        listName: String? = null,
        limit: Int = 50,
    ): List<TodoItem>

    suspend fun search(query: String?, listName: String? = null, limit: Int = 20): List<TodoItem>

    suspend fun count(): Long

    suspend fun clearDone(): Int

    suspend fun clearAll(): Int

    fun observeAll(): StateFlow<List<TodoItem>>
}
