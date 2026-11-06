package com.nidoham.thrisa.core.sync

import com.nidoham.thrisa.core.database.memory.MemoryItem
import com.nidoham.thrisa.core.database.memory.MemoryRepository
import com.nidoham.thrisa.core.service.Supabase
import com.nidoham.thrisa.core.service.SupabaseAuthService
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Row of the Supabase `user_memories` table (see
 * `supabase/migrations/…_user_memories.sql`). Timestamps travel as ISO-8601
 * strings because the column type is `timestamptz`.
 */
@Serializable
@JsonIgnoreUnknownKeys
internal data class MemoryRow(
    val id: Long = 0,
    val uid: String? = null,
    val uuid: String = "",
    val content: String = "",
    val category: String = "general",
    val importance: Int = 3,
    val tags: List<String> = emptyList(),
    val source: String = "ai_tool",
    @SerialName("create_at") val createAt: String? = null,
    @SerialName("update_at") val updateAt: String? = null,
) {
    fun toItem(nowMs: Long = System.currentTimeMillis()): MemoryItem = MemoryItem(
        id = id,
        uuid = uuid,
        content = content,
        category = category,
        importance = importance.coerceIn(1, 5),
        tags = tags,
        createdAt = createAt.toEpochMsOrDefault(nowMs),
        updatedAt = updateAt.toEpochMsOrDefault(nowMs),
        source = source,
    )
}

