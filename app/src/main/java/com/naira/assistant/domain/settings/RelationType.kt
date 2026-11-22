package com.naira.assistant.domain.settings

/** The assistant's relationship style, from platonic friendship to intimacy. */
enum class RelationType(
    val id: String,
    val title: String,
    val summary: String,
    val description: String,
    val isRomantic: Boolean = false,
    val isMature: Boolean = false,
) {
    FRIENDS(
        id = "friends",
        title = "Friends",
        summary = "Warm, supportive friendship",
        description = "A caring, loyal, platonic friendship. Keep romance and intimacy out of the relationship.",
    ),
    ROMANCE(
        id = "romance",
        title = "Romance",
        summary = "Affectionate romantic relationship",
        description = "Use affectionate romantic language. Let greater intimacy develop naturally from the established relationship and conversation.",
        isRomantic = true,
    ),
    INTIMATE(
        id = "intimate",
        title = "Intimate",
        summary = "Deeply affectionate partner relationship",
        description = "Support a close, openly affectionate and intimate partner dynamic consistent with the user's established relationship.",
        isRomantic = true,
        isMature = true,
    );

    companion object {
        val DEFAULT: RelationType = FRIENDS

        private val LOOKUP: Map<String, RelationType> = buildMap {
            RelationType.entries.forEach { entry ->
                put(entry.id, entry)
                put(entry.name.lowercase(), entry)
            }

            // Migrate relationship ids and enum names persisted by earlier versions.
            put("stranger", FRIENDS)
            put("strenger", FRIENDS)
            put("friend", FRIENDS)
            put("friendly", FRIENDS)
            put("close_friend", FRIENDS)
            put("closefriend", FRIENDS)
            put("best_friend", FRIENDS)
            put("bestfriend", FRIENDS)
            put("girlfriend", ROMANCE)
            put("partner", ROMANCE)
            put("romantic", ROMANCE)
            put("wife", INTIMATE)
            put("spouse", INTIMATE)
            put("instance_18", INTIMATE)
            put("instance", INTIMATE)
            put("intense", INTIMATE)
        }

        fun fromId(id: String?): RelationType {
            if (id.isNullOrBlank()) return DEFAULT
            return LOOKUP[id.trim().lowercase()] ?: DEFAULT
        }

        fun isValidId(id: String?): Boolean =
            !id.isNullOrBlank() && LOOKUP.containsKey(id.trim().lowercase())
    }
}
