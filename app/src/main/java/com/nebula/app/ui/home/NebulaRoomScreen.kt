package com.nebula.app.ui.home

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import android.app.Activity
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.ChatBubbleOutline
import com.nebula.app.domain.model.UserProfile
import com.nebula.app.ui.components.NebulaAvatarImage
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import com.nebula.app.ui.dialogs.InRoomMediaSearchSheet
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.domain.model.NebulaRoom
import com.nebula.app.domain.model.NebulaUser
import com.nebula.app.domain.model.RoomChatMessage
import com.nebula.app.domain.model.RoomParticipant
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.PABottomSheet
import com.nebula.app.ui.dialogs.AttachmentPickerSheet
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * NebulaRoomScreen:
 * The central social shared space in NEBULA.
 * - Top Bar: Back, Room Title, Member List Icon, Room Settings Icon (Admin only), Leave Room Button
 * - YouTube Video Area: Single native seekbar/controls without duplicate overlays
 * - Participants row (Avatars with live watching status)
 * - Realtime Room Chat feed
 * - Room Composer with Paperclip attachment picker
 * - Room Members Sheet with 3-dots admin mute/unmute and member public profile click
 * - Room Settings Sheet with name, max members, mic/video toggles, and tags
 */
data class NebulaRoomActions(
    val onAcceptFriendRequest: (String) -> Unit = {},
    val onDeclineFriendRequest: (String) -> Unit = {},
    val onCancelFriendRequest: (NebulaUser) -> Unit = {},
    val onModerateParticipant: (String, Boolean?, Boolean?) -> Unit = { _, _, _ -> },
    val onKickParticipant: (String) -> Unit = {},
    val onAddFriend: (NebulaUser) -> Unit = {},
    val onMessageFriend: (NebulaUser) -> Unit = {},
    val onLeaveRoom: (newHostId: String?) -> Unit = {},
    val onRefreshRoom: () -> Unit = {},
    val onHostPlaybackSample: (Long, Boolean) -> Unit = { _, _ -> },
    val onHostVideoEnded: (Long) -> Unit = {},
    val onRestartVideo: () -> Unit = {},
    val onViewParticipantProfile: (NebulaUser) -> Unit = {},
    val onUpdateRoomSettings: (name: String?, max: Int?, mic: Boolean?, vid: Boolean?, tags: List<String>?) -> Unit = { _, _, _, _, _ -> },
    val onTransferHost: (newHostId: String?, random: Boolean) -> Unit = { _, _ -> },
    val onShareRoomInChat: (NebulaUser) -> Unit = {},
    val onTogglePlayPause: () -> Unit = {},
    val onSendRoomMessage: (String) -> Unit = {},
    val onSendReaction: (String) -> Unit = {},
    val onInviteFriend: (NebulaUser) -> Unit = {},
    val onVotePoll: (String) -> Unit = {},
    val onSeekTo: (Long) -> Unit = {},
    val onUpdateMedia: (String, String) -> Unit = { _, _ -> },
    val onAddToQueue: (title: String, mediaUrl: String) -> Unit = { _, _ -> },
    val onVoteSkipQueueItem: (String) -> Unit = {},
    val onAdvanceQueue: () -> Unit = {},
    val onRemoveQueueItem: (String) -> Unit = {}
)

