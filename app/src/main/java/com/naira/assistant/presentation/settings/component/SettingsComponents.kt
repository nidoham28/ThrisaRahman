@file:Suppress("SpellCheckingInspection")

package com.naira.assistant.presentation.settings.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naira.assistant.core.theme.Aqua
import com.naira.assistant.core.theme.CardStroke
import com.naira.assistant.core.theme.TextHigh
import com.naira.assistant.core.theme.TextSoft
import com.naira.assistant.domain.settings.AssistantLanguage
import com.naira.assistant.domain.settings.AssistantVoice
import com.naira.assistant.domain.settings.AssistantThinkingMode
import com.naira.assistant.domain.settings.Gender
import com.naira.assistant.domain.settings.RelationType
import com.naira.assistant.presentation.settings.model.RowStyle
import com.naira.assistant.presentation.settings.model.SettingsNode
import com.naira.assistant.presentation.settings.model.rootSettingsSections


/* ------------------------------------------------------------------ */
/*  Top bar                                                            */
/* ------------------------------------------------------------------ */

@Composable
internal fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = TextHigh,
                modifier = Modifier.size(26.dp),
            )
        }
        Text(title, color = TextHigh, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
    }
}

/* ------------------------------------------------------------------ */
/*  Root list                                                          */
/* ------------------------------------------------------------------ */

@Composable
internal fun SettingsCloudSyncErrorCard(message: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF171C2C))
            .border(1.dp, CardStroke, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = Color(0xFFFFB36B),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Settings couldn't sync", color = TextHigh, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(3.dp))
                Text(message, color = TextSoft, fontSize = 12.sp)
            }
        }
        TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End)) {
            Text("Retry", color = Aqua)
        }
    }
}

@Composable
internal fun RootList(nodes: List<SettingsNode>, onOpen: (String) -> Unit) {
    val categories = nodes.filterIsInstance<SettingsNode.Category>()
    val sections = rootSettingsSections.mapNotNull { section ->
        val sectionCategories = categories.filter { it.id in section.categoryIds }
        section.takeIf { sectionCategories.isNotEmpty() }?.let { it to sectionCategories }
    }
    val groupedCategoryIds = sections.flatMap { (_, sectionCategories) ->
        sectionCategories.map(SettingsNode.Category::id)
    }.toSet()
    val ungroupedCategories = categories.filterNot { it.id in groupedCategoryIds }
    val rootItems = nodes.filterIsInstance<SettingsNode.Item>()

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        sections.forEach { (section, sectionCategories) ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsSectionTag(section.title)
                NodeCard(sectionCategories, onOpen)
            }
        }
        if (ungroupedCategories.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsSectionTag("More Settings")
                NodeCard(ungroupedCategories, onOpen)
            }
        }
        if (rootItems.isNotEmpty()) NodeCard(rootItems, onOpen)
    }
}

@Composable
private fun SettingsSectionTag(title: String) {
    Text(
        title,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { heading() },
        color = Aqua,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.2.sp,
    )
}

/* ------------------------------------------------------------------ */
/*  Nested list                                                        */
/* ------------------------------------------------------------------ */

@Composable
internal fun NodeCard(nodes: List<SettingsNode>, onOpenCategory: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(RowSurface)
            .border(1.dp, CardStroke.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
    ) {
        nodes.forEachIndexed { index, node ->
            val isLast = index == nodes.lastIndex
            when (node) {
                is SettingsNode.Category -> CategoryRow(node, isLast) { onOpenCategory(node.id) }
                is SettingsNode.Item -> ItemRow(node, isLast)
            }
        }
    }
}

@Composable
internal fun CategoryRow(
    category: SettingsNode.Category,
    isLast: Boolean,
    onClick: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                    .background(category.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(category.icon, contentDescription = null, tint = category.accent, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(category.title, color = TextHigh, fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                category.subtitle?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, color = TextSoft, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(8.dp))
            category.trailing?.invoke()
            if (category.trailing == null) TrailingChevron()
        }
        if (!isLast) Divider(startPadding = 58.dp)
    }
}

@Composable
internal fun ItemRow(item: SettingsNode.Item, isLast: Boolean) {
    val titleColor = when (item.style) {
        RowStyle.DANGER -> Danger
        else -> TextHigh
    }.copy(alpha = if (item.enabled) 1f else 0.45f)

    val iconTint = when (item.style) {
        RowStyle.DANGER -> Danger
        RowStyle.PREMIUM -> Premium
        RowStyle.NORMAL -> TextSoft
    }.copy(alpha = if (item.enabled) 1f else 0.45f)

    val click = item.onClick

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .then(
                    if (click != null && item.enabled) Modifier.clickable { click() }
                    else Modifier
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.icon != null) {
                Icon(item.icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(item.title, color = titleColor, fontSize = 17.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                item.subtitle?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, color = TextSoft.copy(alpha = if (item.enabled) 1f else 0.45f),
                        fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(12.dp))
            // Fall back to the default trailing when none is provided:
            // chevron for clickable rows, nothing otherwise.
            when {
                item.trailing != null -> item.trailing.invoke()
                click != null && item.enabled -> TrailingChevron()
                else -> Unit
            }
        }
        if (!isLast) Divider(startPadding = if (item.icon != null) 50.dp else 16.dp)
    }
}

