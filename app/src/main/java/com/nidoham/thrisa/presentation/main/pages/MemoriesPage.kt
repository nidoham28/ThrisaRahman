package com.nidoham.thrisa.presentation.main.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nidoham.thrisa.core.database.memory.MemoryCategory
import com.nidoham.thrisa.core.database.memory.MemoryItem
import com.nidoham.thrisa.core.database.memory.SqliteMemoryDatabase
import com.nidoham.thrisa.core.theme.Aqua
import com.nidoham.thrisa.core.theme.AquaDeep
import com.nidoham.thrisa.core.theme.CardBrush
import com.nidoham.thrisa.core.theme.CardShape
import com.nidoham.thrisa.core.theme.CardStroke
import com.nidoham.thrisa.core.theme.Lilac
import com.nidoham.thrisa.core.theme.TextHigh
import com.nidoham.thrisa.core.theme.TextSoft
import kotlinx.coroutines.launch

@Composable
fun MemoriesPage() {
    val context = LocalContext.current
    val repository = remember(context) { SqliteMemoryDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    val memories by repository.observeAll().collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) } // null = All
    var memoryToDelete by remember { mutableStateOf<MemoryItem?>(null) }

    // Filter memories based on category and search query
    val filteredMemories = remember(memories, searchQuery, selectedCategory) {
        memories.filter { item ->
            val matchesCategory = selectedCategory == null || item.category.equals(selectedCategory, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                    item.content.contains(searchQuery, ignoreCase = true) ||
                    item.tags.any { it.contains(searchQuery, ignoreCase = true) }
            matchesCategory && matchesSearch
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search memories...", color = TextSoft.copy(alpha = 0.7f), fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = TextSoft, modifier = Modifier.size(20.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Outlined.Clear, contentDescription = "Clear", tint = TextSoft, modifier = Modifier.size(18.dp))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF0F1824),
                unfocusedContainerColor = Color(0xFF0B121C),
                focusedBorderColor = Aqua,
                unfocusedBorderColor = CardStroke,
                focusedTextColor = TextHigh,
                unfocusedTextColor = TextHigh,
            )
        )

        Spacer(Modifier.height(12.dp))

        // Category Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            item {
                CategoryChip(
                    label = "All (${memories.size})",
                    isSelected = selectedCategory == null,
                    onClick = { selectedCategory = null }
                )
            }
            items(MemoryCategory.entries) { cat ->
                val count = memories.count { it.category.equals(cat.id, ignoreCase = true) }
                if (count > 0 || selectedCategory == cat.id) {
                    CategoryChip(
                        label = "${cat.title} ($count)",
                        isSelected = selectedCategory == cat.id,
                        onClick = {
                            selectedCategory = if (selectedCategory == cat.id) null else cat.id
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Memory List or Empty State
        if (filteredMemories.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(CardShape)
                    .background(CardBrush)
                    .border(1.dp, CardStroke, CardShape)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        if (searchQuery.isNotEmpty()) Icons.Outlined.Search else Icons.Outlined.Layers,
                        contentDescription = null,
                        tint = TextSoft.copy(alpha = 0.5f),
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        if (searchQuery.isNotEmpty()) "No matching memories found" else "No memories yet",
                        color = TextHigh,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (searchQuery.isNotEmpty())
                            "Try searching with different keywords."
                        else
                            "As you chat with Thrisa, she will remember facts, preferences, and personal details automatically.",
                        color = TextSoft,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredMemories, key = { it.id }) { memory ->
                    MemoryCard(
                        memory = memory,
                        onDelete = { memoryToDelete = memory }
                    )
                }
            }
        }
    }

    // Delete Confirmation Dialog
    memoryToDelete?.let { memory ->
        AlertDialog(
            onDismissRequest = { memoryToDelete = null },
            containerColor = Color(0xFF141D2A),
            title = { Text("Forget this memory?", color = TextHigh, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "\"${memory.content}\"\n\nThrisa will no longer remember this detail.",
                    color = TextSoft,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch { repository.remove(memory.id) }
                        memoryToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { memoryToDelete = null }) {
                    Text("Cancel", color = TextSoft)
                }
            }
        )
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
            .background(if (isSelected) Aqua.copy(alpha = 0.2f) else Color(0xFF0F1824))
            .border(1.dp, if (isSelected) Aqua else CardStroke, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (isSelected) Aqua else TextSoft,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoryCard(
    memory: MemoryItem,
    onDelete: () -> Unit,
) {
    val categoryMeta = remember(memory.category) { MemoryCategory.fromId(memory.category) }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(CardBrush)
            .border(1.dp, CardStroke, CardShape)
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Top metadata: Category badge + Importance dots + Delete action
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AquaDeep.copy(alpha = 0.5f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            categoryMeta.title,
                            color = Aqua,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Importance indicator dots
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(memory.importance) {
                            Box(
                                Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(Lilac)
                            )
                        }
                    }
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete Memory",
                        tint = TextSoft.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Memory Content text
            Text(
                memory.content,
                color = TextHigh,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            // Tags row
            if (memory.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    memory.tags.forEach { tag ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF141F2D))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("#$tag", color = TextSoft, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
