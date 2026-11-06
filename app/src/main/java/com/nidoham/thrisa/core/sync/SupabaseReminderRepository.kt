package com.nidoham.thrisa.core.sync

import com.nidoham.thrisa.core.database.reminder.RecurrenceType
import com.nidoham.thrisa.core.database.reminder.ReminderItem
import com.nidoham.thrisa.core.database.reminder.ReminderPriority
import com.nidoham.thrisa.core.database.reminder.ReminderRepository
import com.nidoham.thrisa.core.database.reminder.ReminderStatus
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Calendar
import java.util.UUID

/**
 * Row of the Supabase `user_schedule` table (see
 * `supabase/migrations/…_user_schedule.sql`). `timestamptz` columns travel as
 * ISO-8601 strings.
 */
@Serializable
@JsonIgnoreUnknownKeys
internal data class ScheduleRow(
    val id: Long = 0,
    val uid: String? = null,
    val uuid: String = "",
    val title: String = "",
    val description: String = "",
    @SerialName("trigger_at") val triggerAt: String? = null,
    val recurrence: String = "none",
    @SerialName("recurrence_interval") val recurrenceInterval: Int = 0,
    val status: String = "active",
    val priority: String = "normal",
    val tags: List<String> = emptyList(),
    @SerialName("snoozed_until") val snoozedUntil: String? = null,
    @SerialName("next_trigger_at") val nextTriggerAt: String? = null,
    val source: String = "ai_tool",
    @SerialName("create_at") val createAt: String? = null,
    @SerialName("update_at") val updateAt: String? = null,
) {
    fun toItem(nowMs: Long = System.currentTimeMillis()): ReminderItem {
        val triggerMs = triggerAt.toEpochMsOrDefault(nowMs)
        return ReminderItem(
            id = id,
            uuid = uuid,
            title = title,
            description = description,
            triggerAtMs = triggerMs,
            recurrence = RecurrenceType.fromId(recurrence),
            recurrenceInterval = recurrenceInterval.coerceAtLeast(0),
            status = ReminderStatus.fromId(status),
            priority = ReminderPriority.fromId(priority),
            tags = tags,
            snoozedUntilMs = snoozedUntil.toEpochMsOrDefault(0),
            nextTriggerMs = nextTriggerAt.toEpochMsOrDefault(triggerMs),
            createdAt = createAt.toEpochMsOrDefault(nowMs),
            updatedAt = updateAt.toEpochMsOrDefault(nowMs),
            source = source,
        )
    }
}

internal fun ReminderItem.toPushBody(uid: String, uuid: String): JsonObject =
    buildJsonObject {
        put("uid", uid)
        put("uuid", uuid)
        put("title", title.trim())
        put("description", description)
        put("trigger_at", triggerAtMs.toIsoTimestamp())
        put("recurrence", recurrence.id)
        put("recurrence_interval", recurrenceInterval.coerceAtLeast(0))
        put("status", status.id)
        put("priority", priority.id)
        put("tags", buildJsonArray { tags.forEach { add(it) } })
        put(
            "snoozed_until",
            if (snoozedUntilMs > 0) JsonPrimitive(snoozedUntilMs.toIsoTimestamp()) else JsonNull,
        )
        put("next_trigger_at", nextTriggerMs.toIsoTimestamp())
        put("source", source)
    }

/**
 * Cloud-only [ReminderRepository] backed by the Supabase `user_schedule`
 * table. Row ids come from the database, so every device agrees on them. An
 * in-memory cache backs [observeAll] for the Compose UI; mutations require a
 * signed-in user and throw otherwise.
 */
