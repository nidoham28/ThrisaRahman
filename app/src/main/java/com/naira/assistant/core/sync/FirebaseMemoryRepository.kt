package com.naira.assistant.core.sync

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseException
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.naira.assistant.core.database.memory.MemoryItem
import com.naira.assistant.core.database.memory.MemoryRepository
import com.naira.assistant.core.database.memory.MemoryTagging
import com.naira.assistant.core.sync.model.FirebaseMemoryRecord
import com.naira.assistant.core.sync.model.toFirebaseId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirebaseMemoryRepository private constructor(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(),
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
) : MemoryRepository {
    private val _memories = MutableStateFlow<List<MemoryItem>>(emptyList())
    private var listener: ValueEventListener? = null
    private var listenerRef: DatabaseReference? = null
    @Volatile private var listenerUid: String? = null
    @Volatile private var loadedUid: String? = null

    private val authListener = FirebaseAuth.AuthStateListener { auth ->
        bindToUser(auth.currentUser?.uid)
    }

    init {
        firebaseAuth.addAuthStateListener(authListener)
    }

    override fun observeAll(): StateFlow<List<MemoryItem>> = _memories.asStateFlow()

    suspend fun refresh(): Boolean {
        val uid = firebaseAuth.currentUser?.uid
        bindToUser(uid)
        if (uid == null) return true
        return try {
            val snapshot = memoriesRef(uid).get().await()
            if (listenerUid == uid) {
                _memories.value = snapshot.toMemoryItems()
                loadedUid = uid
            }
            true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.e(TAG, "Couldn't refresh memories from Firebase", error)
            false
        }
    }

    private fun bindToUser(uid: String?) {
        if (uid == null) {
            detachListener()
            listenerUid = null
            loadedUid = null
            _memories.value = emptyList()
            return
        }
        if (listenerUid == uid) return

        detachListener()
        listenerUid = uid
        loadedUid = null
        _memories.value = emptyList()
        val ref = memoriesRef(uid)
        val valueListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (listenerUid == uid) {
                    _memories.value = snapshot.toMemoryItems()
                    loadedUid = uid
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Realtime memory listener failed (${error.code})", error.toException())
            }
        }
        listenerRef = ref
        listener = valueListener
        ref.addValueEventListener(valueListener)
    }

    private fun detachListener() {
        val currentListener = listener
        val currentRef = listenerRef
        if (currentListener != null && currentRef != null) {
            currentRef.removeEventListener(currentListener)
        }
        listener = null
        listenerRef = null
    }

    override suspend fun insert(
        content: String,
        category: String,
        importance: Int,
        tags: List<String>,
        source: String,
    ): MemoryItem {
        val uid = requireUserId()
        val uuid = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val normalizedContent = content.trim()
        val normalizedCategory = category.trim().lowercase().ifBlank { "general" }
        val item = MemoryItem(
            id = uuid.toFirebaseId(),
            uuid = uuid,
            content = normalizedContent,
            category = normalizedCategory,
            importance = importance.coerceIn(1, 5),
            tags = MemoryTagging.enrich(normalizedCategory, normalizedContent, tags),
            createdAt = now,
            updatedAt = now,
            source = source,
        )
        memoriesRef(uid).child(item.uuid).setValue(FirebaseMemoryRecord.fromDomain(item)).await()
        return item
    }

    override suspend fun update(
        id: Long,
        content: String,
        category: String?,
        importance: Int?,
        tags: List<String>?,
    ): MemoryItem? {
        val uid = firebaseAuth.currentUser?.uid ?: return null
        val existing = getById(id) ?: return null
        if (firebaseAuth.currentUser?.uid != uid) return null
        val now = System.currentTimeMillis()
        val normalizedContent = content.trim()
        val normalizedCategory = category?.trim()?.lowercase()?.ifBlank { existing.category } ?: existing.category
        val updated = existing.copy(
            content = normalizedContent,
            category = normalizedCategory,
            importance = importance?.coerceIn(1, 5) ?: existing.importance,
            tags = MemoryTagging.enrich(normalizedCategory, normalizedContent, tags ?: existing.tags),
            updatedAt = now,
        )
        val changes = mapOf(
            "content" to updated.content,
            "category" to updated.category,
            "importance" to updated.importance,
            "tags" to updated.tags,
            "updatedAt" to now,
        )
        memoriesRef(uid).child(existing.uuid).updateChildren(changes).await()
        return updated
    }

    override suspend fun remove(id: Long): Boolean {
        val uid = firebaseAuth.currentUser?.uid ?: return false
        val item = getById(id) ?: return false
        if (firebaseAuth.currentUser?.uid != uid) return false
        memoriesRef(uid).child(item.uuid).removeValue().await()
        return true
    }

    override suspend fun merge(
        ids: List<Long>,
        mergedContent: String,
        category: String?,
        importance: Int?,
        tags: List<String>?,
    ): MemoryItem {
        val uid = requireUserId()
        ensureLoaded()
        val sources = ids.distinct().mapNotNull { getById(it) }
        require(sources.isNotEmpty()) { "None of the specified memories found to merge: $ids" }
        check(firebaseAuth.currentUser?.uid == uid) { "Account changed while merging memories." }

        val uuid = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val normalizedContent = mergedContent.trim()
        val normalizedCategory = category?.trim()?.lowercase()?.ifBlank { null }
            ?: sources.firstOrNull { it.category != "general" }?.category
            ?: "general"
        val merged = MemoryItem(
            id = uuid.toFirebaseId(),
            uuid = uuid,
            content = normalizedContent,
            category = normalizedCategory,
            importance = importance?.coerceIn(1, 5) ?: sources.maxOf { it.importance },
            tags = MemoryTagging.enrich(
                normalizedCategory,
                normalizedContent,
                tags ?: sources.flatMap { it.tags },
            ),
            createdAt = now,
            updatedAt = now,
            source = "ai_merge",
        )
        val updates = mutableMapOf<String, Any?>(
            merged.uuid to FirebaseMemoryRecord.fromDomain(merged),
        )
        sources.forEach { updates[it.uuid] = null }
        memoriesRef(uid).updateChildren(updates).await()
        return merged
    }

    override suspend fun search(
        query: String?,
        category: String?,
        limit: Int,
        tags: List<String>?,
    ): List<MemoryItem> {
        ensureLoaded()
        val keyword = query?.trim()?.lowercase()?.takeIf(String::isNotBlank)
        val cleanCategory = category?.trim()?.lowercase()?.takeIf { it.isNotBlank() && it != "all" }
        val cleanTags = tags.orEmpty().map(MemoryTagging::normalizeTag).filter(String::isNotBlank).distinct()
        val hasSearchTerms = keyword != null || cleanTags.isNotEmpty()
        val candidates = _memories.value
            .filter { cleanCategory == null || it.category == cleanCategory }
        return if (!hasSearchTerms) {
            candidates.take(limit.coerceIn(1, 100))
        } else {
            candidates.mapNotNull { memory ->
                val score = MemoryTagging.relevance(memory, keyword, cleanTags)
                if (score > 0) memory to score else null
            }
                .sortedWith(
                    compareByDescending<Pair<MemoryItem, Int>> { it.second }
                        .thenByDescending { it.first.importance }
                        .thenByDescending { it.first.updatedAt },
                )
                .take(limit.coerceIn(1, 100))
                .map { it.first }
        }
    }

    override suspend fun getById(id: Long): MemoryItem? {
        _memories.value.firstOrNull { it.id == id }?.let { return it }
        val uid = firebaseAuth.currentUser?.uid ?: return null
        return memoriesRef(uid).get().await().toMemoryItems().firstOrNull { it.id == id }
    }

    override suspend fun getByUuid(uuid: String): MemoryItem? {
        _memories.value.firstOrNull { it.uuid == uuid }?.let { return it }
        val uid = firebaseAuth.currentUser?.uid ?: return null
        return memoriesRef(uid).child(uuid).get().await().toMemoryItem()
    }

    override suspend fun getAll(category: String?, limit: Int): List<MemoryItem> {
        ensureLoaded()
        return _memories.value
            .filter { category.isNullOrBlank() || category == "all" || it.category == category.lowercase() }
            .take(limit.coerceIn(1, 500))
    }

    override suspend fun count(): Long {
        ensureLoaded()
        return _memories.value.size.toLong()
    }

    override suspend fun clearAll(): Int {
        val uid = firebaseAuth.currentUser?.uid ?: return 0
        ensureLoaded()
        if (firebaseAuth.currentUser?.uid != uid) return 0
        val previous = _memories.value.size
        memoriesRef(uid).removeValue().await()
        return previous
    }

    private fun requireUserId(): String =
        firebaseAuth.currentUser?.uid ?: error("Sign in to use your memories.")

    private suspend fun ensureLoaded() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        if (loadedUid != uid) refresh()
    }

    private fun memoriesRef(uid: String): DatabaseReference =
        FirebasePaths.memories(database, uid)

    private fun DataSnapshot.toMemoryItems(): List<MemoryItem> =
        children.mapNotNull { it.toMemoryItem() }.sortedByDescending(MemoryItem::updatedAt).take(1000)

    private fun DataSnapshot.toMemoryItem(): MemoryItem? {
        val recordKey = key ?: return null
        return try {
            getValue(FirebaseMemoryRecord::class.java)?.toDomain(recordKey)
        } catch (error: DatabaseException) {
            Log.e(TAG, "Ignoring malformed memory record at $recordKey", error)
            null
        }
    }

    companion object {
        private const val TAG = "FirebaseMemoryRepo"

        @Volatile private var instance: FirebaseMemoryRepository? = null

        fun getInstance(): FirebaseMemoryRepository =
            instance ?: synchronized(this) {
                instance ?: FirebaseMemoryRepository().also { instance = it }
            }
    }
}
