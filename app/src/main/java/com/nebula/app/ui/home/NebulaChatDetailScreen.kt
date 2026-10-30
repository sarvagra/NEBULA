package com.nebula.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.filled.Close
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.nebula.app.domain.model.MessageSendState
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
    onRetryMessage: (clientKey: String) -> Unit = {},
    onOpenProfile: () -> Unit,
    onInviteToRoom: (String) -> Unit,
    /** Opens a room from a shared invite card or the pinned banner. */
    onJoinRoomByCode: (String) -> Unit = {},
    /** Device connectivity: drives the offline strip and the attachment gate. */
    isOnline: Boolean = true,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    val density = LocalDensity.current
    val maxDragPx = with(density) { 75.dp.toPx() }
    val dragOffsetAnimatable = remember { Animatable(0f) }
    // A finished upload waits here until the user confirms spoiler state and sends.
    // A picked photo/video on its way to being a message. Keyed by conversation so an
    // unsent draft can never follow the user into a different thread and be sent to the
    // wrong person.
    var draft by remember(conversation.id) { mutableStateOf<PendingAttachment?>(null) }
    val scope = rememberCoroutineScope()
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

                    // A device that has no network says so here, the way Rave's chat header
                    // does, instead of implying the other person went offline.
                    val statusText = when {
                        !isOnline -> "Offline"
                        conversation.isGroup -> "${conversation.participants.size} members"
                        else -> conversation.partnerUser?.status?.ifBlank { "Offline" } ?: "Offline"
                    }

                    Text(
                        text = statusText,
                        style = theme.typography.caption.copy(
                            color = when {
                                !isOnline -> theme.colors.errorColor
                                conversation.partnerUser?.isOnline == true -> theme.colors.successColor
                                else -> theme.colors.textTertiary
                            },
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

        // 6.4: a live invite stays reachable even after the card scrolls away.
        // Dismissing it only hides the banner for this view; the card remains.
        val latestInvite = latestRoomCard(conversation.messages)
        var bannerDismissedForId by remember(conversation.id) { mutableStateOf<String?>(null) }
        val inviteCode = latestInvite?.let { roomCardCode(it) }
        if (inviteCode != null && bannerDismissedForId != latestInvite?.id) {
            RoomInviteBanner(
                roomName = latestInvite.content,
                code = inviteCode,
                onJoin = onJoinRoomByCode,
                onDismiss = { bannerDismissedForId = latestInvite.id }
            )
        }

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
                    .padding(horizontal = 14.dp)
                    .pointerInput(conversation.id) {
                        detectHorizontalDragGestures(
                            onDragStart = { },
                            onDragEnd = {
                                scope.launch {
                                    dragOffsetAnimatable.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    dragOffsetAnimatable.animateTo(0f)
                                }
                            },
                            onHorizontalDrag = { _, delta ->
                                scope.launch {
                                    val next = (dragOffsetAnimatable.value + delta).coerceIn(-maxDragPx, 0f)
                                    dragOffsetAnimatable.snapTo(next)
                                }
                            }
                        )
                    },
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
                            showsReceipt = message.id == lastOwnMessageId,
                            dragOffset = dragOffsetAnimatable.value,
                            onEdit = { editingMessage = message },
                            onUnsend = { onUnsendMessage(message.id) },
                            onRetry = { message.clientKey?.let(onRetryMessage) },
                            onJoinRoomByCode = onJoinRoomByCode
                        )
                    }
                }
            }
        }

        // 3. QUICK ATTACHMENTS (Visible when + is tapped)
        val context = androidx.compose.ui.platform.LocalContext.current
        val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
        var attachmentIsSpoiler by remember(conversation.id) { mutableStateOf(false) }

        fun uploadDraft(target: PendingAttachment) {
            coroutineScope.launch {
                if (!isOnline) {
                    draft = target.copy(state = AttachmentUploadState.OFFLINE, progress = 0)
                    return@launch
                }
                draft = target.copy(state = AttachmentUploadState.UPLOADING, progress = 0, error = null)
                val bytes = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(android.net.Uri.parse(target.localUri))
                            ?.use { it.readBytes() }
                    }.getOrNull() ?: ByteArray(0)
                }
                if (bytes.isEmpty()) {
                    draft = target.copy(state = AttachmentUploadState.FAILED, error = "That file could not be read")
                    return@launch
                }
                val dataUrl = "data:${target.mimeType};base64," +
                    android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                val mediaType = when {
                    target.isVideo -> "video"
                    target.mimeType == "image/gif" -> "gif"
                    else -> "image"
                }
                val response = try {
                    com.nebula.app.data.backend.NebulaApiClient.uploadChatMedia(
                        url = dataUrl,
                        mimeType = target.mimeType,
                        mediaType = mediaType,
                        sizeBytes = bytes.size.toLong(),
                        onProgress = { percent -> draft = draft?.copy(progress = percent) ?: target }
                    )
                } catch (e: Exception) {
                    draft = target.copy(state = AttachmentUploadState.FAILED, error = "Upload interrupted")
                    return@launch
                }
                when (response) {
                    is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Success -> {
                        val remote = response.data.optJSONObject("data")?.optString("mediaUrl").orEmpty()
                            .ifBlank { response.data.optString("mediaUrl") }
                        draft = if (remote.isBlank()) {
                            target.copy(state = AttachmentUploadState.FAILED, error = "The server returned no file")
                        } else {
                            target.copy(
                                state = AttachmentUploadState.READY,
                                remoteUrl = remote,
                                progress = 100
                            )
                        }
                    }
                    is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Error ->
                        draft = target.copy(
                            state = AttachmentUploadState.FAILED,
                            error = if (response.statusCode in 400..499) {
                                response.message
                            } else {
                                "Upload failed. Check your connection."
                            }
                        )
                }
            }
        }

        fun pickAttachment(uri: android.net.Uri, isVideo: Boolean) {
            coroutineScope.launch {
                val mimeType = context.contentResolver.getType(uri)?.lowercase().orEmpty()
                    .ifBlank { if (isVideo) "video/mp4" else "image/jpeg" }
                val maxBytes = if (isVideo) 30 * 1024 * 1024L else 5 * 1024 * 1024L
                val size = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
                    }.getOrNull() ?: 0L
                }
                if (size > maxBytes) {
                    android.widget.Toast.makeText(
                        context,
                        if (isVideo) "Video must be under 30MB" else "Image must be under 5MB",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }
                if (isVideo && mimeType !in setOf("video/mp4", "video/webm")) {
                    android.widget.Toast.makeText(context, "Choose an MP4 or WebM video", android.widget.Toast.LENGTH_LONG).show()
                    return@launch
                }
                showAttachments = false
                // The local file shows immediately; the upload runs behind it.
                val target = PendingAttachment(
                    localUri = uri.toString(),
                    mimeType = if (isVideo) mimeType else mimeType.ifBlank { "image/jpeg" },
                    isVideo = isVideo,
                    state = if (isOnline) AttachmentUploadState.UPLOADING else AttachmentUploadState.OFFLINE
                )
                draft = target
                if (isOnline) uploadDraft(target)
            }
        }

        val imagePickerLauncher = rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
        ) { uri: android.net.Uri? ->
            uri?.let { pickAttachment(it, isVideo = false) }
        }

        val videoPickerLauncher = rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
        ) { uri: android.net.Uri? ->
            uri?.let { pickAttachment(it, isVideo = true) }
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

        // A photo picked while offline starts uploading the moment the network returns,
        // so the user does not have to remember to press Retry.
        androidx.compose.runtime.LaunchedEffect(isOnline) {
            if (!isOnline) return@LaunchedEffect
            draft?.takeIf { it.state == AttachmentUploadState.OFFLINE }?.let { uploadDraft(it) }
        }

        // The picked photo with its own upload state, so a slow or failed upload is
        // visible and retryable instead of disappearing into a toast.
        draft?.let { current ->
            AttachmentDraftCard(
                draft = current,
                isSpoiler = attachmentIsSpoiler,
                isOnline = isOnline,
                onToggleSpoiler = { attachmentIsSpoiler = it },
                onSend = {
                    current.remoteUrl?.takeIf { it.isNotBlank() }?.let { remote ->
                        onSendAttachment("", remote, current.messageType, attachmentIsSpoiler, current.mimeType)
                        draft = null
                        attachmentIsSpoiler = false
                    }
                },
                onRetry = { uploadDraft(current) },
                onDiscard = {
                    draft = null
                    attachmentIsSpoiler = false
                }
            )
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

        // Offline strip: says plainly that a send will not go out, before the user
        // types, rather than after a spinner has been sitting there.
        androidx.compose.animation.AnimatedVisibility(visible = !isOnline) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(theme.colors.errorColor.copy(alpha = 0.12f))
                    .border(1.dp, theme.colors.errorColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(theme.colors.errorColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "!",
                        style = theme.typography.caption.copy(color = Color.White, fontWeight = FontWeight.Bold),
                        fontSize = 10.sp
                    )
                }
                Text(
                    text = "You're offline · messages will send when you reconnect",
                    style = theme.typography.caption.copy(
                        color = theme.colors.errorColor,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
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

/**
 * Message-length cap, shared with the composer's counter and the send button so the
 * limit the UI shows is the limit that is actually enforced.
 */
internal const val MAX_DIRECT_MESSAGE_CHARS = 500
