@file:Suppress("SpellCheckingInspection")

package com.naira.assistant.presentation.settings.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import com.naira.assistant.domain.settings.EmailContact
import com.naira.assistant.domain.settings.isValidEmail
import com.naira.assistant.domain.settings.validateEmailContactInput
import com.naira.assistant.presentation.settings.model.EditField


/* ------------------------------------------------------------------ */
/*  Dialogs                                                            */
/* ------------------------------------------------------------------ */

@Composable
internal fun ApiKeyDialog(
    title: String,
    placeholder: String,
    description: String = "The key is saved on this device and synced to Firebase for your account.",
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
internal fun EditFieldDialog(
    field: EditField,
    initialValue: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember(field) { mutableStateOf(initialValue) }
    val validationError = if (field.isEmail && value.isNotBlank() && !isValidEmail(value)) {
        "Enter a valid email address."
    } else null
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
                        keyboardType = when {
                            field.isPhoneNumber -> KeyboardType.Phone
                            field.isEmail -> KeyboardType.Email
                            else -> KeyboardType.Text
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                validationError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Danger, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(value) },
                enabled = (!field.requiresNonBlank || value.isNotBlank()) && validationError == null,
            ) {
                Text("Save", color = Aqua, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSoft) }
        }
    )
}

@Composable
internal fun EmailContactDialog(
    initialContact: EmailContact?,
    onSave: (name: String, email: String, replacingEmail: String?) -> String?,
    onDelete: (email: String) -> String?,
    onDismiss: () -> Unit,
) {
    var name by remember(initialContact) { mutableStateOf(initialContact?.name.orEmpty()) }
    var email by remember(initialContact) { mutableStateOf(initialContact?.email.orEmpty()) }
    var errorMessage by remember(initialContact) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RowSurface,
        title = {
            Text(
                if (initialContact == null) "Add email contact" else "Edit email contact",
                color = TextHigh,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column {
                Text(
                    "Add the contact name and email address so the assistant can identify the owner.",
                    color = TextSoft,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMessage = null },
                    singleLine = true,
                    label = { Text("Contact name") },
                    placeholder = { Text("e.g. Alex Morgan") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMessage = null },
                    singleLine = true,
                    label = { Text("Email address") },
                    placeholder = { Text("name@example.com") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                errorMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Danger, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val validationError = validateEmailContactInput(name, email)
                    if (validationError != null) {
                        errorMessage = validationError
                    } else {
                        val saveError = onSave(name.trim(), email.trim(), initialContact?.email)
                        if (saveError == null) onDismiss() else errorMessage = saveError
                    }
                },
                enabled = name.isNotBlank() && email.isNotBlank(),
            ) {
                Text("Save", color = Aqua, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            Row {
                if (initialContact != null) {
                    TextButton(
                        onClick = {
                            val deleteError = onDelete(initialContact.email)
                            if (deleteError == null) onDismiss() else errorMessage = deleteError
                        },
                    ) {
                        Text("Remove", color = Danger)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel", color = TextSoft) }
            }
        },
    )
}
