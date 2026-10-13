package com.nebula.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nebula.app.domain.model.NebulaRoom
import com.nebula.app.domain.model.RoomParticipant
import com.nebula.app.theme.Theme

/**
 * NebulaCompactRoomCard:
 * Minimalist, uncluttered room card following the exact specifications:
 * - Room name
 * - Content title (NO timestamps or clutter)
 * - Mini profile avatars of participants with host crowned
 * - Right side banner image of the content being viewed with Join affordance
 */
@Composable
fun NebulaCompactRoomCard(
    room: NebulaRoom,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(theme.colors.surface)
            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onJoin)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT: Name, Content info, Mini Avatars with Host Crown
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = room.name,
                    style = theme.typography.bodyLarge.copy(
                        color = theme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = room.mediaItem?.title ?: "Synchronized stream",
                    style = theme.typography.caption.copy(
                        color = theme.colors.textSecondary,
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Mini profile avatars
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val hostParticipant = room.participants.firstOrNull { it.isHost }
                    val otherParticipants = room.participants.filter { !it.isHost }

                    // Host Avatar with Crown
                    if (hostParticipant != null) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(theme.colors.primaryAccent.copy(alpha = 0.25f))
                                    .border(1.5.dp, theme.colors.primaryAccent, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = hostParticipant.user.avatarLetter.take(1),
                                    style = theme.typography.caption.copy(
                                        color = theme.colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                            // Crown Emoji Badge
                            Text(
                                text = "👑",
                                fontSize = 8.sp,
                                modifier = Modifier.offset(x = 2.dp, y = (-5).dp)
                            )
                        }
                    }

                    // Next participants (up to 2 more)
                    otherParticipants.take(2).forEach { participant ->
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(theme.colors.surfaceElevated)
                                .border(1.5.dp, theme.colors.surface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = participant.user.avatarLetter.take(1),
                                style = theme.typography.caption.copy(
                                    color = theme.colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }

                    // Overflow count (+n more)
                    val remainingCount = room.participantCount - 1 - minOf(otherParticipants.size, 2)
                    if (remainingCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(theme.colors.surfaceBorder)
                                .border(1.5.dp, theme.colors.surface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+$remainingCount",
                                style = theme.typography.caption.copy(
                                    color = theme.colors.textSecondary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }
                }
            }

            val videoId = room.mediaItem?.youtubeVideoId?.takeIf { it.isNotBlank() }
                ?: com.nebula.app.domain.model.SharedMediaItem.extractYouTubeId(room.mediaItem?.mediaUrl ?: "")

            val thumbnailUrl = if (!videoId.isNullOrBlank()) {
                "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
            } else null

            Box(
                modifier = Modifier
                    .width(108.dp)
                    .height(68.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F1118))
                    .border(1.dp, theme.colors.surfaceBorder.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                    .clickable(onClick = onJoin),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnailUrl != null) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = room.mediaItem?.title ?: "Video thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}