@Composable
fun NebulaRoomScreen(
    room: NebulaRoom,
    currentUser: UserProfile? = null,
    friends: List<NebulaUser> = emptyList(),
    friendRequests: List<com.nebula.app.domain.model.NebulaFriendRequest> = emptyList(),
    suggestedPeople: List<NebulaUser> = emptyList(),
    queue: List<com.nebula.app.domain.model.RoomQueueItem> = emptyList(),
    actions: NebulaRoomActions = NebulaRoomActions(),
    modifier: Modifier = Modifier
) {
    val onAcceptFriendRequest = actions.onAcceptFriendRequest
    val onDeclineFriendRequest = actions.onDeclineFriendRequest
    val onCancelFriendRequest = actions.onCancelFriendRequest
    val onModerateParticipant = actions.onModerateParticipant
    val onKickParticipant = actions.onKickParticipant
    val onAddFriend = actions.onAddFriend
    val onMessageFriend = actions.onMessageFriend
    val onLeaveRoom = actions.onLeaveRoom
    val onRefreshRoom = actions.onRefreshRoom
    val onHostPlaybackSample = actions.onHostPlaybackSample
    val onHostVideoEnded = actions.onHostVideoEnded
    val onRestartVideo = actions.onRestartVideo
    val onViewParticipantProfile = actions.onViewParticipantProfile
    val onUpdateRoomSettings = actions.onUpdateRoomSettings
    val onTransferHost = actions.onTransferHost
    val onShareRoomInChat = actions.onShareRoomInChat
    val onTogglePlayPause = actions.onTogglePlayPause
    val onSendRoomMessage = actions.onSendRoomMessage
    val onSendReaction = actions.onSendReaction
    val onInviteFriend = actions.onInviteFriend
    val onVotePoll = actions.onVotePoll
    val onSeekTo = actions.onSeekTo
    val onUpdateMedia = actions.onUpdateMedia
    val onAddToQueue = actions.onAddToQueue
    val onVoteSkipQueueItem = actions.onVoteSkipQueueItem
    val onAdvanceQueue = actions.onAdvanceQueue
    val onRemoveQueueItem = actions.onRemoveQueueItem
    val roomQueue = queue
    val theme = Theme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    val chatListState = rememberLazyListState()

    // Room local state (customizable via Admin Settings)
    var roomName by remember(room.id, room.name) { mutableStateOf(room.name) }
    var maxParticipants by remember(room.id, room.maxParticipants) { mutableIntStateOf(room.maxParticipants) }
    var isMicAllowed by remember(room.id, room.isMicAllowed) { mutableStateOf(room.isMicAllowed) }
    var isVideoAllowed by remember(room.id, room.isVideoAllowed) { mutableStateOf(room.isVideoAllowed) }
    var roomTags by remember(room.id, room.tags) { mutableStateOf(room.tags) }
    var showRoomQueue by remember(room.id) { mutableStateOf(false) }

    // Match server-issued identities; display names/usernames are not authorization keys.
    val currentUserId = currentUser?.id.orEmpty()
    val roomVideoId = room.mediaItem?.youtubeVideoId?.takeIf { it.isNotBlank() }
        ?: com.nebula.app.domain.model.SharedMediaItem.extractYouTubeId(room.mediaItem?.mediaUrl ?: "")
    var guestLocallyPaused by remember(room.id, roomVideoId) { mutableStateOf(false) }
    var hostSeekCommand by remember(room.id, roomVideoId) { mutableStateOf<Pair<Long, Long>?>(null) }
    var seekCommandId by remember(room.id, roomVideoId) { mutableLongStateOf(0L) }
    val isCurrentUserAdmin = currentUserId.isNotBlank() && (
        room.hostId == currentUserId ||
            room.participants.any { it.user.id == currentUserId && it.isHost }
        )
    val seekTo = { positionMs: Long ->
        if (isCurrentUserAdmin) hostSeekCommand = (++seekCommandId) to positionMs
        onSeekTo(positionMs)
    }

    // Room participants list - Deduplicate by user ID or displayName to guarantee 1 instance per user
    val participantsList = remember(room.id, room.participants) {
        room.participants.distinctBy { it.user.id }
    }
    val hostDisplayName = room.hostName.trim().ifBlank { "Host unavailable" }

    // Dialog & Sheet States
    var showMembersSheet by remember { mutableStateOf(false) }
    var showRoomSettings by remember { mutableStateOf(false) }
    var showMediaSearchSheet by remember { mutableStateOf(false) }
    var showHostTransferDialog by remember { mutableStateOf(false) }
    var showLeaveConfirmation by remember { mutableStateOf(false) }
    var showInviteFriends by remember { mutableStateOf(false) }

    // Image/GIF Sharing Cooldown State
    var mediaSendTimestamps by remember { mutableStateOf(listOf<Long>()) }
    var cooldownSecondsRemaining by remember { mutableIntStateOf(0) }
    var pendingImageData by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(cooldownSecondsRemaining) {
        if (cooldownSecondsRemaining > 0) {
            kotlinx.coroutines.delay(1000L)
            cooldownSecondsRemaining -= 1
        }
    }

    // Photo/GIF Picker Launcher with strict format check
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri)?.lowercase() ?: ""
            val isVideo = mimeType.startsWith("video/") || mimeType.contains("video") || mimeType.contains("mp4")
            val isImageOrGif = mimeType.startsWith("image/") || mimeType.contains("gif")

            if (isVideo || !isImageOrGif) {
                Toast.makeText(context, "Blocked: Videos are not allowed. Only images and GIFs are permitted.", Toast.LENGTH_LONG).show()
                return@rememberLauncherForActivityResult
            }

            // Strict 5MB size limit
            val sizeBytes = try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
            } catch (e: Exception) { 0L }

            if (sizeBytes > 5 * 1024 * 1024L) {
                Toast.makeText(context, "Image exceeds 5MB limit. Please select an image under 5MB.", Toast.LENGTH_LONG).show()
                return@rememberLauncherForActivityResult
            }

            // Gentle rate-limiting check: max 2 media in 8 seconds
            val now = System.currentTimeMillis()
            val recent = mediaSendTimestamps.filter { now - it < 8000L }
            if (recent.size >= 2) {
                cooldownSecondsRemaining = 6
                Toast.makeText(context, "Please wait a moment before sending another image", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }

            mediaSendTimestamps = recent + now

            scope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bytes = inputStream?.use { it.readBytes() }
                    if (bytes != null && bytes.isNotEmpty()) {
                        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bitmap != null) {
                            val maxDim = maxOf(bitmap.width, bitmap.height)
                            val scaled = if (maxDim > 1024) {
                                val scale = 1024f / maxDim
                                android.graphics.Bitmap.createScaledBitmap(
                                    bitmap,
                                    (bitmap.width * scale).toInt(),
                                    (bitmap.height * scale).toInt(),
                                    true
                                )
                            } else bitmap

                            val baos = java.io.ByteArrayOutputStream()
                            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, baos)
                            val compressed = baos.toByteArray()
                            val base64 = android.util.Base64.encodeToString(compressed, android.util.Base64.NO_WRAP)
                            val dataUrl = "data:image/jpeg;base64,$base64"

                            pendingImageData = dataUrl
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to send image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Leave Room Flow: Prompts host to select next host or assign randomly if other members exist
    fun triggerLeaveFlow() {
        val otherParticipants = participantsList.filter {
            it.user.id != room.hostId && !it.isHost
        }
        if (isCurrentUserAdmin && otherParticipants.isNotEmpty()) {
            showHostTransferDialog = true
        } else {
            onLeaveRoom(null)
        }
    }

    // Android System Back Button & Hardware Back Handling
    BackHandler {
        if (showHostTransferDialog) {
            showHostTransferDialog = false
        } else if (showMediaSearchSheet) {
            showMediaSearchSheet = false
        } else if (showRoomSettings) {
            showRoomSettings = false
        } else if (showMembersSheet) {
            showMembersSheet = false
        } else {
            showLeaveConfirmation = true
        }
    }

    // Auto scroll chat to newest message
    LaunchedEffect(room.recentMessages.size) {
        if (room.recentMessages.isNotEmpty()) {
            chatListState.animateScrollToItem(room.recentMessages.lastIndex)
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        BackHandler {
            val activity = context as? Activity
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (resolveRoomPlayback(room) == RoomPlaybackTarget.None) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No video playing in landscape",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            } else {
                RoomPlayerSurface(
                    room = room,
                    isHost = isCurrentUserAdmin,
                    locallyPaused = guestLocallyPaused,
                    hostSeekCommand = hostSeekCommand,
                    onHostSample = onHostPlaybackSample,
                    onHostVideoEnded = onHostVideoEnded,
                    onRestartVideo = onRestartVideo,
                    modifier = Modifier.fillMaxSize()
                )

            // Exit Landscape button
            IconButton(
                onClick = {
                    val activity = context as? Activity
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
            ) {
                Icon(
                    imageVector = Icons.Default.FullscreenExit,
                    contentDescription = "Exit Landscape",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // 1. TOP BAR — room identity on the left, playlist and room options on the right.
        // Participants, share, settings and leave live inside the options sheet, so the
        // header stays two controls wide instead of six.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = { showLeaveConfirmation = true },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = theme.colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = roomName,
                        style = theme.typography.bodyLarge.copy(
                            color = theme.colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${participantsList.size} watching • $hostDisplayName",
                        style = theme.typography.caption.copy(
                            color = theme.colors.textSecondary,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Playlist: everything queued for this room, and the way in to add to it.
                IconButton(
                    onClick = { showRoomQueue = true },
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlaylistPlay,
                            contentDescription = "Room playlist",
                            tint = if (roomQueue.isEmpty()) theme.colors.textSecondary else theme.colors.primaryAccent,
                            modifier = Modifier.size(21.dp)
                        )
                        if (roomQueue.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(13.dp)
                                    .clip(CircleShape)
                                    .background(theme.colors.primaryAccent),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${roomQueue.size}",
                                    style = theme.typography.caption.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    fontSize = 8.sp
                                )
                            }
                        }
                    }
                }

                if (isCurrentUserAdmin) {
                    IconButton(
                        onClick = { showMediaSearchSheet = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Play something else",
                            tint = theme.colors.textPrimary,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }

                IconButton(
                    onClick = {
                        showMembersSheet = true
                        onRefreshRoom()
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Room options",
                        tint = theme.colors.textPrimary,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }

        // 2. PLAYER — edge to edge, 16:9, and nothing else competing with the video.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
        ) {
            if (resolveRoomPlayback(room) == RoomPlaybackTarget.None) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(enabled = isCurrentUserAdmin) { showMediaSearchSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.size(30.dp)
                        )
                        Text(
                            text = if (isCurrentUserAdmin) "Tap to put something on" else "The host is picking something",
                            style = theme.typography.caption.copy(color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        )
                    }
                }
            } else {
                RoomPlayerSurface(
                    room = room,
                    isHost = isCurrentUserAdmin,
                    locallyPaused = guestLocallyPaused,
                    hostSeekCommand = hostSeekCommand,
                    onHostSample = onHostPlaybackSample,
                    onHostVideoEnded = onHostVideoEnded,
                    onRestartVideo = onRestartVideo,
                    modifier = Modifier.fillMaxSize()
                )

                if (isCurrentUserAdmin) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable { showMediaSearchSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Play something else",
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                } else {
                    // A guest may still step out of the shared timeline for a moment
                    // without leaving the room.
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable { guestLocallyPaused = !guestLocallyPaused },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (guestLocallyPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (guestLocallyPaused) "Resume" else "Pause for me",
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                IconButton(
                    onClick = {
                        val activity = context as? Activity
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Landscape mode",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }


        // 5. LIVE CHAT — the feed fades into the dark under the player, so messages
        // dissolve as they scroll up instead of meeting a hard edge.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                state = chatListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 52.dp, bottom = 6.dp)
            ) {
                items(room.recentMessages) { msg ->
                    RoomChatMessageItem(message = msg)
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                theme.colors.background,
                                theme.colors.background.copy(alpha = 0f)
                            )
                        )
                    )
            )
        }

        // 6. CHAT COMPOSER WITH ATTACHMENT PAPERCLIP BUTTON
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
                // Image / GIF upload button (WhatsApp-style direct gallery picker)
                IconButton(
                    onClick = {
                        if (cooldownSecondsRemaining > 0) {
                            Toast.makeText(context, "Please wait ${cooldownSecondsRemaining}s before sharing another image", Toast.LENGTH_SHORT).show()
                            return@IconButton
                        }
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    if (cooldownSecondsRemaining > 0) {
                        Text(
                            text = "${cooldownSecondsRemaining}s",
                            style = theme.typography.caption.copy(
                                color = Color(0xFFF59E0B),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Share Image or GIF",
                            tint = theme.colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Chat Input Field
                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    textStyle = theme.typography.bodyMedium.copy(
                        color = theme.colors.textPrimary,
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(theme.colors.primaryAccent),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (inputText.isEmpty()) {
                            Text(
                                text = "Message room...",
                                style = theme.typography.bodyMedium.copy(
                                    color = theme.colors.textTertiary,
                                    fontSize = 14.sp
                                )
                            )
                        }
                        innerTextField()
                    }
                )

                // Send button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (inputText.isNotBlank()) theme.colors.primaryAccent else theme.colors.surfaceElevated)
                        .clickable(enabled = inputText.isNotBlank()) {
                            onSendRoomMessage(inputText)
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

    // --- MODALS & DIALOGS ---

    // Host Transfer Dialog when host leaves and other participants remain
    if (showHostTransferDialog) {
        val otherParticipants = participantsList.filter {
            it.user.id != room.hostId && !it.isHost
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showHostTransferDialog = false },
            title = {
                Text(
                    text = "Transfer Host & Leave",
                    style = theme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = theme.colors.textPrimary)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "As the host, select who will take over the room, or assign randomly:",
                        style = theme.typography.bodyMedium.copy(color = theme.colors.textSecondary)
                    )

                    otherParticipants.forEach { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(theme.colors.surface)
                                .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(8.dp))
                                .clickable {
                                    showHostTransferDialog = false
                                    onLeaveRoom(p.user.id)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = p.user.displayName,
                                style = theme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = theme.colors.textPrimary)
                            )
                            Text(
                                text = "Make Host",
                                style = theme.typography.caption.copy(color = theme.colors.primaryAccent, fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.colors.primaryAccent.copy(alpha = 0.15f))
                            .border(1.dp, theme.colors.primaryAccent.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .clickable {
                                showHostTransferDialog = false
                                val randomNext = otherParticipants.randomOrNull()?.user?.id
                                onLeaveRoom(randomNext)
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🎲 Randomly Assign Host & Leave",
                            style = theme.typography.bodyMedium.copy(color = theme.colors.primaryAccent, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showHostTransferDialog = false }) {
                    Text("Cancel", color = theme.colors.textSecondary)
                }
            },
            containerColor = theme.colors.surfaceElevated
        )
    }

    // Room Members Bottom Sheet
    if (showMembersSheet) {
        RoomMembersSheet(
            isVisible = showMembersSheet,
            participants = participantsList,
            friends = friends,
            friendRequests = friendRequests,
            suggestedPeople = suggestedPeople,
            currentUserId = currentUserId,
            isHost = isCurrentUserAdmin,
            onShareRoom = {
                val code = room.inviteCode
                if (code.isNullOrBlank()) {
                    Toast.makeText(context, "This room has no share link yet", Toast.LENGTH_SHORT).show()
                } else {
                    val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            android.content.Intent.EXTRA_TEXT,
                            "Join my Nebula watch party: https://nebula.app/room/$code"
                        )
                    }
                    context.startActivity(android.content.Intent.createChooser(share, "Share room"))
                }
            },
            onOpenSettings = {
                showMembersSheet = false
                showRoomSettings = true
            },
            onLeaveRoomAction = {
                showMembersSheet = false
                showLeaveConfirmation = true
            },
            onInviteRoom = {
                showMembersSheet = false
                showInviteFriends = true
            },
            onAddFriend = onAddFriend,
            onAcceptFriendRequest = onAcceptFriendRequest,
            onDeclineFriendRequest = onDeclineFriendRequest,
            onCancelFriendRequest = onCancelFriendRequest,
            onMessageFriend = onMessageFriend,
                onInviteFriend = onInviteFriend,
            onToggleAudioMute = { userId, muted -> onModerateParticipant(userId, muted, null) },
            onToggleVideoMute = { userId, cameraOff -> onModerateParticipant(userId, null, cameraOff) },
            onKickParticipant = onKickParticipant,
            canModerate = isCurrentUserAdmin,
            onSelectParticipantProfile = { memberUser ->
                showMembersSheet = false
                onViewParticipantProfile(memberUser)
            },
            onDismiss = { showMembersSheet = false }
        )
    }

    // In-Room Host Media Search & Queue Sheet
    if (showMediaSearchSheet) {
        InRoomMediaSearchSheet(
            isVisible = showMediaSearchSheet,
            onDismiss = { showMediaSearchSheet = false },
            onSubmitMediaLink = { selectedMedia ->
                val url = selectedMedia.youtubeVideoId?.takeIf { it.isNotBlank() }
                    ?.let { "https://www.youtube.com/watch?v=$it" }
                    ?: selectedMedia.mediaUrl.orEmpty()

                // Cheap offline pre-check: obvious non-videos never reach the network.
                val precheck = com.nebula.app.ui.dialogs.mediaLinkPrecheckError(url)
                when {
                    precheck != null -> precheck
                    room.id.isBlank() -> {
                        // No server round trip is possible without a room; keep the old local path.
                        onUpdateMedia(selectedMedia.title, url)
                        null
                    }
                    else -> when (val response = com.nebula.app.data.backend.NebulaApiClient.updateMedia(
                        roomId = room.id,
                        title = selectedMedia.title,
                        mediaUrl = url,
                        playbackPosition = 0L,
                        isPlaying = true
                    )) {
                        is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Success -> {
                            // Server confirmed the switch: refresh from the server instead of
                            // optimistically trusting local state, then close the sheet.
                            onRefreshRoom()
                            Toast.makeText(context, "Switched video to: ${selectedMedia.title}", Toast.LENGTH_SHORT).show()
                            null
                        }
                        is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Error -> {
                            // 422 (or any rejection): surface the server's sentence as one inline
                            // line and leave the local now-playing state exactly as it was.
                            response.message
                        }
                    }
                }
            }
        )
    }

    // Room Settings Sheet (Admin only)
    if (showRoomSettings) {
        RoomSettingsSheet(
            isVisible = showRoomSettings,
            roomName = roomName,
            maxParticipants = maxParticipants,
            isMicAllowed = isMicAllowed,
            isVideoAllowed = isVideoAllowed,
            tags = roomTags,
            onSaveSettings = { newName, newMax, newMic, newVideo, newTags ->
                roomName = newName
                maxParticipants = newMax
                isMicAllowed = newMic
                isVideoAllowed = newVideo
                roomTags = newTags
                showRoomSettings = false
                onUpdateRoomSettings(newName, newMax, newMic, newVideo, newTags)
                Toast.makeText(context, "Room settings updated successfully", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showRoomSettings = false }
        )
    }

    pendingImageData?.let { imageData ->
        val previewBitmap = remember(imageData) {
            runCatching {
                val bytes = android.util.Base64.decode(imageData.substringAfter("base64,"), android.util.Base64.DEFAULT)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }
        androidx.compose.ui.window.Dialog(onDismissRequest = { pendingImageData = null }) {
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = theme.colors.surfaceElevated
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Share in this room", color = theme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 380.dp)
                            .clip(RoundedCornerShape(16.dp)).background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (previewBitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = previewBitmap.asImageBitmap(),
                                contentDescription = "Image preview before sending",
                                modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                        } else {
                            Text("Image preview unavailable", color = Color.White)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Everyone in the room can see this image.", color = theme.colors.textSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = { pendingImageData = null }, modifier = Modifier.weight(1f)
                        ) { Text("Cancel") }
                        Button(
                            onClick = {
                                onSendRoomMessage("[IMG:$imageData]")
                                pendingImageData = null
                                Toast.makeText(context, "Photo sent", Toast.LENGTH_SHORT).show()
                            },
                            enabled = previewBitmap != null,
                            modifier = Modifier.weight(1f)
                        ) { Text("Send image") }
                    }
                }
            }
        }
    }

    RoomQueueSheet(
        isVisible = showRoomQueue,
        queue = roomQueue,
        currentUserId = currentUserId,
        isHost = isCurrentUserAdmin,
        onAddToQueue = { title, url -> onAddToQueue(title, url) },
        onVoteSkip = onVoteSkipQueueItem,
        onAdvance = onAdvanceQueue,
        onRemove = onRemoveQueueItem,
        onDismiss = { showRoomQueue = false }
    )

    if (showLeaveConfirmation) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLeaveConfirmation = false },
            title = { Text("Leave this room?") },
            text = { Text("Your playback and chat will stop syncing. You can rejoin later.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showLeaveConfirmation = false
                    triggerLeaveFlow()
                }) { Text("Leave room", color = Color(0xFFEF4444)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showLeaveConfirmation = false }) {
                    Text("Stay in room")
                }
            },
            containerColor = theme.colors.surfaceElevated
        )
    }

    if (showInviteFriends) {
        RoomInviteFriendsSheet(
            friends = friends.filterNot { friend -> participantsList.any { it.user.id == friend.id } }
                .sortedWith(compareByDescending<NebulaUser> { it.currentRoomId != null || it.isOnline }.thenBy { it.displayName.lowercase() }),
            onInvite = { friend ->
                onInviteFriend(friend)
                showInviteFriends = false
            },
            onSendInChat = { friend ->
                onShareRoomInChat(friend)
                showInviteFriends = false
            },
            onDismiss = { showInviteFriends = false }
        )
    }
}

@Composable
private fun RoomChatMessageItem(message: RoomChatMessage) {
    val theme = Theme.current
    var showFullImage by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        NebulaAvatarImage(message.senderAvatarUrl, message.senderAvatar, modifier = Modifier.size(28.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = message.senderName,
                    style = theme.typography.caption.copy(
                        color = theme.colors.primaryAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                )

                Text(
                    text = message.timeLabel,
                    style = theme.typography.caption.copy(
                        color = theme.colors.textTertiary,
                        fontSize = 10.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            val isImageMessage = (message.content.startsWith("[IMG:") && message.content.contains("]")) ||
                message.content.startsWith("data:image/") ||
                message.content.contains("Shared Photo")
            val rawUrl = when {
                message.content.startsWith("[IMG:") -> message.content.substringAfter("[IMG:").substringBefore("]")
                message.content.startsWith("data:image/") -> message.content
                else -> ""
            }
            if (isImageMessage) {
                val caption = if (message.content.startsWith("[IMG:")) {
                    message.content.substringAfter("]", "").trim()
                } else ""

                val decodedBitmap = remember(rawUrl) {
                    if (rawUrl.startsWith("data:image/") && rawUrl.contains("base64,")) {
                        try {
                            val base64Data = rawUrl.substringAfter("base64,")
                            val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.colors.surfaceElevated)
                        .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(12.dp))
                        .clickable { showFullImage = true }
                ) {
                    if (decodedBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = decodedBitmap.asImageBitmap(),
                            contentDescription = "Shared photo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 3f)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else if (rawUrl.isNotBlank()) {
                        coil.compose.AsyncImage(
                            model = rawUrl,
                            contentDescription = "Shared photo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 3f)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = theme.colors.primaryAccent,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Photo shared in room",
                                style = theme.typography.bodySmall.copy(color = theme.colors.textPrimary)
                            )
                        }
                    }
                }
                if (caption.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = caption,
                        style = theme.typography.bodySmall.copy(
                            color = theme.colors.textPrimary,
                            fontSize = 13.sp
                        )
                    )
                }
            } else {
                Text(
                    text = message.content,
                    style = theme.typography.bodySmall.copy(
                        color = theme.colors.textPrimary,
                        fontSize = 13.sp
                    )
                )
            }

            if (showFullImage) {
                com.nebula.app.ui.components.NebulaImageViewer(
                    images = listOf(rawUrl),
                    onDismiss = { showFullImage = false }
                )
            }
        }
    }
}