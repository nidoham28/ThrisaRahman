package com.naira.assistant.presentation.main.pages

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.naira.assistant.core.theme.Aqua
import com.naira.assistant.core.theme.CardStroke
import com.naira.assistant.core.theme.ErrorSoft
import com.naira.assistant.core.theme.FieldSurface
import com.naira.assistant.core.theme.Night
import com.naira.assistant.core.theme.OnAqua
import com.naira.assistant.core.theme.SuccessAccent
import com.naira.assistant.core.theme.TextHigh
import com.naira.assistant.core.theme.TextSoft
import com.naira.assistant.presentation.main.UiMessage
import com.naira.assistant.presentation.main.UiToolCall
import kotlinx.coroutines.launch

// Chat-specific surfaces, tuned to the shared night palette.
private val UserBubble = Color(0xFF0F3D3A)
private val AssistantBubble = Color(0xFF101B2A)
private val ErrorBubble = Color(0xFF2B161A)
private val ErrorStroke = Color(0xFF5C2B30)
private val ErrorText = ErrorSoft
private val ToolChipSurface = Color(0xFF0C1523)
private val ToolSuccess = SuccessAccent

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
    onClearChat: () -> Unit,
    modifier: Modifier = Modifier,
    onStop: (() -> Unit)? = null,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var showClearChatConfirmation by rememberSaveable { mutableStateOf(false) }

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

    BoxWithConstraints(modifier.fillMaxSize().background(Night)) {
        val horizontalPadding = if (maxWidth < 360.dp) 10.dp else 16.dp
        val verticalPadding = if (maxHeight < 480.dp) 8.dp else 16.dp
        Column(
            Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
                .align(Alignment.Center)
                .imePadding()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding)
        ) {
            if (messages.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { showClearChatConfirmation = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Aqua,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Clear chat", color = Aqua)
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
                            MessageBubble(
                                message = message,
                                onOpenImage = onOpenImage,
                            )
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
                    enabled = !isBusy,
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = BodySize,
                        lineHeight = BodyLineHeight
                    ),
                    placeholder = {
                        Text(
                            text = "Message Naira…",
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

    if (showClearChatConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearChatConfirmation = false },
            title = { Text("Clear this conversation?") },
            text = { Text("This removes the messages in the current chat and starts a new conversation.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearChatConfirmation = false
                        onClearChat()
                    },
                ) {
                    Text("Clear chat")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
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
                        .background(Color(0xCC0D1622)),
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
private fun MessageBubble(
    message: UiMessage,
    onOpenImage: (String) -> Unit,
) {
    // Skip empty assistant placeholders (a streamed message with no content
    // yet). User messages are guaranteed non-empty by `canSend`, so a
    // user message reaching here with no content would be a bug upstream —
    // don't hide it.
    if (!message.isUser &&
        !message.isError &&
        message.text.isBlank() &&
        message.imageUri == null &&
        message.toolCalls.isEmpty()
    ) return

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
                        ToolCallChip(call = call)
                    }
                }
                if (message.text.isNotBlank() || message.imageUri != null) {
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (message.text.isNotBlank()) {
                MessageText(
                    text = message.text,
                    color = if (message.isError) ErrorText else TextHigh,
                    isReceiver = !message.isUser,
                )
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

private data class MarkdownCodeBlock(
    val start: Int,
    val endExclusive: Int,
    val code: String,
    val language: String,
)

private data class MarkdownLine(
    val start: Int,
    val end: Int,
    val nextStart: Int,
)

private data class MarkdownFence(
    val marker: Char,
    val length: Int,
    val indent: Int,
    val info: String,
)

private val CodeLanguageLabels = mapOf(
    "java" to "Java",
    "kotlin" to "Kotlin",
    "kt" to "Kotlin",
    "kts" to "Kotlin DSL",
    "javascript" to "JavaScript",
    "js" to "JavaScript",
    "typescript" to "TypeScript",
    "ts" to "TypeScript",
    "python" to "Python",
    "py" to "Python",
    "ruby" to "Ruby",
    "rb" to "Ruby",
    "php" to "PHP",
    "r" to "R",
    "scala" to "Scala",
    "lua" to "Lua",
    "perl" to "Perl",
    "powershell" to "PowerShell",
    "ps1" to "PowerShell",
    "dart" to "Dart",
    "c" to "C",
    "cpp" to "C++",
    "c++" to "C++",
    "csharp" to "C#",
    "cs" to "C#",
    "c#" to "C#",
    "objective-c" to "Objective-C",
    "objective-c++" to "Objective-C++",
    "go" to "Go",
    "rust" to "Rust",
    "rs" to "Rust",
    "swift" to "Swift",
    "sql" to "SQL",
    "html" to "HTML",
    "css" to "CSS",
    "bash" to "Bash",
    "sh" to "Shell",
    "shell" to "Shell",
    "json" to "JSON",
    "xml" to "XML",
    "yaml" to "YAML",
    "yml" to "YAML",
    "toml" to "TOML",
    "ini" to "INI",
    "dockerfile" to "Dockerfile",
    "makefile" to "Makefile",
    "graphql" to "GraphQL",
    "protobuf" to "Protocol Buffers",
    "gradle" to "Gradle",
)

private fun displayCodeLanguage(language: String): String? {
    val key = language.trim().lowercase().substringBefore(' ')
    if (key.isBlank()) return "Code"
    if (key == "text" || key == "plaintext" || key == "markdown" || key == "md") return null
    return CodeLanguageLabels[key]
}

private fun parseMarkdownCodeBlocks(markdown: String): List<MarkdownCodeBlock> {
    val lines = mutableListOf<MarkdownLine>()
    var lineStart = 0
    while (lineStart <= markdown.length) {
        val newline = markdown.indexOf('\n', startIndex = lineStart)
        val rawEnd = if (newline == -1) markdown.length else newline
        val lineEnd = if (rawEnd > lineStart && markdown[rawEnd - 1] == '\r') rawEnd - 1 else rawEnd
        val nextStart = if (newline == -1) markdown.length else newline + 1
        lines += MarkdownLine(lineStart, lineEnd, nextStart)
        if (newline == -1) break
        lineStart = nextStart
    }

    val blocks = mutableListOf<MarkdownCodeBlock>()
    var index = 0
    while (index < lines.size) {
        val openingLine = lines[index]
        val opening = markdownFence(markdown.substring(openingLine.start, openingLine.end))
        if (opening == null ||
            (opening.marker == '`' && opening.info.contains('`'))
        ) {
            index++
            continue
        }

        val contentStart = openingLine.nextStart
        var closingIndex = index + 1
        while (closingIndex < lines.size) {
            val line = lines[closingIndex]
            val candidate = markdownFence(markdown.substring(line.start, line.end))
            if (candidate != null && candidate.marker == opening.marker &&
                candidate.length >= opening.length && candidate.info.isBlank()
            ) break
            closingIndex++
        }

        val hasClosingFence = closingIndex < lines.size
        val closingLine = lines.getOrNull(closingIndex)
        val contentEnd = closingLine?.start ?: markdown.length
        var code = markdown.substring(contentStart.coerceAtMost(contentEnd), contentEnd)
        if (hasClosingFence) {
            code = code.removeSuffix("\n").removeSuffix("\r")
        }
        if (opening.indent > 0) {
            code = code.lineSequence()
                .map { line -> line.dropWhileIndexedSpaces(opening.indent) }
                .joinToString("\n")
        }

        val language = opening.info.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
        val blockEnd = closingLine?.nextStart ?: markdown.length
        blocks += MarkdownCodeBlock(openingLine.start, blockEnd, code, language)
        index = if (hasClosingFence) closingIndex + 1 else lines.size
    }
    return blocks
}

private fun markdownFence(line: String): MarkdownFence? {
    val indent = line.takeWhile { it == ' ' }.length
    if (indent > 3 || indent == line.length) return null
    val marker = line[indent]
    if (marker != '`' && marker != '~') return null
    var fenceEnd = indent
    while (fenceEnd < line.length && line[fenceEnd] == marker) fenceEnd++
    val length = fenceEnd - indent
    if (length < 3) return null
    return MarkdownFence(marker, length, indent, line.substring(fenceEnd).trim())
}

private fun String.dropWhileIndexedSpaces(maxSpaces: Int): String {
    var spaces = 0
    while (spaces < length && spaces < maxSpaces && this[spaces] == ' ') spaces++
    return drop(spaces)
}

@Composable
private fun MessageText(text: String, color: Color, isReceiver: Boolean) {
    val blocks = remember(text, isReceiver) {
        if (isReceiver) {
            parseMarkdownCodeBlocks(text)
                .filter { displayCodeLanguage(it.language) != null }
                .toList()
        } else emptyList()
    }
    if (blocks.isEmpty()) {
        SelectionContainer {
            Text(text = text, color = color, fontSize = BodySize, lineHeight = BodyLineHeight)
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var cursor = 0
        blocks.forEach { block ->
            val before = text.substring(cursor, block.start)
            if (before.isNotBlank()) {
                SelectionContainer {
                    Text(text = before, color = color, fontSize = BodySize, lineHeight = BodyLineHeight)
                }
            }
            CodePanel(
                code = block.code,
                language = displayCodeLanguage(block.language) ?: "Code",
            )
            cursor = block.endExclusive
        }
        val trailing = text.substring(cursor)
        if (trailing.isNotBlank()) {
            SelectionContainer {
                Text(text = trailing, color = color, fontSize = BodySize, lineHeight = BodyLineHeight)
            }
        }
    }
}

@Composable
private fun CodePanel(
    code: String,
    language: String,
) {
    val context = LocalContext.current
    val horizontalState = rememberScrollState()
    val verticalState = rememberScrollState()
    val lineCount = remember(code) { code.count { it == '\n' } + 1 }
    val lineNumbers = remember(code, lineCount) {
        (1..lineCount).joinToString("\n") { it.toString().padStart(lineCount.toString().length) }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ToolChipSurface)
            .border(1.dp, CardStroke, RoundedCornerShape(12.dp))
            .padding(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = language,
                    color = Aqua,
                    fontSize = CaptionSize,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Aqua.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Text("$lineCount lines", color = TextSoft, fontSize = 10.sp, maxLines = 1)
            }
            IconButton(
                onClick = { copyText(context, code, language) },
                modifier = Modifier.size(MinTouchTarget),
            ) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = "Copy code",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .verticalScroll(verticalState),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = lineNumbers,
                    color = TextSoft.copy(alpha = 0.7f),
                    fontSize = CaptionSize,
                    lineHeight = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                    softWrap = false,
                    modifier = Modifier.padding(end = 10.dp),
                )
                SelectionContainer {
                    Text(
                        text = code,
                        color = TextHigh,
                        fontSize = CaptionSize,
                        lineHeight = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        softWrap = false,
                        modifier = Modifier.horizontalScroll(horizontalState),
                    )
                }
            }
        }
    }
}

private fun copyText(context: Context, text: String, label: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
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
            .animateContentSize()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 8.dp)
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
            lineHeight = 16.sp,
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
    if (hasText) Spacer(Modifier.height(10.dp))

    // minHeight keeps the bubble from collapsing to 0 while the image loads.
    // Tapping always opens the in-app viewer: external viewer apps crash on
    // `file://` cache URIs (FileUriExposedException) and cannot read them.
    AsyncImage(
        model = imageUri.toUri(),
        contentDescription = if (isUser) "Attached photo" else "Generated image",
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp, max = 380.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(FieldSurface)
            .clickable(
                role = Role.Button,
                onClickLabel = "Open image",
                onClick = { onOpenImage(imageUri) },
            ),
    )

    val caption = when {
        savedTo != null -> "Saved to $savedTo · tap to view"
        else -> "Tap to view"
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = caption,
        color = TextSoft,
        fontSize = CaptionSize,
        lineHeight = 16.sp
    )
}
