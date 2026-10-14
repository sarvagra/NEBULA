package com.nebula.app.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.nebula.app.ui.components.NebulaAvatarImage
import com.nebula.app.ui.components.PABottomSheet

/**
 * FriendProfileSheet:
 * Clean, restrained social profile sheet for NEBULA.
 * - Avatar & display name
 * - @username & bio
 * - Mutual friends count
 * - Current activity status
 * - Actions: Message, Invite to room, Friend status
 */
@Composable
fun FriendProfileSheet(
    isVisible: Boolean,
    user: NebulaUser?,
    onMessage: (NebulaUser) -> Unit,
    onInviteToRoom: (NebulaUser) -> Unit,
    onViewFullProfile: ((NebulaUser) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    if (user == null) return
    val theme = Theme.current

    PABottomSheet(
        isVisible = isVisible,
        onDismissRequest = onDismiss,
        title = "Profile"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // AVATAR
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(theme.colors.surfaceElevated)
                        .border(2.dp, theme.colors.primaryAccent.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    NebulaAvatarImage(user.avatarUrl, user.avatarLetter, modifier = Modifier.size(76.dp), fallbackTextSize = 26.sp)
                }

                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (user.isOnline) theme.colors.successColor else theme.colors.textTertiary)
                        .border(2.dp, theme.colors.background, CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // NAME & USERNAME
            Text(
                text = user.displayName,
                style = theme.typography.titleMedium.copy(
                    color = theme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp
                )
            )

            Text(
                text = user.username,
                style = theme.typography.bodySmall.copy(
                    color = theme.colors.textSecondary,
                    fontSize = 13.sp
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // BIO
            Text(
                text = user.bio,
                style = theme.typography.bodyMedium.copy(
                    color = theme.colors.textPrimary,
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp
                ),
                modifier = Modifier.padding(horizontal = 14.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // STATUS & MUTUALS INFO CARD
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.colors.surface)
                    .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Current Status",
                        style = theme.typography.caption.copy(color = theme.colors.textSecondary)
                    )
                    Text(
                        text = user.status,
                        style = theme.typography.caption.copy(
                            color = if (user.currentRoomId != null) theme.colors.primaryAccent else theme.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Mutual Connections",
                        style = theme.typography.caption.copy(color = theme.colors.textSecondary)
                    )
                    Text(
                        text = "${user.mutualFriendsCount} friends in common",
                        style = theme.typography.caption.copy(
                            color = theme.colors.textPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // PRIMARY ACTIONS: Message & Invite to Room
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        onDismiss()
                        onMessage(user)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.colors.primaryAccent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Message",
                            style = theme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp
                            )
                        )
                    }
                }

                Button(
                    onClick = {
                        onDismiss()
                        onInviteToRoom(user)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.colors.surfaceElevated,
                        contentColor = theme.colors.textPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = theme.colors.primaryAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Invite",
                            style = theme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp
                            )
                        )
                    }
                }
            }

            if (onViewFullProfile != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        onDismiss()
                        onViewFullProfile(user)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.colors.surface,
                        contentColor = theme.colors.primaryAccent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.surfaceBorder),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Text(
                        text = "View Profile",
                        style = theme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}
