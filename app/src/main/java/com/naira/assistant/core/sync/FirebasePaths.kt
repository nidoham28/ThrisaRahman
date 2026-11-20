package com.naira.assistant.core.sync

import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

/** Canonical Realtime Database locations used by the app. */
internal object FirebasePaths {
    private const val USERS = "users"
    private const val ASSISTANTS = "assistants"
    private const val USER_SETTINGS = "user_settings"
    private const val MEMORIES = "memories"
    private const val REMINDERS = "reminders"
    private const val PROVIDER_KEYS = "providerKeys"

    /** Profile and account data: users/{uid}. */
    fun user(database: FirebaseDatabase, uid: String): DatabaseReference =
        database.reference.child(USERS).child(uid)

    /** A user's assistant character collection: assistants/{uid}/{assistantId}. */
    fun assistants(database: FirebaseDatabase, uid: String): DatabaseReference =
        database.reference.child(ASSISTANTS).child(uid)

    /** App preferences and options: user_settings/{uid}. */
    fun cloudSettings(database: FirebaseDatabase, uid: String): DatabaseReference =
        database.reference.child(USER_SETTINGS).child(uid)

    /** Private memories: memories/{uid}/{memoryId}. */
    fun memories(database: FirebaseDatabase, uid: String): DatabaseReference =
        database.reference.child(MEMORIES).child(uid)

    fun reminders(database: FirebaseDatabase, uid: String): DatabaseReference =
        user(database, uid).child(REMINDERS)

    fun providerKeys(database: FirebaseDatabase, uid: String): DatabaseReference =
        user(database, uid).child(PROVIDER_KEYS)
}
