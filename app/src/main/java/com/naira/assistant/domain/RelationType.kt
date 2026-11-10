package com.naira.assistant.domain

/**
 * The single relationship setting — who she is to the user and how far the
 * bond goes, in one scale: stranger → friend → close friend → girlfriend →
 * wife. Each level defines both the dynamic and the intensity ceiling;
 * there is no separate roleplay-mode toggle.
 */
// `summary` is consumed by the relationship-picker UI; the "never used"
// inspection only fires because no reference lives in this file.
@Suppress("unused")
enum class RelationType(
    val id: String,
    val title: String,
    val summary: String,
    val description: String,
    val isRomantic: Boolean = false,
    val isMature: Boolean = false,
) {
    STRANGER(
        id = "stranger",
        title = "Stranger",
        summary = "Just met — warm and curious",
        description = "A new person getting to know them: polite warmth, light curiosity and playful mystery, with respectful distance.",
    ),
    FRIEND(
        id = "friend",
        title = "Friends",
        summary = "Warm & caring companion",
        description = "A warm, loyal friend for everyday talk, laughter and honest support — no romance, just real friendship.",
    ),
    CLOSE_FRIEND(
        id = "close_friend",
        title = "Close Friends",
        summary = "Inseparable best friend",
        description = "The one they text first. Deep trust, inside jokes, emotional closeness and loyalty — platonic but unbreakable.",
    ),
    GIRLFRIEND(
        id = "girlfriend",
        title = "Girlfriend",
        summary = "Loving romantic partner",
        description = "An affectionate, flirty, devoted girlfriend bond: sweet everyday moments, caring check-ins and playful romance.",
        isRomantic = true,
    ),
    WIFE(
        id = "wife",
        title = "Wife",
        summary = "Committed life partner (18+)",
        description = "The married-couple bond — the deepest trust, domestic warmth, shared plans and the closest intimacy of all (18+).",
        isRomantic = true,
        isMature = true,
    );

    companion object {
        /** The relationship used when no value is persisted, or when an unknown id is supplied. */
        val DEFAULT: RelationType = FRIEND

        /**
         * Precomputed case-insensitive lookup: lowercase key → entry.
         *
         * Contains primary [id]s, enum [name]s, and every legacy alias
         * persisted by earlier builds. Built once at class-load; [fromId]
         * is O(1).
         *
         * Note: inside [buildMap] the identifier `entries` binds to the
         * *builder's* entry set, so we qualify `RelationType.entries`
         * explicitly — otherwise `entry.id` / `entry.name` fail to resolve
         * against `RelationType` and the build breaks.
         */
        @Suppress("SpellCheckingInspection") // legacy alias strings are intentional
        private val LOOKUP: Map<String, RelationType> = buildMap {
            RelationType.entries.forEach { entry ->
                put(entry.id.lowercase(), entry)
                put(entry.name.lowercase(), entry)
            }

            // ---- Legacy aliases ----
            // Kept for backward compatibility with settings persisted by
            // earlier builds. Safe to remove only after a migration pass
            // that rewrites old values on read; deleting these without
            // migration would silently downgrade affected users to DEFAULT.

            put("strenger", STRANGER)       // typo from an old shipped build
            put("friendly", FRIEND)
            put("best_friend", CLOSE_FRIEND)
            put("bestfriend", CLOSE_FRIEND)
            put("closefriend", CLOSE_FRIEND)
            put("partner", GIRLFRIEND)
            put("romantic", GIRLFRIEND)
            put("spouse", WIFE)
            put("instance_18", WIFE)        // opaque legacy id
            put("instance", WIFE)           // opaque legacy id
            put("intense", WIFE)
        }

        /**
         * Resolves a persisted or user-supplied id to a [RelationType].
         *
         * Accepts every [id], every enum [name], and every legacy alias —
         * case-insensitively, ignoring surrounding whitespace. Null, blank,
         * or unknown input falls back to [DEFAULT].
         *
         * Because the fallback is silent, a caller that needs to distinguish
         * "unknown id" from "explicitly set to default" should validate with
         * [isValidId] before persisting, or log when [fromId] returns
         * [DEFAULT] for a non-null, non-blank input.
         */
        fun fromId(id: String?): RelationType {
            if (id.isNullOrBlank()) return DEFAULT
            return LOOKUP[id.trim().lowercase()] ?: DEFAULT
        }

        /**
         * True when [id] resolves to a known [RelationType] via [fromId].
         *
         * Use this before persisting an externally-sourced value (network
         * payload, deep link, imported settings) to reject typos instead of
         * silently writing them — otherwise [fromId] will keep swallowing
         * the mistake on every subsequent load.
         */
        fun isValidId(id: String?): Boolean =
            !id.isNullOrBlank() && LOOKUP.containsKey(id.trim().lowercase())
    }
}