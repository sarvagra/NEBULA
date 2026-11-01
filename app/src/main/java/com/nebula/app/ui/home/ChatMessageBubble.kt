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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent

/**
 * One message in a thread: media, text, grouping, spoilers and delivery state.
 *
 * Split out of the conversation screen so a change to how a single bubble renders
 * cannot touch the screen's layout, and so the bubble can be exercised on its own.
 */

@Composable
internal fun AttachmentAction(
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
internal fun DateSeparator(label: String) {
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

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
internal fun ModernChatBubble(
    message: ChatMessage,
    showAvatar: Boolean,
    isGroupedWithPrevious: Boolean,
    isGroupedWithNext: Boolean,
    showsReceipt: Boolean,
    dragOffset: Float,
    onEdit: () -> Unit,
    onUnsend: () -> Unit,
    onRetry: () -> Unit = {},
    onDelete: () -> Unit = {},
    onJoinRoomByCode: (String) -> Unit = {}
) {
    val density = LocalDensity.current
    var showActions by remember(message.id) { mutableStateOf(false) }
    var showPile by remember(message.id) { mutableStateOf(false) }
    var showSingleImage by remember(message.id) { mutableStateOf(false) }
    var showFailedOptionsDialog by remember(message.id) { mutableStateOf(false) }
    val theme = Theme.current
    val isMe = message.role == MessageRole.USER
    // A room card also carries a mediaUrl, but it is a link to a watch party rather
    // than a file. It shares the borderless bubble treatment with media while keeping
    // its own content branch below.
    val isRoomCard = roomCardCode(message) != null
    val mediaUrlForViewer = message.mediaUrl.orEmpty().ifBlank { message.mediaUrls.firstOrNull().orEmpty() }
    val isMediaMessage = message.deletedAt == null && !isRoomCard &&
        (!message.mediaUrl.isNullOrBlank() || message.mediaUrls.isNotEmpty() || message.messageType in listOf("media", "image", "video"))
    var spoilerRevealed by remember(message.id) { mutableStateOf(!message.isSpoiler) }
    var showVideoPlayer by remember(message.id) { mutableStateOf(false) }
    val isVideoMessage = message.mediaMimeType?.startsWith("video/") == true || message.messageType == "video"
    var mediaAspectRatio by remember(message.id) { mutableStateOf(4f / 3f) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = dragOffset }
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

        // A failed send gets a clean red circle with an exclamation mark.
        // Tapping it presents Resend or Delete options.
        val failedToSend = isMe && message.deletedAt == null && message.sendState == MessageSendState.FAILED
        if (failedToSend) {
            Box(
                modifier = Modifier
                    .padding(end = 5.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(theme.colors.errorColor)
                    .clickable { showFailedOptionsDialog = true }
                    .semantics { contentDescription = "Message not sent. Tap for options" },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "!",
                    style = theme.typography.bodyMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    ),
                    fontSize = 13.sp
                )
            }
        }

        Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .combinedClickable(
                    onClick = {
                        if (isMe && message.sendState == MessageSendState.FAILED) {
                            showFailedOptionsDialog = true
                        }
                    },
                    onLongClick = {
                        // Edit and unsend address a server message by id. A message that
                        // never got one has no id to act on, so the sheet is not offered.
                        if (isMe && message.deletedAt == null && message.sendState == MessageSendState.SENT) {
                            showActions = true
                        }
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
                .then(
                    if (isMediaMessage && !isVideoMessage) Modifier
                    else Modifier.background(if (isMe) theme.colors.primaryAccent else theme.colors.surface)
                )
                .then(
                    if (isMediaMessage && !isVideoMessage) Modifier
                    else Modifier.border(
                        width = 1.dp,
                        color = if (isMe) Color.Transparent else theme.colors.surfaceBorder,
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isMe) 16.dp else 4.dp,
                            bottomEnd = if (isMe) 4.dp else 16.dp
                        )
                    )
                )
                .padding(
                    horizontal = if (isMediaMessage && !isVideoMessage) 0.dp else 14.dp,
                    vertical = if (isMediaMessage && !isVideoMessage) 0.dp else 10.dp
                )
        ) {
            val isMedia = isMediaMessage
            // The Surface slot stacks its children in a Box, so the delivery marker has
            // to live in a Column or it would be painted on top of the message text.
            Column {
            if (isRoomCard) {
                RoomShareCard(
                    roomName = message.content,
                    code = roomCardCode(message).orEmpty(),
                    isMine = isMe,
                    onJoin = onJoinRoomByCode
                )
            } else if (isVideoMessage) {
                val mediaUrl = message.mediaUrl.orEmpty().ifBlank { message.mediaUrls.firstOrNull().orEmpty() }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isMe) Color.White.copy(alpha = 0.16f)
                                else theme.colors.surfaceElevated
                            )
                            .border(
                                1.dp,
                                if (isMe) Color.White.copy(alpha = 0.25f)
                                else theme.colors.surfaceBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                if (!spoilerRevealed) spoilerRevealed = true
                                else if (mediaUrl.isNotBlank()) showVideoPlayer = true
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (isMe) Color.White else theme.colors.primaryAccent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (!spoilerRevealed) Icons.Default.Visibility else Icons.Default.PlayArrow,
                                contentDescription = "Play Video",
                                tint = if (isMe) theme.colors.primaryAccent else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (!spoilerRevealed) "Spoiler [ Video ]" else "[ Video ]",
                                style = theme.typography.bodyMedium.copy(
                                    color = if (isMe) Color.White else theme.colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                            )
                            Text(
                                text = if (!spoilerRevealed) "Tap to reveal & play" else "Tap to play",
                                style = theme.typography.caption.copy(
                                    color = if (isMe) Color.White.copy(alpha = 0.8f) else theme.colors.textSecondary,
                                    fontSize = 10.5.sp
                                )
                            )
                        }
                    }
                    if (message.content.isNotBlank()) {
                        Text(
                            text = message.content,
                            style = theme.typography.bodyMedium.copy(
                                color = if (isMe) Color.White else theme.colors.textPrimary,
                                fontSize = 13.5.sp
                            )
                        )
                    }
                }
            } else if (isMedia) {
                val mediaUrl = message.mediaUrl.orEmpty().ifBlank { message.mediaUrls.firstOrNull().orEmpty() }
                val pile = if (message.mediaUrls.isNotEmpty()) {
                    message.mediaUrls.filter { it.isNotBlank() }
                } else if (mediaUrl.isNotBlank()) {
                    listOf(mediaUrl)
                } else {
                    emptyList()
                }
                val isPile = pile.size > 1

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val mediaBoxModifier = if (isPile) {
                        Modifier
                            .fillMaxWidth(0.76f)
                            .aspectRatio(1f)
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    } else {
                        Modifier
                            .fillMaxWidth(0.74f)
                            .aspectRatio(mediaAspectRatio)
                    }
                    Box(
                        modifier = if (isPile) {
                            mediaBoxModifier
                        } else {
                            mediaBoxModifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (theme.colors.isDark) Color(0xFF14131A) else theme.colors.surfaceElevated)
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPile) {
                            val displayCards = pile.take(3)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { if (spoilerRevealed) showPile = true else spoilerRevealed = true },
                                contentAlignment = Alignment.Center
                            ) {
                                for (i in displayCards.indices.reversed()) {
                                    val cardUrl = displayCards[i]
                                    val rotation = when (i) {
                                        2 -> if (isMe) 11.5f else -11.5f
                                        1 -> if (isMe) -7.5f else 7.5f
                                        else -> 0f
                                    }
                                    val xOffset = when (i) {
                                        2 -> if (isMe) 16.dp else (-16).dp
                                        1 -> if (isMe) (-14).dp else 14.dp
                                        else -> 0.dp
                                    }
                                    val yOffset = when (i) {
                                        2 -> (-14).dp
                                        1 -> (-8).dp
                                        else -> 0.dp
                                    }
                                    val cardScale = when (i) {
                                        2 -> 0.88f
                                        1 -> 0.94f
                                        else -> 1f
                                    }
                                    val scrimAlpha = when (i) {
                                        2 -> 0.28f
                                        1 -> 0.14f
                                        else -> 0f
                                    }
                                    val elevation = when (i) {
                                        0 -> 10.dp
                                        1 -> 4.dp
                                        else -> 2.dp
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .offset(x = xOffset, y = yOffset)
                                            .rotate(rotation)
                                            .scale(cardScale)
                                            .shadow(
                                                elevation = elevation,
                                                shape = RoundedCornerShape(16.dp),
                                                clip = false
                                            )
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(if (theme.colors.isDark) Color(0xFF14131A) else theme.colors.surfaceElevated)
                                            .border(
                                                width = 1.dp,
                                                color = if (theme.colors.isDark) (if (i == 0) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.18f)) else theme.colors.surfaceBorder,
                                                shape = RoundedCornerShape(16.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (cardUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = cardUrl,
                                                contentDescription = "Photo ${i + 1} of ${pile.size}",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        }
                                        if (scrimAlpha > 0f) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        if (theme.colors.isDark) Color.Black.copy(alpha = scrimAlpha)
                                                        else Color.White.copy(alpha = scrimAlpha)
                                                    )
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(6.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (theme.colors.isDark) Color(0xDD0B0E1B) else theme.colors.surfaceElevated,
                                    border = BorderStroke(1.dp, if (theme.colors.isDark) Color(0xFF8B5CF6).copy(alpha = 0.5f) else theme.colors.surfaceBorder)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PhotoLibrary,
                                            contentDescription = null,
                                            tint = theme.colors.primaryAccent,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "${pile.size} Photos",
                                            color = theme.colors.textPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        } else if (mediaUrl.isNotBlank()) {
                            AsyncImage(
                                model = mediaUrl,
                                contentDescription = "Shared image",
                                onSuccess = { state ->
                                    val size = state.painter.intrinsicSize
                                    if (size.width > 0f && size.height > 0f) {
                                        mediaAspectRatio = (size.width / size.height).coerceIn(0.55f, 2.0f)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (theme.colors.isDark) Color(0xFF14131A) else theme.colors.surfaceElevated)
                                    .clickable {
                                        if (spoilerRevealed) showSingleImage = true else spoilerRevealed = true
                                    },
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = "Photo unavailable",
                                style = theme.typography.bodyMedium.copy(
                                    color = if (isMe) Color.White else theme.colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }

                        // Theme-aware, beautiful spoiler shield (no muddy blur artifacts)
                        if (!spoilerRevealed) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (theme.colors.isDark) Color(0xE00D0C14)
                                        else Color(0xF2F8FAFC)
                                    )
                                    .clickable { spoilerRevealed = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = if (theme.colors.isDark) Color.Black.copy(alpha = 0.72f) else Color.White,
                                    border = BorderStroke(1.dp, if (theme.colors.isDark) Color.White.copy(alpha = 0.22f) else theme.colors.surfaceBorder),
                                    shadowElevation = 3.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Visibility,
                                            contentDescription = null,
                                            tint = theme.colors.primaryAccent,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = "Reveal spoiler",
                                            color = theme.colors.textPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (message.content.isNotBlank()) {
                        Text(
                            text = message.content,
                            style = theme.typography.caption.copy(
                                color = if (isMe) Color.White.copy(alpha = 0.85f) else theme.colors.textSecondary,
                                fontSize = 11.5.sp
                            )
                        )
                    }
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
        }

                if (showSingleImage && mediaUrlForViewer.isNotBlank()) {
                    com.nebula.app.ui.components.NebulaImageViewer(
                        images = listOf(mediaUrlForViewer),
                        onDismiss = { showSingleImage = false }
                    )
                }
                if (showPile) {
                    val pileList = if (message.mediaUrls.isNotEmpty()) message.mediaUrls else listOfNotNull(message.mediaUrl)
                    com.nebula.app.ui.components.NebulaImageViewer(
                        images = pileList,
                        onDismiss = { showPile = false }
                    )
                }
                if (showVideoPlayer && mediaUrlForViewer.isNotBlank()) {
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = { showVideoPlayer = false },
                        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.94f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "[ Video ]",
                                        style = theme.typography.titleMedium.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    IconButton(onClick = { showVideoPlayer = false }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close",
                                            tint = Color.White
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black)
                                ) {
                                    AndroidView(
                                        factory = { ctx ->
                                            android.widget.VideoView(ctx).apply {
                                                setVideoURI(android.net.Uri.parse(mediaUrlForViewer))
                                                val controller = android.widget.MediaController(ctx)
                                                controller.setAnchorView(this)
                                                setMediaController(controller)
                                                setOnPreparedListener { it.start() }
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(16f / 9f)
                                    )
                                }
                            }
                        }
                    }
                }
                if (showActions) {
                    MessageActionSheet(
                        message = message,
                        // A photo has no text to correct: its sheet is unsend and info.
                        showEdit = !isMediaMessage,
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

                if (showFailedOptionsDialog) {
                    AlertDialog(
                        onDismissRequest = { showFailedOptionsDialog = false },
                        title = {
                            Text(
                                text = "Message not sent",
                                style = theme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = theme.colors.textPrimary
                                )
                            )
                        },
                        text = {
                            Text(
                                text = "Would you like to resend this message or delete it?",
                                style = theme.typography.bodyMedium.copy(color = theme.colors.textSecondary)
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showFailedOptionsDialog = false
                                    onRetry()
                                }
                            ) {
                                Text("Resend", color = theme.colors.primaryAccent, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showFailedOptionsDialog = false
                                    onDelete()
                                }
                            ) {
                                Text("Delete", color = theme.colors.errorColor, fontWeight = FontWeight.Bold)
                            }
                        },
                        containerColor = theme.colors.surfaceElevated
                    )
                }
            }
        }

        // Timestamp & status revealed when sliding finger (supports sliding to the right or left)
        val revealAlpha = (kotlin.math.abs(dragOffset) / with(density) { 30.dp.toPx() }).coerceIn(0f, 1f)
        if (revealAlpha > 0.02f) {
            val isSlidingRight = dragOffset > 0
            Row(
                modifier = Modifier
                    .align(if (isSlidingRight) Alignment.CenterStart else Alignment.CenterEnd)
                    .graphicsLayer {
                        translationX = if (isSlidingRight) {
                            -with(density) { 70.dp.toPx() } + dragOffset
                        } else {
                            with(density) { 72.dp.toPx() } + dragOffset
                        }
                        alpha = revealAlpha
                    }
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = message.timestamp.clockLabel(),
                    style = theme.typography.caption.copy(
                        color = theme.colors.textTertiary,
                        fontSize = 11.sp
                    )
                )
                if (isMe && message.deletedAt == null) {
                    when {
                        message.sendState == MessageSendState.SENDING -> {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(theme.colors.textTertiary)
                            )
                        }
                        message.sendState == MessageSendState.SENT -> {
                            val isRead = message.readAt != null
                            Icon(
                                imageVector = if (isRead) Icons.Default.DoneAll else Icons.Default.Done,
                                contentDescription = if (isRead) "Read" else "Delivered",
                                tint = if (isRead) theme.colors.primaryAccent else theme.colors.textTertiary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
