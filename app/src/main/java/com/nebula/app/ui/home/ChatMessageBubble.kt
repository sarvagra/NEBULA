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
    onJoinRoomByCode: (String) -> Unit = {}
) {
    val density = LocalDensity.current
    var showActions by remember(message.id) { mutableStateOf(false) }
    var showPile by remember(message.id) { mutableStateOf(false) }
    var showSingleImage by remember(message.id) { mutableStateOf(false) }
    val theme = Theme.current
    val isMe = message.role == MessageRole.USER
    // A room card also carries a mediaUrl, but it is a link to a watch party rather
    // than a file. It shares the borderless bubble treatment with media while keeping
    // its own content branch below.
    val isRoomCard = roomCardCode(message) != null
    val mediaUrlForViewer = message.mediaUrl.orEmpty()
    val isMediaMessage = message.deletedAt == null &&
        (message.mediaUrl != null || message.messageType == "media" || message.messageType == "video")
    var spoilerRevealed by remember(message.id) { mutableStateOf(!message.isSpoiler) }
    var videoStarted by remember(message.id) { mutableStateOf(false) }
    var videoPrepared by remember(message.id) { mutableStateOf(false) }
    var videoBuffering by remember(message.id) { mutableStateOf(false) }
    var videoBufferPercent by remember(message.id) { mutableIntStateOf(0) }
    var videoFailed by remember(message.id) { mutableStateOf(false) }
    // Default to a landscape-ish box until the real dimensions arrive.
    val isVideoMessage = message.mediaMimeType?.startsWith("video/") == true || message.messageType == "video"
    var mediaAspectRatio by remember(message.id) { mutableStateOf(4f / 3f) }
    // The picture is only painted once its real ratio is known. Painting it into a
    // guessed 4:3 box first made a portrait photo sit letterboxed inside a box that
    // then jumped to the right size underneath it.
    var mediaRatioKnown by remember(message.id) { mutableStateOf(false) }

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

        // A failed send gets the same treatment every messenger uses: a red mark beside
        // the bubble, tappable, rather than a caption the user has to read and decode.
        // It sits outside the bubble so it stays visible on image messages too.
        val failedToSend = isMe && message.deletedAt == null && message.sendState == MessageSendState.FAILED
        if (failedToSend) {
            Box(
                modifier = Modifier
                    .padding(end = 5.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(theme.colors.errorColor)
                    .clickable(onClick = onRetry)
                    .semantics { contentDescription = "Not sent. Tap to retry" },
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
                    // Tapping a message that did not send retries it. Everything else
                    // stays inert so the thread is not full of buttons.
                    onClick = {
                        if (isMe && message.sendState == MessageSendState.FAILED) onRetry()
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
                    if (isMediaMessage) Modifier
                    else Modifier.background(if (isMe) theme.colors.primaryAccent else theme.colors.surface)
                )
                .then(
                    if (isMediaMessage) Modifier
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
                    horizontal = if (isMediaMessage) 0.dp else 14.dp,
                    vertical = if (isMediaMessage) 0.dp else 10.dp
                )
        ) {
            val isMedia = message.deletedAt == null && !isRoomCard &&
                (message.mediaUrl != null || message.messageType == "media")
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
            } else if (isMedia) {
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
                        val pile = message.mediaUrls
                        if (pile.size > 1) {
                            // One stack, not a grid: the photos sit on top of each other
                            // with a slight lean, and the whole stack opens together in
                            // the viewer, which swipes between them.
                            val visible = pile.takeLast(3)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { if (spoilerRevealed) showPile = true else spoilerRevealed = true }
                                    .then(if (!spoilerRevealed) Modifier.blur(18.dp) else Modifier)
                                    .padding(14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                visible.forEachIndexed { index, _ ->
                                    val depth = visible.lastIndex - index
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .padding(start = (depth * 8).dp, bottom = (depth * 6).dp)
                                            .rotate(if (depth % 2 == 0) -6f else 6f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (depth == 0) Color.Black.copy(alpha = 0.35f)
                                                else if (isMe) theme.colors.primaryAccent.copy(alpha = 0.55f)
                                                else theme.colors.surfaceElevated
                                            )
                                            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (depth == 0) {
                                            val topUrl = pile.last()
                                            if (topUrl.isNotBlank()) {
                                                AsyncImage(
                                                    model = topUrl,
                                                    contentDescription = "Shared photo",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                )
                                            }
                                        }
                                    }
                                }
                                Surface(
                                    modifier = Modifier.align(Alignment.BottomEnd),
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.Black.copy(alpha = 0.7f)
                                ) {
                                    Text(
                                        text = "${pile.size}",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else if (isVideo && mediaUrl.isNotBlank()) {
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
                                onSuccess = { state ->
                                    // Coil hands back the picture's own aspect; the box is
                                    // resized to it before any pixels are drawn.
                                    val size = state.painter.intrinsicSize
                                    if (size.width > 0f && size.height > 0f) {
                                        mediaAspectRatio = (size.width / size.height).coerceIn(0.5f, 2.2f)
                                    }
                                    mediaRatioKnown = true
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable {
                                        if (spoilerRevealed) showSingleImage = true else spoilerRevealed = true
                                    }
                                    .then(if (!spoilerRevealed) Modifier.blur(18.dp) else Modifier),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                loading = {
                                    // Real loading feedback: a shimmering sweep plus an
                                    // indeterminate spinner, so a slow image is visibly
                                    // working rather than looking stuck.
                                    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "img-load")
                                    val shimmer by transition.animateFloat(
                                        initialValue = 0f,
                                        targetValue = 1f,
                                        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                                            animation = androidx.compose.animation.core.tween(1100),
                                            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
                                        ),
                                        label = "shimmer"
                                    )
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .background(
                                                androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                    colors = listOf(
                                                        theme.colors.surfaceElevated.copy(alpha = 0.55f),
                                                        theme.colors.surfaceBorder.copy(alpha = 0.75f),
                                                        theme.colors.surfaceElevated.copy(alpha = 0.55f)
                                                    ),
                                                    startX = shimmer * 900f - 450f,
                                                    endX = shimmer * 900f + 450f
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        androidx.compose.material3.CircularProgressIndicator(
                                            modifier = Modifier.size(22.dp),
                                            strokeWidth = 2.dp,
                                            color = theme.colors.primaryAccent
                                        )
                                    }
                                },
                                error = {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("Image unavailable", color = if (isMe) Color.White else theme.colors.textSecondary, fontSize = 12.sp)
                                    }
                                },
                                success = { if (mediaRatioKnown) SubcomposeAsyncImageContent() }
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
                        // The receipt rides on the picture itself; a caption row of
                        // "Attachment" plus a tick underneath it was the awkward part.
                        if (isMe && message.deletedAt == null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                when {
                                    message.sendState == MessageSendState.SENDING -> Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                    )
                                    message.sendState == MessageSendState.SENT -> {
                                        val seen = message.readAt != null
                                        val delivered = seen || message.deliveredAt != null
                                        Icon(
                                            imageVector = if (delivered) Icons.Default.DoneAll else Icons.Default.Done,
                                            contentDescription = when {
                                                seen -> "Seen"
                                                delivered -> "Delivered"
                                                else -> "Sent"
                                            },
                                            tint = if (seen) theme.colors.primaryAccent else Color.White,
                                            modifier = Modifier.size(13.dp)
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

            // Delivery state for messages this device sent. Only a server-confirmed
            // message is shown without a marker, so a lost connection can never look
            // like a delivered message.
            // Only the transient state needs words; failure is the red badge beside the
            // bubble. A failed message stays tappable as a whole, not just on the icon.
            // One marker for every state this device knows about, in the order a
            // message moves through it: dot while it is still going out, one tick once
            // the server has it, two ticks when the recipient's device has it, and two
            // accent ticks when they have opened it.
            if (isMe && message.deletedAt == null && !isMediaMessage) {
                val markerColor = Color.White.copy(alpha = 0.75f)
                Row(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    when {
                        message.sendState == MessageSendState.SENDING -> Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(markerColor)
                        )
                        message.sendState == MessageSendState.SENT -> {
                            val seen = message.readAt != null
                            val delivered = seen || message.deliveredAt != null
                            Icon(
                                imageVector = if (delivered) Icons.Default.DoneAll else Icons.Default.Done,
                                contentDescription = when {
                                    seen -> "Seen"
                                    delivered -> "Delivered"
                                    else -> "Sent"
                                },
                                tint = if (seen) theme.colors.primaryAccent else markerColor,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
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
                    com.nebula.app.ui.components.NebulaImageViewer(
                        images = message.mediaUrls,
                        onDismiss = { showPile = false }
                    )
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
            }
        }

        // Instagram-style timestamp & tick mark revealed on the far right when sliding left
        val revealAlpha = (kotlin.math.abs(dragOffset) / with(density) { 35.dp.toPx() }).coerceIn(0f, 1f)
        if (revealAlpha > 0.02f) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .graphicsLayer {
                        translationX = with(density) { 72.dp.toPx() } + dragOffset
                        alpha = revealAlpha
                    }
                    .padding(end = 4.dp),
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
                // A message that never reached the server cannot be "delivered"; showing
                // a tick there would contradict the red mark.
                if (isMe && message.deletedAt == null && message.sendState == MessageSendState.SENT) {
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
