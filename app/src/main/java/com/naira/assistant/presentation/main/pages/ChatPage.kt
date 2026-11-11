package com.naira.assistant.presentation.main.pages

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.naira.assistant.core.theme.Aqua
import com.naira.assistant.core.theme.CardStroke
import com.naira.assistant.core.theme.Night
import com.naira.assistant.core.theme.OnAqua
import com.naira.assistant.core.theme.TextHigh
import com.naira.assistant.core.theme.TextSoft
import com.naira.assistant.presentation.main.UiMessage
import com.naira.assistant.presentation.main.UiToolCall
import kotlinx.coroutines.launch

// Local surface colors
private val FieldSurface = Color(0xFF0D1520)
private val UserBubble = Color(0xFF123C3B)
private val AssistantBubble = Color(0xFF0E1621)
private val ErrorBubble = Color(0xFF2A1518)
private val ErrorStroke = Color(0xFF5A2A2E)
private val ErrorText = Color(0xFFFF9F9F)
private val ToolChipSurface = Color(0xFF0B1219)
private val ToolSuccess = Color(0xFF4ADE80)

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

/**
 * True when [uri] can be handed to an external viewer app.
 *
 * Accepts `content://` (Photo Picker / SAF) and `file://` (saved images).
 * `http(s)://` is deliberately excluded — the receiving app would need a
 * network permission and the tap-to-open flow is meant for local attachments.
 */
private fun isOpenableImageUri(uri: String): Boolean =
    uri.startsWith("content://") || uri.startsWith("file://")

@Composable
fun ChatPage(
    messages: List<UiMessage>,
    busyStatus: String?,
    onSend: (String, String?) -> Unit,
    onOpenImage: (String) -> Unit,
    modifier: Modifier = Modifier,
    onStop: (() -> Unit)? = null,
) {
    var draft by rememberSaveable { mutableStateOf("") }

    // `remember` (not `rememberSaveable`): the URI string itself survives
    // process death, but the transient `content://` grant that
    // `PickVisualMedia` issues does NOT — the picker grants the permission
    // for the activity's lifetime, not persistably (Photo Picker has no
    // persistable-grant path). Restoring the string across process death
    // would render a broken thumbnail and fail on send. `remember` correctly
    // drops the attachment after process death while surviving rotation
    // (config changes are recompositions, not restarts).
    var attachedImage by remember { mutableStateOf<String?>(null) }
    val isBusy = busyStatus != null
    val canSend = (draft.isNotBlank() || attachedImage != null) && !isBusy

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

    // Length of the newest message's text. Changes on every streamed chunk,
    // so the auto-scroll effect below re-fires while the assistant types.
    // Without this, streaming text grows *upward* in reverse layout and the
    // user only ever sees the first few lines.
    val latestTextLength = messages.lastOrNull()?.text?.length ?: 0
    val latestToolCount = messages.lastOrNull()?.toolCalls?.size ?: 0

    // Sticky auto-scroll. Fires on:
    //  - new message appended (lastMessageId)
    //  - busy indicator toggled (isBusy)
    //  - streamed text chunk (latestTextLength)
    //  - tool call added on the newest message (latestToolCount)
    // Skips if the user has scrolled up past the two newest items, *unless*
    // the newest message is the user's own send (they expect to land there).
    LaunchedEffect(lastMessageId, isBusy, latestTextLength, latestToolCount) {
        val last = messages.lastOrNull() ?: return@LaunchedEffect
        if (listState.firstVisibleItemIndex <= 1 || last.isUser) {
            // scrollToItem (not animateScrollToItem): during streaming this
            // fires per character; animating would jitter and fight input.
            // When already at index 0, it's a no-op.
            listState.scrollToItem(0)
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

                if (showJumpToLatest) {
                    JumpToLatestPill(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp),
                        onClick = { scope.launch { listState.animateScrollToItem(0) } }
                    )
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
                        fontSize = BodySize,
                        lineHeight = BodyLineHeight,
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
private fun JumpToLatestPill(
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(FieldSurface)
            .border(1.dp, CardStroke, RoundedCornerShape(20.dp))
            .clickable(
                role = Role.Button,
                onClickLabel = "Jump to latest message",
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.ArrowDownward,
            contentDescription = null, // text below names the action
            tint = Aqua,
            modifier = Modifier.size(14.dp)
        )
        Text("Latest", color = TextHigh, fontSize = CaptionSize)
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
    // Bottom padding aligns the fixed-height button with the multi-line
    // OutlinedTextField's text baseline (the field has ~4 dp of internal
    // bottom padding). Removing it makes the buttons sit visually low.
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
                imageVector = icon,
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
                // Decorative: the caption below already says "Photo attached".
                contentDescription = null,
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
                    // Use semantics + clickable instead of onClickLabel alone:
                    // onClickLabel *supplements* a name but doesn't provide
                    // one on all TalkBack versions. `contentDescription`
                    // guarantees a stable name.
                    .semantics { contentDescription = "Remove attachment" }
                    .clickable(role = Role.Button, onClick = onRemove),
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
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null, // parent Box carries the name
                        tint = TextHigh,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Text(
            text = "Photo attached · ask anything about it",
            color = TextSoft,
            fontSize = SecondarySize,
        )
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
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = Aqua,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "What can I help you create?",
            color = TextHigh,
            fontSize = TitleSize,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Ask a question, generate an image, or attach a photo to talk about it.",
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
                SuggestionRow(suggestion = suggestion, onClick = { onSuggestion(suggestion) })
            }
        }
    }
}

