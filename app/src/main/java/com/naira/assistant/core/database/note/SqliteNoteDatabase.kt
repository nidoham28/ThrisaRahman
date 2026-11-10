package com.naira.assistant.core.database.note

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

class SqliteNoteDatabase private constructor(
    context: Context,
    dbName: String = DATABASE_NAME,
) : SQLiteOpenHelper(context.applicationContext, dbName, null, DATABASE_VERSION), NoteRepository {

    private val dbScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dbMutex = Mutex()
    private val _notesFlow = MutableStateFlow<List<NoteItem>>(emptyList())

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
            CREATE TABLE IF NOT EXISTS $TABLE_NOTES (
                $COL_ID         INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_UUID       TEXT    NOT NULL UNIQUE,
                $COL_TITLE      TEXT    NOT NULL,
                $COL_CONTENT    TEXT    NOT NULL DEFAULT '',
                $COL_TAGS       TEXT    NOT NULL DEFAULT '',
                $COL_PINNED     INTEGER NOT NULL DEFAULT 0,
                $COL_COLOR      TEXT    NOT NULL DEFAULT '',
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_UPDATED_AT INTEGER NOT NULL,
                $COL_SOURCE     TEXT    NOT NULL DEFAULT 'ai_tool'
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_notes_title ON $TABLE_NOTES($COL_TITLE);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_notes_updated ON $TABLE_NOTES($COL_UPDATED_AT);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_notes_pinned ON $TABLE_NOTES($COL_PINNED);")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future schema migrations
    }

    override suspend fun insert(
        title: String,
        content: String,
        tags: List<String>,
        pinned: Boolean,
        color: String,
        source: String,
    ): NoteItem = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val now = System.currentTimeMillis()
            val uuid = UUID.randomUUID().toString()
            val cleanTags = tags.map { it.trim() }.filter { it.isNotBlank() }

            val values = ContentValues().apply {
                put(COL_UUID, uuid)
                put(COL_TITLE, title.trim())
                put(COL_CONTENT, content.trim())
                put(COL_TAGS, cleanTags.joinToString(","))
                put(COL_PINNED, if (pinned) 1 else 0)
                put(COL_COLOR, color.trim())
                put(COL_CREATED_AT, now)
                put(COL_UPDATED_AT, now)
                put(COL_SOURCE, source)
            }

            val insertedId = writableDatabase.insertOrThrow(TABLE_NOTES, null, values)
            val item = NoteItem(
                id = insertedId,
                uuid = uuid,
                title = title.trim(),
                content = content.trim(),
                tags = cleanTags,
                pinned = pinned,
                color = color.trim(),
                createdAt = now,
                updatedAt = now,
                source = source,
            )
            refreshCache()
            item
        }
    }

    override suspend fun update(
        id: Long,
        title: String?,
        content: String?,
        tags: List<String>?,
        pinned: Boolean?,
        color: String?,
    ): NoteItem? = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val existing = getByIdInternal(id) ?: return@withLock null
            val now = System.currentTimeMillis()

            val newTitle = title?.trim() ?: existing.title
            val newContent = content?.trim() ?: existing.content
            val newTags = tags?.map { it.trim() }?.filter { it.isNotBlank() } ?: existing.tags
            val newPinned = pinned ?: existing.pinned
            val newColor = color?.trim() ?: existing.color

            val values = ContentValues().apply {
                put(COL_TITLE, newTitle)
                put(COL_CONTENT, newContent)
                put(COL_TAGS, newTags.joinToString(","))
                put(COL_PINNED, if (newPinned) 1 else 0)
                put(COL_COLOR, newColor)
                put(COL_UPDATED_AT, now)
            }

            val rows = writableDatabase.update(TABLE_NOTES, values, "$COL_ID = ?", arrayOf(id.toString()))
            if (rows > 0) {
                val updated = existing.copy(
                    title = newTitle,
                    content = newContent,
                    tags = newTags,
                    pinned = newPinned,
                    color = newColor,
                    updatedAt = now,
                )
                refreshCache()
                updated
            } else null
        }
    }

    override suspend fun delete(id: Long): Boolean = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val rows = writableDatabase.delete(TABLE_NOTES, "$COL_ID = ?", arrayOf(id.toString()))
            if (rows > 0) {
                refreshCache()
                true
            } else false
        }
    }

    override suspend fun getById(id: Long): NoteItem? = withContext(Dispatchers.IO) {
        dbMutex.withLock { getByIdInternal(id) }
    }

    override suspend fun getAll(limit: Int): List<NoteItem> = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            readableDatabase.query(
                TABLE_NOTES,
                null,
                null,
                null,
                null,
                null,
                "$COL_PINNED DESC, $COL_UPDATED_AT DESC",
                limit.coerceIn(1, 200).toString()
            ).use { c ->
                val list = ArrayList<NoteItem>(c.count)
                while (c.moveToNext()) list.add(c.toNoteItem())
                list
            }
        }
    }

    override suspend fun search(
        query: String?,
        tags: String?,
        limit: Int,
    ): List<NoteItem> = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val cleanQuery = query?.trim()?.takeIf { it.isNotBlank() }
            val cleanTags = tags?.trim()?.takeIf { it.isNotBlank() }

            val selParts = mutableListOf<String>()
            val args = mutableListOf<String>()

            if (cleanQuery != null) {
                val like = "%$cleanQuery%"
                selParts += "($COL_TITLE LIKE ? OR $COL_CONTENT LIKE ? OR $COL_TAGS LIKE ?)"
                args += like; args += like; args += like
            }
            if (cleanTags != null) {
                selParts += "$COL_TAGS LIKE ?"
                args += "%$cleanTags%"
            }

            readableDatabase.query(
                TABLE_NOTES,
                null,
                selParts.joinToString(" AND ").ifEmpty { null },
                args.toTypedArray().ifEmpty { null },
                null,
                null,
                "$COL_PINNED DESC, $COL_UPDATED_AT DESC",
                limit.coerceIn(1, 100).toString()
            ).use { c ->
                val list = ArrayList<NoteItem>(c.count)
                while (c.moveToNext()) list.add(c.toNoteItem())
                list
            }
        }
    }

    override suspend fun count(): Long = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE_NOTES", null)
                .use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
        }
    }

    override suspend fun clearAll(): Int = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val count = writableDatabase.delete(TABLE_NOTES, null, null)
            refreshCache()
            count
        }
    }

    override fun observeAll(): StateFlow<List<NoteItem>> = _notesFlow.asStateFlow()

    private fun getByIdInternal(id: Long): NoteItem? =
        readableDatabase.query(TABLE_NOTES, null, "$COL_ID = ?", arrayOf(id.toString()), null, null, null, "1")
            .use { c -> if (c.moveToFirst()) c.toNoteItem() else null }

    private fun refreshCache() {
        val list = readableDatabase.query(
            TABLE_NOTES, null, null, null, null, null, "$COL_PINNED DESC, $COL_UPDATED_AT DESC"
        ).use { c ->
            val result = ArrayList<NoteItem>(c.count)
            while (c.moveToNext()) result.add(c.toNoteItem())
            result
        }
        _notesFlow.value = list
    }

    private fun Cursor.toNoteItem() = NoteItem(
        id = getLong(getColumnIndexOrThrow(COL_ID)),
        uuid = getString(getColumnIndexOrThrow(COL_UUID)),
        title = getString(getColumnIndexOrThrow(COL_TITLE)),
        content = getString(getColumnIndexOrThrow(COL_CONTENT)).orEmpty(),
        tags = getString(getColumnIndexOrThrow(COL_TAGS)).orEmpty().let { raw ->
            if (raw.isBlank()) emptyList()
            else raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        },
        pinned = getInt(getColumnIndexOrThrow(COL_PINNED)) == 1,
        color = getString(getColumnIndexOrThrow(COL_COLOR)).orEmpty(),
        createdAt = getLong(getColumnIndexOrThrow(COL_CREATED_AT)),
        updatedAt = getLong(getColumnIndexOrThrow(COL_UPDATED_AT)),
        source = getString(getColumnIndexOrThrow(COL_SOURCE)),
    )

    companion object {
        const val DATABASE_NAME = "thrisa_notes.db"
        const val DATABASE_VERSION = 1

        const val TABLE_NOTES = "notes"
        const val COL_ID = "id"
        const val COL_UUID = "uuid"
        const val COL_TITLE = "title"
        const val COL_CONTENT = "content"
        const val COL_TAGS = "tags"
        const val COL_PINNED = "pinned"
        const val COL_COLOR = "color"
        const val COL_CREATED_AT = "created_at"
        const val COL_UPDATED_AT = "updated_at"
        const val COL_SOURCE = "source"

        @Volatile private var instance: SqliteNoteDatabase? = null

        fun getInstance(context: Context): SqliteNoteDatabase =
            instance ?: synchronized(this) {
                instance ?: SqliteNoteDatabase(context.applicationContext).also { instance = it }
            }

        fun createInMemory(context: Context, name: String? = null): SqliteNoteDatabase =
            SqliteNoteDatabase(context, name ?: "test_notes_${System.nanoTime()}.db")
    }
}
