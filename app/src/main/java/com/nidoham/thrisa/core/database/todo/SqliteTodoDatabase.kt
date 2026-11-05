package com.nidoham.thrisa.core.database.todo

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

class SqliteTodoDatabase private constructor(
    context: Context,
    dbName: String = DATABASE_NAME,
) : SQLiteOpenHelper(context.applicationContext, dbName, null, DATABASE_VERSION), TodoRepository {

    private val dbScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dbMutex = Mutex()
    private val _todosFlow = MutableStateFlow<List<TodoItem>>(emptyList())

    init {
        dbScope.launch { refreshCache() }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_TODOS (
                $COL_ID           INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_UUID         TEXT    NOT NULL UNIQUE,
                $COL_TITLE        TEXT    NOT NULL,
                $COL_NOTES        TEXT    NOT NULL DEFAULT '',
                $COL_DONE         INTEGER NOT NULL DEFAULT 0,
                $COL_DUE_AT_MS    INTEGER NOT NULL DEFAULT 0,
                $COL_PRIORITY     TEXT    NOT NULL DEFAULT 'normal',
                $COL_TAGS         TEXT    NOT NULL DEFAULT '',
                $COL_LIST_NAME    TEXT    NOT NULL DEFAULT 'inbox',
                $COL_CREATED_AT   INTEGER NOT NULL,
                $COL_UPDATED_AT   INTEGER NOT NULL,
                $COL_COMPLETED_AT INTEGER NOT NULL DEFAULT 0,
                $COL_SOURCE       TEXT    NOT NULL DEFAULT 'ai_tool'
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_todos_done ON $TABLE_TODOS($COL_DONE);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_todos_priority ON $TABLE_TODOS($COL_PRIORITY);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_todos_list_name ON $TABLE_TODOS($COL_LIST_NAME);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_todos_due_at ON $TABLE_TODOS($COL_DUE_AT_MS);")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Schema upgrades
    }

    override suspend fun insert(
        title: String,
        notes: String,
        dueAtMs: Long,
        priority: TodoPriority,
        tags: List<String>,
        listName: String,
        source: String,
    ): TodoItem = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val now = System.currentTimeMillis()
            val uuid = UUID.randomUUID().toString()
            val cleanTags = tags.map { it.trim() }.filter { it.isNotBlank() }
            val cleanList = listName.trim().ifBlank { "inbox" }

            val values = ContentValues().apply {
                put(COL_UUID, uuid)
                put(COL_TITLE, title.trim())
                put(COL_NOTES, notes.trim())
                put(COL_DONE, 0)
                put(COL_DUE_AT_MS, dueAtMs.coerceAtLeast(0L))
                put(COL_PRIORITY, priority.id)
                put(COL_TAGS, cleanTags.joinToString(","))
                put(COL_LIST_NAME, cleanList)
                put(COL_CREATED_AT, now)
                put(COL_UPDATED_AT, now)
                put(COL_COMPLETED_AT, 0L)
                put(COL_SOURCE, source)
            }

            val insertedId = writableDatabase.insertOrThrow(TABLE_TODOS, null, values)
            val item = TodoItem(
                id = insertedId,
                uuid = uuid,
                title = title.trim(),
                notes = notes.trim(),
                done = false,
                dueAtMs = dueAtMs.coerceAtLeast(0L),
                priority = priority,
                tags = cleanTags,
                listName = cleanList,
                createdAt = now,
                updatedAt = now,
                completedAt = 0L,
                source = source,
            )
            refreshCache()
            item
        }
    }

    override suspend fun update(
        id: Long,
        title: String?,
        notes: String?,
        done: Boolean?,
        dueAtMs: Long?,
        priority: TodoPriority?,
        tags: List<String>?,
        listName: String?,
    ): TodoItem? = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val existing = getByIdInternal(id) ?: return@withLock null
            val now = System.currentTimeMillis()

            val newTitle = title?.trim() ?: existing.title
            val newNotes = notes?.trim() ?: existing.notes
            val newDone = done ?: existing.done
            val newDueAt = dueAtMs?.coerceAtLeast(0L) ?: existing.dueAtMs
            val newPriority = priority ?: existing.priority
            val newTags = tags?.map { it.trim() }?.filter { it.isNotBlank() } ?: existing.tags
            val newListName = listName?.trim()?.ifBlank { "inbox" } ?: existing.listName
            val newCompletedAt = if (newDone != existing.done) {
                if (newDone) now else 0L
            } else existing.completedAt

            val values = ContentValues().apply {
                put(COL_TITLE, newTitle)
                put(COL_NOTES, newNotes)
                put(COL_DONE, if (newDone) 1 else 0)
                put(COL_DUE_AT_MS, newDueAt)
                put(COL_PRIORITY, newPriority.id)
                put(COL_TAGS, newTags.joinToString(","))
                put(COL_LIST_NAME, newListName)
                put(COL_UPDATED_AT, now)
                put(COL_COMPLETED_AT, newCompletedAt)
            }

            val rows = writableDatabase.update(TABLE_TODOS, values, "$COL_ID = ?", arrayOf(id.toString()))
            if (rows > 0) {
                val updated = existing.copy(
                    title = newTitle,
                    notes = newNotes,
                    done = newDone,
                    dueAtMs = newDueAt,
                    priority = newPriority,
                    tags = newTags,
                    listName = newListName,
                    updatedAt = now,
                    completedAt = newCompletedAt,
                )
                refreshCache()
                updated
            } else null
        }
    }

    override suspend fun delete(id: Long): Boolean = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val rows = writableDatabase.delete(TABLE_TODOS, "$COL_ID = ?", arrayOf(id.toString()))
            if (rows > 0) {
                refreshCache()
                true
            } else false
        }
    }

    override suspend fun markDone(id: Long): TodoItem? = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val existing = getByIdInternal(id) ?: return@withLock null
            val now = System.currentTimeMillis()
            val values = ContentValues().apply {
                put(COL_DONE, 1)
                put(COL_COMPLETED_AT, now)
                put(COL_UPDATED_AT, now)
            }
            val rows = writableDatabase.update(TABLE_TODOS, values, "$COL_ID = ?", arrayOf(id.toString()))
            if (rows > 0) {
                val updated = existing.copy(done = true, completedAt = now, updatedAt = now)
                refreshCache()
                updated
            } else null
        }
    }

    override suspend fun markUndone(id: Long): TodoItem? = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val existing = getByIdInternal(id) ?: return@withLock null
            val now = System.currentTimeMillis()
            val values = ContentValues().apply {
                put(COL_DONE, 0)
                put(COL_COMPLETED_AT, 0L)
                put(COL_UPDATED_AT, now)
            }
            val rows = writableDatabase.update(TABLE_TODOS, values, "$COL_ID = ?", arrayOf(id.toString()))
            if (rows > 0) {
                val updated = existing.copy(done = false, completedAt = 0L, updatedAt = now)
                refreshCache()
                updated
            } else null
        }
    }

    override suspend fun getById(id: Long): TodoItem? = withContext(Dispatchers.IO) {
        dbMutex.withLock { getByIdInternal(id) }
    }

    override suspend fun getAll(
        done: Boolean?,
        priority: String?,
        listName: String?,
        limit: Int,
    ): List<TodoItem> = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val selParts = mutableListOf<String>()
            val args = mutableListOf<String>()

            if (done != null) {
                selParts += "$COL_DONE = ?"
                args += if (done) "1" else "0"
            }
            if (!priority.isNullOrBlank() && priority != "all") {
                selParts += "$COL_PRIORITY = ?"
                args += priority.trim().lowercase()
            }
            if (!listName.isNullOrBlank() && listName != "all") {
                selParts += "$COL_LIST_NAME = ?"
                args += listName.trim()
            }

            readableDatabase.query(
                TABLE_TODOS,
                null,
                selParts.joinToString(" AND ").ifEmpty { null },
                args.toTypedArray().ifEmpty { null },
                null,
                null,
                "$COL_DONE ASC, $COL_UPDATED_AT DESC",
                limit.coerceIn(1, 200).toString()
            ).use { c ->
                val list = ArrayList<TodoItem>(c.count)
                while (c.moveToNext()) list.add(c.toTodoItem())
                list
            }
        }
    }

    override suspend fun search(
        query: String?,
        listName: String?,
        limit: Int,
    ): List<TodoItem> = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val cleanQuery = query?.trim()?.takeIf { it.isNotBlank() }
            val cleanList = listName?.trim()?.takeIf { it.isNotBlank() && it != "all" }

            val selParts = mutableListOf<String>()
            val args = mutableListOf<String>()

            if (cleanQuery != null) {
                val like = "%$cleanQuery%"
                selParts += "($COL_TITLE LIKE ? OR $COL_NOTES LIKE ? OR $COL_TAGS LIKE ?)"
                args += like; args += like; args += like
            }
            if (cleanList != null) {
                selParts += "$COL_LIST_NAME = ?"
                args += cleanList
            }

            readableDatabase.query(
                TABLE_TODOS,
                null,
                selParts.joinToString(" AND ").ifEmpty { null },
                args.toTypedArray().ifEmpty { null },
                null,
                null,
                "$COL_DONE ASC, $COL_UPDATED_AT DESC",
                limit.coerceIn(1, 100).toString()
            ).use { c ->
                val list = ArrayList<TodoItem>(c.count)
                while (c.moveToNext()) list.add(c.toTodoItem())
                list
            }
        }
    }

    override suspend fun count(): Long = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE_TODOS", null)
                .use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
        }
    }

    override suspend fun clearDone(): Int = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val count = writableDatabase.delete(TABLE_TODOS, "$COL_DONE = 1", null)
            refreshCache()
            count
        }
    }

    override suspend fun clearAll(): Int = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val count = writableDatabase.delete(TABLE_TODOS, null, null)
            refreshCache()
            count
        }
    }

    override fun observeAll(): StateFlow<List<TodoItem>> = _todosFlow.asStateFlow()

    private fun getByIdInternal(id: Long): TodoItem? =
        readableDatabase.query(TABLE_TODOS, null, "$COL_ID = ?", arrayOf(id.toString()), null, null, null, "1")
            .use { c -> if (c.moveToFirst()) c.toTodoItem() else null }

    private fun refreshCache() {
        val list = readableDatabase.query(
            TABLE_TODOS, null, null, null, null, null, "$COL_DONE ASC, $COL_UPDATED_AT DESC"
        ).use { c ->
            val result = ArrayList<TodoItem>(c.count)
            while (c.moveToNext()) result.add(c.toTodoItem())
            result
        }
        _todosFlow.value = list
    }

    private fun Cursor.toTodoItem() = TodoItem(
        id = getLong(getColumnIndexOrThrow(COL_ID)),
        uuid = getString(getColumnIndexOrThrow(COL_UUID)),
        title = getString(getColumnIndexOrThrow(COL_TITLE)),
        notes = getString(getColumnIndexOrThrow(COL_NOTES)).orEmpty(),
        done = getInt(getColumnIndexOrThrow(COL_DONE)) == 1,
        dueAtMs = getLong(getColumnIndexOrThrow(COL_DUE_AT_MS)),
        priority = TodoPriority.fromId(getString(getColumnIndexOrThrow(COL_PRIORITY))),
        tags = getString(getColumnIndexOrThrow(COL_TAGS)).orEmpty().let { raw ->
            if (raw.isBlank()) emptyList()
            else raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        },
        listName = getString(getColumnIndexOrThrow(COL_LIST_NAME)).orEmpty().ifBlank { "inbox" },
        createdAt = getLong(getColumnIndexOrThrow(COL_CREATED_AT)),
        updatedAt = getLong(getColumnIndexOrThrow(COL_UPDATED_AT)),
        completedAt = getLong(getColumnIndexOrThrow(COL_COMPLETED_AT)),
        source = getString(getColumnIndexOrThrow(COL_SOURCE)),
    )

    companion object {
        const val DATABASE_NAME = "thrisa_todos.db"
        const val DATABASE_VERSION = 1

        const val TABLE_TODOS = "todos"
        const val COL_ID = "id"
        const val COL_UUID = "uuid"
        const val COL_TITLE = "title"
        const val COL_NOTES = "notes"
        const val COL_DONE = "done"
        const val COL_DUE_AT_MS = "due_at_ms"
        const val COL_PRIORITY = "priority"
        const val COL_TAGS = "tags"
        const val COL_LIST_NAME = "list_name"
        const val COL_CREATED_AT = "created_at"
        const val COL_UPDATED_AT = "updated_at"
        const val COL_COMPLETED_AT = "completed_at"
        const val COL_SOURCE = "source"

        @Volatile private var instance: SqliteTodoDatabase? = null

        fun getInstance(context: Context): SqliteTodoDatabase =
            instance ?: synchronized(this) {
                instance ?: SqliteTodoDatabase(context.applicationContext).also { instance = it }
            }

        fun createInMemory(context: Context, name: String? = null): SqliteTodoDatabase =
            SqliteTodoDatabase(context, name ?: "test_todos_${System.nanoTime()}.db")
    }
}