@Composable
private fun SuggestionRow(suggestion: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clip(RoundedCornerShape(16.dp))
            .background(FieldSurface)
            .border(1.dp, CardStroke, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = Aqua,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = suggestion,
            color = TextHigh,
            fontSize = SecondarySize,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun MessageBubble(message: UiMessage, onOpenImage: (String) -> Unit) {
    // Skip empty assistant placeholders (a streamed message with no content
    // yet). User messages are guaranteed non-empty by `canSend`, so a
    // user message reaching here with no content would be a bug upstream —
    // don't hide it.
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
            if (message.toolCalls.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    message.toolCalls.forEach { call ->
                        ToolCallChip(call)
                    }
                }
                if (message.text.isNotBlank() || message.imageUri != null) {
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (message.text.isNotBlank()) {
                SelectionContainer {
                    Text(
                        text = message.text,
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

/**
 * Compact, tappable summary of one tool call inside an assistant bubble:
 * friendly tool name + live status, expanding to the raw arguments and result.
 * Expansion state survives scroll disposal because list items are keyed by
 * message id.
 */
@Composable
private fun ToolCallChip(call: UiToolCall) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ToolChipSurface)
            .border(1.dp, CardStroke, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button) { expanded = !expanded }
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .animateContentSize()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Build,
                contentDescription = null, // the tool name text names the row
                tint = Aqua,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = call.name.friendlyToolName(),
                color = TextHigh,
                fontSize = SecondarySize,
                modifier = Modifier.weight(1f)
            )
            when {
                call.isRunning -> CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp,
                    color = Aqua,
                    trackColor = CardStroke
                )
                call.isError -> Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Failed",
                    tint = ErrorText,
                    modifier = Modifier.size(14.dp)
                )
                else -> Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "Done",
                    tint = ToolSuccess,
                    modifier = Modifier.size(14.dp)
                )
            }
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null, // row is named by the tool name; state via expand
                tint = TextSoft,
                modifier = Modifier
                    .size(14.dp)
                    .rotate(if (expanded) 180f else 0f)
            )
        }
        if (expanded) {
            if (call.input.isNotBlank() && call.input != "{}") {
                ToolCallDetail(label = "Arguments", value = call.input)
            }
            if (call.result.isNotBlank()) {
                ToolCallDetail(
                    label = if (call.isError) "Error" else "Result",
                    value = call.result,
                    isError = call.isError,
                )
            }
        }
    }
}

@Composable
private fun ToolCallDetail(label: String, value: String, isError: Boolean = false) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(label, color = TextSoft, fontSize = CaptionSize)
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            color = if (isError) ErrorText else TextSoft,
            fontSize = CaptionSize,
            lineHeight = 16.sp
        )
    }
}

/** "set_timer" → "Set Timer"; graceful for unknown names. */
private fun String.friendlyToolName(): String =
    split('_')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
        .ifBlank { "Tool" }

@Composable
private fun MessageImage(
    imageUri: String,
    isUser: Boolean,
    savedTo: String?,
    hasText: Boolean,
    onOpenImage: (String) -> Unit,
) {
    val canOpen = isOpenableImageUri(imageUri)
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
            .then(
                if (canOpen) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = "Open image",
                        onClick = { onOpenImage(imageUri) },
                    )
                } else Modifier
            )
    )

    val caption = when {
        savedTo != null -> "Saved to $savedTo" + if (canOpen) " · tap to open" else ""
        canOpen -> "Tap to open"
        else -> null
    }
    caption?.let {
        Spacer(Modifier.height(8.dp))
        Text(
            text = it,
            color = TextSoft,
            fontSize = CaptionSize,
            lineHeight = 16.sp
        )
    }
}