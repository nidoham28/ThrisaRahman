package com.nidoham.thrisa.core.database.memory

import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for persistent AI memories.
 *
 * Implements a clean, decoupled architecture so the local SQLite engine
 * can seamlessly replace or sync with remote backends like Supabase in the future.
 */
interface MemoryRepository {

    /**
     * Inserts a new memory item into the local database.
     */
    suspend fun insert(
        content: String,
        category: String = MemoryCategory.GENERAL.id,
        importance: Int = 3,
        tags: List<String> = emptyList(),
        source: String = "ai_tool",
    ): MemoryItem

    /**
     * Updates an existing memory item by ID.
     * Returns the updated item, or null if not found.
     */
    suspend fun update(
        id: Long,
        content: String,
        category: String? = null,
        importance: Int? = null,
        tags: List<String>? = null,
    ): MemoryItem?

    /**
     * Removes/forgets a memory item by ID.
     * Returns true if deleted, false if not found.
     */
    suspend fun remove(id: Long): Boolean

    /**
     * Atomically merges multiple existing memories into a single consolidated memory.
     * The old memories are removed and the new unified memory is stored in a single transaction.
     */
    suspend fun merge(
        ids: List<Long>,
        mergedContent: String,
        category: String? = null,
        importance: Int? = null,
        tags: List<String>? = null,
    ): MemoryItem

    /**
     * Searches memories by query text (content or tags) and optional category.
     */
    suspend fun search(
        query: String? = null,
        category: String? = null,
        limit: Int = 20,
    ): List<MemoryItem>

    /**
     * Fetches a memory item by its local primary key [id].
     */
    suspend fun getById(id: Long): MemoryItem?

    /**
     * Fetches a memory item by its globally unique [uuid].
     */
    suspend fun getByUuid(uuid: String): MemoryItem?

    /**
     * Retrieves all memories, optionally filtered by category.
     */
    suspend fun getAll(
        category: String? = null,
        limit: Int = 100,
    ): List<MemoryItem>

    /**
     * Total number of stored memories.
     */
    suspend fun count(): Long

    /**
     * Clears all memories from the local database.
     */
    suspend fun clearAll(): Int

    /**
     * Real-time observable StateFlow of all memories for Compose UI reactivity.
     */
    fun observeAll(): StateFlow<List<MemoryItem>>

    /**
     * Fetches items pending sync (for future Supabase sync / migration).
     */
    suspend fun getPendingSync(): List<MemoryItem>

    /**
     * Marks a local item as synced with remote backend.
     */
    suspend fun markSynced(id: Long, remoteId: String)
}