internal fun String?.toEpochMsOrDefault(defaultMs: Long): Long =
    this?.let {
        try {
            Instant.parse(it).toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    } ?: defaultMs

internal fun Long.toIsoTimestamp(): String = Instant.ofEpochMilli(this).toString()

/**
 * Cloud-only [MemoryRepository] backed by the Supabase `user_memories` table.
 *
 * All reads and writes go straight to PostgREST as the signed-in user (row
 * level security keeps every row owned). An in-memory cache backs
 * [observeAll] so Compose pages stay reactive; it reloads when the signed-in
 * user changes and after every mutation. Mutations require a signed-in user
 * and throw otherwise — the tool layer surfaces that as an error bubble.
 */
class SupabaseMemoryRepository private constructor(
    private val client: SupabaseClient = Supabase.client,
    private val currentUserId: () -> String? = { SupabaseAuthService().currentUser()?.id },
) : MemoryRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cacheMutex = Mutex()
    private val _memories = MutableStateFlow<List<MemoryItem>>(emptyList())

    @Volatile
    private var loadedUid: String? = null

    init {
        scope.launch { refresh() }
        // Auth may finish restoring after this singleton is created — reload
        // whenever the session becomes authenticated, clear it on sign-out.
        scope.launch {
            Supabase.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> refresh()
                    is SessionStatus.NotAuthenticated -> clearCache()
                    else -> Unit
                }
            }
        }
    }

    /**
     * Reloads the cache from the cloud. The "loaded" flag is only set on
     * success, so a failed pull is retried by the next read.
     */
    suspend fun refresh(): Boolean {
        val uid = currentUserId()
        if (uid == null) {
            clearCache()
            return true
        }
        return try {
            val rows = client.from("user_memories")
                .select {
                    filter { eq("uid", uid) }
                    order("update_at", Order.DESCENDING)
                    limit(CACHE_LIMIT.toLong())
                }
                .decodeList<MemoryRow>()
                .map { it.toItem() }
            cacheMutex.withLock {
                loadedUid = uid
                _memories.value = rows
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun clearCache() {
        cacheMutex.withLock {
            loadedUid = null
            _memories.value = emptyList()
        }
    }

    override fun observeAll(): StateFlow<List<MemoryItem>> = _memories.asStateFlow()

    override suspend fun insert(
        content: String,
        category: String,
        importance: Int,
        tags: List<String>,
        source: String,
    ): MemoryItem {
        val uid = requireSignedIn()
        val cleanCategory = category.trim().lowercase().ifBlank { "general" }
        val cleanTags = tags.map { it.trim() }.filter { it.isNotBlank() }
        val body = buildJsonObject {
            put("uid", uid)
            put("uuid", newUuid())
            put("content", content.trim())
            put("category", cleanCategory)
            put("importance", importance.coerceIn(1, 5))
            put("tags", buildJsonArray { cleanTags.forEach { add(it) } })
            put("source", source)
        }
        val row = client.from("user_memories")
            .upsert(body) { select() }
            .decodeSingle<MemoryRow>()
        refresh()
        return row.toItem()
    }

    override suspend fun update(
        id: Long,
        content: String,
        category: String?,
        importance: Int?,
        tags: List<String>?,
    ): MemoryItem? {
        requireSignedIn()
        val existing = fetchById(id) ?: return null
        val patch = buildJsonObject {
            put("content", content.trim())
            put(
                "category",
                category?.trim()?.lowercase()?.ifBlank { existing.category } ?: existing.category,
            )
            put("importance", (importance ?: existing.importance).coerceIn(1, 5))
            if (tags != null) {
                put(
                    "tags",
                    buildJsonArray { tags.map { it.trim() }.filter { it.isNotBlank() }.forEach { add(it) } },
                )
            }
        }
        val row = client.from("user_memories")
            .update(patch) {
                filter { eq("id", id) }
                select()
            }
            .decodeSingleOrNull<MemoryRow>() ?: return null
        refresh()
        return row.toItem()
    }

    override suspend fun remove(id: Long): Boolean {
        requireSignedIn()
        fetchById(id) ?: return false
        client.from("user_memories").delete {
            filter { eq("id", id) }
        }
        refresh()
        return true
    }

    override suspend fun merge(
        ids: List<Long>,
        mergedContent: String,
        category: String?,
        importance: Int?,
        tags: List<String>?,
    ): MemoryItem {
        requireSignedIn()
        require(ids.isNotEmpty()) { "Cannot merge memories without at least one memory ID." }
        val sources = fetchByIds(ids)
        require(sources.isNotEmpty()) { "None of the specified memories found to merge: $ids" }

        val finalCategory = category?.trim()?.lowercase()?.ifBlank { null }
            ?: sources.firstOrNull { it.category != "general" }?.category
            ?: "general"
        val finalImportance = importance?.coerceIn(1, 5)
            ?: sources.maxOf { it.importance }
        val finalTags = tags?.map { it.trim() }?.filter { it.isNotBlank() }
            ?: sources.flatMap { it.tags }.distinct()

        val merged = insert(
            content = mergedContent,
            category = finalCategory,
            importance = finalImportance,
            tags = finalTags,
            source = "ai_merge",
        )
        client.from("user_memories").delete {
            filter { isIn("id", ids) }
        }
        refresh()
        return merged
    }

    override suspend fun search(
        query: String?,
        category: String?,
        limit: Int,
    ): List<MemoryItem> {
        val rows = cachedRows()
        val keyword = query?.trim()?.takeIf { it.isNotBlank() }?.lowercase()
        val cleanCat = category?.trim()?.lowercase()?.takeIf { it.isNotBlank() && it != "all" }
        return rows.asSequence()
            .filter { cleanCat == null || it.category == cleanCat }
            .filter { keyword == null || it.content.lowercase().contains(keyword) || it.tags.any { tag -> tag.lowercase().contains(keyword) } }
            .sortedWith(compareByDescending<MemoryItem> { it.importance }.thenByDescending { it.updatedAt })
            .take(limit.coerceIn(1, 100))
            .toList()
    }

    override suspend fun getById(id: Long): MemoryItem? {
        cachedRows().firstOrNull { it.id == id }?.let { return it }
        return fetchById(id)?.toItem()
    }

    override suspend fun getByUuid(uuid: String): MemoryItem? {
        cachedRows().firstOrNull { it.uuid == uuid }?.let { return it }
        val uid = currentUserId() ?: return null
        return client.from("user_memories")
            .select {
                filter {
                    eq("uid", uid)
                    eq("uuid", uuid)
                }
                limit(1)
            }
            .decodeList<MemoryRow>()
            .firstOrNull()
            ?.toItem()
    }

    override suspend fun getAll(
        category: String?,
        limit: Int,
    ): List<MemoryItem> {
        val cleanCat = category?.trim()?.lowercase()?.takeIf { it.isNotBlank() && it != "all" }
        return cachedRows()
            .filter { cleanCat == null || it.category == cleanCat }
            .sortedByDescending { it.updatedAt }
            .take(limit.coerceIn(1, 500))
    }

    override suspend fun count(): Long = cachedRows().size.toLong()

    override suspend fun clearAll(): Int {
        requireSignedIn()
        val previous = cachedRows().size
        val uid = currentUserId()
        if (uid != null) {
            client.from("user_memories").delete {
                filter { eq("uid", uid) }
            }
        }
        refresh()
        return previous
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun requireSignedIn(): String =
        currentUserId() ?: error("Sign in to use your memories.")

    /** Serves reads from the cache, reloading it when needed. */
    private suspend fun cachedRows(): List<MemoryItem> {
        val uid = currentUserId()
        if (uid != null && (uid != loadedUid || _memories.value.isEmpty())) refresh()
        return _memories.value
    }

    private suspend fun fetchById(id: Long): MemoryRow? {
        val uid = currentUserId() ?: return null
        return client.from("user_memories")
            .select {
                filter {
                    eq("uid", uid)
                    eq("id", id)
                }
                limit(1)
            }
            .decodeList<MemoryRow>()
            .firstOrNull()
    }

    private suspend fun fetchByIds(ids: List<Long>): List<MemoryRow> {
        val uid = currentUserId() ?: return emptyList()
        return client.from("user_memories")
            .select {
                filter {
                    eq("uid", uid)
                    isIn("id", ids)
                }
            }
            .decodeList<MemoryRow>()
    }

    private fun newUuid(): String = java.util.UUID.randomUUID().toString()

    companion object {
        /** Cache cap; mirrors the migration's expected per-user scale. */
        const val CACHE_LIMIT = 1000

        @Volatile
        private var instance: SupabaseMemoryRepository? = null

        fun getInstance(): SupabaseMemoryRepository =
            instance ?: synchronized(this) {
                instance ?: SupabaseMemoryRepository().also { instance = it }
            }
    }
}
