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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
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
import android.webkit.WebChromeClient
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
import com.nebula.app.ui.dialogs.FriendProfileSheet
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
@Composable
fun NebulaRoomScreen(
    room: NebulaRoom,
    currentUser: UserProfile? = null,
    friends: List<NebulaUser> = emptyList(),
    friendRequests: List<com.nebula.app.domain.model.NebulaFriendRequest> = emptyList(),
    suggestedPeople: List<NebulaUser> = emptyList(),
    onAcceptFriendRequest: (String) -> Unit = {},
    onDeclineFriendRequest: (String) -> Unit = {},
    onCancelFriendRequest: (NebulaUser) -> Unit = {},
    onModerateParticipant: (String, Boolean?, Boolean?) -> Unit = { _, _, _ -> },
    onKickParticipant: (String) -> Unit = {},
    onAddFriend: (NebulaUser) -> Unit = {},
    onMessageFriend: (NebulaUser) -> Unit = {},
    onLeaveRoom: (newHostId: String?) -> Unit,
    onRefreshRoom: () -> Unit = {},
    onHostPlaybackSample: (Long, Boolean) -> Unit = { _, _ -> },
    onUpdateRoomSettings: (name: String?, max: Int?, mic: Boolean?, vid: Boolean?, tags: List<String>?) -> Unit = { _, _, _, _, _ -> },
    onTransferHost: (newHostId: String?, random: Boolean) -> Unit = { _, _ -> },
    onTogglePlayPause: () -> Unit,
    onSendRoomMessage: (String) -> Unit,
    onSendReaction: (String) -> Unit,
    onInviteFriend: (NebulaUser) -> Unit = {},
    onVotePoll: (String) -> Unit = {},
    onSeekTo: (Long) -> Unit = {},
    onUpdateMedia: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
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

    // Match server-issued identities; display names/usernames are not authorization keys.
    val currentUserId = currentUser?.id.orEmpty()
    val roomVideoId = room.mediaItem?.youtubeVideoId?.takeIf { it.isNotBlank() }
        ?: com.nebula.app.domain.model.SharedMediaItem.extractYouTubeId(room.mediaItem?.mediaUrl ?: "")
    var guestLocallyPaused by remember(room.id, roomVideoId) { mutableStateOf(false) }
    val isCurrentUserAdmin = currentUserId.isNotBlank() && (
        room.hostId == currentUserId ||
            room.participants.any { it.user.id == currentUserId && it.isHost }
        )

    // Room participants list - Deduplicate by user ID or displayName to guarantee 1 instance per user
    val participantsList = remember(room.id, room.participants) {
        room.participants.distinctBy { it.user.id }
    }
    val hostDisplayName = room.hostName.trim().ifBlank { "Host unavailable" }

    // Dialog & Sheet States
    var showMembersSheet by remember { mutableStateOf(false) }
    var showRoomSettings by remember { mutableStateOf(false) }
    var showMediaSearchSheet by remember { mutableStateOf(false) }
    var selectedMemberForProfile by remember { mutableStateOf<NebulaUser?>(null) }
    var showHostTransferDialog by remember { mutableStateOf(false) }
    var showLeaveConfirmation by remember { mutableStateOf(false) }
    var showInviteFriends by remember { mutableStateOf(false) }

    // Image/GIF Sharing Anti-Spam & Cooldown State
    var mediaSendTimestamps by remember { mutableStateOf(listOf<Long>()) }
    var cooldownSecondsRemaining by remember { mutableIntStateOf(0) }
    var isMediaBanned by remember { mutableStateOf(false) }
    var pendingImageData by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(cooldownSecondsRemaining) {
        if (cooldownSecondsRemaining > 0) {
            kotlinx.coroutines.delay(1000L)
            cooldownSecondsRemaining -= 1
        }
    }

    // Photo/GIF Picker Launcher with strict format check & spam detection
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

            // Rate-limiting check: max 2 media in 10 seconds
            val now = System.currentTimeMillis()
            val recent = mediaSendTimestamps.filter { now - it < 10000L }
            if (recent.size >= 2) {
                cooldownSecondsRemaining = 15
                Toast.makeText(context, "Slow down! Image sharing is on cooldown for 15s to prevent spam.", Toast.LENGTH_LONG).show()
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
        } else if (selectedMemberForProfile != null) {
            selectedMemberForProfile = null
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
            val videoId = room.mediaItem?.youtubeVideoId?.takeIf { it.isNotBlank() }
                ?: com.nebula.app.domain.model.SharedMediaItem.extractYouTubeId(room.mediaItem?.mediaUrl ?: "")

            if (videoId.isNullOrBlank()) {
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
                NebulaYoutubePlayer(
                    roomId = room.id,
                    videoId = videoId,
                    playbackPositionMs = room.playbackPositionMs,
                    isPlaying = room.isPlaying,
                    isHost = isCurrentUserAdmin,
                    locallyPaused = guestLocallyPaused,
                    onHostSample = onHostPlaybackSample,
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
        // 1. TOP BAR: Back, Room Info, Members Icon, Settings (Admin), Leave Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                IconButton(
                    onClick = { showInviteFriends = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = "Invite friends to room",
                        tint = theme.colors.primaryAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = roomName,
                        style = theme.typography.bodyLarge.copy(
                            color = theme.colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "${participantsList.size} watching • Hosted by $hostDisplayName",
                        style = theme.typography.caption.copy(
                            color = theme.colors.textSecondary,
                            fontSize = 11.5.sp
                        )
                    )
                }
            }

            // Top Right Action Cluster: Members, Admin Settings, Leave
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // View Participants / Members Icon Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.colors.surface)
                        .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(8.dp))
                        .clickable {
                            showMembersSheet = true
                            onRefreshRoom()
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = "View Participants",
                            tint = theme.colors.textPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "${participantsList.size}",
                            style = theme.typography.caption.copy(
                                color = theme.colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }

                // Settings Option (Accessible only to Admins)
                if (isCurrentUserAdmin) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.colors.surface)
                            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(8.dp))
                            .clickable { showMediaSearchSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search & Queue Video",
                            tint = theme.colors.primaryAccent,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.colors.surface)
                            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(8.dp))
                            .clickable { showRoomSettings = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Room Settings",
                            tint = theme.colors.textPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                // Leave / Exit Room Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .clickable { triggerLeaveFlow() }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Leave",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Leave",
                            style = theme.typography.caption.copy(
                                color = Color(0xFFEF4444),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }

        // 2. LARGE SYNCHRONIZED PLAYER AREA (16:9 cinema layout with clean single seekbar)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF181B26),
                            Color(0xFF0F1118)
                        )
                    )
                )
                .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
        ) {
            // 2. LARGE SYNCHRONIZED PLAYER AREA (16:9 cinema layout with clean single seekbar)
            val videoId = room.mediaItem?.youtubeVideoId?.takeIf { it.isNotBlank() }
                ?: com.nebula.app.domain.model.SharedMediaItem.extractYouTubeId(room.mediaItem?.mediaUrl ?: "")

            if (videoId.isNullOrBlank()) {
                // Room in Idle Position: Clean placeholder, zero network errors
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = "Idle Room",
                        tint = theme.colors.textSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Room in Idle Position",
                        style = theme.typography.bodyLarge.copy(
                            color = theme.colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "No video playing • Search YouTube to stream together",
                        style = theme.typography.caption.copy(
                            color = theme.colors.textSecondary,
                            fontSize = 11.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { showMediaSearchSheet = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFCC0000)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Search YouTube",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                NebulaYoutubePlayer(
                    roomId = room.id,
                    videoId = videoId,
                    playbackPositionMs = room.playbackPositionMs,
                    isPlaying = room.isPlaying,
                    isHost = isCurrentUserAdmin,
                    locallyPaused = guestLocallyPaused,
                    onHostSample = onHostPlaybackSample,
                    modifier = Modifier.fillMaxSize()
                )

                // Fullscreen / Landscape Toggle Button
                IconButton(
                    onClick = {
                        val activity = context as? Activity
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Landscape mode",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

            }
        }

        // 2b. PLAYBACK CONTROLS BAR (Host Controls & Participant Sync Status)
        val hasVideo = !room.mediaItem?.youtubeVideoId.isNullOrBlank() ||
            !room.mediaItem?.mediaUrl.isNullOrBlank()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (isCurrentUserAdmin) {
                if (hasVideo) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Rewind 10s
                        IconButton(
                            onClick = { onSeekTo(maxOf(0L, room.playbackPositionMs - 10000L)) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastRewind,
                                contentDescription = "Rewind 10s",
                                tint = theme.colors.textPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Play / Pause
                        IconButton(
                            onClick = onTogglePlayPause,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (room.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (room.isPlaying) "Pause" else "Play",
                                tint = theme.colors.primaryAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Forward 10s
                        IconButton(
                            onClick = { onSeekTo(room.playbackPositionMs + 10000L) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "Forward 10s",
                                tint = theme.colors.textPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Stop / Reset Video
                        IconButton(
                            onClick = {
                                if (room.isPlaying) onTogglePlayPause()
                                onSeekTo(0L)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop Video",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // In-room Change / Queue Video
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.colors.surface)
                            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(8.dp))
                            .clickable { showMediaSearchSheet = true }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Video",
                            tint = theme.colors.primaryAccent,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Switch Video",
                            style = theme.typography.caption.copy(
                                color = theme.colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.colors.surface.copy(alpha = 0.5f))
                            .border(1.dp, theme.colors.surfaceBorder.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .clickable { showMediaSearchSheet = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = "Idle",
                                tint = theme.colors.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Room Idle • Ready to stream",
                                style = theme.typography.caption.copy(
                                    color = theme.colors.textSecondary,
                                    fontSize = 11.sp
                                )
                            )
                        }
                        Text(
                            text = "Search YouTube",
                            style = theme.typography.caption.copy(
                                color = Color(0xFFEF4444),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.colors.surface.copy(alpha = 0.6f))
                        .border(1.dp, theme.colors.surfaceBorder.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Host control",
                        tint = theme.colors.textSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (hasVideo) "Synchronized with host ($hostDisplayName)" else "Waiting for host to pick video",
                        style = theme.typography.caption.copy(
                            color = theme.colors.textSecondary,
                            fontSize = 11.5.sp
                        )
                    )
                }
                if (hasVideo) {
                    IconButton(
                        onClick = { guestLocallyPaused = !guestLocallyPaused },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (guestLocallyPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (guestLocallyPaused) "Resume for me at host position" else "Pause for me",
                            tint = theme.colors.primaryAccent
                        )
                    }
                }
            }
        }


        // 4. INTERACTIVE ROOM POLL (Poll Play) if active
        if (room.activePoll != null) {
            val poll = room.activePoll
            val totalVotes = poll.options.sumOf { it.votes }.coerceAtLeast(1)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.colors.surface)
                    .border(1.dp, theme.colors.primaryAccent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(theme.colors.primaryAccent.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "POLL PLAY",
                                style = theme.typography.caption.copy(
                                    color = theme.colors.primaryAccent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.5.sp
                                )
                            )
                        }
                        Text(
                            text = poll.question,
                            style = theme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = theme.colors.textPrimary,
                                fontSize = 13.sp
                            )
                        )
                    }
                    Text(
                        text = "${poll.options.sumOf { it.votes }} votes",
                        style = theme.typography.caption.copy(
                            color = theme.colors.textSecondary,
                            fontSize = 11.sp
                        )
                    )
                }

                poll.options.forEach { opt ->
                    val isUserVote = opt.id == poll.userVotedOptionId
                    val fraction = opt.votes.toFloat() / totalVotes.toFloat()

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.colors.surfaceElevated)
                            .clickable { onVotePoll(opt.id) }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(34.dp)
                                .background(if (isUserVote) theme.colors.primaryAccent.copy(alpha = 0.3f) else theme.colors.surfaceBorder.copy(alpha = 0.5f))
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = opt.text,
                                style = theme.typography.caption.copy(
                                    fontWeight = if (isUserVote) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isUserVote) theme.colors.primaryAccent else theme.colors.textPrimary,
                                    fontSize = 12.sp
                                )
                            )
                            Text(
                                text = "${opt.votes}",
                                style = theme.typography.caption.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = theme.colors.textSecondary,
                                    fontSize = 11.5.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        // 5. REALTIME ROOM CHAT FEED
        LazyColumn(
            state = chatListState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            items(room.recentMessages) { msg ->
                RoomChatMessageItem(message = msg)
            }
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
                        if (isMediaBanned) {
                            Toast.makeText(context, "You are banned from sending files in this room due to repeated spam", Toast.LENGTH_LONG).show()
                            return@IconButton
                        }
                        if (cooldownSecondsRemaining > 0) {
                            isMediaBanned = true
                            Toast.makeText(context, "Attempted upload during cooldown! You are now banned from sending files in this room.", Toast.LENGTH_LONG).show()
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
                            tint = if (isMediaBanned) Color(0xFFEF4444) else theme.colors.textSecondary,
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
                selectedMemberForProfile = memberUser
            },
            onDismiss = { showMembersSheet = false }
        )
    }

    // In-Room Host Media Search & Queue Sheet
    if (showMediaSearchSheet) {
        InRoomMediaSearchSheet(
            isVisible = showMediaSearchSheet,
            onDismiss = { showMediaSearchSheet = false },
            onSelectMedia = { selectedMedia ->
                val url = selectedMedia.youtubeVideoId?.let { "https://www.youtube.com/watch?v=$it" } ?: selectedMedia.mediaUrl
                onUpdateMedia(selectedMedia.title, url)
                showMediaSearchSheet = false
                Toast.makeText(context, "Switched video to: ${selectedMedia.title}", Toast.LENGTH_SHORT).show()
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

    // Public Profile Sheet for clicked member
    if (selectedMemberForProfile != null) {
        FriendProfileSheet(
            isVisible = true,
            user = selectedMemberForProfile,
            onMessage = { u ->
                selectedMemberForProfile = null
                onSendRoomMessage("@${u.displayName} ")
            },
            onInviteToRoom = {
                selectedMemberForProfile = null
            },
            onDismiss = {
                selectedMemberForProfile = null
            }
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
            friends = friends.filterNot { friend -> participantsList.any { it.user.id == friend.id } },
            onInvite = { friend ->
                onInviteFriend(friend)
                showInviteFriends = false
            },
            onDismiss = { showInviteFriends = false }
        )
    }
}

@Composable
private fun NebulaYoutubePlayer(
    roomId: String,
    videoId: String,
    playbackPositionMs: Long,
    isPlaying: Boolean,
    isHost: Boolean,
    locallyPaused: Boolean,
    onHostSample: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val player = remember(roomId, videoId) { mutableStateOf<WebView?>(null) }
    val latestSample = remember { AtomicReference(onHostSample) }
    val latestIsHost = remember { AtomicBoolean(isHost) }
    SideEffect {
        latestSample.set(onHostSample)
        latestIsHost.set(isHost)
    }
    DisposableEffect(player.value) {
        onDispose {
            player.value?.apply {
                stopLoading()
                removeJavascriptInterface("Nebula")
                destroy()
            }
            player.value = null
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun sample(seconds: Double, state: Int) {
                        if (latestIsHost.get() && (state == 1 || state == 3)) {
                            latestSample.get().invoke((seconds * 1000.0).toLong().coerceAtLeast(0L), true)
                        } else if (latestIsHost.get() && state == 2) {
                            latestSample.get().invoke((seconds * 1000.0).toLong().coerceAtLeast(0L), false)
                        }
                    }
                }, "Nebula")
                player.value = this
                val html = """
                    <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"></head>
                    <body style="margin:0;background:#000"><div id="p"></div><script src="https://www.youtube.com/iframe_api"></script><script>
                    let player, pending={position:$playbackPositionMs,playing:$isPlaying,timestamp:Date.now()}, localPause=false, ready=false;
                    function onYouTubeIframeAPIReady(){player=new YT.Player('p',{width:'100%',height:'100%',videoId:'$videoId',playerVars:{autoplay:0,controls:0,disablekb:1,playsinline:1,rel:0,iv_load_policy:3,enablejsapi:1},events:{onReady:()=>{ready=true;pending=window.__nebulaCommand||pending;applyPending();},onStateChange:(e)=>{if(window.Nebula){try{Nebula.sample(player.getCurrentTime()||0,e.data)}catch(_){}}}}});}
                    function applyPending(){if(!ready||!pending)return;let s=pending; if(localPause){player.pauseVideo();return;}let target=Math.max(0,(s.position+(s.playing?Math.max(0,Date.now()-s.timestamp):0))/1000);let now=player.getCurrentTime()||0; if(Math.abs(now-target)>0.65)player.seekTo(target,true); if(s.playing){if(player.getPlayerState()!==1)player.playVideo();}else if(player.getPlayerState()===1||player.getPlayerState()===3)player.pauseVideo();}
                    function sync(position,playing,timestamp,pauseLocal){pending={position:position,playing:playing,timestamp:timestamp};window.__nebulaCommand=pending;localPause=pauseLocal;applyPending();}
                    setInterval(()=>{if(ready&&player&&window.Nebula){try{Nebula.sample(player.getCurrentTime()||0,player.getPlayerState())}catch(_){}}},250);
                    </script></body></html>
                """.trimIndent()
                loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)
            }
        },
        update = { /* UI recompositions never issue playback commands. */ }
    )
    val syncPosition = if (isHost) 0L else playbackPositionMs
    LaunchedEffect(player.value, videoId, syncPosition, isPlaying, locallyPaused) {
        player.value?.evaluateJavascript(
            "window.__nebulaCommand={position:$playbackPositionMs,playing:$isPlaying,timestamp:${System.currentTimeMillis()}};if(typeof sync==='function')sync($playbackPositionMs,$isPlaying,${System.currentTimeMillis()},$locallyPaused);",
            null
        )
    }
}

/**
 * RoomMembersSheet:
 * Displays all participants in the room.
 * - Each row: Avatar, name, host tag, status indicators (mic/camera)
 * - 3 dots menu beside each member: Admin toggle for audio/video mute
 * - Clicking anywhere else on row: opens member's public profile
 * - Dismiss / click outside: returns simply to the room
 */
@Composable
fun RoomMembersSheet(
    isVisible: Boolean,
    participants: List<RoomParticipant>,
    friends: List<NebulaUser>,
    friendRequests: List<com.nebula.app.domain.model.NebulaFriendRequest>,
    suggestedPeople: List<NebulaUser>,
    currentUserId: String,
    onAddFriend: (NebulaUser) -> Unit,
    onAcceptFriendRequest: (String) -> Unit,
    onDeclineFriendRequest: (String) -> Unit,
    onCancelFriendRequest: (NebulaUser) -> Unit,
    onMessageFriend: (NebulaUser) -> Unit,
    onInviteFriend: (NebulaUser) -> Unit,
    onToggleAudioMute: (String, Boolean) -> Unit,
    onToggleVideoMute: (String, Boolean) -> Unit,
    onKickParticipant: (String) -> Unit,
    canModerate: Boolean,
    onSelectParticipantProfile: (NebulaUser) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = Theme.current
    val context = LocalContext.current
    var menuOpenUserId by remember { mutableStateOf<String?>(null) }
    var kickConfirmationUser by remember { mutableStateOf<NebulaUser?>(null) }

    PABottomSheet(
        isVisible = isVisible,
        onDismissRequest = onDismiss,
        title = "Room Members (${participants.size})"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Tap a member to view profile, manage friendship, or adjust media controls.",
                style = theme.typography.caption.copy(
                    color = theme.colors.textTertiary,
                    fontSize = 11.5.sp
                ),
                modifier = Modifier.padding(bottom = 4.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(participants) { participant ->
                    val user = participant.user
                    val isAudioMuted = !participant.isAudioActive
                    val isVideoMuted = !participant.isVideoActive
                    val isMe = user.id.isNotBlank() && user.id == currentUserId
                    val isFriend = friends.any { it.id == user.id }
                    val outgoingRequest = suggestedPeople.any { it.id == user.id && it.friendRequestPending }
                    val incomingRequest = friendRequests.firstOrNull { it.sender.id == user.id }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(theme.colors.surface)
                            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // User Avatar & Name (Clickable to open profile)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { if (!isMe) onSelectParticipantProfile(user) }
                        ) {
                            Box(contentAlignment = Alignment.BottomEnd) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(theme.colors.surfaceElevated)
                                        .border(1.dp, theme.colors.surfaceBorder, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    NebulaAvatarImage(
                                        avatarUrl = user.avatarUrl,
                                        fallbackLetter = user.avatarLetter,
                                        modifier = Modifier.size(38.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (user.isOnline) theme.colors.successColor else theme.colors.textTertiary)
                                        .border(1.5.dp, theme.colors.surface, CircleShape)
                                )
                            }

                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = user.displayName,
                                        style = theme.typography.bodyMedium.copy(
                                            color = theme.colors.textPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.5.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (participant.isHost) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(theme.colors.primaryAccent.copy(alpha = 0.15f))
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = "HOST",
                                                style = theme.typography.caption.copy(
                                                    color = theme.colors.primaryAccent,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 9.sp
                                                )
                                            )
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Audio status indicator
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isAudioMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                            contentDescription = null,
                                            tint = if (isAudioMuted) Color(0xFFEF4444) else theme.colors.successColor,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = if (isAudioMuted) "Muted" else "Mic On",
                                            style = theme.typography.caption.copy(
                                                color = if (isAudioMuted) Color(0xFFEF4444) else theme.colors.textSecondary,
                                                fontSize = 10.sp
                                            )
                                        )
                                    }

                                    // Video status indicator
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isVideoMuted) Icons.Default.VideocamOff else Icons.Default.Videocam,
                                            contentDescription = null,
                                            tint = if (isVideoMuted) Color(0xFFEF4444) else theme.colors.successColor,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = if (isVideoMuted) "Camera Off" else "Camera On",
                                            style = theme.typography.caption.copy(
                                                color = if (isVideoMuted) Color(0xFFEF4444) else theme.colors.textSecondary,
                                                fontSize = 10.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Friendship controls reflect server state; self is never actionable.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isMe) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(theme.colors.surfaceElevated)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "You",
                                        style = theme.typography.caption.copy(
                                            color = theme.colors.textSecondary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            } else if (incomingRequest != null) {
                                TextButton(onClick = { onAcceptFriendRequest(incomingRequest.id) }) {
                                    Text("Accept", color = theme.colors.primaryAccent)
                                }
                                TextButton(onClick = { onDeclineFriendRequest(incomingRequest.id) }) {
                                    Text("Decline", color = theme.colors.textSecondary)
                                }
                            } else if (outgoingRequest) {
                                TextButton(onClick = { onCancelFriendRequest(user) }) {
                                    Text("Cancel request", color = theme.colors.textSecondary)
                                }
                            } else if (!isFriend) {
                                Button(
                                    onClick = { onAddFriend(user) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = theme.colors.primaryAccent,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PersonAdd,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "Add Friend",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                Text("Friend", color = theme.colors.successColor, fontSize = 11.sp)
                            }

                            // Only the host can change another participant's room state.
                            if (canModerate && !isMe && !participant.isHost) {
                            Box {
                                IconButton(
                                    onClick = { menuOpenUserId = user.id },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Member options",
                                        tint = theme.colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = menuOpenUserId == user.id,
                                    onDismissRequest = { menuOpenUserId = null },
                                    modifier = Modifier.background(theme.colors.surfaceElevated)
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (isAudioMuted) "Unmute Audio" else "Mute Audio",
                                                style = theme.typography.bodySmall.copy(color = theme.colors.textPrimary)
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (isAudioMuted) Icons.Default.Mic else Icons.Default.MicOff,
                                                contentDescription = null,
                                                tint = if (isAudioMuted) theme.colors.successColor else Color(0xFFEF4444)
                                            )
                                        },
                                        onClick = {
                                            onToggleAudioMute(user.id, !isAudioMuted)
                                            menuOpenUserId = null
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (isVideoMuted) "Turn On Video" else "Turn Off Video",
                                                style = theme.typography.bodySmall.copy(color = theme.colors.textPrimary)
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (isVideoMuted) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                                contentDescription = null,
                                                tint = if (isVideoMuted) theme.colors.successColor else Color(0xFFEF4444)
                                            )
                                        },
                                        onClick = {
                                            onToggleVideoMute(user.id, !isVideoMuted)
                                            menuOpenUserId = null
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Remove from room", color = Color(0xFFEF4444)) },
                                        onClick = {
                                            kickConfirmationUser = user
                                            menuOpenUserId = null
                                        }
                                    )
                                }
                            }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    kickConfirmationUser?.let { target ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { kickConfirmationUser = null },
            title = { Text("Remove ${target.displayName}?") },
            text = { Text("They will be removed from this room and cannot rejoin until they join again.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    onKickParticipant(target.id)
                    kickConfirmationUser = null
                }) { Text("Remove", color = Color(0xFFEF4444)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { kickConfirmationUser = null }) { Text("Cancel") }
            },
            containerColor = theme.colors.surfaceElevated
        )
    }
}

