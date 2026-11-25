package com.naira.assistant.core.database

import android.content.Context
import java.io.File

/**
 * Renames SQLite database files persisted under the app's former "Thrisa" name so
 * existing user data keeps loading after the Naira rebrand.
 *
 * Must be called before the database helper opens [currentName]; call sites
 * synchronize on their own singleton, and the operation is idempotent.
 */
internal object LegacyDatabaseFiles {

    fun renameLegacyDatabase(context: Context, legacyName: String, currentName: String) {
        val dir = context.applicationContext.getDatabasePath(currentName).parentFile ?: return
        val legacy = File(dir, legacyName)
        val current = File(dir, currentName)
        if (!legacy.exists() || current.exists()) return
        for (suffix in listOf("-wal", "-shm", "")) {
            val source = File(dir, legacyName + suffix)
            if (!source.exists()) continue
            val target = File(dir, currentName + suffix)
            if (target.exists()) continue
            if (!source.renameTo(target) && suffix.isEmpty()) return
        }
    }
}
