package com.nebula.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
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
    var inputText by remember { mutableStateOf("") }
    var showAttachments by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
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
                items(conversation.messages) { message ->
                    ModernChatBubble(
                        message = message,
                        onEdit = { editingMessage = message },
                        onUnsend = { onUnsendMessage(message.id) }
                    )
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
                                    val mediaUrl = res.data.optString("mediaUrl")
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
                                val mediaUrl = res.data.optString("mediaUrl")
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
                                val mediaUrl = res.data.optString("mediaUrl")
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Image Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.colors.surfaceElevated)
                        .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(8.dp))
                        .clickable {
                            imagePickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "📷 Image",
                        style = theme.typography.caption.copy(
                            color = theme.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    )
                }

                // Video Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.colors.surfaceElevated)
                        .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(8.dp))
                        .clickable {
                            videoPickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.VideoOnly
                                )
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "🎥 Video",
                        style = theme.typography.caption.copy(
                            color = theme.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Hide as spoiler", style = theme.typography.caption.copy(color = theme.colors.textSecondary, fontSize = 11.sp))
                    Switch(checked = attachmentIsSpoiler, onCheckedChange = { attachmentIsSpoiler = it })
                }
            }
        }

        AnimatedVisibility(visible = isUploadingMedia) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text("Uploading attachment · $uploadProgress%", style = theme.typography.caption.copy(color = theme.colors.textSecondary, fontSize = 11.sp))
                LinearProgressIndicator(progress = { uploadProgress / 100f }, modifier = Modifier.fillMaxWidth())
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
                    modifier = Modifier.weight(1f),
                    textStyle = theme.typography.bodyMedium.copy(
                        color = theme.colors.textPrimary,
                        fontSize = 14.5.sp
                    ),
                    cursorBrush = SolidColor(theme.colors.primaryAccent),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (inputText.isEmpty()) {
                            Text(
                                text = "Message...",
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
                        .background(if (inputText.isNotBlank()) theme.colors.primaryAccent else theme.colors.surfaceElevated)
                        .clickable(enabled = inputText.isNotBlank()) {
                            onSendMessage(inputText)
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
        editingMessage?.let { message ->
            var editedText by remember(message.id) { mutableStateOf(message.content) }
            AlertDialog(
                onDismissRequest = { editingMessage = null },
                title = { Text("Edit message") },
                text = { BasicTextField(value = editedText, onValueChange = { editedText = it }, modifier = Modifier.fillMaxWidth(), textStyle = theme.typography.bodyMedium.copy(color = theme.colors.textPrimary)) },
                confirmButton = { TextButton(onClick = { if (editedText.isNotBlank()) onEditMessage(message.id, editedText.trim()); editingMessage = null }) { Text("Save") } },
                dismissButton = { TextButton(onClick = { editingMessage = null }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun ModernChatBubble(message: ChatMessage, onEdit: () -> Unit, onUnsend: () -> Unit) {
    val theme = Theme.current
    val isMe = message.role == MessageRole.USER
    var showHistory by remember(message.id) { mutableStateOf(false) }
    var spoilerRevealed by remember(message.id) { mutableStateOf(!message.isSpoiler) }
    var videoStarted by remember(message.id) { mutableStateOf(false) }
    var videoPrepared by remember(message.id) { mutableStateOf(false) }
    var videoBuffering by remember(message.id) { mutableStateOf(false) }
    var videoBufferPercent by remember(message.id) { mutableIntStateOf(0) }
    var videoFailed by remember(message.id) { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isMe) NebulaAvatarImage(message.senderAvatarUrl, message.senderAvatarLetter, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(if (isMe) 0.dp else 7.dp))
        Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isMe) Color.Black.copy(alpha = 0.25f) else theme.colors.surfaceElevated),
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
            Text(
                text = message.timeLabel,
                style = theme.typography.caption.copy(
                    color = theme.colors.textTertiary,
                    fontSize = 10.5.sp
                )
            )
            if (isMe) {
                Icon(
                    imageVector = Icons.Default.DoneAll,
                    contentDescription = null,
                    tint = theme.colors.primaryAccent,
                    modifier = Modifier.size(12.dp)
                )
            }
            if (message.editedAt != null && message.deletedAt == null && message.editHistory.isNotEmpty()) {
                Text(
                    text = "History",
                    modifier = Modifier.clickable { showHistory = !showHistory },
                    style = theme.typography.caption.copy(color = theme.colors.primaryAccent, fontSize = 10.5.sp)
                )
            }
        }
        if (isMe && message.deletedAt == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Edit", modifier = Modifier.clickable { onEdit() }, style = theme.typography.caption.copy(color = theme.colors.primaryAccent, fontSize = 10.5.sp))
                if (System.currentTimeMillis() - message.timestamp <= 2 * 60 * 1000L) Text("Unsend", modifier = Modifier.clickable { onUnsend() }, style = theme.typography.caption.copy(color = theme.colors.textSecondary, fontSize = 10.5.sp))
            }
        }
        androidx.compose.animation.AnimatedVisibility(visible = showHistory && message.deletedAt == null && message.editHistory.isNotEmpty()) {
            Surface(
                modifier = Modifier.padding(top = 5.dp).widthIn(max = 270.dp),
                shape = RoundedCornerShape(12.dp),
                color = theme.colors.surfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.surfaceBorder),
                shadowElevation = 4.dp
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Edit history", style = theme.typography.caption.copy(color = theme.colors.textSecondary, fontWeight = FontWeight.SemiBold))
                    message.editHistory.forEachIndexed { index, previous ->
                        Text("${index + 1}. $previous", style = theme.typography.bodySmall.copy(color = theme.colors.textPrimary))
                    }
                }
            }
        }
        }
    }
}
