package com.nidoham.thrisa.core.database.memory

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * High-performance, native SQLite local database for AI memories.
 *
 * Designed with an offline-first architecture, WAL (Write-Ahead Logging) mode,
 * atomic transactions for memory merging, and sync lifecycle tracking so it can
 * seamlessly replace or synchronize with Supabase in the future.
 */
class SqliteMemoryDatabase private constructor(
    context: Context,
    dbName: String = DATABASE_NAME,
) : SQLiteOpenHelper(context.applicationContext, dbName, null, DATABASE_VERSION), MemoryRepository {

    private val dbScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dbMutex = Mutex()
    private val _memoriesFlow = MutableStateFlow<List<MemoryItem>>(emptyList())

    init {
        // Initial load of memories into reactive StateFlow
        dbScope.launch {
            refreshCache()
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_MEMORIES (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_UUID TEXT NOT NULL UNIQUE,
                $COL_CONTENT TEXT NOT NULL,
                $COL_CATEGORY TEXT NOT NULL DEFAULT 'general',
                $COL_IMPORTANCE INTEGER NOT NULL DEFAULT 3,
                $COL_TAGS TEXT NOT NULL DEFAULT '',
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_UPDATED_AT INTEGER NOT NULL,
                $COL_SOURCE TEXT NOT NULL DEFAULT 'ai_tool',
                $COL_SYNC_STATUS TEXT NOT NULL DEFAULT 'LOCAL_ONLY',
                $COL_REMOTE_ID TEXT
            );
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_memories_category ON $TABLE_MEMORIES($COL_CATEGORY);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_memories_updated_at ON $TABLE_MEMORIES($COL_UPDATED_AT DESC);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_memories_importance ON $TABLE_MEMORIES($COL_IMPORTANCE DESC);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_memories_sync ON $TABLE_MEMORIES($COL_SYNC_STATUS);")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future schema migrations
    }

    // ------------------------------------------------------------------
    // MemoryRepository Implementation
    // ------------------------------------------------------------------

    override suspend fun insert(
        content: String,
        category: String,
        importance: Int,
        tags: List<String>,
        source: String,
    ): MemoryItem = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val now = System.currentTimeMillis()
            val uuid = UUID.randomUUID().toString()
            val cleanCategory = category.trim().lowercase().ifBlank { MemoryCategory.GENERAL.id }
            val cleanImportance = importance.coerceIn(1, 5)
            val cleanTags = tags.map { it.trim() }.filter { it.isNotBlank() }

            val values = ContentValues().apply {
                put(COL_UUID, uuid)
                put(COL_CONTENT, content.trim())
                put(COL_CATEGORY, cleanCategory)
                put(COL_IMPORTANCE, cleanImportance)
                put(COL_TAGS, cleanTags.joinToString(","))
                put(COL_CREATED_AT, now)
                put(COL_UPDATED_AT, now)
                put(COL_SOURCE, source)
                put(COL_SYNC_STATUS, SyncStatus.LOCAL_ONLY.name)
            }

            val insertedId = writableDatabase.insertOrThrow(TABLE_MEMORIES, null, values)
            val item = MemoryItem(
                id = insertedId,
                uuid = uuid,
                content = content.trim(),
                category = cleanCategory,
                importance = cleanImportance,
                tags = cleanTags,
                createdAt = now,
                updatedAt = now,
                source = source,
                syncStatus = SyncStatus.LOCAL_ONLY,
            )
            refreshCache()
            item
        }
    }

    override suspend fun update(
        id: Long,
        content: String,
        category: String?,
        importance: Int?,
        tags: List<String>?,
    ): MemoryItem? = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val existing = getByIdInternal(id) ?: return@withLock null
            val now = System.currentTimeMillis()

            val newCategory = category?.trim()?.lowercase()?.ifBlank { existing.category } ?: existing.category
            val newImportance = importance?.coerceIn(1, 5) ?: existing.importance
            val newTags = tags?.map { it.trim() }?.filter { it.isNotBlank() } ?: existing.tags

            val values = ContentValues().apply {
                put(COL_CONTENT, content.trim())
                put(COL_CATEGORY, newCategory)
                put(COL_IMPORTANCE, newImportance)
                put(COL_TAGS, newTags.joinToString(","))
                put(COL_UPDATED_AT, now)
                put(COL_SYNC_STATUS, SyncStatus.PENDING_UPDATE.name)
            }

            val updatedRows = writableDatabase.update(
                TABLE_MEMORIES,
                values,
                "$COL_ID = ?",
                arrayOf(id.toString())
            )

            if (updatedRows > 0) {
                val updatedItem = existing.copy(
                    content = content.trim(),
                    category = newCategory,
                    importance = newImportance,
                    tags = newTags,
                    updatedAt = now,
                    syncStatus = SyncStatus.PENDING_UPDATE,
                )
                refreshCache()
                updatedItem
            } else {
                null
            }
        }
    }

    override suspend fun remove(id: Long): Boolean = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val rows = writableDatabase.delete(
                TABLE_MEMORIES,
                "$COL_ID = ?",
                arrayOf(id.toString())
            )
            if (rows > 0) {
                refreshCache()
                true
            } else {
                false
            }
        }
    }

    override suspend fun merge(
        ids: List<Long>,
        mergedContent: String,
        category: String?,
        importance: Int?,
        tags: List<String>?,
    ): MemoryItem = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            require(ids.isNotEmpty()) { "Cannot merge memories without at least one memory ID." }
            val db = writableDatabase

            db.beginTransaction()
            try {
                // 1. Fetch memories being merged
                val memories = ids.mapNotNull { getByIdInternal(it) }
                require(memories.isNotEmpty()) { "None of the specified memories found to merge: $ids" }

                val now = System.currentTimeMillis()
                val uuid = UUID.randomUUID().toString()

                // Resolve combined category & importance
                val finalCategory = category?.trim()?.lowercase()?.ifBlank { null }
                    ?: memories.firstOrNull { it.category != MemoryCategory.GENERAL.id }?.category
                    ?: MemoryCategory.GENERAL.id

                val finalImportance = importance?.coerceIn(1, 5)
                    ?: memories.maxOfOrNull { it.importance }
                    ?: 3

                val finalTags = tags?.map { it.trim() }?.filter { it.isNotBlank() }
                    ?: memories.flatMap { it.tags }.distinct()

                // 2. Insert new merged memory
                val values = ContentValues().apply {
                    put(COL_UUID, uuid)
                    put(COL_CONTENT, mergedContent.trim())
                    put(COL_CATEGORY, finalCategory)
                    put(COL_IMPORTANCE, finalImportance)
                    put(COL_TAGS, finalTags.joinToString(","))
                    put(COL_CREATED_AT, now)
                    put(COL_UPDATED_AT, now)
                    put(COL_SOURCE, "ai_merge")
                    put(COL_SYNC_STATUS, SyncStatus.LOCAL_ONLY.name)
                }
                val newId = db.insertOrThrow(TABLE_MEMORIES, null, values)

                // 3. Delete old merged memories
                for (oldId in ids) {
                    db.delete(TABLE_MEMORIES, "$COL_ID = ?", arrayOf(oldId.toString()))
                }

                db.setTransactionSuccessful()

                val newItem = MemoryItem(
                    id = newId,
                    uuid = uuid,
                    content = mergedContent.trim(),
                    category = finalCategory,
                    importance = finalImportance,
                    tags = finalTags,
                    createdAt = now,
                    updatedAt = now,
                    source = "ai_merge",
                    syncStatus = SyncStatus.LOCAL_ONLY,
                )
                refreshCache()
                newItem
            } finally {
                db.endTransaction()
            }
        }
    }

    override suspend fun search(
        query: String?,
        category: String?,
        limit: Int,
    ): List<MemoryItem> = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val cleanQuery = query?.trim()?.takeIf { it.isNotBlank() }
            val cleanCat = category?.trim()?.lowercase()?.takeIf { it.isNotBlank() && it != "all" }
            val safeLimit = limit.coerceIn(1, 100)

            val selectionParts = mutableListOf<String>()
            val args = mutableListOf<String>()

            if (cleanQuery != null) {
                selectionParts += "($COL_CONTENT LIKE ? OR $COL_TAGS LIKE ?)"
                val pattern = "%$cleanQuery%"
                args += pattern
                args += pattern
            }

            if (cleanCat != null) {
                selectionParts += "$COL_CATEGORY = ?"
                args += cleanCat
            }

            val selection = if (selectionParts.isNotEmpty()) selectionParts.joinToString(" AND ") else null
            val selectionArgs = if (args.isNotEmpty()) args.toTypedArray() else null

            val cursor = readableDatabase.query(
                TABLE_MEMORIES,
                null,
                selection,
                selectionArgs,
                null,
                null,
                "$COL_IMPORTANCE DESC, $COL_UPDATED_AT DESC",
                safeLimit.toString()
            )

            cursor.use { c ->
                val list = ArrayList<MemoryItem>(c.count)
                while (c.moveToNext()) {
                    list.add(c.toMemoryItem())
                }
                list
            }
        }
    }

    override suspend fun getById(id: Long): MemoryItem? = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            getByIdInternal(id)
        }
    }

    private fun getByIdInternal(id: Long): MemoryItem? {
        val cursor = readableDatabase.query(
            TABLE_MEMORIES,
            null,
            "$COL_ID = ?",
            arrayOf(id.toString()),
            null,
            null,
            null,
            "1"
        )
        return cursor.use { c ->
            if (c.moveToFirst()) c.toMemoryItem() else null
        }
    }

    override suspend fun getByUuid(uuid: String): MemoryItem? = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val cursor = readableDatabase.query(
                TABLE_MEMORIES,
                null,
                "$COL_UUID = ?",
                arrayOf(uuid),
                null,
                null,
                null,
                "1"
            )
            cursor.use { c ->
                if (c.moveToFirst()) c.toMemoryItem() else null
            }
        }
    }

    override suspend fun getAll(
        category: String?,
        limit: Int,
    ): List<MemoryItem> = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val cleanCat = category?.trim()?.lowercase()?.takeIf { it.isNotBlank() && it != "all" }
            val safeLimit = limit.coerceIn(1, 500)

            val (selection, args) = if (cleanCat != null) {
                "$COL_CATEGORY = ?" to arrayOf(cleanCat)
            } else {
                null to null
            }

            val cursor = readableDatabase.query(
                TABLE_MEMORIES,
                null,
                selection,
                args,
                null,
                null,
                "$COL_UPDATED_AT DESC",
                safeLimit.toString()
            )

            cursor.use { c ->
                val list = ArrayList<MemoryItem>(c.count)
                while (c.moveToNext()) {
                    list.add(c.toMemoryItem())
                }
                list
            }
        }
    }

    override suspend fun count(): Long = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val cursor = readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE_MEMORIES", null)
            cursor.use { c ->
                if (c.moveToFirst()) c.getLong(0) else 0L
            }
        }
    }

    override suspend fun clearAll(): Int = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val count = writableDatabase.delete(TABLE_MEMORIES, null, null)
            refreshCache()
            count
        }
    }

    override fun observeAll(): StateFlow<List<MemoryItem>> = _memoriesFlow.asStateFlow()

    override suspend fun getPendingSync(): List<MemoryItem> = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val cursor = readableDatabase.query(
                TABLE_MEMORIES,
                null,
                "$COL_SYNC_STATUS IN (?, ?, ?)",
                arrayOf(
                    SyncStatus.PENDING_INSERT.name,
                    SyncStatus.PENDING_UPDATE.name,
                    SyncStatus.PENDING_DELETE.name
                ),
                null,
                null,
                "$COL_UPDATED_AT ASC"
            )
            cursor.use { c ->
                val list = ArrayList<MemoryItem>(c.count)
                while (c.moveToNext()) {
                    list.add(c.toMemoryItem())
                }
                list
            }
        }
    }

    override suspend fun markSynced(id: Long, remoteId: String) = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val values = ContentValues().apply {
                put(COL_SYNC_STATUS, SyncStatus.SYNCED.name)
                put(COL_REMOTE_ID, remoteId)
            }
            writableDatabase.update(TABLE_MEMORIES, values, "$COL_ID = ?", arrayOf(id.toString()))
            refreshCache()
        }
    }

    private fun refreshCache() {
        val cursor = readableDatabase.query(
            TABLE_MEMORIES,
            null,
            null,
            null,
            null,
            null,
            "$COL_IMPORTANCE DESC, $COL_UPDATED_AT DESC"
        )
        val list = cursor.use { c ->
            val result = ArrayList<MemoryItem>(c.count)
            while (c.moveToNext()) {
                result.add(c.toMemoryItem())
            }
            result
        }
        _memoriesFlow.value = list
    }

    private fun Cursor.toMemoryItem(): MemoryItem {
        val id = getLong(getColumnIndexOrThrow(COL_ID))
        val uuid = getString(getColumnIndexOrThrow(COL_UUID))
        val content = getString(getColumnIndexOrThrow(COL_CONTENT))
        val category = getString(getColumnIndexOrThrow(COL_CATEGORY))
        val importance = getInt(getColumnIndexOrThrow(COL_IMPORTANCE))
        val rawTags = getString(getColumnIndexOrThrow(COL_TAGS)).orEmpty()
        val tags = if (rawTags.isBlank()) emptyList() else rawTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val createdAt = getLong(getColumnIndexOrThrow(COL_CREATED_AT))
        val updatedAt = getLong(getColumnIndexOrThrow(COL_UPDATED_AT))
        val source = getString(getColumnIndexOrThrow(COL_SOURCE))
        val syncStatusStr = getString(getColumnIndexOrThrow(COL_SYNC_STATUS))
        val syncStatus = runCatching { SyncStatus.valueOf(syncStatusStr) }.getOrDefault(SyncStatus.LOCAL_ONLY)
        val remoteId = getString(getColumnIndexOrThrow(COL_REMOTE_ID))

        return MemoryItem(
            id = id,
            uuid = uuid,
            content = content,
            category = category,
            importance = importance,
            tags = tags,
            createdAt = createdAt,
            updatedAt = updatedAt,
            source = source,
            syncStatus = syncStatus,
            remoteId = remoteId,
        )
    }

    companion object {
        const val DATABASE_NAME = "thrisa_memories.db"
        const val DATABASE_VERSION = 1

        const val TABLE_MEMORIES = "memories"
        const val COL_ID = "id"
        const val COL_UUID = "uuid"
        const val COL_CONTENT = "content"
        const val COL_CATEGORY = "category"
        const val COL_IMPORTANCE = "importance"
        const val COL_TAGS = "tags"
        const val COL_CREATED_AT = "created_at"
        const val COL_UPDATED_AT = "updated_at"
        const val COL_SOURCE = "source"
        const val COL_SYNC_STATUS = "sync_status"
        const val COL_REMOTE_ID = "remote_id"

        @Volatile
        private var instance: SqliteMemoryDatabase? = null

        fun getInstance(context: Context): SqliteMemoryDatabase =
            instance ?: synchronized(this) {
                instance ?: SqliteMemoryDatabase(context.applicationContext).also { instance = it }
            }

        /** Creates an in-memory or isolated database instance for unit tests. */
        fun createInMemory(context: Context, name: String? = null): SqliteMemoryDatabase =
            SqliteMemoryDatabase(context, name ?: "test_memories_${System.nanoTime()}.db")
    }
}
