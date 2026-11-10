package com.naira.assistant.domain

/**
 * Bottom-navigation destinations, in visual order.
 *
 * Deliberately framework-free: this enum lives in the domain layer and must
 * not depend on Compose or Android. Icons are mapped in the presentation
 * layer via `com.naira.assistant.presentation.main.component.icon`.
 *
 * Enum ordinal is the visual position. Reordering the enum reorders the
 * bottom bar — by design, so there is one source of truth.
 *
 * @property label English fallback label. For localized UI, map to a string
 *   resource in presentation; `label` is used as the accessibility
 *   content description and as the default visible text.
 * @property isAction true for the centre action button (mic). Action entries
 *   are not selectable pages and must not be persisted as "current tab".
 */
enum class NavDestination(
    val label: String,
    val isAction: Boolean = false,
) {
    HOME("Home"),
    CHAT("Chat"),
    /** Centre action (mic) — not a selectable page. */
    SPEAK("Speak", isAction = true),
    MEMORIES("Memories"),
    PROFILE("Profile"),
    ;

    companion object {
        /** Selectable pages only — excludes the centre action. */
        val pageEntries: List<NavDestination>
            get() = entries.filterNot { it.isAction }
    }
}

/**
 * Index of the centre action in visual order.
 *
 * Derived from [NavDestination.SPEAK]'s ordinal — cannot drift out of sync
 * with the enum, unlike the previous `const val MicIndex = 2`.
 *
 * Prefer [NavDestination.SPEAK] directly in new code; this alias is kept
 * for existing positional call sites (`destinations[MicIndex]`, `when (i)
 * { MicIndex -> … }`).
 */
val MicIndex: Int
    get() = NavDestination.SPEAK.ordinal