package com.naira.assistant.core.sync

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseException
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.naira.assistant.core.database.reminder.RecurrenceType
import com.naira.assistant.core.database.reminder.ReminderItem
import com.naira.assistant.core.database.reminder.ReminderPriority
import com.naira.assistant.core.database.reminder.ReminderRepository
import com.naira.assistant.core.database.reminder.ReminderStatus
import com.naira.assistant.core.sync.model.FirebaseReminderRecord
import com.naira.assistant.core.sync.model.toFirebaseId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirebaseReminderRepository private constructor(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(),
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
) : ReminderRepository {
    private val _reminders = MutableStateFlow<List<ReminderItem>>(emptyList())
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

    override fun observeAll(): StateFlow<List<ReminderItem>> = _reminders.asStateFlow()

    suspend fun refresh(): Boolean {
        val uid = firebaseAuth.currentUser?.uid
        bindToUser(uid)
        if (uid == null) return true
        return try {
            val snapshot = remindersRef(uid).get().await()
            if (listenerUid == uid) {
                _reminders.value = snapshot.toReminderItems()
                loadedUid = uid
            }
            true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.e(TAG, "Couldn't refresh reminders from Firebase", error)
            false
        }
    }

    private fun bindToUser(uid: String?) {
        if (uid == null) {
            detachListener()
            listenerUid = null
            loadedUid = null
            _reminders.value = emptyList()
            return
        }
        if (listenerUid == uid) return

        detachListener()
        listenerUid = uid
        loadedUid = null
        _reminders.value = emptyList()
        val ref = remindersRef(uid)
        val valueListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (listenerUid == uid) {
                    _reminders.value = snapshot.toReminderItems()
                    loadedUid = uid
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Realtime reminder listener failed (${error.code})", error.toException())
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
        title: String,
        description: String,
        triggerAtMs: Long,
        recurrence: RecurrenceType,
        recurrenceInterval: Int,
        priority: ReminderPriority,
        tags: List<String>,
        source: String,
    ): ReminderItem {
        val uid = requireUserId()
        val uuid = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val item = ReminderItem(
            id = uuid.toFirebaseId(),
            uuid = uuid,
            title = title.trim(),
            description = description.trim(),
            triggerAtMs = triggerAtMs,
            recurrence = recurrence,
            recurrenceInterval = recurrenceInterval.coerceAtLeast(0),
            status = ReminderStatus.ACTIVE,
            priority = priority,
            tags = tags.map(String::trim).filter(String::isNotBlank),
            snoozedUntilMs = 0,
            nextTriggerMs = triggerAtMs,
            createdAt = now,
            updatedAt = now,
            source = source,
        )
        remindersRef(uid).child(item.uuid).setValue(FirebaseReminderRecord.fromDomain(item)).await()
        return item
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
        val uid = firebaseAuth.currentUser?.uid ?: return null
        val existing = getById(id) ?: return null
        if (firebaseAuth.currentUser?.uid != uid) return null
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            title = title?.trim() ?: existing.title,
            description = description ?: existing.description,
            triggerAtMs = triggerAtMs ?: existing.triggerAtMs,
            recurrence = recurrence ?: existing.recurrence,
            recurrenceInterval = recurrenceInterval?.coerceAtLeast(0) ?: existing.recurrenceInterval,
            status = status ?: existing.status,
            priority = priority ?: existing.priority,
            tags = tags?.map(String::trim)?.filter(String::isNotBlank) ?: existing.tags,
            snoozedUntilMs = snoozedUntilMs ?: existing.snoozedUntilMs,
            nextTriggerMs = triggerAtMs ?: existing.nextTriggerMs,
            updatedAt = now,
        )
        val changes = mutableMapOf<String, Any>("updatedAt" to now)
        title?.let { changes["title"] = updated.title }
        description?.let { changes["description"] = updated.description }
        triggerAtMs?.let {
            changes["triggerAtMs"] = updated.triggerAtMs
            changes["nextTriggerMs"] = updated.nextTriggerMs
        }
        recurrence?.let { changes["recurrence"] = updated.recurrence.id }
        recurrenceInterval?.let { changes["recurrenceInterval"] = updated.recurrenceInterval }
        status?.let { changes["status"] = updated.status.id }
        priority?.let { changes["priority"] = updated.priority.id }
        tags?.let { changes["tags"] = updated.tags }
        snoozedUntilMs?.let { changes["snoozedUntilMs"] = updated.snoozedUntilMs }

        remindersRef(uid).child(existing.uuid).updateChildren(changes).await()
        return updated
    }

    override suspend fun delete(id: Long): Boolean {
        val uid = firebaseAuth.currentUser?.uid ?: return false
        val item = getById(id) ?: return false
        if (firebaseAuth.currentUser?.uid != uid) return false
        remindersRef(uid).child(item.uuid).removeValue().await()
        return true
    }

    override suspend fun markDone(id: Long): ReminderItem? =
        mutateItem(id) { it.copy(status = ReminderStatus.DONE, updatedAt = System.currentTimeMillis()) }

    override suspend fun snooze(id: Long, until: Long): ReminderItem? =
        mutateItem(id) {
            it.copy(
                status = ReminderStatus.SNOOZED,
                snoozedUntilMs = until,
                nextTriggerMs = until,
                updatedAt = System.currentTimeMillis(),
            )
        }

    override suspend fun cancel(id: Long): ReminderItem? =
        mutateItem(id) { it.copy(status = ReminderStatus.CANCELLED, updatedAt = System.currentTimeMillis()) }

    override suspend fun getById(id: Long): ReminderItem? {
        _reminders.value.firstOrNull { it.id == id }?.let { return it }
        val uid = firebaseAuth.currentUser?.uid ?: return null
        return remindersRef(uid).get().await().toReminderItems().firstOrNull { it.id == id }
    }

    override suspend fun getByUuid(uuid: String): ReminderItem? {
        _reminders.value.firstOrNull { it.uuid == uuid }?.let { return it }
        val uid = firebaseAuth.currentUser?.uid ?: return null
        return remindersRef(uid).child(uuid).get().await().toReminderItem()
    }

    override suspend fun getAll(status: String?, priority: String?, limit: Int): List<ReminderItem> {
        ensureLoaded()
        return _reminders.value
            .filter {
                (status.isNullOrBlank() || status == "all" || it.status.id == status) &&
                    (priority.isNullOrBlank() || priority == "all" || it.priority.id == priority)
            }
            .take(limit.coerceIn(1, 200))
    }

    override suspend fun getUpcoming(fromMs: Long, toMs: Long, limit: Int): List<ReminderItem> {
        ensureLoaded()
        return _reminders.value
            .filter {
                it.status == ReminderStatus.ACTIVE &&
                    it.nextTriggerMs >= fromMs && it.nextTriggerMs < toMs
            }
            .take(limit.coerceIn(1, 100))
    }

    override suspend fun search(query: String?, status: String?, limit: Int): List<ReminderItem> {
        ensureLoaded()
        val keyword = query?.trim()?.lowercase()?.takeIf(String::isNotBlank)
        val cleanStatus = status?.trim()?.lowercase()?.takeIf { it.isNotBlank() && it != "all" }
        return _reminders.value
            .filter { cleanStatus == null || it.status.id == cleanStatus }
            .filter {
                keyword == null || it.title.lowercase().contains(keyword) ||
                    it.description.lowercase().contains(keyword) ||
                    it.tags.any { tag -> tag.lowercase().contains(keyword) }
            }
            .take(limit.coerceIn(1, 100))
    }

    override suspend fun count(): Long {
        ensureLoaded()
        return _reminders.value.size.toLong()
    }

    override suspend fun clearAll(): Int {
        val uid = firebaseAuth.currentUser?.uid ?: return 0
        ensureLoaded()
        if (firebaseAuth.currentUser?.uid != uid) return 0
        val previous = _reminders.value.size
        remindersRef(uid).removeValue().await()
        return previous
    }

    private suspend fun mutateItem(id: Long, transform: (ReminderItem) -> ReminderItem): ReminderItem? {
        val uid = firebaseAuth.currentUser?.uid ?: return null
        val item = getById(id) ?: return null
        if (firebaseAuth.currentUser?.uid != uid) return null
        val updated = transform(item)
        remindersRef(uid).child(item.uuid).setValue(FirebaseReminderRecord.fromDomain(updated)).await()
        return updated
    }

    private fun requireUserId(): String =
        firebaseAuth.currentUser?.uid ?: error("Sign in to use your schedule.")

    private suspend fun ensureLoaded() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        if (loadedUid != uid) refresh()
    }

    private fun remindersRef(uid: String): DatabaseReference =
        FirebasePaths.reminders(database, uid)

    private fun DataSnapshot.toReminderItems(): List<ReminderItem> =
        children.mapNotNull { it.toReminderItem() }.sortedBy(ReminderItem::nextTriggerMs).take(1000)

    private fun DataSnapshot.toReminderItem(): ReminderItem? {
        val recordKey = key ?: return null
        return try {
            getValue(FirebaseReminderRecord::class.java)?.toDomain(recordKey)
        } catch (error: DatabaseException) {
            Log.e(TAG, "Ignoring malformed reminder record at $recordKey", error)
            null
        }
    }

    companion object {
        private const val TAG = "FirebaseReminderRepo"

        @Volatile private var instance: FirebaseReminderRepository? = null

        fun getInstance(): FirebaseReminderRepository =
            instance ?: synchronized(this) {
                instance ?: FirebaseReminderRepository().also { instance = it }
            }
    }
}
