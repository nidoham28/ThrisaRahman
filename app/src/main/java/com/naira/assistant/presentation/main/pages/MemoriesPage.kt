package com.naira.assistant.presentation.main.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naira.assistant.core.database.memory.MemoryCategory
import com.naira.assistant.core.database.memory.MemoryItem
import com.naira.assistant.core.sync.SupabaseMemoryRepository
import com.naira.assistant.core.theme.Aqua
import com.naira.assistant.core.theme.AquaDeep
import com.naira.assistant.core.theme.CardBrush
import com.naira.assistant.core.theme.CardShape
import com.naira.assistant.core.theme.CardStroke
import com.naira.assistant.core.theme.Lilac
import com.naira.assistant.core.theme.TextHigh
import com.naira.assistant.core.theme.TextSoft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val DeleteRed = Color(0xFFE53935)
private val DeleteErrorText = Color(0xFFFF9F9F)
private val DialogSurface = Color(0xFF141D2A)
private val FieldSurfaceFocused = Color(0xFF0F1824)
private val FieldSurfaceUnfocused = Color(0xFF0B121C)
private val TagSurface = Color(0xFF141F2D)
private val MinTouchTarget = 48.dp

/**
 * [assistantName] is used in the empty state and delete-confirmation copy.
 * Defaults to "NAIRA" to match the app's wordmark; override if you later
 * centralise the assistant name in a single config source.
 */
@Composable
fun MemoriesPage(
    modifier: Modifier = Modifier,
    assistantName: String = "NAIRA",
) {
    val repository = remember { SupabaseMemoryRepository.getInstance() }
    val scope = rememberCoroutineScope()

    // Lifecycle-aware collection: pauses while the screen is backgrounded.
    val memories by repository.observeAll().collectAsStateWithLifecycle()

    // rememberSaveable: search + filter survive rotation.
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }

    // Not saveable — MemoryItem is a domain type, not Bundle-friendly.
    // Losing the dialog on rotation is acceptable (user re-opens).
    var memoryToDelete by remember { mutableStateOf<MemoryItem?>(null) }

    // Shows a spinner only when the DB is empty *and* the first refresh is
    // still in flight. If the local DB already has cached data, no spinner
    // flashes (avoids the "No memories yet" flicker on cold open).
    var isLoading by remember { mutableStateOf(true) }

    // Initial load + every resume. Refresh failures are non-fatal — the
    // local DB is authoritative for rendering.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch {
            try {
                repository.refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Swallow — best-effort sync.
            } finally {
                isLoading = false
            }
        }
    }

    val filteredMemories = remember(memories, searchQuery, selectedCategory) {
        memories.filter { item ->
            val matchesCategory = selectedCategory == null ||
                    item.category.equals(selectedCategory, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                    item.content.contains(searchQuery, ignoreCase = true) ||
                    item.tags.any { it.contains(searchQuery, ignoreCase = true) }
            matchesCategory && matchesSearch
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        SearchField(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
        )

        Spacer(Modifier.height(12.dp))

        CategoryFilterRow(
            memories = memories,
            selectedCategory = selectedCategory,
            onCategoryChange = { selectedCategory = it },
        )

        Spacer(Modifier.height(14.dp))

        when {
            isLoading && memories.isEmpty() -> LoadingState(
                modifier = Modifier.fillMaxWidth().weight(1f),
            )

            filteredMemories.isEmpty() -> EmptyState(
                isSearching = searchQuery.isNotBlank(),
                assistantName = assistantName,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )

            else -> MemoriesList(
                memories = filteredMemories,
                onDeleteRequest = { memoryToDelete = it },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }

    memoryToDelete?.let { memory ->
        DeleteConfirmationDialog(
            memory = memory,
            assistantName = assistantName,
            onDismiss = { memoryToDelete = null },
            onDelete = { onSuccess ->
                scope.launch {
                    try {
                        repository.remove(memory.id)
                        onSuccess()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Caller shows the error inline and keeps the dialog open.
                        throw DeleteFailedException()
                    }
                }
            },
        )
    }
}

/** Marker exception so [DeleteConfirmationDialog] can distinguish user error from cancellation. */
private class DeleteFailedException : Exception()

// ──────────────────────────────────────────────────────────────────────────
// Search field
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                "Search memories...",
                color = TextSoft.copy(alpha = 0.7f),
                fontSize = 14.sp,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = TextSoft,
                modifier = Modifier.size(20.dp),
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Outlined.Clear,
                        contentDescription = "Clear search",
                        tint = TextSoft,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = FieldSurfaceFocused,
            unfocusedContainerColor = FieldSurfaceUnfocused,
            focusedBorderColor = Aqua,
            unfocusedBorderColor = CardStroke,
            focusedTextColor = TextHigh,
            unfocusedTextColor = TextHigh,
        ),
    )
}

