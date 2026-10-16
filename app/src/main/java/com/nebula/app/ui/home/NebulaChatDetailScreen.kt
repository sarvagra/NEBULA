package com.nebula.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Done
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import com.nebula.app.domain.model.ChatMessage
import com.nebula.app.domain.model.MessageRole
import com.nebula.app.domain.model.NebulaConversation
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.NebulaAvatarImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent

/**
 * NebulaChatDetailScreen:
 * Clean, production-ready social conversation screen.
 * Messages occupy the natural content area with zero artificial voids.
 * Composer is securely anchored above the soft keyboard with zero fake AI buttons.
 */
@Composable
fun NebulaChatDetailScreen(
    conversation: NebulaConversation,
    showInlineSuggestions: Boolean,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSendAttachment: (text: String, mediaUrl: String, messageType: String, isSpoiler: Boolean, mimeType: String) -> Unit,
    onEditMessage: (messageId: String, text: String) -> Unit,
    onUnsendMessage: (messageId: String) -> Unit,
    onOpenProfile: () -> Unit,
    onInviteToRoom: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var inputText by remember { mutableStateOf("") }
    val composerFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    // Editing re-uses the composer instead of spawning a dialog: the text comes
    // back, the send button becomes an edit action, and sending rewrites the
    // existing bubble rather than adding a new one.
    androidx.compose.runtime.LaunchedEffect(editingMessage?.id) {
        val editing = editingMessage ?: return@LaunchedEffect
        inputText = editing.content
        composerFocus.requestFocus()
    }
    var showAttachments by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(conversation.messages.size) {
        if (conversation.messages.isNotEmpty()) {
            listState.animateScrollToItem(conversation.messages.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.colors.background)
            .statusBarsPadding()
            .imePadding()
    ) {
        // 1. TOP HEADER: Back, Avatar, Name, Online Status, Room Invite Shortcut
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = theme.colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenProfile() }
                        .padding(end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(theme.colors.surfaceElevated)
                        .border(1.dp, theme.colors.surfaceBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    NebulaAvatarImage(conversation.partnerUser?.avatarUrl, conversation.partnerUser?.avatarLetter ?: "G", modifier = Modifier.size(38.dp))
                }

                Column {
                    Text(
                        text = conversation.title,
                        style = theme.typography.bodyLarge.copy(
                            color = theme.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    )

                    val statusText = if (conversation.isGroup) "${conversation.participants.size} members" else conversation.partnerUser?.status?.ifBlank { "Offline" } ?: "Offline"

                    Text(
                        text = statusText,
                        style = theme.typography.caption.copy(
                            color = if (conversation.partnerUser?.isOnline == true) theme.colors.successColor else theme.colors.textTertiary,
                            fontSize = 11.5.sp
                        )
                    )
                }
                }
            }

            // Room invite shortcut
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.colors.surface)
                    .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(8.dp))
                    .clickable { onInviteToRoom(conversation.id) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = "Invite to Room",
                        tint = theme.colors.primaryAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Invite",
                        style = theme.typography.caption.copy(
                            color = theme.colors.primaryAccent,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp
                        )
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(theme.colors.surfaceBorder)
        )

        // 2. MESSAGES STREAM OR CLEAN SOCIAL EMPTY STATE
        if (conversation.messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(theme.colors.surfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        NebulaAvatarImage(conversation.partnerUser?.avatarUrl, conversation.partnerUser?.avatarLetter ?: "N", modifier = Modifier.size(64.dp))
                    }
                    Text(
                        text = "Say hello to ${conversation.title}!",
                        style = theme.typography.titleMedium.copy(
                            color = theme.colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Send a message or invite them to a live room.",
                        style = theme.typography.bodySmall.copy(
                            color = theme.colors.textTertiary,
                            fontSize = 13.sp
                        )
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 14.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val messages = conversation.messages
                val lastOwnMessageId = messages.lastOrNull {
                    it.role == MessageRole.USER && it.deletedAt == null
                }?.id
                itemsIndexed(messages, key = { _, m -> m.id }) { index, message ->
                    val previous = messages.getOrNull(index - 1)
                    val next = messages.getOrNull(index + 1)
                    // Consecutive messages from the same sender form one visual
                    // group: the avatar appears once, on the first bubble.
                    val startsGroup = previous?.senderKeyOrNull() != message.senderKeyOrNull()
                    val endsGroup = next?.senderKeyOrNull() != message.senderKeyOrNull()
                    val showDateSeparator = previous == null || !previous.isSameDayAs(message)

                    Column {
                        if (showDateSeparator) {
                            DateSeparator(label = message.dateLabel())
                        }
                        ModernChatBubble(
                            message = message,
                            showAvatar = startsGroup,
                            isGroupedWithPrevious = !startsGroup,
                            isGroupedWithNext = !endsGroup,
                            // Receipts belong to the newest message the user sent only.
                            showsReceipt = message.id == lastOwnMessageId,
                            onEdit = { editingMessage = message },
                            onUnsend = { onUnsendMessage(message.id) }
                        )
                    }
                }
            }
        }

        // 3. QUICK ATTACHMENTS (Visible when + is tapped)
        val context = androidx.compose.ui.platform.LocalContext.current
        val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
        var isUploadingMedia by remember { mutableStateOf(false) }
        var uploadProgress by remember { mutableIntStateOf(0) }
        var attachmentIsSpoiler by remember { mutableStateOf(false) }

        val imagePickerLauncher = rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
        ) { uri: android.net.Uri? ->
            uri?.let {
                coroutineScope.launch {
                    val mimeType = context.contentResolver.getType(it)?.lowercase().orEmpty()
                    val size = withContext(Dispatchers.IO) {
                        context.contentResolver.openFileDescriptor(it, "r")?.use { fd -> fd.statSize } ?: 0L
                    }
                    if (size > 5 * 1024 * 1024L) {
                        android.widget.Toast.makeText(context, "Image must be under 5MB", android.widget.Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    if (mimeType == "image/gif") {
                        val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(it)?.use { stream -> stream.readBytes() } ?: ByteArray(0) }
                        if (bytes.isEmpty() || bytes.size > 5 * 1024 * 1024) {
                            android.widget.Toast.makeText(context, "GIF must be under 5MB", android.widget.Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        isUploadingMedia = true
                        try {
                            val dataUrl = "data:image/gif;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                            when (val res = com.nebula.app.data.backend.NebulaApiClient.uploadChatMedia(dataUrl, "image/gif", "gif", bytes.size.toLong()) { uploadProgress = it }) {
                                is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Success -> {
                                    val mediaUrl = res.data.optJSONObject("data")?.optString("mediaUrl").orEmpty().ifBlank { res.data.optString("mediaUrl") }
                                    if (mediaUrl.isNotBlank()) onSendAttachment("", mediaUrl, "media", attachmentIsSpoiler, "image/gif")
                                    else android.widget.Toast.makeText(context, "GIF upload returned no saved file", android.widget.Toast.LENGTH_LONG).show()
                                }
                                is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Error -> android.widget.Toast.makeText(context, res.message, android.widget.Toast.LENGTH_LONG).show()
                            }
                        } finally {
                            isUploadingMedia = false
                            showAttachments = false
                        }
                        return@launch
                    }
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(it)?.use { stream -> stream.readBytes() } ?: ByteArray(0)
                    }
                    if (bytes.isEmpty() || bytes.size > 5 * 1024 * 1024) {
                        android.widget.Toast.makeText(context, "Image must be under 5MB", android.widget.Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    isUploadingMedia = true
                    try {
                        val dataUrl = "data:$mimeType;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        when (val res = com.nebula.app.data.backend.NebulaApiClient.uploadChatMedia(
                            url = dataUrl,
                            mimeType = mimeType,
                            mediaType = "image",
                            sizeBytes = bytes.size.toLong(),
                            onProgress = { uploadProgress = it }
                        )) {
                            is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Success -> {
                                val mediaUrl = res.data.optJSONObject("data")?.optString("mediaUrl").orEmpty().ifBlank { res.data.optString("mediaUrl") }
                                if (mediaUrl.isNotBlank()) onSendAttachment("", mediaUrl, "media", attachmentIsSpoiler, mimeType)
                                else android.widget.Toast.makeText(context, "Image upload returned no saved file", android.widget.Toast.LENGTH_LONG).show()
                            }
                            is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Error ->
                                android.widget.Toast.makeText(context, res.message, android.widget.Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "Image could not be uploaded", android.widget.Toast.LENGTH_LONG).show()
                    } finally {
                        isUploadingMedia = false
                        showAttachments = false
                    }
                }
            }
        }

        val videoPickerLauncher = rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
        ) { uri: android.net.Uri? ->
            uri?.let {
                coroutineScope.launch {
                    val mimeType = context.contentResolver.getType(it)?.lowercase() ?: "video/mp4"
                    if (mimeType !in setOf("video/mp4", "video/webm")) {
                        android.widget.Toast.makeText(context, "Choose an MP4 or WebM video", android.widget.Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    val size = withContext(Dispatchers.IO) {
                        context.contentResolver.openFileDescriptor(it, "r")?.use { fd -> fd.statSize } ?: 0L
                    }
                    if (size > 30 * 1024 * 1024L) {
                        android.widget.Toast.makeText(
                            context,
                            "Video must be under 30MB",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }
                    isUploadingMedia = true
                    try {
                        val videoBytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(it)?.use { stream -> stream.readBytes() } ?: ByteArray(0) }
                        if (videoBytes.isEmpty() || videoBytes.size > 30 * 1024 * 1024) {
                            android.widget.Toast.makeText(context, "Video must be under 30MB", android.widget.Toast.LENGTH_LONG).show()
                            isUploadingMedia = false
                            return@launch
                        }
                        val dataUrl = "data:$mimeType;base64," + android.util.Base64.encodeToString(videoBytes, android.util.Base64.NO_WRAP)
                        val res = com.nebula.app.data.backend.NebulaApiClient.uploadChatMedia(
                            url = dataUrl,
                            mimeType = mimeType,
                            mediaType = "video",
                            sizeBytes = videoBytes.size.toLong(),
                            onProgress = { uploadProgress = it }
                        )
                        when (res) {
                            is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Success -> {
                                val mediaUrl = res.data.optJSONObject("data")?.optString("mediaUrl").orEmpty().ifBlank { res.data.optString("mediaUrl") }
                                if (mediaUrl.isNotBlank()) onSendAttachment("", mediaUrl, "media", attachmentIsSpoiler, mimeType)
                                else android.widget.Toast.makeText(context, "Video upload returned no saved file", android.widget.Toast.LENGTH_LONG).show()
                            }
                            is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Error -> {
                                android.widget.Toast.makeText(context, res.message, android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "Video could not be uploaded", android.widget.Toast.LENGTH_LONG).show()
                    }
                    isUploadingMedia = false
                    showAttachments = false
                }
            }
        }

        AnimatedVisibility(
            visible = showAttachments,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            // Same horizontal inset as the composer so the row lines up with the
            // input field instead of floating at its own margin.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AttachmentAction(
                    icon = Icons.Default.Image,
                    label = "Image",
                    onClick = {
                        imagePickerLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    }
                )
                AttachmentAction(
                    icon = Icons.Default.Videocam,
                    label = "Video",
                    onClick = {
                        videoPickerLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.VideoOnly
                            )
                        )
                    }
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Spoiler",
                    style = theme.typography.caption.copy(
                        color = theme.colors.textSecondary,
                        fontSize = 11.5.sp
                    )
                )
                Switch(
                    checked = attachmentIsSpoiler,
                    onCheckedChange = { attachmentIsSpoiler = it },
                    modifier = Modifier.scale(0.8f),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = theme.colors.onPrimaryAccent,
                        checkedTrackColor = theme.colors.primaryAccent,
                        uncheckedThumbColor = theme.colors.textSecondary,
                        uncheckedTrackColor = theme.colors.surfaceElevated,
                        uncheckedBorderColor = theme.colors.surfaceBorder
                    )
                )
            }
        }

        // Upload feedback is a single green progress hairline on the composer's top
        // border. No percentage text and no extra row during a send.
        AnimatedVisibility(visible = isUploadingMedia) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .background(theme.colors.surfaceBorder.copy(alpha = 0.35f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((uploadProgress.coerceIn(0, 100)) / 100f)
                        .fillMaxHeight()
                        .background(theme.colors.successColor)
                )
            }
        }

        // Edit mode banner: shows what is being changed and offers a way out.
        androidx.compose.animation.AnimatedVisibility(visible = editingMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(3.dp, 16.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(theme.colors.successColor)
                )
                Text(
                    text = "Editing message",
                    style = theme.typography.caption.copy(
                        color = theme.colors.successColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp
                    )
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Cancel",
                    modifier = Modifier.clickable {
                        editingMessage = null
                        inputText = ""
                    },
                    style = theme.typography.caption.copy(
                        color = theme.colors.textSecondary,
                        fontSize = 11.5.sp
                    )
                )
            }
        }

        // 4. CLEAN COMPOSER PINNED ABOVE KEYBOARD
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(theme.colors.surface)
                    .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Attach icon (+)
                IconButton(
                    onClick = { showAttachments = !showAttachments },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Attach",
                        tint = if (showAttachments) theme.colors.primaryAccent else theme.colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Input Field
                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(composerFocus),
                    textStyle = theme.typography.bodyMedium.copy(
                        color = theme.colors.textPrimary,
                        fontSize = 14.5.sp
                    ),
                    cursorBrush = SolidColor(theme.colors.primaryAccent),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (inputText.length > MAX_DIRECT_MESSAGE_CHARS) {
                            Text(
                                text = "${inputText.length}/$MAX_DIRECT_MESSAGE_CHARS",
                                style = theme.typography.caption.copy(
                                    color = theme.colors.errorColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        } else if (inputText.isEmpty()) {
                            Text(
                                text = if (editingMessage != null) "Edit message..." else "Message...",
                                style = theme.typography.bodyMedium.copy(
                                    color = theme.colors.textTertiary,
                                    fontSize = 14.5.sp
                                )
                            )
                        }
                        innerTextField()
                    }
                )

                // Send Button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                inputText.isBlank() || inputText.length > MAX_DIRECT_MESSAGE_CHARS -> theme.colors.surfaceElevated
                                editingMessage != null -> theme.colors.successColor
                                else -> theme.colors.primaryAccent
                            }
                        )
                        .clickable(enabled = inputText.isNotBlank() && inputText.length <= MAX_DIRECT_MESSAGE_CHARS) {
                            val editing = editingMessage
                            if (editing != null) {
                                onEditMessage(editing.id, inputText.trim())
                                editingMessage = null
                            } else {
                                onSendMessage(inputText)
                            }
                            inputText = ""
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (inputText.isNotBlank()) Color.White else theme.colors.textTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
/** Identity used to decide whether two consecutive messages share a group. */
private fun ChatMessage.senderKeyOrNull(): String? =
    senderId ?: senderName ?: if (role == MessageRole.USER) "me" else null

private fun ChatMessage.isSameDayAs(other: ChatMessage): Boolean {
    val a = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val b = java.util.Calendar.getInstance().apply { timeInMillis = other.timestamp }
    return a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
        a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)
}