@Composable
internal fun Divider(startPadding: Dp) {
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.width(startPadding))
        Box(Modifier.weight(1f).height(1.dp).background(CardStroke.copy(alpha = 0.4f)))
    }
}

/* ------------------------------------------------------------------ */
/*  Trailing components                                                */
/* ------------------------------------------------------------------ */

@Composable
internal fun TrailingChevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = TextSoft.copy(alpha = 0.6f),
        modifier = Modifier.size(20.dp)
    )
}

@Composable
internal fun TrailingValue(text: String) {
    Text(text, color = TextSoft, fontSize = 13.sp)
}

@Composable
internal fun TrailingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = label },
    )
}

@Composable
internal fun ThemeDropdownTrailing(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = when (selected.lowercase()) {
        "light" -> "Light"
        "dark" -> "Dark"
        else -> "System"
    }

    Box {
        TextButton(onClick = { expanded = true }) {
            Text(selectedLabel, color = Aqua, fontSize = 13.sp)
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = "Choose theme",
                tint = TextSoft,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

@Composable
internal fun WakeWordSensitivityDropdown(selectedThreshold: Float, onSelect: (Float) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = when {
        selectedThreshold <= 0.17f -> "High"
        selectedThreshold >= 0.35f -> "Low"
        else -> "Balanced"
    }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(selectedLabel, color = Aqua, fontSize = 13.sp)
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = "Choose wake-word sensitivity",
                tint = TextSoft,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(
                "High sensitivity" to 0.15f,
                "Balanced" to 0.25f,
                "Low sensitivity" to 0.40f,
            ).forEach { (label, threshold) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onSelect(threshold)
                    },
                )
            }
        }
    }
}

@Composable
internal fun TrailingBadge(text: String, accent: Color = Aqua) {
    Box(
        Modifier.clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.16f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(text, color = accent, fontSize = 9.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun GenderDropdown(selected: Gender, onSelect: (Gender) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Text(selected.label, color = Aqua, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Choose gender",
                tint = TextSoft, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(RowSurface)
                .border(1.dp, CardStroke.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
        ) {
            Gender.entries.forEach { gender ->
                DropdownMenuItem(
                    text = { Text(gender.label, color = TextHigh, fontSize = 14.sp) },
                    onClick = { onSelect(gender); expanded = false },
                )
            }
        }
    }
}

@Composable
internal fun SoundNameDropdown(
    selected: String,
    assistantVoice: AssistantVoice,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Text(selected, color = Aqua, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Choose sound name",
                tint = TextSoft, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(RowSurface)
                .border(1.dp, CardStroke.copy(alpha = 0.7f), RoundedCornerShape(12.dp)),
        ) {
            assistantVoice.geminiVoiceNames.forEach { voiceName ->
                DropdownMenuItem(
                    text = { Text(voiceName, color = TextHigh, fontSize = 14.sp) },
                    onClick = { onSelect(voiceName); expanded = false },
                )
            }
        }
    }
}

@Composable
internal fun RelationTypeDropdown(selected: RelationType, onSelect: (RelationType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Text(selected.title, color = selected.accent(), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Choose relationship",
                tint = TextSoft, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(RowSurface)
                .border(1.dp, CardStroke.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
        ) {
            RelationType.entries.forEach { relationship ->
                DropdownMenuItem(
                    text = { Text(relationship.title, color = TextHigh, fontSize = 14.sp) },
                    onClick = { onSelect(relationship); expanded = false },
                )
            }
        }
    }
}

@Composable
internal fun LanguageDropdown(selected: AssistantLanguage, onSelect: (AssistantLanguage) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Text(selected.label, color = Aqua, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Choose assistant language",
                tint = TextSoft, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(RowSurface)
                .border(1.dp, CardStroke.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
        ) {
            AssistantLanguage.entries.forEach { language ->
                val isSelected = language == selected
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                language.label, color = TextHigh, fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Spacer(Modifier.weight(1f))
                            if (isSelected) {
                                Icon(Icons.Outlined.Check, contentDescription = null,
                                    tint = Aqua, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    onClick = { onSelect(language); expanded = false }
                )
            }
        }
    }
}

@Composable
internal fun AssistantThinkingDropdown(
    selected: AssistantThinkingMode,
    onSelect: (AssistantThinkingMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Text(selected.title, color = Aqua, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = "Choose model thinking mode",
                tint = TextSoft,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(RowSurface)
                .border(1.dp, CardStroke.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
        ) {
            AssistantThinkingMode.entries.forEach { mode ->
                val isSelected = mode == selected
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                mode.title,
                                color = TextHigh,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            Spacer(Modifier.weight(1f))
                            if (isSelected) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = Aqua,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    },
                    onClick = { onSelect(mode); expanded = false },
                )
            }
        }
    }
}