@Composable
private fun RoomInviteFriendsSheet(
    friends: List<NebulaUser>,
    onInvite: (NebulaUser) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = Theme.current
    PABottomSheet(isVisible = true, onDismissRequest = onDismiss, title = "Invite a friend") {
        if (friends.isEmpty()) {
            Text(
                "All your friends are already here",
                color = theme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(friends, key = { it.id }) { friend ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(theme.colors.surface).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        NebulaAvatarImage(friend.avatarUrl, friend.avatarLetter, modifier = Modifier.size(38.dp))
                        Text(friend.displayName, color = theme.colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1)
                        Button(onClick = { onInvite(friend) }) { Text("Invite") }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * RoomSettingsSheet:
 * Admin controls for the active room:
 * - Room Name
 * - Max participants count
 * - Mic permissions toggle
 * - Video permissions toggle
 * - Room tags selector
 */
@Composable
fun RoomSettingsSheet(
    isVisible: Boolean,
    roomName: String,
    maxParticipants: Int,
    isMicAllowed: Boolean,
    isVideoAllowed: Boolean,
    tags: List<String>,
    onSaveSettings: (String, Int, Boolean, Boolean, List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = Theme.current
    var nameState by remember(roomName, isVisible) { mutableStateOf(roomName) }
    var maxState by remember(maxParticipants, isVisible) { mutableIntStateOf(maxParticipants) }
    var micState by remember(isMicAllowed, isVisible) { mutableStateOf(isMicAllowed) }
    var videoState by remember(isVideoAllowed, isVisible) { mutableStateOf(isVideoAllowed) }
    var tagsState by remember(tags, isVisible) { mutableStateOf(tags.toSet()) }

    val availableTags = listOf("Cinema", "Music", "WatchParty", "Chill", "Study", "Anime", "Gaming", "Lounge")
    val maxOptions = listOf(5, 10, 20, 50, 100)

    PABottomSheet(
        isVisible = isVisible,
        onDismissRequest = onDismiss,
        title = "Room Settings"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Room Name
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "ROOM NAME",
                    style = theme.typography.caption.copy(
                        color = theme.colors.textSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontSize = 11.sp
                    )
                )

                OutlinedTextField(
                    value = nameState,
                    onValueChange = { nameState = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.colors.primaryAccent,
                        unfocusedBorderColor = theme.colors.surfaceBorder,
                        focusedTextColor = theme.colors.textPrimary,
                        unfocusedTextColor = theme.colors.textPrimary,
                        focusedContainerColor = theme.colors.surface,
                        unfocusedContainerColor = theme.colors.surface
                    ),
                    singleLine = true
                )
            }

            // Max Participants
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "MAX PARTICIPANTS: ${if (maxState >= 100) "Unlimited (100)" else maxState}",
                    style = theme.typography.caption.copy(
                        color = theme.colors.textSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontSize = 11.sp
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    maxOptions.forEach { count ->
                        val isSelected = maxState == count
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) theme.colors.primaryAccent else theme.colors.surface)
                                .border(
                                    1.dp,
                                    if (isSelected) theme.colors.primaryAccent else theme.colors.surfaceBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { maxState = count },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (count >= 100) "∞" else "$count",
                                style = theme.typography.caption.copy(
                                    color = if (isSelected) Color.White else theme.colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }

            // Permissions Toggles
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.colors.surface)
                    .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Mic Allowed Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Microphone Allowed",
                            style = theme.typography.bodyMedium.copy(
                                color = theme.colors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Text(
                            text = "Allow members to speak and voice chat",
                            style = theme.typography.caption.copy(
                                color = theme.colors.textSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }

                    Switch(
                        checked = micState,
                        onCheckedChange = { micState = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = theme.colors.primaryAccent,
                            uncheckedThumbColor = theme.colors.textTertiary,
                            uncheckedTrackColor = theme.colors.surfaceElevated
                        )
                    )
                }

                // Video Allowed Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Video Cameras Allowed",
                            style = theme.typography.bodyMedium.copy(
                                color = theme.colors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Text(
                            text = "Allow members to stream camera video",
                            style = theme.typography.caption.copy(
                                color = theme.colors.textSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }

                    Switch(
                        checked = videoState,
                        onCheckedChange = { videoState = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = theme.colors.primaryAccent,
                            uncheckedThumbColor = theme.colors.textTertiary,
                            uncheckedTrackColor = theme.colors.surfaceElevated
                        )
                    )
                }
            }

            // Room Tags
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "ROOM TAGS",
                    style = theme.typography.caption.copy(
                        color = theme.colors.textSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontSize = 11.sp
                    )
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(availableTags) { tag ->
                        val isSelected = tagsState.contains(tag)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) theme.colors.primaryAccent.copy(alpha = 0.2f) else theme.colors.surface)
                                .border(
                                    1.dp,
                                    if (isSelected) theme.colors.primaryAccent else theme.colors.surfaceBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    tagsState = if (isSelected) tagsState - tag else tagsState + tag
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "#$tag",
                                style = theme.typography.caption.copy(
                                    color = if (isSelected) theme.colors.primaryAccent else theme.colors.textSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.5.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Save Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.colors.primaryAccent)
                    .clickable {
                        onSaveSettings(
                            nameState.ifBlank { "Watch Lounge" },
                            maxState,
                            micState,
                            videoState,
                            tagsState.toList()
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Save Room Settings",
                    style = theme.typography.bodyMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ParticipantAvatarPill(
    participant: RoomParticipant,
    onClick: () -> Unit
) {
    val theme = Theme.current

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(theme.colors.surface)
            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(theme.colors.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = participant.user.avatarLetter,
                style = theme.typography.caption.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = theme.colors.textPrimary
                )
            )
        }

        Text(
            text = participant.user.displayName.split(" ").first(),
            style = theme.typography.caption.copy(
                color = theme.colors.textPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 11.5.sp
            )
        )

        if (participant.isHost) {
            Text(
                text = "HOST",
                style = theme.typography.caption.copy(
                    color = theme.colors.primaryAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp
                )
            )
        }
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
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(theme.colors.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message.senderAvatar,
                style = theme.typography.caption.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = theme.colors.textPrimary
                )
            )
        }

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
                androidx.compose.ui.window.Dialog(onDismissRequest = { showFullImage = false }) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .clickable { showFullImage = false },
                        contentAlignment = Alignment.Center
                    ) {
                        val fullBitmap = if (rawUrl.startsWith("data:image/")) {
                            try {
                                val bytes = android.util.Base64.decode(rawUrl.substringAfter("base64,"), android.util.Base64.DEFAULT)
                                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            } catch (_: Exception) { null }
                        } else null
                        if (fullBitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = fullBitmap.asImageBitmap(),
                                contentDescription = "Full-size shared photo",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                        } else {
                            coil.compose.AsyncImage(
                                model = rawUrl,
                                contentDescription = "Full-size shared photo",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                        }
                    }
                }
            }
        }
    }
}
