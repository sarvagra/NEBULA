package com.nebula.app.ui.home

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PeopleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.domain.model.NebulaConversation
import com.nebula.app.domain.model.NebulaPresenceStatus
import com.nebula.app.domain.model.NebulaRoom
import com.nebula.app.domain.model.NebulaUser
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.NebulaCompactRoomCard
import com.nebula.app.ui.components.NebulaAvatarImage

/**
 * NebulaHomeScreen:
 * Clean social starting point showing "What's happening".
 * - ACTIVE NOW (friends status)
 * - ROOMS FOR YOU (compact watch-together room cards)
 * - RECENT CHATS (direct conversations)
 */
@Composable
fun NebulaHomeScreen(
    friends: List<NebulaUser>,
    rooms: List<NebulaRoom>,
    conversations: List<NebulaConversation>,
    onJoinRoom: (String) -> Unit,
    onCreateRoomClick: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onFriendClick: (NebulaUser) -> Unit,
    onInviteToRoom: (NebulaUser) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    var selectedActiveFriend by remember { mutableStateOf<NebulaUser?>(null) }
    // Active Now means active. A friend list where everyone reads "Offline" under the
    // heading defeats the section, so the row is filtered down to people who are
    // actually connected and the empty state owns the rest.
    val activeFriends = friends.filter { it.isOnline }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // 1. ACTIVE NOW
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "ACTIVE NOW",
                            style = theme.typography.caption.copy(
                                color = theme.colors.textSecondary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                fontSize = 11.5.sp
                            )
                        )

                        Text(
                            text = when (activeFriends.size) {
                                0 -> "Nobody online"
                                1 -> "1 friend online"
                                else -> "${activeFriends.size} friends online"
                            },
                            style = theme.typography.caption.copy(
                                color = theme.colors.textTertiary,
                                fontSize = 11.5.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (activeFriends.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.colors.surface)
                                .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(12.dp))
                                .padding(vertical = 14.dp, horizontal = 16.dp)
                        ) {
                            Text(
                                text = "Nobody's active right now • Friends who come online will show up here",
                                style = theme.typography.bodySmall.copy(color = theme.colors.textSecondary, fontSize = 12.sp)
                            )
                        }
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(activeFriends) { friend ->
                                ActiveFriendItem(
                                    user = friend,
                                    onClick = { selectedActiveFriend = friend }
                                )
                            }
                        }
                    }
                }
            }

        // 2. ROOMS FOR YOU
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "ROOMS FOR YOU",
                        style = theme.typography.caption.copy(
                            color = theme.colors.textSecondary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            fontSize = 11.5.sp
                        )
                    )

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onCreateRoomClick)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create room",
                            tint = theme.colors.primaryAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Create Room",
                            style = theme.typography.caption.copy(
                                color = theme.colors.primaryAccent,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (rooms.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(theme.colors.surface)
                            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
                            .clickable(onClick = onCreateRoomClick)
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = theme.colors.primaryAccent,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "No active rooms right now",
                                style = theme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = theme.colors.textPrimary
                                )
                            )
                            Text(
                                text = "Tap to create a watch party and invite friends",
                                style = theme.typography.caption.copy(color = theme.colors.textSecondary)
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        rooms.take(5).forEach { room ->
                            NebulaCompactRoomCard(
                                room = room,
                                onJoin = { onJoinRoom(room.id) }
                            )
                        }
                    }
                }
            }
        }

        // 3. RECENT CHATS
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
            ) {
                Text(
                    text = "RECENT CHATS",
                    style = theme.typography.caption.copy(
                        color = theme.colors.textSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        fontSize = 11.5.sp
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (conversations.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(theme.colors.surface)
                            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No recent messages • Start a conversation with a friend",
                            style = theme.typography.bodySmall.copy(color = theme.colors.textSecondary, fontSize = 12.sp)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(theme.colors.surface)
                            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
                    ) {
                        conversations.take(3).forEachIndexed { index, conv ->
                            HomeChatRow(
                                conversation = conv,
                                onClick = { onOpenConversation(conv.id) }
                            )
                            if (index < conversations.take(3).lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(0.6.dp)
                                        .background(theme.colors.surfaceBorder.copy(alpha = 0.4f))
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Active friend quick popup card
    selectedActiveFriend?.let { friend ->
        ActiveFriendQuickSheet(
            friend = friend,
            onDismiss = { selectedActiveFriend = null },
            onFollowToRoom = { roomId ->
                selectedActiveFriend = null
                onJoinRoom(roomId)
            },
            onInviteToRoom = { f ->
                selectedActiveFriend = null
                onInviteToRoom(f)
            },
            onViewProfile = { f ->
                selectedActiveFriend = null
                onFriendClick(f)
            }
        )
    }
}
}

@Composable
private fun ActiveFriendItem(
    user: NebulaUser,
    onClick: () -> Unit
) {
    val theme = Theme.current

    Column(
        modifier = Modifier
            .width(76.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(theme.colors.surfaceElevated)
                    .border(
                        width = if (user.currentRoomId != null) 2.dp else 1.dp,
                        color = if (user.currentRoomId != null) theme.colors.primaryAccent else theme.colors.surfaceBorder,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                NebulaAvatarImage(user.avatarUrl, user.avatarLetter, modifier = Modifier.size(54.dp))
            }

            // Green / grey dot denotes online / offline clearly without conflict
            val isOnline = user.isOnline && user.profileStatus != "offline" && user.presenceStatus != NebulaPresenceStatus.OFFLINE
            val dotColor = if (isOnline) Color(0xFF10B981) else Color(0xFF6B7280)
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(dotColor)
                    .border(2.dp, theme.colors.background, CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = user.displayName.split(" ").first(),
            style = theme.typography.caption.copy(
                color = theme.colors.textPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // For premium users: show custom typed status. For normal users: dynamic presence without redundant offline.
        val isPremium = user.tier.equals("premium", ignoreCase = true) || user.tier.equals("premium_plus", ignoreCase = true)
        val subtitle = if (isPremium && user.customStatus.isNotBlank()) {
            user.customStatus
        } else {
            when {
                user.currentRoomId != null || user.presenceStatus == NebulaPresenceStatus.IN_A_ROOM -> "In a room"
                user.presenceStatus == NebulaPresenceStatus.PRIVATE_CHAT -> "In chat"
                user.isOnline -> "Active now"
                else -> ""
            }
        }

        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = theme.typography.caption.copy(
                    color = if (isPremium && user.customStatus.isNotBlank()) theme.colors.primaryAccent else theme.colors.textSecondary,
                    fontSize = 10.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveFriendQuickSheet(
    friend: NebulaUser,
    onDismiss: () -> Unit,
    onFollowToRoom: (String) -> Unit,
    onInviteToRoom: (NebulaUser) -> Unit,
    onViewProfile: (NebulaUser) -> Unit
) {
    val theme = Theme.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = theme.colors.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // PFP with online dot
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(theme.colors.surfaceElevated)
                        .border(2.dp, theme.colors.primaryAccent.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    NebulaAvatarImage(friend.avatarUrl, friend.avatarLetter, modifier = Modifier.size(68.dp))
                }
                val isOnline = friend.isOnline && friend.profileStatus != "offline" && friend.presenceStatus != NebulaPresenceStatus.OFFLINE
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (isOnline) Color(0xFF10B981) else Color(0xFF6B7280))
                        .border(2.5.dp, theme.colors.surface, CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Display name & handle
            Text(
                text = friend.displayName,
                style = theme.typography.titleMedium.copy(
                    color = theme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            )
            Text(
                text = if (friend.username.startsWith("@")) friend.username else "@${friend.username}",
                style = theme.typography.bodySmall.copy(
                    color = theme.colors.textSecondary,
                    fontSize = 13.sp
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Friend count & Status chip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Friend count
                val friendCount = friend.friendsCount ?: 0
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.colors.surfaceElevated)
                        .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PeopleOutline,
                        contentDescription = null,
                        tint = theme.colors.textSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "$friendCount ${if (friendCount == 1) "friend" else "friends"}",
                        style = theme.typography.caption.copy(
                            color = theme.colors.textSecondary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    )
                }

                // Presence / Custom status chip
                val isOnline = friend.isOnline && friend.profileStatus != "offline" && friend.presenceStatus != NebulaPresenceStatus.OFFLINE
                val statusText = when {
                    friend.customStatus.isNotBlank() -> friend.customStatus
                    !isOnline -> "Offline"
                    friend.currentRoomId != null || friend.presenceStatus == NebulaPresenceStatus.IN_A_ROOM -> "In a room"
                    friend.presenceStatus == NebulaPresenceStatus.PRIVATE_CHAT -> "In chat"
                    else -> "Active now"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.colors.surfaceElevated)
                        .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) Color(0xFF10B981) else Color(0xFF6B7280))
                    )
                    Text(
                        text = statusText,
                        style = theme.typography.caption.copy(
                            color = theme.colors.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Bio
            if (friend.bio.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(theme.colors.surfaceElevated)
                        .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = friend.bio,
                        style = theme.typography.bodySmall.copy(
                            color = theme.colors.textPrimary,
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp
                        ),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action button:
            // "follow to room button if theyre in a room else invite to a room...
            // remember inviting opens the create room panel and when room is created theyre invited automatically."
            if (friend.currentRoomId != null) {
                Button(
                    onClick = {
                        onDismiss()
                        onFollowToRoom(friend.currentRoomId)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.colors.primaryAccent,
                        contentColor = Color.White
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Follow to Room",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                Button(
                    onClick = {
                        onDismiss()
                        onInviteToRoom(friend)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.colors.primaryAccent,
                        contentColor = Color.White
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GroupAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Invite to a Room",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = {
                    onDismiss()
                    onViewProfile(friend)
                }
            ) {
                Text(
                    text = "View full profile",
                    style = theme.typography.caption.copy(
                        color = theme.colors.textSecondary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

@Composable
private fun HomeRoomCard(
    room: NebulaRoom,
    onJoin: () -> Unit
) {
    val theme = Theme.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(theme.colors.surface)
            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onJoin)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = room.name,
                        style = theme.typography.bodyLarge.copy(
                            color = theme.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(theme.colors.primaryAccent.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${room.participantCount} watching",
                            style = theme.typography.caption.copy(
                                color = theme.colors.primaryAccent,
                                fontWeight = FontWeight.Medium,
                                fontSize = 10.5.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Watching: ${room.mediaItem?.title ?: "Synchronized stream"}",
                    style = theme.typography.bodySmall.copy(
                        color = theme.colors.textSecondary,
                        fontSize = 12.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Who is actually in the room, so the card shows faces instead of a
                // number that could belong to anyone.
                if (room.participants.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        room.participants.take(4).forEachIndexed { index, participant ->
                            Box(
                                modifier = Modifier
                                    .padding(start = if (index == 0) 0.dp else (-8).dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, theme.colors.background, CircleShape)
                            ) {
                                NebulaAvatarImage(
                                    avatarUrl = participant.user.avatarUrl,
                                    fallbackLetter = participant.user.avatarLetter,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        val extra = (room.participantCount - minOf(4, room.participants.size)).coerceAtLeast(0)
                        if (extra > 0) {
                            Text(
                                text = " +$extra",
                                style = theme.typography.caption.copy(
                                    color = theme.colors.textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = onJoin,
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.colors.primaryAccent,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Join",
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Join",
                        style = theme.typography.caption.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeChatRow(
    conversation: NebulaConversation,
    onClick: () -> Unit
) {
    val theme = Theme.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val avatarLetter = conversation.partnerUser?.avatarLetter ?: "G"
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(theme.colors.surfaceElevated)
                .border(1.dp, theme.colors.surfaceBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            NebulaAvatarImage(conversation.partnerUser?.avatarUrl, avatarLetter, modifier = Modifier.size(42.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = conversation.title,
                    style = theme.typography.bodyMedium.copy(
                        color = theme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = conversation.lastMessageTime,
                    style = theme.typography.caption.copy(
                        color = theme.colors.textTertiary,
                        fontSize = 11.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = conversation.lastMessageText,
                style = theme.typography.bodySmall.copy(
                    color = if (conversation.unreadCount > 0) theme.colors.textPrimary else theme.colors.textSecondary,
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.Medium else FontWeight.Normal,
                    fontSize = 12.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (conversation.unreadCount > 0) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(theme.colors.primaryAccent)
            )
        }
    }
}
