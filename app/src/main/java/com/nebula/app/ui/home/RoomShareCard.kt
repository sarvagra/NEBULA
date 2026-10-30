package com.nebula.app.ui.home

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.domain.model.ChatMessage
import com.nebula.app.domain.model.MessageRole
import com.nebula.app.theme.Theme

/**
 * A watch-party invite inside a thread.
 *
 * The card carries the room's shareable code, never its database id: the same rule the
 * deep link and the share sheet follow, so no invite surface can expose an internal id.
 */
private const val ROOM_CARD_PREFIX = "nebula://room/"

/** The room code a message points at, or null when it is not a room card. */
fun roomCardCode(message: ChatMessage): String? {
    if (message.deletedAt != null) return null
    if (message.messageType != "room_card") return null
    val raw = message.mediaUrl ?: return null
    if (!raw.lowercase().startsWith(ROOM_CARD_PREFIX)) return null
    return raw.substring(ROOM_CARD_PREFIX.length).trim().takeIf { it.isNotBlank() }?.uppercase()
}

/** Newest room invite still visible in a thread, used for the pinned banner. */
fun latestRoomCard(messages: List<ChatMessage>): ChatMessage? =
    messages.lastOrNull { roomCardCode(it) != null }

@Composable
fun RoomShareCard(
    roomName: String,
    code: String,
    isMine: Boolean,
    onJoin: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    val background = if (isMine) {
        Brush.linearGradient(listOf(theme.colors.primaryAccent, theme.colors.primaryAccent.copy(alpha = 0.72f)))
    } else {
        Brush.linearGradient(listOf(theme.colors.surfaceElevated, theme.colors.surface))
    }
    Column(
        modifier = modifier
            .width(250.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(if (isMine) Modifier else Modifier.border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp)))
            .background(background)
            .clickable { onJoin(code) }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = "Watch party invite",
                    style = theme.typography.caption.copy(
                        color = Color.White.copy(alpha = 0.82f),
                        fontWeight = FontWeight.SemiBold
                    ),
                    fontSize = 10.5.sp
                )
                Text(
                    text = roomName.ifBlank { "Nebula room" },
                    style = theme.typography.bodyLarge.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    ),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color.White.copy(alpha = 0.2f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Join room · $code",
                style = theme.typography.bodyMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                ),
                fontSize = 12.sp
            )
        }
    }
}

/**
 * Pinned above the composer while the thread's newest invite is still the way into
 * the party, so a member who scrolled past the card does not have to hunt for it.
 */
@Composable
fun RoomInviteBanner(
    roomName: String,
    code: String,
    onJoin: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = Theme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(theme.colors.surfaceElevated)
            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(12.dp))
            .clickable { onJoin(code) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(theme.colors.successColor)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = roomName.ifBlank { "Watch party" },
                style = theme.typography.bodyMedium.copy(
                    color = theme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "In this room · tap to join",
                style = theme.typography.caption.copy(color = theme.colors.textSecondary),
                fontSize = 11.sp
            )
        }
        Text(
            text = "Join",
            style = theme.typography.bodyMedium.copy(
                color = theme.colors.primaryAccent,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onJoin(code) }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "×",
            style = theme.typography.bodyLarge.copy(color = theme.colors.textTertiary),
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onDismiss() }
                .padding(horizontal = 6.dp)
        )
    }
}
