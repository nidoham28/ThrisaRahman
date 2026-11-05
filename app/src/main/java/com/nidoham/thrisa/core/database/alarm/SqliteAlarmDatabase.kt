package com.nidoham.thrisa.core.database.alarm

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

class SqliteAlarmDatabase private constructor(
    context: Context,
    dbName: String = DATABASE_NAME,
) : SQLiteOpenHelper(context.applicationContext, dbName, null, DATABASE_VERSION), AlarmRepository {

    private val dbScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dbMutex = Mutex()
    private val _alarmsFlow = MutableStateFlow<List<AlarmItem>>(emptyList())

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
            CREATE TABLE IF NOT EXISTS $TABLE_ALARMS (
                $COL_ID         INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_UUID       TEXT    NOT NULL UNIQUE,
                $COL_HOUR       INTEGER NOT NULL,
                $COL_MINUTE     INTEGER NOT NULL,
                $COL_LABEL      TEXT    NOT NULL DEFAULT '',
                $COL_DAYS       TEXT    NOT NULL DEFAULT '',
                $COL_ENABLED    INTEGER NOT NULL DEFAULT 1,
                $COL_VIBRATE    INTEGER NOT NULL DEFAULT 1,
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_UPDATED_AT INTEGER NOT NULL,
                $COL_SOURCE     TEXT    NOT NULL DEFAULT 'ai_tool'
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_alarms_time ON $TABLE_ALARMS($COL_HOUR, $COL_MINUTE);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_alarms_enabled ON $TABLE_ALARMS($COL_ENABLED);")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Schema migrations
    }

    override suspend fun insert(
        hour: Int,
        minute: Int,
        label: String,
        days: List<Int>,
        enabled: Boolean,
        vibrate: Boolean,
        source: String,
    ): AlarmItem = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val now = System.currentTimeMillis()
            val uuid = UUID.randomUUID().toString()
            val cleanDays = days.distinct().sorted()

            val values = ContentValues().apply {
                put(COL_UUID, uuid)
                put(COL_HOUR, hour.coerceIn(0, 23))
                put(COL_MINUTE, minute.coerceIn(0, 59))
                put(COL_LABEL, label.trim())
                put(COL_DAYS, cleanDays.joinToString(","))
                put(COL_ENABLED, if (enabled) 1 else 0)
                put(COL_VIBRATE, if (vibrate) 1 else 0)
                put(COL_CREATED_AT, now)
                put(COL_UPDATED_AT, now)
                put(COL_SOURCE, source)
            }

            val insertedId = writableDatabase.insertOrThrow(TABLE_ALARMS, null, values)
            val item = AlarmItem(
                id = insertedId,
                uuid = uuid,
                hour = hour.coerceIn(0, 23),
                minute = minute.coerceIn(0, 59),
                label = label.trim(),
                days = cleanDays,
                enabled = enabled,
                vibrate = vibrate,
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
        hour: Int?,
        minute: Int?,
        label: String?,
        days: List<Int>?,
        enabled: Boolean?,
        vibrate: Boolean?,
    ): AlarmItem? = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val existing = getByIdInternal(id) ?: return@withLock null
            val now = System.currentTimeMillis()

            val newHour = hour?.coerceIn(0, 23) ?: existing.hour
            val newMinute = minute?.coerceIn(0, 59) ?: existing.minute
            val newLabel = label?.trim() ?: existing.label
            val newDays = days?.distinct()?.sorted() ?: existing.days
            val newEnabled = enabled ?: existing.enabled
            val newVibrate = vibrate ?: existing.vibrate

            val values = ContentValues().apply {
                put(COL_HOUR, newHour)
                put(COL_MINUTE, newMinute)
                put(COL_LABEL, newLabel)
                put(COL_DAYS, newDays.joinToString(","))
                put(COL_ENABLED, if (newEnabled) 1 else 0)
                put(COL_VIBRATE, if (newVibrate) 1 else 0)
                put(COL_UPDATED_AT, now)
            }

            val rows = writableDatabase.update(TABLE_ALARMS, values, "$COL_ID = ?", arrayOf(id.toString()))
            if (rows > 0) {
                val updated = existing.copy(
                    hour = newHour,
                    minute = newMinute,
                    label = newLabel,
                    days = newDays,
                    enabled = newEnabled,
                    vibrate = newVibrate,
                    updatedAt = now,
                )
                refreshCache()
                updated
            } else null
        }
    }

    override suspend fun delete(id: Long): Boolean = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val rows = writableDatabase.delete(TABLE_ALARMS, "$COL_ID = ?", arrayOf(id.toString()))
            if (rows > 0) {
                refreshCache()
                true
            } else false
        }
    }

    override suspend fun getById(id: Long): AlarmItem? = withContext(Dispatchers.IO) {
        dbMutex.withLock { getByIdInternal(id) }
    }

    override suspend fun getAll(enabledOnly: Boolean?, limit: Int): List<AlarmItem> = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val sel = if (enabledOnly != null) "$COL_ENABLED = ?" else null
            val args = if (enabledOnly != null) arrayOf(if (enabledOnly) "1" else "0") else null

            readableDatabase.query(
                TABLE_ALARMS,
                null,
                sel,
                args,
                null,
                null,
                "$COL_HOUR ASC, $COL_MINUTE ASC",
                limit.coerceIn(1, 200).toString()
            ).use { c ->
                val list = ArrayList<AlarmItem>(c.count)
                while (c.moveToNext()) list.add(c.toAlarmItem())
                list
            }
        }
    }

    override suspend fun count(): Long = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE_ALARMS", null)
                .use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
        }
    }

    override suspend fun clearAll(): Int = withContext(Dispatchers.IO) {
        dbMutex.withLock {
            val count = writableDatabase.delete(TABLE_ALARMS, null, null)
            refreshCache()
            count
        }
    }

    override fun observeAll(): StateFlow<List<AlarmItem>> = _alarmsFlow.asStateFlow()

    private fun getByIdInternal(id: Long): AlarmItem? =
        readableDatabase.query(TABLE_ALARMS, null, "$COL_ID = ?", arrayOf(id.toString()), null, null, null, "1")
            .use { c -> if (c.moveToFirst()) c.toAlarmItem() else null }

    private fun refreshCache() {
        val list = readableDatabase.query(
            TABLE_ALARMS, null, null, null, null, null, "$COL_HOUR ASC, $COL_MINUTE ASC"
        ).use { c ->
            val result = ArrayList<AlarmItem>(c.count)
            while (c.moveToNext()) result.add(c.toAlarmItem())
            result
        }
        _alarmsFlow.value = list
    }

    private fun Cursor.toAlarmItem() = AlarmItem(
        id = getLong(getColumnIndexOrThrow(COL_ID)),
        uuid = getString(getColumnIndexOrThrow(COL_UUID)),
        hour = getInt(getColumnIndexOrThrow(COL_HOUR)),
        minute = getInt(getColumnIndexOrThrow(COL_MINUTE)),
        label = getString(getColumnIndexOrThrow(COL_LABEL)).orEmpty(),
        days = getString(getColumnIndexOrThrow(COL_DAYS)).orEmpty().let { raw ->
            if (raw.isBlank()) emptyList()
            else raw.split(",").mapNotNull { it.trim().toIntOrNull() }
        },
        enabled = getInt(getColumnIndexOrThrow(COL_ENABLED)) == 1,
        vibrate = getInt(getColumnIndexOrThrow(COL_VIBRATE)) == 1,
        createdAt = getLong(getColumnIndexOrThrow(COL_CREATED_AT)),
        updatedAt = getLong(getColumnIndexOrThrow(COL_UPDATED_AT)),
        source = getString(getColumnIndexOrThrow(COL_SOURCE)),
    )

    companion object {
        const val DATABASE_NAME = "thrisa_alarms.db"
        const val DATABASE_VERSION = 1

        const val TABLE_ALARMS = "alarms"
        const val COL_ID = "id"
        const val COL_UUID = "uuid"
        const val COL_HOUR = "hour"
        const val COL_MINUTE = "minute"
        const val COL_LABEL = "label"
        const val COL_DAYS = "days"
        const val COL_ENABLED = "enabled"
        const val COL_VIBRATE = "vibrate"
        const val COL_CREATED_AT = "created_at"
        const val COL_UPDATED_AT = "updated_at"
        const val COL_SOURCE = "source"

        @Volatile private var instance: SqliteAlarmDatabase? = null

        fun getInstance(context: Context): SqliteAlarmDatabase =
            instance ?: synchronized(this) {
                instance ?: SqliteAlarmDatabase(context.applicationContext).also { instance = it }
            }

        fun createInMemory(context: Context, name: String? = null): SqliteAlarmDatabase =
            SqliteAlarmDatabase(context, name ?: "test_alarms_${System.nanoTime()}.db")
    }
}