/** Minute-accurate local clock time for a message, e.g. "14:07". */
internal fun Long.clockLabel(): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(this))

/** WhatsApp-style non-editable date divider: TODAY / YESTERDAY / 15 SEPTEMBER 2025. */
private fun ChatMessage.dateLabel(): String {
    val messageDay = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = java.util.Calendar.getInstance()
    fun sameDay(x: java.util.Calendar, y: java.util.Calendar) =
        x.get(java.util.Calendar.YEAR) == y.get(java.util.Calendar.YEAR) &&
            x.get(java.util.Calendar.DAY_OF_YEAR) == y.get(java.util.Calendar.DAY_OF_YEAR)
    if (sameDay(messageDay, today)) return "TODAY"
    val yesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
    if (sameDay(messageDay, yesterday)) return "YESTERDAY"
    return java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.getDefault())
        .format(java.util.Date(timestamp)).uppercase(java.util.Locale.getDefault())
}

@Composable
private fun AttachmentAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val theme = Theme.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(theme.colors.surface)
            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = theme.colors.primaryAccent,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = theme.typography.caption.copy(
                color = theme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
        )
    }
}

@Composable
private fun DateSeparator(label: String) {
    val theme = Theme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = theme.colors.surfaceElevated
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                style = theme.typography.caption.copy(
                    color = theme.colors.textSecondary,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp
                )
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ModernChatBubble(
    message: ChatMessage,
    showAvatar: Boolean,
    isGroupedWithPrevious: Boolean,
    isGroupedWithNext: Boolean,
    showsReceipt: Boolean,
    onEdit: () -> Unit,
    onUnsend: () -> Unit
) {
    var showActions by remember(message.id) { mutableStateOf(false) }
    val theme = Theme.current
    val isMe = message.role == MessageRole.USER
    var spoilerRevealed by remember(message.id) { mutableStateOf(!message.isSpoiler) }
    var videoStarted by remember(message.id) { mutableStateOf(false) }
    var videoPrepared by remember(message.id) { mutableStateOf(false) }
    var videoBuffering by remember(message.id) { mutableStateOf(false) }
    var videoBufferPercent by remember(message.id) { mutableIntStateOf(0) }
    var videoFailed by remember(message.id) { mutableStateOf(false) }
    // Default to a landscape-ish box until the real dimensions arrive.
    val isVideoMessage = message.mediaMimeType?.startsWith("video/") == true || message.messageType == "video"
    var mediaAspectRatio by remember(message.id) { mutableStateOf(4f / 3f) }
    androidx.compose.runtime.LaunchedEffect(message.id, message.mediaUrl) {
        val url = message.mediaUrl
        if (!url.isNullOrBlank()) {
            val ratio = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val source = if (url.startsWith("data:")) {
                        val csv = url.substringAfter("base64,", "")
                        android.util.Base64.decode(csv, android.util.Base64.DEFAULT)
                    } else {
                        java.net.URL(url).openStream().use { it.readBytes() }
                    }
                    val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeByteArray(source, 0, source.size, options)
                    if (options.outWidth > 0 && options.outHeight > 0) {
                        options.outWidth.toFloat() / options.outHeight.toFloat()
                    } else null
                }.getOrNull()
            }
            if (ratio != null && ratio.isFinite() && ratio > 0f) {
                mediaAspectRatio = ratio.coerceIn(0.5f, 2.2f)
            } else {
                // Fall back to a measured-but-shown image's intrinsic size.
                mediaAspectRatio = 4f / 3f
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (isGroupedWithPrevious) 2.dp else 10.dp),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isMe) {
            if (showAvatar) {
                NebulaAvatarImage(message.senderAvatarUrl, message.senderAvatarLetter, modifier = Modifier.size(28.dp))
            } else {
                // Keep the text column aligned with the first bubble of the group.
                Spacer(Modifier.width(28.dp))
            }
        }
        Spacer(Modifier.width(if (isMe) 0.dp else 7.dp))
        Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .combinedClickable(
                    // Long press owns the actions now; a plain tap stays inert so
                    // the thread is not full of buttons.
                    onClick = {},
                    onLongClick = {
                        if (isMe && message.deletedAt == null) showActions = true
                    }
                )
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isMe) 16.dp else 4.dp,
                        bottomEnd = if (isMe) 4.dp else 16.dp
                    )
                )
                .background(if (isMe) theme.colors.primaryAccent else theme.colors.surface)
                .border(
                    width = 1.dp,
                    color = if (isMe) Color.Transparent else theme.colors.surfaceBorder,
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isMe) 16.dp else 4.dp,
                        bottomEnd = if (isMe) 4.dp else 16.dp
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            val isMedia = message.deletedAt == null && (message.mediaUrl != null || message.messageType == "media")
            if (isMedia) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Border-less media surface. The height follows the picture's
                    // own aspect ratio so a portrait photo no longer sits inside a
                    // wide coloured slab with empty bars either side.
                    val mediaBoxModifier = if (isVideoMessage) {
                        Modifier.fillMaxWidth().height(180.dp)
                    } else {
                        Modifier
                            .fillMaxWidth(0.72f)
                            .aspectRatio(mediaAspectRatio)
                    }
                    Box(
                        modifier = mediaBoxModifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isMe) Color.Black.copy(alpha = 0.25f) else theme.colors.surfaceElevated
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val mediaUrl = message.mediaUrl.orEmpty()
                        val isVideo = message.mediaMimeType?.startsWith("video/") == true || message.messageType == "video"
                        if (isVideo && mediaUrl.isNotBlank()) {
                            AndroidView(
                                factory = { context ->
                                    android.widget.VideoView(context).apply {
                                        setVideoURI(android.net.Uri.parse(mediaUrl))
                                        setOnPreparedListener { player ->
                                            player.isLooping = false
                                            videoPrepared = true
                                            videoFailed = false
                                            player.setOnBufferingUpdateListener { _, percent ->
                                                videoBufferPercent = percent.coerceIn(0, 100)
                                                videoBuffering = percent < 100
                                            }
                                            if (videoStarted) start()
                                        }
                                        setOnInfoListener { _, what, _ ->
                                            when (what) {
                                                android.media.MediaPlayer.MEDIA_INFO_BUFFERING_START -> videoBuffering = true
                                                android.media.MediaPlayer.MEDIA_INFO_BUFFERING_END -> videoBuffering = false
                                            }
                                            true
                                        }
                                        setOnErrorListener { _, _, _ ->
                                            videoFailed = true
                                            videoBuffering = false
                                            true
                                        }
                                    }
                                },
                                update = { player ->
                                    if (videoStarted && videoPrepared && !videoFailed && !player.isPlaying) player.start()
                                },
                                modifier = Modifier.fillMaxSize().then(if (!spoilerRevealed) Modifier.blur(18.dp) else Modifier)
                            )
                        } else if (mediaUrl.isNotBlank()) {
                            SubcomposeAsyncImage(
                                model = mediaUrl,
                                contentDescription = "Shared image",
                                modifier = Modifier.fillMaxSize().then(if (!spoilerRevealed) Modifier.blur(18.dp) else Modifier),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                loading = {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("Loading image…", color = if (isMe) Color.White else theme.colors.textSecondary, fontSize = 12.sp)
                                    }
                                },
                                error = {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("Image unavailable", color = if (isMe) Color.White else theme.colors.textSecondary, fontSize = 12.sp)
                                    }
                                },
                                success = { SubcomposeAsyncImageContent() }
                            )
                        } else {
                            Text(
                                text = "Attachment unavailable",
                                style = theme.typography.bodyMedium.copy(
                                    color = if (isMe) Color.White else theme.colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                        if (isVideo && videoFailed && spoilerRevealed) {
                            Surface(
                                modifier = Modifier.align(Alignment.Center),
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black.copy(alpha = 0.78f)
                            ) {
                                Text("Video unavailable", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Color.White, fontSize = 12.sp)
                            }
                        } else if (isVideo && videoBuffering && spoilerRevealed) {
                            Surface(
                                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black.copy(alpha = 0.78f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    androidx.compose.material3.LinearProgressIndicator(
                                        progress = { videoBufferPercent / 100f },
                                        modifier = Modifier.width(110.dp),
                                        color = Color.White,
                                        trackColor = Color.White.copy(alpha = 0.3f)
                                    )
                                    Text("$videoBufferPercent%", color = Color.White, fontSize = 11.sp)
                                }
                            }
                        }
                        if (!spoilerRevealed) {
                            Surface(
                                modifier = Modifier.align(Alignment.Center).clickable { spoilerRevealed = true },
                                shape = RoundedCornerShape(18.dp),
                                color = Color.Black.copy(alpha = 0.72f)
                            ) { Text("Reveal spoiler", modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp), color = Color.White, fontSize = 12.sp) }
                        } else if (isVideo && !videoStarted) {
                            Surface(
                                modifier = Modifier.align(Alignment.Center).clickable { videoStarted = true },
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.72f)
                            ) { Text("▶", modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp), color = Color.White, fontSize = 20.sp) }
                        }
                    }
                    Text(
                        text = message.content.ifBlank { if (message.mediaUrl?.contains("gif", true) == true) "GIF" else "Attachment" },
                        style = theme.typography.caption.copy(
                            color = if (isMe) Color.White.copy(alpha = 0.85f) else theme.colors.textSecondary,
                            fontSize = 11.5.sp
                        )
                    )
                }
            } else {
                Text(
                    text = message.content,
                    style = theme.typography.bodyMedium.copy(
                        color = if (isMe) Color.White else theme.colors.textPrimary,
                        fontSize = 14.sp,
                        lineHeight = 19.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Real clock time, not a relative "Recent"/"Just now" label.
            Text(
                text = message.timestamp.clockLabel(),
                style = theme.typography.caption.copy(
                    color = theme.colors.textTertiary,
                    fontSize = 10.5.sp
                )
            )
            if (isMe && message.deletedAt == null && showsReceipt) {
                // One tick = delivered, two accent ticks = the recipient opened it.
                val isRead = message.readAt != null
                Icon(
                    imageVector = if (isRead) Icons.Default.DoneAll else Icons.Default.Done,
                    contentDescription = if (isRead) "Read" else "Delivered",
                    tint = if (isRead) theme.colors.primaryAccent else theme.colors.textTertiary,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
        if (showActions) {
            MessageActionSheet(
                message = message,
                onEdit = {
                    showActions = false
                    onEdit()
                },
                onUnsend = {
                    showActions = false
                    onUnsend()
                },
                onDismiss = { showActions = false }
            )
        }
        }
    }
}

/**
 * Long-press action sheet for a message the user sent.
 *
 * Both actions are available only during the two-minute window and each is drawn
 * with a progress bar that depletes over the remaining time, so the deadline is
 * visible rather than implied. Edit is green because it keeps the message alive;
 * unsend drains to red because it destroys it.
 */
@Composable
private fun MessageActionSheet(
    message: ChatMessage,
    onEdit: () -> Unit,
    onUnsend: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = Theme.current
    var remainingFraction by remember(message.id) {
        mutableFloatStateOf(remainingFractionFor(message.timestamp))
    }
    androidx.compose.runtime.LaunchedEffect(message.id) {
        while (remainingFraction > 0f) {
            kotlinx.coroutines.delay(250)
            remainingFraction = remainingFractionFor(message.timestamp)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = theme.colors.surfaceElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.surfaceBorder),
            modifier = Modifier.widthIn(min = 240.dp, max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Message",
                    style = theme.typography.caption.copy(
                        color = theme.colors.textSecondary,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.6.sp
                    ),
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                )
                CountdownAction(
                    label = "Edit",
                    subtitle = if (remainingFraction > 0f) "Send the corrected text" else "Edit window closed",
                    fraction = remainingFraction,
                    accent = theme.colors.successColor,
                    enabled = remainingFraction > 0f,
                    onClick = onEdit
                )
                CountdownAction(
                    label = "Unsend",
                    subtitle = if (remainingFraction > 0f) "Remove for everyone" else "Unsend window closed",
                    fraction = remainingFraction,
                    accent = theme.colors.errorColor,
                    enabled = remainingFraction > 0f,
                    onClick = onUnsend
                )
            }
        }
    }
}

/** 1f at send time, draining to 0f when the two-minute window expires. */
private fun remainingFractionFor(timestampMs: Long): Float {
    val elapsed = System.currentTimeMillis() - timestampMs
    return (1f - (elapsed.toFloat() / (2 * 60 * 1000f))).coerceIn(0f, 1f)
}

@Composable
private fun CountdownAction(
    label: String,
    subtitle: String,
    fraction: Float,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val theme = Theme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.colors.surface)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = theme.typography.bodyMedium.copy(
                color = if (enabled) theme.colors.textPrimary else theme.colors.textTertiary,
                fontWeight = FontWeight.SemiBold
            )
        )
        Text(
            text = subtitle,
            style = theme.typography.caption.copy(
                color = theme.colors.textTertiary,
                fontSize = 11.sp
            )
        )
        // Depleting bar: full accent at send time, empty when the window closes.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(theme.colors.surfaceBorder.copy(alpha = 0.4f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(if (enabled) accent else theme.colors.textTertiary.copy(alpha = 0.4f))
            )
        }
    }
}

/** Hard ceiling for a direct message body, mirrored by the server schema. */
internal const val MAX_DIRECT_MESSAGE_CHARS = 500
