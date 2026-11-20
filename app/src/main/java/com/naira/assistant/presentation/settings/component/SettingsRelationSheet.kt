@file:Suppress("SpellCheckingInspection")

package com.naira.assistant.presentation.settings.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naira.assistant.core.theme.CardStroke
import com.naira.assistant.core.theme.Lilac
import com.naira.assistant.core.theme.Night
import com.naira.assistant.core.theme.TextHigh
import com.naira.assistant.core.theme.TextSoft
import com.naira.assistant.domain.settings.RelationType
import kotlinx.coroutines.launch


/* ------------------------------------------------------------------ */
/*  Unused-but-referenced helpers                                      */
/*  (kept for the flow described in the comments; wire up when needed) */
/* ------------------------------------------------------------------ */

@Composable
@Suppress("unused")
internal fun SectionLabel(text: String) {
    Text(
        text,
        color = TextSoft.copy(alpha = 0.8f),
        fontSize = 11.sp,
        letterSpacing = 1.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("unused")
internal fun RelationTypeSheet(
    selected: RelationType,
    onApply: (RelationType) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf(selected) }
    var confirmMature by remember { mutableStateOf(false) }

    val platonic = RelationType.entries.filter { !it.isRomantic }
    val romantic = RelationType.entries.filter { it.isRomantic }

    fun applyAndClose(type: RelationType) {
        onApply(type)
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetSurface,
        contentColor = TextHigh
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                        .background(Lilac.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.People, contentDescription = null,
                        tint = Lilac, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Relationship", color = TextHigh, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text("Who she is to you — from stranger to wife", color = TextSoft, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(16.dp))

            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                platonic.forEach { type ->
                    RelationOptionCard(type, isSelected = type == pending) { pending = type }
                }
                Spacer(Modifier.height(6.dp))
                SectionLabel("ROMANTIC BOND")
                romantic.forEach { type ->
                    RelationOptionCard(type, isSelected = type == pending) { pending = type }
                }
                Spacer(Modifier.height(4.dp))
            }

            Spacer(Modifier.height(12.dp))

            val changed = pending != selected
            Button(
                onClick = {
                    if (pending.isMature && !selected.isMature) confirmMature = true
                    else applyAndClose(pending)
                },
                enabled = changed,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = pending.accent(),
                    contentColor = Night,
                    disabledContainerColor = CardStroke.copy(alpha = 0.5f),
                    disabledContentColor = TextSoft
                )
            ) {
                Text(
                    if (changed) "Use ${pending.title}" else "Currently ${selected.title}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmMature) {
        AlertDialog(
            onDismissRequest = { confirmMature = false },
            containerColor = RowSurface,
            icon = {
                Icon(Icons.Outlined.LocalFireDepartment, contentDescription = null,
                    tint = Danger, modifier = Modifier.size(26.dp))
            },
            title = {
                Text("Enable 18+ content?", color = TextHigh, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Text(
                    "“${pending.title}” may include explicit or intense content. " +
                            "Continue only if you are 18 or older. You can switch back at any time.",
                    color = TextSoft, fontSize = 13.sp, lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmMature = false; applyAndClose(pending) }) {
                    Text("I'm 18 or older", color = Danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmMature = false }) { Text("Cancel", color = TextSoft) }
            }
        )
    }
}

@Composable
@Suppress("unused")
internal fun RelationOptionCard(
    type: RelationType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val accent = type.accent()
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(14.dp)

    val borderColor by animateColorAsState(
        if (isSelected) accent else CardStroke.copy(alpha = 0.6f), label = "border"
    )
    val bgColor by animateColorAsState(
        if (isSelected) accent.copy(alpha = 0.09f) else OptionSurface, label = "bg"
    )

    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bgColor)
            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, shape)
            .selectable(
                selected = isSelected,
                role = Role.RadioButton,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
                .background(accent.copy(alpha = if (isSelected) 0.22f else 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(type.icon(), contentDescription = null, tint = accent, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                type.title, color = TextHigh, fontSize = 15.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
            )
            Spacer(Modifier.height(3.dp))
            Text(
                type.description, color = TextSoft.copy(alpha = 0.9f),
                fontSize = 12.sp, lineHeight = 16.sp
            )
        }

        Spacer(Modifier.width(10.dp))
        RadioDot(selected = isSelected, accent = accent)
    }
}

@Composable
@Suppress("unused")
internal fun RadioDot(selected: Boolean, accent: Color) {
    val ring by animateColorAsState(
        if (selected) accent else TextSoft.copy(alpha = 0.4f), label = "ring"
    )
    Box(
        Modifier.size(22.dp).clip(CircleShape)
            .border(1.5.dp, ring, CircleShape)
            .background(if (selected) accent.copy(alpha = 0.2f) else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = accent,
                modifier = Modifier.size(14.dp))
        }
    }
}
