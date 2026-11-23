package com.naira.assistant.presentation.settings.page

import com.naira.assistant.presentation.settings.model.SettingsNode

/** Base contract for a top-level Settings category page. */
internal abstract class CategoryNameUi {
    abstract fun build(context: SettingsPageContext): SettingsNode.Category
}
