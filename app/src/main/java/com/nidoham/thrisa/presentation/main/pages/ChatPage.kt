package com.nidoham.thrisa.presentation.main.pages

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.nidoham.thrisa.core.theme.Aqua
import com.nidoham.thrisa.core.theme.CardStroke
import com.nidoham.thrisa.core.theme.Night
import com.nidoham.thrisa.core.theme.OnAqua
import com.nidoham.thrisa.core.theme.TextHigh
import com.nidoham.thrisa.core.theme.TextSoft
import com.nidoham.thrisa.domain.AssistantMode
import com.nidoham.thrisa.presentation.main.UiMessage
import kotlinx.coroutines.launch

// Local surface colors
private val FieldSurface = Color(0xFF0D1520)
private val UserBubble = Color(0xFF123C3B)
private val AssistantBubble = Color(0xFF0E1621)
private val ErrorBubble = Color(0xFF2A1518)
private val ErrorStroke = Color(0xFF5A2A2E)
private val ErrorText = Color(0xFFFF9F9F)

// Type scale
private val TitleSize = 20.sp
private val BodySize = 15.sp
private val BodyLineHeight = 22.sp
private val SecondarySize = 14.sp
private val CaptionSize = 12.sp

// Layout constants
private val MinTouchTarget = 48.dp
private val MaxBubbleWidth = 560.dp

private val Suggestions = listOf(
    "Generate a cozy watercolor café at sunset",
    "Help me plan a productive day",
    "Explain something complicated in simple words",
)

@Composable
fun ChatPage(
    messages: List<UiMessage>,
    busyStatus: String?,
    onSend: (String, String?) -> Unit,
    onOpenImage: (String) -> Unit,
    modifier: Modifier = Modifier,
    onStop: (() -> Unit)? = null,
    assistantMode: AssistantMode = AssistantMode.NORMAL,
    reduceSensitiveContent: Boolean = true,
    onSelectAssistantMode: (AssistantMode) -> Unit = {},
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var attachedImage by rememberSaveable { mutableStateOf<String?>(null) }
    val isBusy = busyStatus != null
    val canSend = (draft.isNotBlank() || attachedImage != null) && !isBusy
    var modeMenuExpanded by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { attachedImage = it.toString() }
    }

    val listState = rememberLazyListState()
    val reversed = remember(messages) { messages.asReversed() }
    val scope = rememberCoroutineScope()

    // Floating "Latest" pill appears only after the user scrolls up a bit.
    val showJumpToLatest by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 2 }
    }

    val lastMessageId = messages.lastOrNull()?.id

    // Sticky auto-scroll: follow the conversation when the user is near the
    // latest message, and ALWAYS when the newest message is their own send.
    LaunchedEffect(lastMessageId, isBusy) {
        val last = messages.lastOrNull() ?: return@LaunchedEffect
        if (listState.firstVisibleItemIndex <= 1 || last.isUser) {
            listState.animateScrollToItem(0)
        }
    }

    fun sendDraft() {
        if (!canSend) return
        onSend(draft.trim(), attachedImage)
        draft = ""
        attachedImage = null
    }

    Column(
        modifier
            .fillMaxSize()
            .background(Night)
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Assistant mode", color = TextSoft, fontSize = CaptionSize)
            Box {
                TextButton(
                    onClick = { modeMenuExpanded = true },
                    enabled = !isBusy,
                ) {
                    Text(
                        if (assistantMode == AssistantMode.NORMAL) "Normal ▾" else "18+ Companion ▾",
                        color = if (assistantMode == AssistantMode.NORMAL) Aqua else Color(0xFFFF9F9F),
                    )
                }
                DropdownMenu(
                    expanded = modeMenuExpanded,
                    onDismissRequest = { modeMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Normal") },
                        onClick = {
                            onSelectAssistantMode(AssistantMode.NORMAL)
                            modeMenuExpanded = false
                        },
                    )
                    if (!reduceSensitiveContent) {
                        DropdownMenuItem(
                            text = { Text("18+ Companion") },
                            onClick = {
                                modeMenuExpanded = false
                                onSelectAssistantMode(AssistantMode.ADULT_ROLEPLAY)
                            },
                        )
                    }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) {
                EmptyChatState(
                    onSuggestion = { draft = it },
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (busyStatus != null) {
                        item(key = "busy-status") { BusyStatus(busyStatus) }
                    }
                    items(reversed, key = { it.id }) { message ->
                        MessageBubble(message, onOpenImage)
                    }
                }

                // One-tap jump back to the newest message.
                if (showJumpToLatest) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(FieldSurface)
                            .border(1.dp, CardStroke, RoundedCornerShape(20.dp))
                            .clickable(role = Role.Button) {
                                scope.launch { listState.animateScrollToItem(0) }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.ArrowDownward,
                            contentDescription = null,
                            tint = Aqua,
                            modifier = Modifier.size(14.dp)
                        )
                        Text("Latest", color = TextHigh, fontSize = CaptionSize)
                    }
                }

            }
        }

        Spacer(Modifier.height(10.dp))

        attachedImage?.let { uri ->
            AttachmentPreview(uri = uri, onRemove = { attachedImage = null })
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ComposerButton(
                icon = Icons.Outlined.AddPhotoAlternate,
                description = "Attach an image",
                enabled = !isBusy,
                onClick = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            )
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = BodySize,
                    lineHeight = BodyLineHeight
                ),
                placeholder = {
                    Text(
                        text = "Ask me anything?",
                        color = TextSoft,
                        fontSize = BodySize
                    )
                },
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(onSend = { sendDraft() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextHigh,
                    unfocusedTextColor = TextHigh,
                    focusedBorderColor = Aqua,
                    unfocusedBorderColor = CardStroke,
                    cursorColor = Aqua,
                    focusedContainerColor = FieldSurface,
                    unfocusedContainerColor = FieldSurface,
                ),
                maxLines = 4,
            )
            if (isBusy && onStop != null) {
                ComposerButton(
                    icon = Icons.Outlined.Stop,
                    description = "Stop generating",
                    filled = true,
                    onClick = onStop,
                )
            } else {
                ComposerButton(
                    icon = Icons.AutoMirrored.Outlined.Send,
                    description = "Send message",
                    enabled = canSend,
                    filled = true,
                    onClick = { sendDraft() },
                )
            }
        }

    }
}

