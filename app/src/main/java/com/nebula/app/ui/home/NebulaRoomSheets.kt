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
 * The room's sheets: who is here, who can be invited, and the host's settings.
 *
 * These are modal surfaces over the room, so they are kept apart from the room's
 * layout: a change to a member row cannot disturb playback or the chat composer.
 */


/**
 * RoomMembersSheet:
 * Displays all participants in the room.
 * - Each row: Avatar, name, host tag, status indicators (mic/camera)
 * - 3 dots menu beside each member: Admin toggle for audio/video mute
 * - Clicking anywhere else on row: opens member's public profile
 * - Dismiss / click outside: returns simply to the room
 */
@Composable
internal fun RoomMembersSheet(
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
internal fun RoomInviteFriendsSheet(
    friends: List<NebulaUser>,
    onInvite: (NebulaUser) -> Unit,
    onSendInChat: (NebulaUser) -> Unit = {},
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
                        // Sends the invite as a card into the thread instead of pulling
                        // them in immediately, so it is still there tomorrow.
                        IconButton(onClick = { onSendInChat(friend) }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send invite in chat",
                                tint = theme.colors.textSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
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
internal fun RoomSettingsSheet(
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
    // Same steps as the create-room sheet: 4 · 8 · 12 · 16 · 20 · 24.
    val maxOptions = listOf(4, 8, 12, 16, 20, 24)

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
                    text = "MAX PARTICIPANTS: $maxState",
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
                                text = "$count",
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
internal fun ParticipantAvatarPill(
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
            NebulaAvatarImage(participant.user.avatarUrl, participant.user.avatarLetter, modifier = Modifier.size(22.dp))
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
