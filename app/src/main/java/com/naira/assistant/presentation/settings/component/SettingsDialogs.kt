@file:Suppress("SpellCheckingInspection")

package com.naira.assistant.presentation.settings.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naira.assistant.core.theme.Aqua
import com.naira.assistant.core.theme.TextHigh
import com.naira.assistant.core.theme.TextSoft
import com.naira.assistant.domain.settings.AsrProvider
import com.naira.assistant.presentation.settings.model.EditField


/* ------------------------------------------------------------------ */
/*  Dialogs                                                            */
/* ------------------------------------------------------------------ */

@Composable
internal fun ApiKeyDialog(
    title: String,
    placeholder: String,
    description: String = "The key is stored on this device for your signed-in account.",
    initialKey: String,
    onSave: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    var key by remember(title) { mutableStateOf(initialKey) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RowSurface,
        title = { Text(title, color = TextHigh, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(description, color = TextSoft, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    placeholder = { Text(placeholder, color = TextSoft.copy(alpha = 0.5f)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(key) }) {
                Text("Save", color = Aqua, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            Row {
                if (initialKey.isNotBlank()) {
                    TextButton(onClick = onRemove) {
                        Text("Remove", color = Danger)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel", color = TextSoft) }
            }
        }
    )
}

@Composable
internal fun AsrApiKeyDialog(
    provider: AsrProvider,
    hasSavedKey: Boolean,
    onSave: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var key by remember(provider) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RowSurface,
        title = {
            Text(
                "${provider.displayName} API Key",
                color = TextHigh,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column {
                Text(
                    "Stored encrypted on this device and never synced. Enter a new key to replace the saved key.",
                    color = TextSoft,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    placeholder = { Text("Enter API key", color = TextSoft.copy(alpha = 0.5f)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(key) },
                enabled = key.isNotBlank(),
            ) {
                Text("Save", color = Aqua, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            Row {
                if (hasSavedKey) {
                    TextButton(onClick = onRemove) {
                        Text("Remove", color = Danger)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextSoft)
                }
            }
        },
    )
}

@Composable
internal fun EditFieldDialog(
    field: EditField,
    initialValue: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember(field) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RowSurface,
        title = { Text(field.title, color = TextHigh, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(field.description, color = TextSoft, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    placeholder = { Text(field.placeholder, color = TextSoft.copy(alpha = 0.5f)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (field.isPhoneNumber) KeyboardType.Phone else KeyboardType.Text
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(value) },
                enabled = !field.requiresNonBlank || value.isNotBlank(),
            ) {
                Text("Save", color = Aqua, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSoft) }
        }
    )
}