class SupabaseReminderRepository private constructor(
    private val client: SupabaseClient = Supabase.client,
    private val currentUserId: () -> String? = { SupabaseAuthService().currentUser()?.id },
) : ReminderRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cacheMutex = Mutex()
    private val _reminders = MutableStateFlow<List<ReminderItem>>(emptyList())

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
            val rows = client.from("user_schedule")
                .select {
                    filter { eq("uid", uid) }
                    order("next_trigger_at", Order.ASCENDING)
                    limit(CACHE_LIMIT.toLong())
                }
                .decodeList<ScheduleRow>()
                .map { it.toItem() }
            cacheMutex.withLock {
                loadedUid = uid
                _reminders.value = rows
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
            _reminders.value = emptyList()
        }
    }

    override fun observeAll(): StateFlow<List<ReminderItem>> = _reminders.asStateFlow()

    override suspend fun insert(
        title: String,
        description: String,
        triggerAtMs: Long,
        recurrence: RecurrenceType,
        recurrenceInterval: Int,
        priority: ReminderPriority,
        tags: List<String>,
        source: String,
    ): ReminderItem {
        val uid = requireSignedIn()
        val row = client.from("user_schedule")
            .upsert(
                ReminderItem(
                    title = title,
                    description = description,
                    triggerAtMs = triggerAtMs,
                    recurrence = recurrence,
                    recurrenceInterval = recurrenceInterval,
                    priority = priority,
                    tags = tags.map { it.trim() }.filter { it.isNotBlank() },
                    source = source,
                ).toPushBody(uid, uuid = UUID.randomUUID().toString()),
            ) { select() }
            .decodeSingle<ScheduleRow>()
        refresh()
        return row.toItem()
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
    ): ReminderItem? {
        requireSignedIn()
        val existingItem = fetchById(id)?.toItem() ?: return null
        val newTriggerAt = triggerAtMs ?: existingItem.triggerAtMs
        val patch = buildJsonObject {
            put("title", (title ?: existingItem.title).trim())
            put("description", description ?: existingItem.description)
            put("trigger_at", newTriggerAt.toIsoTimestamp())
            put("recurrence", (recurrence ?: existingItem.recurrence).id)
            put("recurrence_interval", recurrenceInterval ?: existingItem.recurrenceInterval)
            put("status", (status ?: existingItem.status).id)
            put("priority", (priority ?: existingItem.priority).id)
            if (tags != null) {
                put(
                    "tags",
                    buildJsonArray { tags.map { it.trim() }.filter { it.isNotBlank() }.forEach { add(it) } },
                )
            }
            put(
                "snoozed_until",
                if ((snoozedUntilMs ?: existingItem.snoozedUntilMs) > 0) {
                    JsonPrimitive((snoozedUntilMs ?: existingItem.snoozedUntilMs).toIsoTimestamp())
                } else {
                    JsonNull
                },
            )
            put(
                "next_trigger_at",
                computeNextTrigger(existingItem.copy(triggerAtMs = newTriggerAt), triggerAtMs != null).toIsoTimestamp(),
            )
        }
        val row = client.from("user_schedule")
            .update(patch) {
                filter { eq("id", id) }
                select()
            }
            .decodeSingleOrNull<ScheduleRow>() ?: return null
        refresh()
        return row.toItem()
    }

    override suspend fun delete(id: Long): Boolean {
        requireSignedIn()
        fetchById(id) ?: return false
        client.from("user_schedule").delete {
            filter { eq("id", id) }
        }
        refresh()
        return true
    }

    override suspend fun markDone(id: Long): ReminderItem? {
        requireSignedIn()
        val existing = fetchById(id)?.toItem() ?: return null
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
        val newStatus =
            if (existing.recurrence == RecurrenceType.NONE) ReminderStatus.DONE else ReminderStatus.ACTIVE
        val row = client.from("user_schedule")
            .update(
                buildJsonObject {
                    put("status", newStatus.id)
                    put("next_trigger_at", nextTrigger.toIsoTimestamp())
                },
            ) {
                filter { eq("id", id) }
                select()
            }
            .decodeSingleOrNull<ScheduleRow>() ?: return null
        refresh()
        return row.toItem()
    }

    override suspend fun snooze(id: Long, until: Long): ReminderItem? {
        requireSignedIn()
        val row = client.from("user_schedule")
            .update(
                buildJsonObject {
                    put("status", ReminderStatus.SNOOZED.id)
                    put("snoozed_until", until.toIsoTimestamp())
                    put("next_trigger_at", until.toIsoTimestamp())
                },
            ) {
                filter { eq("id", id) }
                select()
            }
            .decodeSingleOrNull<ScheduleRow>() ?: return null
        refresh()
        return row.toItem()
    }

    override suspend fun cancel(id: Long): ReminderItem? {
        requireSignedIn()
        val row = client.from("user_schedule")
            .update(
                buildJsonObject { put("status", ReminderStatus.CANCELLED.id) },
            ) {
                filter { eq("id", id) }
                select()
            }
            .decodeSingleOrNull<ScheduleRow>() ?: return null
        refresh()
        return row.toItem()
    }

    override suspend fun getById(id: Long): ReminderItem? {
        cachedRows().firstOrNull { it.id == id }?.let { return it }
        return fetchById(id)?.toItem()
    }

    override suspend fun getByUuid(uuid: String): ReminderItem? {
        cachedRows().firstOrNull { it.uuid == uuid }?.let { return it }
        val uid = currentUserId() ?: return null
        return client.from("user_schedule")
            .select {
                filter {
                    eq("uid", uid)
                    eq("uuid", uuid)
                }
                limit(1)
            }
            .decodeList<ScheduleRow>()
            .firstOrNull()
            ?.toItem()
    }

    override suspend fun getAll(
        status: String?,
        priority: String?,
        limit: Int,
    ): List<ReminderItem> {
        val uid = currentUserId() ?: return emptyList()
        return client.from("user_schedule")
            .select {
                filter {
                    eq("uid", uid)
                    if (!status.isNullOrBlank() && status != "all") eq("status", status.trim().lowercase())
                    if (!priority.isNullOrBlank() && priority != "all") eq("priority", priority.trim().lowercase())
                }
                order("next_trigger_at", Order.ASCENDING)
                limit(limit.coerceIn(1, 200).toLong())
            }
            .decodeList<ScheduleRow>()
            .map { it.toItem() }
    }

    override suspend fun getUpcoming(fromMs: Long, toMs: Long, limit: Int): List<ReminderItem> {
        val uid = currentUserId() ?: return emptyList()
        return client.from("user_schedule")
            .select {
                filter {
                    eq("uid", uid)
                    eq("status", ReminderStatus.ACTIVE.id)
                    gte("next_trigger_at", fromMs.toIsoTimestamp())
                    lt("next_trigger_at", toMs.toIsoTimestamp())
                }
                order("next_trigger_at", Order.ASCENDING)
                limit(limit.coerceIn(1, 100).toLong())
            }
            .decodeList<ScheduleRow>()
            .map { it.toItem() }
    }

    override suspend fun search(
        query: String?,
        status: String?,
        limit: Int,
    ): List<ReminderItem> {
        val uid = currentUserId() ?: return emptyList()
        val keyword = query?.trim()?.takeIf { it.isNotBlank() }?.lowercase()
        val cleanStatus = status?.trim()?.lowercase()?.takeIf { it.isNotBlank() && it != "all" }
        // Match title/description/tags locally, mirroring the original SQLite search.
        return client.from("user_schedule")
            .select {
                filter {
                    eq("uid", uid)
                    if (cleanStatus != null) eq("status", cleanStatus)
                }
                order("next_trigger_at", Order.ASCENDING)
                limit(CACHE_LIMIT.toLong())
            }
            .decodeList<ScheduleRow>()
            .map { it.toItem() }
            .asSequence()
            .filter {
                keyword == null ||
                    it.title.lowercase().contains(keyword) ||
                    it.description.lowercase().contains(keyword) ||
                    it.tags.any { tag -> tag.lowercase().contains(keyword) }
            }
            .take(limit.coerceIn(1, 100))
            .toList()
    }

    override suspend fun count(): Long {
        val uid = currentUserId() ?: return 0
        return client.from("user_schedule")
            .select {
                filter { eq("uid", uid) }
                limit(CACHE_LIMIT.toLong())
            }
            .decodeList<ScheduleRow>()
            .size.toLong()
    }

    override suspend fun clearAll(): Int {
        requireSignedIn()
        val previous = cachedRows().size
        val uid = currentUserId()
        if (uid != null) {
            client.from("user_schedule").delete {
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
        currentUserId() ?: error("Sign in to use your schedule.")

    private suspend fun cachedRows(): List<ReminderItem> {
        val uid = currentUserId()
        if (uid != null && (uid != loadedUid || _reminders.value.isEmpty())) refresh()
        return _reminders.value
    }

    private suspend fun fetchById(id: Long): ScheduleRow? {
        val uid = currentUserId() ?: return null
        return client.from("user_schedule")
            .select {
                filter {
                    eq("uid", uid)
                    eq("id", id)
                }
                limit(1)
            }
            .decodeList<ScheduleRow>()
            .firstOrNull()
    }

    /**
     * Next fire time after an edit. A changed [triggerAtChanged] trigger
     * resets the schedule to the new trigger time; otherwise the existing
     * next trigger is kept.
     */
    private fun computeNextTrigger(base: ReminderItem, triggerAtChanged: Boolean): Long =
        if (triggerAtChanged) base.triggerAtMs else base.nextTriggerMs

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

    companion object {
        const val CACHE_LIMIT = 1000

        @Volatile
        private var instance: SupabaseReminderRepository? = null

        fun getInstance(): SupabaseReminderRepository =
            instance ?: synchronized(this) {
                instance ?: SupabaseReminderRepository().also { instance = it }
            }
    }
}
