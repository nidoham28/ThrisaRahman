package presentation.main.pages

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nidoham.thrisa.core.theme.Aqua
import com.nidoham.thrisa.core.theme.CardStroke
import com.nidoham.thrisa.core.theme.Night
import com.nidoham.thrisa.core.theme.OnAqua
import com.nidoham.thrisa.core.theme.TextHigh
import com.nidoham.thrisa.core.theme.TextSoft
import com.nidoham.thrisa.presentation.main.UiMessage

@Composable
fun ChatPage(
    messages: List<UiMessage>,
    busyStatus: String?,
    onSend: (String, String?) -> Unit,
    onOpenImage: (String) -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    var attachedImage by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, messages.lastOrNull()?.imageUri, busyStatus) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    // System photo picker — no storage permission needed on any API level.
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        attachedImage = uri?.toString()
    }
    val canSend = (draft.isNotBlank() || attachedImage != null) && busyStatus == null

    Column(
        Modifier.fillMaxSize().background(Night).padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text("Conversation", color = TextHigh, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Chat, attach a photo to ask about or restyle it, or create images.",
            color = TextSoft, fontSize = 13.sp
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    EmptyChatState()
                }
            }
            items(messages, key = { it.id }) { message ->
                MessageBubble(message, onOpenImage)
            }
            if (busyStatus != null) {
                item(key = "busy-status") {
                    Text(busyStatus, color = TextSoft, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp))
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        attachedImage?.let { imageUri ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Box {
                    AsyncImage(
                        model = Uri.parse(imageUri),
                        contentDescription = "Attached image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0D1520))
                    )
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(2.dp)
                            .size(20.dp).clip(RoundedCornerShape(10.dp))
                            .background(Color(0xCC0D1520))
                            .clickable { attachedImage = null },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "Remove attachment",
                            tint = TextHigh, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text("Attached · ask anything about it", color = TextSoft, fontSize = 12.sp)
            }
        }

        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                enabled = busyStatus == null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (busyStatus == null) Color(0xFF0D1520) else CardStroke)
            ) {
                Icon(
                    Icons.Outlined.AddPhotoAlternate,
                    contentDescription = "Attach an image",
                    tint = if (busyStatus == null) Aqua else TextSoft
                )
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        if (attachedImage != null) "Ask about the photo (optional)…"
                        else "Message or describe an image…",
                        color = TextSoft
                    )
                },
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextHigh,
                    unfocusedTextColor = TextHigh,
                    focusedBorderColor = Aqua,
                    unfocusedBorderColor = CardStroke,
                    cursorColor = Aqua,
                    focusedContainerColor = Color(0xFF0D1520),
                    unfocusedContainerColor = Color(0xFF0D1520),
                ),
                maxLines = 4,
            )
            IconButton(
                onClick = {
                    if (canSend) {
                        onSend(draft.trim(), attachedImage)
                        draft = ""
                        attachedImage = null
                    }
                },
                enabled = canSend,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (canSend) Aqua else CardStroke)
            ) {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send message",
                    tint = if (canSend) OnAqua else TextSoft)
            }
        }
    }
}

@Composable
private fun EmptyChatState() {
    Box(Modifier.fillMaxWidth().padding(vertical = 42.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Aqua, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(12.dp))
            Text("What can I help you create?", color = TextHigh, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(
                "Try: “Generate a cozy watercolor café at sunset.” — or attach a photo to talk about it.",
                color = TextSoft, fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun MessageBubble(message: UiMessage, onOpenImage: (String) -> Unit) {
    val bubbleColor = if (message.isUser) Color(0xFF123C3B) else Color(0xFF0E1621)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            Modifier.fillMaxWidth(0.88f).clip(RoundedCornerShape(18.dp)).background(bubbleColor)
                .padding(horizontal = 14.dp, vertical = 11.dp)
        ) {
            if (message.text.isNotBlank()) {
                Text(message.text, color = if (message.isError) Color(0xFFFF9F9F) else TextHigh, fontSize = 14.sp, lineHeight = 20.sp)
            }
            message.imageUri?.let { imageUri ->
                val canOpenExternally = imageUri.startsWith("content://")
                if (message.text.isNotBlank()) Spacer(Modifier.height(9.dp))
                AsyncImage(
                    model = Uri.parse(imageUri),
                    contentDescription = if (message.isUser) "Attached photo" else "Generated image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .then(if (canOpenExternally) Modifier.clickable { onOpenImage(imageUri) } else Modifier)
                )
                // Only generated images carry a save location; attached photos
                // just show inline (tap to open when the system can resolve them).
                if (message.imageSavedTo != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Saved to ${message.imageSavedTo ?: "Pictures/Thrisa"}" + if (canOpenExternally) " · tap to open" else "",
                        color = TextSoft,
                        fontSize = 11.sp
                    )
                } else if (canOpenExternally) {
                    Spacer(Modifier.height(6.dp))
                    Text("Tap to open", color = TextSoft, fontSize = 11.sp)
                }
            }
        }
    }
}
