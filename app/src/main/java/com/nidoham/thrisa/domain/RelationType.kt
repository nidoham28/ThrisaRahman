package com.nidoham.thrisa.domain

/**
 * The single relationship setting — who she is to the user and how far the
 * bond goes, in one scale: stranger → friend → close friend → girlfriend →
 * wife. Each level defines both the dynamic and the intensity ceiling;
 * there is no separate roleplay-mode toggle.
 */
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
        val DEFAULT = FRIEND

        fun fromId(id: String?): RelationType {
            if (id == null) return DEFAULT
            return entries.firstOrNull {
                it.id.equals(id, ignoreCase = true) ||
                        it.name.equals(id, ignoreCase = true) ||
                        (id.equals("strenger", ignoreCase = true) && it == STRANGER) ||
                        (id.equals("best_friend", ignoreCase = true) && it == CLOSE_FRIEND) ||
                        (id.equals("bestfriend", ignoreCase = true) && it == CLOSE_FRIEND) ||
                        (id.equals("closefriend", ignoreCase = true) && it == CLOSE_FRIEND) ||
                        // Legacy ids from the old two-setting system
                        (id.equals("partner", ignoreCase = true) && it == GIRLFRIEND) ||
                        (id.equals("spouse", ignoreCase = true) && it == WIFE) ||
                        (id.equals("romantic", ignoreCase = true) && it == GIRLFRIEND) ||
                        (id.equals("friendly", ignoreCase = true) && it == FRIEND) ||
                        (id.equals("instance_18", ignoreCase = true) && it == WIFE) ||
                        (id.equals("instance", ignoreCase = true) && it == WIFE) ||
                        (id.equals("intense", ignoreCase = true) && it == WIFE)
            } ?: DEFAULT
        }
    }
}