@Composable
private fun ComposerButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
) {
    val container = when {
        !enabled -> CardStroke
        filled -> Aqua
        else -> FieldSurface
    }
    val tint = when {
        !enabled -> TextSoft
        filled -> OnAqua
        else -> Aqua
    }
    Box(modifier.padding(bottom = 4.dp)) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(MinTouchTarget)
                .clip(RoundedCornerShape(16.dp))
                .background(container)
        ) {
            Icon(
                icon,
                contentDescription = description,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun BusyStatus(status: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = Aqua,
            trackColor = CardStroke
        )
        Text(status, color = TextSoft, fontSize = SecondarySize)
    }
}

@Composable
private fun AttachmentPreview(uri: String, onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(bottom = 10.dp)
    ) {
        Box(Modifier.size(72.dp)) {
            AsyncImage(
                model = uri.toUri(),
                contentDescription = "Attached image",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(FieldSurface)
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Remove attachment",
                        onClick = onRemove
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xCC0D1520)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = null,
                        tint = TextHigh,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Text("Photo attached · ask anything about it", color = TextSoft, fontSize = SecondarySize)
    }
}

@Composable
private fun EmptyChatState(onSuggestion: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = Aqua,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "What can I help you create?",
            color = TextHigh,
            fontSize = TitleSize,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Ask a question, generate an image, or attach a photo to talk about it.",
            color = TextSoft,
            fontSize = SecondarySize,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.widthIn(max = 420.dp)
        ) {
            Suggestions.forEach { suggestion ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = MinTouchTarget)
                        .clip(RoundedCornerShape(16.dp))
                        .background(FieldSurface)
                        .border(1.dp, CardStroke, RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button) { onSuggestion(suggestion) }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = Aqua,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        suggestion,
                        color = TextHigh,
                        fontSize = SecondarySize,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: UiMessage, onOpenImage: (String) -> Unit) {
    if (!message.isUser && !message.isError && message.text.isBlank() && message.imageUri == null) return

    val isUser = message.isUser
    val shape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = if (isUser) 20.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 20.dp
    )
    val background = when {
        message.isError -> ErrorBubble
        isUser -> UserBubble
        else -> AssistantBubble
    }

    BoxWithConstraints(
        Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            Modifier
                .widthIn(max = minOf(maxWidth * 0.88f, MaxBubbleWidth))
                .clip(shape)
                .background(background)
                .then(if (message.isError) Modifier.border(1.dp, ErrorStroke, shape) else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (message.text.isNotBlank()) {
                SelectionContainer {
                    Text(
                        message.text,
                        color = if (message.isError) ErrorText else TextHigh,
                        fontSize = BodySize,
                        lineHeight = BodyLineHeight
                    )
                }
            }
            message.imageUri?.let { imageUri ->
                MessageImage(
                    imageUri = imageUri,
                    isUser = isUser,
                    savedTo = message.imageSavedTo,
                    hasText = message.text.isNotBlank(),
                    onOpenImage = onOpenImage
                )
            }
        }
    }
}

@Composable
private fun MessageImage(
    imageUri: String,
    isUser: Boolean,
    savedTo: String?,
    hasText: Boolean,
    onOpenImage: (String) -> Unit,
) {
    val canOpen = imageUri.startsWith("content://")
    if (hasText) Spacer(Modifier.height(10.dp))

    // minHeight keeps the bubble from collapsing to 0 while the image loads.
    AsyncImage(
        model = imageUri.toUri(),
        contentDescription = if (isUser) "Attached photo" else "Generated image",
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp, max = 380.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(FieldSurface)
            .then(if (canOpen) Modifier.clickable { onOpenImage(imageUri) } else Modifier)
    )

    val caption = when {
        savedTo != null -> "Saved to $savedTo" + if (canOpen) " · tap to open" else ""
        canOpen -> "Tap to open"
        else -> null
    }
    caption?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = TextSoft, fontSize = CaptionSize, lineHeight = 16.sp)
    }
}