// ──────────────────────────────────────────────────────────────────────────
// Category filter
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun CategoryFilterRow(
    memories: List<MemoryItem>,
    selectedCategory: String?,
    onCategoryChange: (String?) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        item {
            CategoryChip(
                label = "All (${memories.size})",
                isSelected = selectedCategory == null,
                onClick = { onCategoryChange(null) },
            )
        }
        items(MemoryCategory.entries) { cat ->
            val count = memories.count { it.category.equals(cat.id, ignoreCase = true) }
            // Keep the selected category visible even when it drops to 0,
            // so the user can see *why* the list is empty and tap to deselect.
            if (count > 0 || selectedCategory == cat.id) {
                CategoryChip(
                    label = "${cat.title} ($count)",
                    isSelected = selectedCategory == cat.id,
                    onClick = {
                        onCategoryChange(if (selectedCategory == cat.id) null else cat.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) Aqua.copy(alpha = 0.2f) else FieldSurfaceFocused)
            .border(1.dp, if (isSelected) Aqua else CardStroke, RoundedCornerShape(20.dp))
            // `selectable` announces "selected" state to TalkBack and gives
            // the chip a Role.Tab so it reads as part of a filter bar.
            .selectable(
                selected = isSelected,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (isSelected) Aqua else TextSoft,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Loading / Empty / List
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            strokeWidth = 2.5.dp,
            color = Aqua,
            trackColor = CardStroke,
        )
    }
}

@Composable
private fun EmptyState(
    isSearching: Boolean,
    assistantName: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(CardShape)
            .background(CardBrush)
            .border(1.dp, CardStroke, CardShape)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (isSearching) Icons.Outlined.Search else Icons.Outlined.Layers,
                contentDescription = null,
                tint = TextSoft.copy(alpha = 0.5f),
                modifier = Modifier.size(44.dp),
            )
            Text(
                text = if (isSearching) "No matching memories found" else "No memories yet",
                color = TextHigh,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (isSearching) {
                    "Try searching with different keywords."
                } else {
                    "As you chat with $assistantName, facts, preferences, and personal details will be remembered automatically."
                },
                color = TextSoft,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun MemoriesList(
    memories: List<MemoryItem>,
    onDeleteRequest: (MemoryItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        // Small bottom padding so the last card doesn't sit flush against
        // the nav bar (or the screen edge in previews).
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        items(memories, key = { it.id }) { memory ->
            MemoryCard(
                memory = memory,
                onDelete = { onDeleteRequest(memory) },
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Memory card
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun MemoryCard(
    memory: MemoryItem,
    onDelete: () -> Unit,
) {
    val categoryMeta = remember(memory.category) {
        MemoryCategory.fromId(memory.category)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(CardBrush)
            .border(1.dp, CardStroke, CardShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AquaDeep.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = categoryMeta.title,
                        color = Aqua,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                ImportanceDots(count = memory.importance)
            }

            IconButton(
                onClick = onDelete,
                // Default IconButton size is 48dp — no need to override.
                // The icon inside stays 16dp; the touch target is Material-compliant.
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Delete memory",
                    tint = TextSoft.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Text(
            text = memory.content,
            color = TextHigh,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )

        if (memory.tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                memory.tags.forEach { tag ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(TagSurface)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "#$tag",
                            color = TextSoft,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportanceDots(count: Int) {
    // Clamp so a corrupt/malicious value can't render 1,000 dots.
    val dots = count.coerceIn(0, 5)
    if (dots == 0) return
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(dots) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(Lilac)
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Delete confirmation
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun DeleteConfirmationDialog(
    memory: MemoryItem,
    assistantName: String,
    onDismiss: () -> Unit,
    onDelete: (onSuccess: () -> Unit) -> Unit,
) {
    // Keyed on memory.id so a fresh dialog always starts clean — even if the
    // user cancels and re-opens the same memory, or opens a different one.
    var isDeleting by remember(memory.id) { mutableStateOf(false) }
    var deleteError by remember(memory.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        // Do not dismiss while the delete is in flight — the operation
        // continues otherwise and the user gets no feedback either way.
        onDismissRequest = { if (!isDeleting) onDismiss() },
        containerColor = DialogSurface,
        title = {
            Text(
                text = "Forget this memory?",
                color = TextHigh,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = memory.content,
                    color = TextHigh,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$assistantName will no longer remember this detail.",
                    color = TextSoft,
                    fontSize = 13.sp,
                )
                deleteError?.let { message ->
                    Text(
                        text = message,
                        color = DeleteErrorText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isDeleting = true
                    deleteError = null
                    try {
                        onDelete { onDismiss() }
                    } catch (_: DeleteFailedException) {
                        deleteError = "Couldn't delete this memory. Please try again."
                        isDeleting = false
                    }
                },
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(containerColor = DeleteRed),
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    Text("Delete", color = Color.White)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDeleting,
            ) {
                Text("Cancel", color = TextSoft)
            }
        },
    )
}