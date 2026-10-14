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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    modifier: Modifier = Modifier
) {
    val theme = Theme.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
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
                        text = "${friends.count { it.isOnline }} friends online",
                        style = theme.typography.caption.copy(
                            color = theme.colors.textTertiary,
                            fontSize = 11.5.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (friends.isEmpty()) {
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
                            text = "No friends active right now • Connect with people in the Friends tab",
                            style = theme.typography.bodySmall.copy(color = theme.colors.textSecondary, fontSize = 12.sp)
                        )
                    }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(friends) { friend ->
                            ActiveFriendItem(
                                user = friend,
                                onClick = { onFriendClick(friend) }
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

            // Consistent status indicator dot
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color(user.presenceStatus.hexColor))
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

        Text(
            text = user.presenceStatus.label,
            style = theme.typography.caption.copy(
                color = Color(user.presenceStatus.hexColor),
                fontSize = 10.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
