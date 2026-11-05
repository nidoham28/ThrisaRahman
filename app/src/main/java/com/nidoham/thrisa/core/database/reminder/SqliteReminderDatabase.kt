package com.nidoham.thrisa.core.database.reminder

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
import java.util.Calendar
import java.util.UUID

/**
 * Native SQLite local database for AI-managed reminders.
 *
 * Design mirrors [com.nidoham.thrisa.core.database.memory.SqliteMemoryDatabase]:
 * - Singleton pattern with thread-safe double-check locking.
 * - WAL mode + foreign key enforcement.
 * - Mutex-serialised writes for correctness under concurrent coroutines.
 * - Reactive [StateFlow] cache for Compose UI without extra queries.
 * - Schema version bumped as new columns are added.
 */
class SqliteReminderDatabase private constructor(
    context: Context,
    dbName: String = DATABASE_NAME,
) : SQLiteOpenHelper(context.applicationContext, dbName, null, DATABASE_VERSION),
    ReminderRepository {

    private val dbScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dbMutex = Mutex()
    private val _remindersFlow = MutableStateFlow<List<ReminderItem>>(emptyList())

    init {
        dbScope.launch { refreshCache() }
    }

    // ------------------------------------------------------------------
    // SQLiteOpenHelper
    // ------------------------------------------------------------------

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_REMINDERS (
                $COL_ID                 INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_UUID               TEXT    NOT NULL UNIQUE,
                $COL_TITLE              TEXT    NOT NULL,
                $COL_DESCRIPTION        TEXT    NOT NULL DEFAULT '',
                $COL_TRIGGER_AT_MS      INTEGER NOT NULL,
                $COL_RECURRENCE         TEXT    NOT NULL DEFAULT 'none',
                $COL_RECURRENCE_INTERVAL INTEGER NOT NULL DEFAULT 0,
                $COL_STATUS             TEXT    NOT NULL DEFAULT 'active',
                $COL_PRIORITY           TEXT    NOT NULL DEFAULT 'normal',
                $COL_TAGS               TEXT    NOT NULL DEFAULT '',
                $COL_SNOOZED_UNTIL_MS   INTEGER NOT NULL DEFAULT 0,
                $COL_NEXT_TRIGGER_MS    INTEGER NOT NULL,
                $COL_CREATED_AT         INTEGER NOT NULL,
                $COL_UPDATED_AT         INTEGER NOT NULL,
                $COL_SOURCE             TEXT    NOT NULL DEFAULT 'ai_tool'
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_reminders_status ON $TABLE_REMINDERS($COL_STATUS);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_reminders_next_trigger ON $TABLE_REMINDERS($COL_NEXT_TRIGGER_MS);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_reminders_priority ON $TABLE_REMINDERS($COL_PRIORITY);")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future schema migrations go here
    }

    // ------------------------------------------------------------------
    // ReminderRepository Implementation
    // ------------------------------------------------------------------

    override suspend fun insert(
        title: String,
        description: String,
        triggerAtMs: Long,
        recurrence: RecurrenceType,
        recurrenceInterval: Int,
        priority: ReminderPriority,
        tags: List<String>,
        source: String,
    ): ReminderItem = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val now = System.currentTimeMillis()
            val uuid = UUID.randomUUID().toString()
            val cleanTags = tags.map { it.trim() }.filter { it.isNotBlank() }

            val values = ContentValues().apply {
                put(COL_UUID, uuid)
                put(COL_TITLE, title.trim())
                put(COL_DESCRIPTION, description.trim())
                put(COL_TRIGGER_AT_MS, triggerAtMs)
                put(COL_RECURRENCE, recurrence.id)
                put(COL_RECURRENCE_INTERVAL, recurrenceInterval.coerceAtLeast(0))
                put(COL_STATUS, ReminderStatus.ACTIVE.id)
                put(COL_PRIORITY, priority.id)
                put(COL_TAGS, cleanTags.joinToString(","))
                put(COL_SNOOZED_UNTIL_MS, 0L)
                put(COL_NEXT_TRIGGER_MS, triggerAtMs)
                put(COL_CREATED_AT, now)
                put(COL_UPDATED_AT, now)
                put(COL_SOURCE, source)
            }

            val insertedId = writableDatabase.insertOrThrow(TABLE_REMINDERS, null, values)
            val item = ReminderItem(
                id = insertedId,
                uuid = uuid,
                title = title.trim(),
                description = description.trim(),
                triggerAtMs = triggerAtMs,
                recurrence = recurrence,
                recurrenceInterval = recurrenceInterval.coerceAtLeast(0),
                status = ReminderStatus.ACTIVE,
                priority = priority,
                tags = cleanTags,
                snoozedUntilMs = 0L,
                nextTriggerMs = triggerAtMs,
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
        description: String?,
        triggerAtMs: Long?,
        recurrence: RecurrenceType?,
        recurrenceInterval: Int?,
        status: ReminderStatus?,
        priority: ReminderPriority?,
        tags: List<String>?,
        snoozedUntilMs: Long?,
    ): ReminderItem? = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val existing = getByIdInternal(id) ?: return@withLock null
            val now = System.currentTimeMillis()

            val newTitle = title?.trim() ?: existing.title
            val newDescription = description?.trim() ?: existing.description
            val newTriggerAt = triggerAtMs ?: existing.triggerAtMs
            val newRecurrence = recurrence ?: existing.recurrence
            val newInterval = recurrenceInterval?.coerceAtLeast(0) ?: existing.recurrenceInterval
            val newStatus = status ?: existing.status
            val newPriority = priority ?: existing.priority
            val newTags = tags?.map { it.trim() }?.filter { it.isNotBlank() } ?: existing.tags
            val newSnoozedUntil = snoozedUntilMs ?: existing.snoozedUntilMs

            // Re-compute nextTriggerMs when trigger time or recurrence changes
            val newNextTrigger = if (triggerAtMs != null || recurrence != null) newTriggerAt else existing.nextTriggerMs

            val values = ContentValues().apply {
                put(COL_TITLE, newTitle)
                put(COL_DESCRIPTION, newDescription)
                put(COL_TRIGGER_AT_MS, newTriggerAt)
                put(COL_RECURRENCE, newRecurrence.id)
                put(COL_RECURRENCE_INTERVAL, newInterval)
                put(COL_STATUS, newStatus.id)
                put(COL_PRIORITY, newPriority.id)
                put(COL_TAGS, newTags.joinToString(","))
                put(COL_SNOOZED_UNTIL_MS, newSnoozedUntil)
                put(COL_NEXT_TRIGGER_MS, newNextTrigger)
                put(COL_UPDATED_AT, now)
            }

            val rows = writableDatabase.update(TABLE_REMINDERS, values, "$COL_ID = ?", arrayOf(id.toString()))
            if (rows > 0) {
                val updated = existing.copy(
                    title = newTitle,
                    description = newDescription,
                    triggerAtMs = newTriggerAt,
                    recurrence = newRecurrence,
                    recurrenceInterval = newInterval,
                    status = newStatus,
                    priority = newPriority,
                    tags = newTags,
                    snoozedUntilMs = newSnoozedUntil,
                    nextTriggerMs = newNextTrigger,
                    updatedAt = now,
                )
                refreshCache()
                updated
            } else null
        }
    }

    override suspend fun delete(id: Long): Boolean = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val rows = writableDatabase.delete(TABLE_REMINDERS, "$COL_ID = ?", arrayOf(id.toString()))
            if (rows > 0) { refreshCache(); true } else false
        }
    }

    override suspend fun markDone(id: Long): ReminderItem? = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val existing = getByIdInternal(id) ?: return@withLock null
            val now = System.currentTimeMillis()

            // For recurring reminders, compute the next trigger time
            val nextTrigger = when (existing.recurrence) {
                RecurrenceType.NONE -> existing.nextTriggerMs
                RecurrenceType.DAILY -> existing.nextTriggerMs + 24L * 60 * 60 * 1000
                RecurrenceType.WEEKLY -> existing.nextTriggerMs + 7L * 24 * 60 * 60 * 1000
                RecurrenceType.MONTHLY -> addMonths(existing.nextTriggerMs, 1)
                RecurrenceType.YEARLY -> addMonths(existing.nextTriggerMs, 12)
                RecurrenceType.CUSTOM -> existing.nextTriggerMs + existing.recurrenceInterval * 60L * 1000
                RecurrenceType.WEEKDAYS -> nextWeekday(existing.nextTriggerMs)
                RecurrenceType.WEEKENDS -> nextWeekend(existing.nextTriggerMs)
            }

            val newStatus = if (existing.recurrence == RecurrenceType.NONE) ReminderStatus.DONE else ReminderStatus.ACTIVE

            val values = ContentValues().apply {
                put(COL_STATUS, newStatus.id)
                put(COL_NEXT_TRIGGER_MS, nextTrigger)
                put(COL_UPDATED_AT, now)
            }

            val rows = writableDatabase.update(TABLE_REMINDERS, values, "$COL_ID = ?", arrayOf(id.toString()))
            if (rows > 0) {
                val updated = existing.copy(status = newStatus, nextTriggerMs = nextTrigger, updatedAt = now)
                refreshCache()
                updated
            } else null
        }
    }

    override suspend fun snooze(id: Long, until: Long): ReminderItem? = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val existing = getByIdInternal(id) ?: return@withLock null
            val now = System.currentTimeMillis()
            val values = ContentValues().apply {
                put(COL_STATUS, ReminderStatus.SNOOZED.id)
                put(COL_SNOOZED_UNTIL_MS, until)
                put(COL_NEXT_TRIGGER_MS, until)
                put(COL_UPDATED_AT, now)
            }
            val rows = writableDatabase.update(TABLE_REMINDERS, values, "$COL_ID = ?", arrayOf(id.toString()))
            if (rows > 0) {
                val updated = existing.copy(
                    status = ReminderStatus.SNOOZED,
                    snoozedUntilMs = until,
                    nextTriggerMs = until,
                    updatedAt = now,
                )
                refreshCache()
                updated
            } else null
        }
    }

    override suspend fun cancel(id: Long): ReminderItem? = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val existing = getByIdInternal(id) ?: return@withLock null
            val now = System.currentTimeMillis()
            val values = ContentValues().apply {
                put(COL_STATUS, ReminderStatus.CANCELLED.id)
                put(COL_UPDATED_AT, now)
            }
            val rows = writableDatabase.update(TABLE_REMINDERS, values, "$COL_ID = ?", arrayOf(id.toString()))
            if (rows > 0) {
                val updated = existing.copy(status = ReminderStatus.CANCELLED, updatedAt = now)
                refreshCache()
                updated
            } else null
        }
    }

    override suspend fun getById(id: Long): ReminderItem? = withContext(Dispatchers.IO) {
        dbMutex.withLock { getByIdInternal(id) }
    }

    override suspend fun getByUuid(uuid: String): ReminderItem? = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            readableDatabase.query(
                TABLE_REMINDERS, null, "$COL_UUID = ?", arrayOf(uuid), null, null, null, "1"
            ).use { c -> if (c.moveToFirst()) c.toReminderItem() else null }
        }
    }

    override suspend fun getAll(
        status: String?,
        priority: String?,
        limit: Int,
    ): List<ReminderItem> = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val selParts = mutableListOf<String>()
            val args = mutableListOf<String>()
            if (!status.isNullOrBlank() && status != "all") { selParts += "$COL_STATUS = ?"; args += status.trim().lowercase() }
            if (!priority.isNullOrBlank() && priority != "all") { selParts += "$COL_PRIORITY = ?"; args += priority.trim().lowercase() }

            readableDatabase.query(
                TABLE_REMINDERS, null,
                selParts.joinToString(" AND ").ifEmpty { null },
                args.toTypedArray().ifEmpty { null },
                null, null,
                "$COL_NEXT_TRIGGER_MS ASC",
                limit.coerceIn(1, 200).toString()
            ).use { c ->
                val list = ArrayList<ReminderItem>(c.count)
                while (c.moveToNext()) list.add(c.toReminderItem())
                list
            }
        }
    }

    override suspend fun getUpcoming(fromMs: Long, toMs: Long, limit: Int): List<ReminderItem> =
        withContext(Dispatchers.IO) {
            dbMutex.withLock {
                readableDatabase.query(
                    TABLE_REMINDERS, null,
                    "$COL_NEXT_TRIGGER_MS >= ? AND $COL_NEXT_TRIGGER_MS < ? AND $COL_STATUS = ?",
                    arrayOf(fromMs.toString(), toMs.toString(), ReminderStatus.ACTIVE.id),
                    null, null,
                    "$COL_NEXT_TRIGGER_MS ASC",
                    limit.coerceIn(1, 100).toString()
                ).use { c ->
                    val list = ArrayList<ReminderItem>(c.count)
                    while (c.moveToNext()) list.add(c.toReminderItem())
                    list
                }
            }
        }

    override suspend fun search(
        query: String?,
        status: String?,
        limit: Int,
    ): List<ReminderItem> = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val cleanQuery = query?.trim()?.takeIf { it.isNotBlank() }
            val cleanStatus = status?.trim()?.lowercase()?.takeIf { it.isNotBlank() && it != "all" }

            val selParts = mutableListOf<String>()
            val args = mutableListOf<String>()

            if (cleanQuery != null) {
                val like = "%$cleanQuery%"
                selParts += "($COL_TITLE LIKE ? OR $COL_DESCRIPTION LIKE ? OR $COL_TAGS LIKE ?)"
                args += like; args += like; args += like
            }
            if (cleanStatus != null) { selParts += "$COL_STATUS = ?"; args += cleanStatus }

            readableDatabase.query(
                TABLE_REMINDERS, null,
                selParts.joinToString(" AND ").ifEmpty { null },
                args.toTypedArray().ifEmpty { null },
                null, null,
                "$COL_NEXT_TRIGGER_MS ASC",
                limit.coerceIn(1, 100).toString()
            ).use { c ->
                val list = ArrayList<ReminderItem>(c.count)
                while (c.moveToNext()) list.add(c.toReminderItem())
                list
            }
        }
    }

    override suspend fun count(): Long = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE_REMINDERS", null)
                .use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
        }
    }

    override suspend fun clearAll(): Int = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val count = writableDatabase.delete(TABLE_REMINDERS, null, null)
            refreshCache()
            count
        }
    }

    override fun observeAll(): StateFlow<List<ReminderItem>> = _remindersFlow.asStateFlow()

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun getByIdInternal(id: Long): ReminderItem? =
        readableDatabase.query(TABLE_REMINDERS, null, "$COL_ID = ?", arrayOf(id.toString()), null, null, null, "1")
            .use { c -> if (c.moveToFirst()) c.toReminderItem() else null }

    private fun refreshCache() {
        val list = readableDatabase.query(
            TABLE_REMINDERS, null, null, null, null, null, "$COL_NEXT_TRIGGER_MS ASC"
        ).use { c ->
            val result = ArrayList<ReminderItem>(c.count)
            while (c.moveToNext()) result.add(c.toReminderItem())
            result
        }
        _remindersFlow.value = list
    }

    private fun Cursor.toReminderItem() = ReminderItem(
        id = getLong(getColumnIndexOrThrow(COL_ID)),
        uuid = getString(getColumnIndexOrThrow(COL_UUID)),
        title = getString(getColumnIndexOrThrow(COL_TITLE)),
        description = getString(getColumnIndexOrThrow(COL_DESCRIPTION)).orEmpty(),
        triggerAtMs = getLong(getColumnIndexOrThrow(COL_TRIGGER_AT_MS)),
        recurrence = RecurrenceType.fromId(getString(getColumnIndexOrThrow(COL_RECURRENCE))),
        recurrenceInterval = getInt(getColumnIndexOrThrow(COL_RECURRENCE_INTERVAL)),
        status = ReminderStatus.fromId(getString(getColumnIndexOrThrow(COL_STATUS))),
        priority = ReminderPriority.fromId(getString(getColumnIndexOrThrow(COL_PRIORITY))),
        tags = getString(getColumnIndexOrThrow(COL_TAGS)).orEmpty().let { raw ->
            if (raw.isBlank()) emptyList()
            else raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        },
        snoozedUntilMs = getLong(getColumnIndexOrThrow(COL_SNOOZED_UNTIL_MS)),
        nextTriggerMs = getLong(getColumnIndexOrThrow(COL_NEXT_TRIGGER_MS)),
        createdAt = getLong(getColumnIndexOrThrow(COL_CREATED_AT)),
        updatedAt = getLong(getColumnIndexOrThrow(COL_UPDATED_AT)),
        source = getString(getColumnIndexOrThrow(COL_SOURCE)),
    )

    /** Add [months] calendar months to [epochMs], returning the result in ms. */
    private fun addMonths(epochMs: Long, months: Int): Long {
        val cal = Calendar.getInstance().also { it.timeInMillis = epochMs }
        cal.add(Calendar.MONTH, months)
        return cal.timeInMillis
    }

    /** Next occurrence that falls on Mon–Fri after [epochMs]. */
    private fun nextWeekday(epochMs: Long): Long {
        val cal = Calendar.getInstance().also { it.timeInMillis = epochMs }
        do {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        } while (cal.get(Calendar.DAY_OF_WEEK) in listOf(Calendar.SATURDAY, Calendar.SUNDAY))
        return cal.timeInMillis
    }

    /** Next occurrence that falls on Sat or Sun after [epochMs]. */
    private fun nextWeekend(epochMs: Long): Long {
        val cal = Calendar.getInstance().also { it.timeInMillis = epochMs }
        do {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        } while (cal.get(Calendar.DAY_OF_WEEK) !in listOf(Calendar.SATURDAY, Calendar.SUNDAY))
        return cal.timeInMillis
    }

    // ------------------------------------------------------------------
    // Schema constants
    // ------------------------------------------------------------------

    companion object {
        const val DATABASE_NAME = "thrisa_reminders.db"
        const val DATABASE_VERSION = 1

        const val TABLE_REMINDERS = "reminders"
        const val COL_ID = "id"
        const val COL_UUID = "uuid"
        const val COL_TITLE = "title"
        const val COL_DESCRIPTION = "description"
        const val COL_TRIGGER_AT_MS = "trigger_at_ms"
        const val COL_RECURRENCE = "recurrence"
        const val COL_RECURRENCE_INTERVAL = "recurrence_interval"
        const val COL_STATUS = "status"
        const val COL_PRIORITY = "priority"
        const val COL_TAGS = "tags"
        const val COL_SNOOZED_UNTIL_MS = "snoozed_until_ms"
        const val COL_NEXT_TRIGGER_MS = "next_trigger_ms"
        const val COL_CREATED_AT = "created_at"
        const val COL_UPDATED_AT = "updated_at"
        const val COL_SOURCE = "source"

        @Volatile private var instance: SqliteReminderDatabase? = null

        fun getInstance(context: Context): SqliteReminderDatabase =
            instance ?: synchronized(this) {
                instance ?: SqliteReminderDatabase(context.applicationContext).also { instance = it }
            }

        /** Creates an isolated test database (in-memory-style named DB). */
        fun createInMemory(context: Context, name: String? = null): SqliteReminderDatabase =
            SqliteReminderDatabase(context, name ?: "test_reminders_${System.nanoTime()}.db")
    }
}
