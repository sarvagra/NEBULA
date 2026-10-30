package com.nebula.app.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.domain.model.NebulaUser
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.NebulaProfileHeader
import com.nebula.app.ui.components.PABottomSheet

/**
 * FriendProfileSheet — the quick profile shown from a room, a friend list or a
 * message avatar.
 *
 * It deliberately reuses the same immersive header as the full profile screen
 * (full-bleed banner, blurred top band, overlapping avatar, presence and social
 * counters). A sheet that rendered a bare centered avatar made the same person
 * look like a different product depending on where you tapped them.
 */
@Composable
fun FriendProfileSheet(
    isVisible: Boolean,
    user: NebulaUser?,
    onMessage: (NebulaUser) -> Unit,
    onInviteToRoom: (NebulaUser) -> Unit,
    onJoinTheirRoom: ((String) -> Unit)? = null,
    onViewFullProfile: ((NebulaUser) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    if (user == null) return
    val theme = Theme.current
    // Someone sitting in a room gets "Join their room"; someone who is merely online
    // gets "Invite to a room". Two different jobs, so they never share a button.

    PABottomSheet(
        isVisible = isVisible,
        onDismissRequest = onDismiss,
        title = null
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            NebulaProfileHeader(
                user = user,
                friendsCount = user.friendsCount,
                isFriendCountPublic = user.friendsCount != null,
                compact = true,
                topBar = {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.34f))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close profile",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                actions = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SheetAction(
                            text = "Message",
                            icon = Icons.AutoMirrored.Filled.Chat,
                            filled = true,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onDismiss()
                                onMessage(user)
                            }
                        )
                        val roomId = user.currentRoomId
                        SheetAction(
                            text = if (roomId != null) "Join room" else "Invite",
                            icon = Icons.Default.Tv,
                            filled = false,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onDismiss()
                                if (roomId != null && onJoinTheirRoom != null) onJoinTheirRoom(roomId)
                                else onInviteToRoom(user)
                            }
                        )
                    }

                    if (onViewFullProfile != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.colors.surface)
                                .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    onDismiss()
                                    onViewFullProfile(user)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "View full profile",
                                style = theme.typography.bodyMedium.copy(
                                    color = theme.colors.primaryAccent,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }
                }
            )

            // Mutual connections stay outside the header so they read as a detail
            // rather than another counter clipped into the banner.
            if (user.mutualFriendsCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 14.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.colors.surface)
                        .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${user.mutualFriendsCount} friends in common",
                        style = theme.typography.caption.copy(
                            color = theme.colors.textSecondary,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SheetAction(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val theme = Theme.current
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (filled) theme.colors.primaryAccent else theme.colors.surfaceElevated)
            .border(1.dp, if (filled) Color.Transparent else theme.colors.surfaceBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (filled) theme.colors.onPrimaryAccent else theme.colors.primaryAccent,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                style = theme.typography.bodyMedium.copy(
                    color = if (filled) theme.colors.onPrimaryAccent else theme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp
                )
            )
        }
    }
}
