package com.nebula.app.ui.profile

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.nebula.app.domain.model.NebulaPresenceStatus
import com.nebula.app.domain.model.NebulaUser
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.NebulaAvatarImage

private val BANNER_PALETTES = listOf(
    listOf(Color(0xFF1E1B4B), Color(0xFF312E81)), // Deep Indigo
    listOf(Color(0xFF3B0764), Color(0xFF581C87)), // Nebula Violet
    listOf(Color(0xFF042F2E), Color(0xFF115E59)), // Midnight Teal
    listOf(Color(0xFF450A0A), Color(0xFF7F1D1D)), // Cosmic Crimson
    listOf(Color(0xFF090B10), Color(0xFF1E222D))  // Obsidian Glass
)

@Composable
fun NebulaPublicProfileScreen(
    user: NebulaUser,
    isCurrentUser: Boolean,
    onBack: () -> Unit,
    onMessage: (NebulaUser) -> Unit,
    onAddFriend: (NebulaUser) -> Unit = {},
    onCancelRequest: (NebulaUser) -> Unit = {},
    onInviteToRoom: (NebulaUser) -> Unit,
    onOpenEditProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    var isFriendState by remember(user.id) { mutableStateOf(user.isFriend) }
    var isRequestPending by remember(user.id) { mutableStateOf(user.friendRequestPending) }

    val bannerGradient = remember(user.bannerIndex) {
        val safeIndex = (user.bannerIndex).coerceIn(0, BANNER_PALETTES.lastIndex)
        BANNER_PALETTES[safeIndex]
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.colors.background)
            .statusBarsPadding()
    ) {
        // 1. TOP BAR: Back Navigation + Title + Action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = theme.colors.textPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Text(
                text = user.username,
                style = theme.typography.titleMedium.copy(
                    color = theme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            )

            IconButton(
                onClick = { /* Share profile link */ },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share Profile",
                    tint = theme.colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 2. SCROLLABLE PROFILE CONTENT
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // HERO BANNER + OVERLAPPING AVATAR
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column {
                        // Visible Profile Banner
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .then(
                                    if (user.bannerUrl.isNullOrBlank()) Modifier.background(Brush.horizontalGradient(bannerGradient))
                                    else Modifier.background(Color.Black)
                                )
                                .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(18.dp))
                        ) {
                            if (!user.bannerUrl.isNullOrBlank()) {
                                val bannerUri = user.bannerUrl!!
                                val base64Bitmap = remember(bannerUri) {
                                    if (bannerUri.startsWith("data:image")) {
                                        try {
                                            val data = bannerUri.substringAfter("base64,")
                                            val decoded = Base64.decode(data, Base64.DEFAULT)
                                            BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
                                        } catch (e: Exception) {
                                            null
                                        }
                                    } else null
                                }
                                if (base64Bitmap != null) {
                                    Image(
                                        bitmap = base64Bitmap.asImageBitmap(),
                                        contentDescription = "Profile Banner",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    AsyncImage(
                                        model = bannerUri,
                                        contentDescription = "Profile Banner",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(46.dp))
                    }

                    // Overlapping Avatar
                    Box(
                        modifier = Modifier
                            .offset(x = 16.dp, y = 72.dp)
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(theme.colors.surfaceElevated)
                            .border(3.dp, theme.colors.background, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        NebulaAvatarImage(
                            avatarUrl = user.avatarUrl,
                            fallbackLetter = user.avatarLetter,
                            modifier = Modifier.fillMaxSize(),
                            fallbackTextSize = 32.sp
                        )

                        // Status indicator dot
                        val isUserOffline = user.status.equals("offline", ignoreCase = true) || user.presenceStatus == NebulaPresenceStatus.OFFLINE
                        val dotColor = if (isUserOffline) theme.colors.textTertiary else if (user.status.isNotBlank() && !user.status.equals("offline", ignoreCase = true)) theme.colors.successColor else Color(user.presenceStatus.hexColor)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = (-4).dp, y = (-4).dp)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                                .border(2.5.dp, theme.colors.background, CircleShape)
                        )
                    }
                }
            }

            // USER INFO & SOCIAL BADGES
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = user.displayName,
                                style = theme.typography.titleLarge.copy(
                                    color = theme.colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp
                                )
                            )
                            Text(
                                text = user.username,
                                style = theme.typography.bodyMedium.copy(
                                    color = theme.colors.primaryAccent,
                                    fontSize = 14.sp
                                )
                            )
                        }

                        // Presence Badge Pill (supports custom text up to 8 chars for premium, defaults like online/offline)
                        val rawStatus = user.status.trim()
                        val isUserOffline = rawStatus.equals("offline", ignoreCase = true) || user.presenceStatus == NebulaPresenceStatus.OFFLINE
                        val displayStatus = when {
                            rawStatus.isNotBlank() -> rawStatus.take(8)
                            else -> user.presenceStatus.label
                        }
                        val statusColor = when {
                            isUserOffline -> theme.colors.textSecondary
                            rawStatus.isNotBlank() && !rawStatus.equals("online", ignoreCase = true) -> theme.colors.primaryAccent
                            else -> theme.colors.successColor
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(statusColor.copy(alpha = 0.15f))
                                .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = displayStatus,
                                style = theme.typography.caption.copy(
                                    color = statusColor,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.5.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = user.bio,
                        style = theme.typography.bodyMedium.copy(
                            color = theme.colors.textSecondary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isCurrentUser) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(theme.colors.primaryAccent)
                                    .clickable { onOpenEditProfile() },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Edit Profile",
                                        style = theme.typography.bodyMedium.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }
                            }
                        } else {
                            // Friend status button
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isFriendState) theme.colors.surfaceElevated else theme.colors.primaryAccent)
                                    .border(1.dp, if (isFriendState) theme.colors.surfaceBorder else Color.Transparent, RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (!isFriendState && !isRequestPending) {
                                            isRequestPending = true
                                            onAddFriend(user)
                                        } else if (isRequestPending) {
                                            isRequestPending = false
                                            onCancelRequest(user)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isFriendState) Icons.Default.Check else Icons.Default.PersonAdd,
                                        contentDescription = null,
                                        tint = if (isFriendState) theme.colors.textPrimary else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = when {
                                            isFriendState -> "Friends"
                                            isRequestPending -> "Withdraw Request"
                                            else -> "Add Friend"
                                        },
                                        style = theme.typography.bodyMedium.copy(
                                            color = if (isFriendState) theme.colors.textPrimary else Color.White,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }
                            }

                            // Message button
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(theme.colors.surfaceElevated)
                                    .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(12.dp))
                                    .clickable { onMessage(user) },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Chat,
                                        contentDescription = null,
                                        tint = theme.colors.textPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Message",
                                        style = theme.typography.bodyMedium.copy(
                                            color = theme.colors.textPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }
                            }

                            // Invite to Room
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(theme.colors.surfaceElevated)
                                    .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(12.dp))
                                    .clickable { onInviteToRoom(user) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tv,
                                    contentDescription = "Invite to Room",
                                    tint = theme.colors.primaryAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // INTERESTS & ROOM CATEGORY BADGES
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Text(
                        text = "INTERESTS & ROOM BADGES",
                        style = theme.typography.caption.copy(
                            color = theme.colors.textSecondary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            fontSize = 11.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        user.interests.forEach { interest ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(theme.colors.surfaceElevated)
                                    .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = interest,
                                    style = theme.typography.caption.copy(
                                        color = theme.colors.textPrimary,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // PUBLIC STATS (Derived from user activity)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Text(
                        text = "PUBLIC ACTIVITY",
                        style = theme.typography.caption.copy(
                            color = theme.colors.textSecondary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            fontSize = 11.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Rooms Hosted
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(theme.colors.surface)
                                .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
                                .padding(14.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Hosted",
                                    style = theme.typography.caption.copy(
                                        color = theme.colors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                )
                                Text(
                                    text = "${user.roomsHostedCount}",
                                    style = theme.typography.titleLarge.copy(
                                        color = theme.colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp
                                    )
                                )
                                Text(
                                    text = "Rooms",
                                    style = theme.typography.caption.copy(
                                        color = theme.colors.textTertiary,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }

                        // Rooms Joined
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(theme.colors.surface)
                                .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
                                .padding(14.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Joined",
                                    style = theme.typography.caption.copy(
                                        color = theme.colors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                )
                                Text(
                                    text = "${user.roomsJoinedCount}",
                                    style = theme.typography.titleLarge.copy(
                                        color = theme.colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp
                                    )
                                )
                                Text(
                                    text = "Rooms",
                                    style = theme.typography.caption.copy(
                                        color = theme.colors.textTertiary,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }

                        // Rooms Hosted
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(theme.colors.surface)
                                .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
                                .padding(14.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Hosted",
                                    style = theme.typography.caption.copy(
                                        color = theme.colors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                )
                                Text(
                                    text = "${user.roomsHostedCount}",
                                    style = theme.typography.titleLarge.copy(
                                        color = theme.colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp
                                    )
                                )
                                Text(
                                    text = "Rooms",
                                    style = theme.typography.caption.copy(
                                        color = theme.colors.textTertiary,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // TOP CONTENT
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Text(
                        text = "TOP CONTENT",
                        style = theme.typography.caption.copy(
                            color = theme.colors.textSecondary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            fontSize = 11.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val hasActivity = user.roomsHostedCount > 0
                    if (!hasActivity) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(theme.colors.surface)
                                .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No watch sessions or top content recorded yet",
                                style = theme.typography.caption.copy(
                                    color = theme.colors.textSecondary,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(theme.colors.surface)
                                .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
                                .padding(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "01",
                                            style = theme.typography.titleMedium.copy(
                                                color = theme.colors.primaryAccent,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        Column {
                                            Text(
                                                text = "Ambient Space Odyssey",
                                                style = theme.typography.bodyMedium.copy(
                                                    color = theme.colors.textPrimary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            )
                                            Text(
                                                text = "8 rooms • 22.4h synced",
                                                style = theme.typography.caption.copy(
                                                    color = theme.colors.textTertiary,
                                                    fontSize = 11.sp
                                                )
                                            )
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(theme.colors.surfaceBorder)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "02",
                                            style = theme.typography.titleMedium.copy(
                                                color = theme.colors.primaryAccent,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        Column {
                                            Text(
                                                text = "Cyberpunk: Edgerunners",
                                                style = theme.typography.bodyMedium.copy(
                                                    color = theme.colors.textPrimary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            )
                                            Text(
                                                text = "5 rooms • 8.1h synced",
                                                style = theme.typography.caption.copy(
                                                    color = theme.colors.textTertiary,
                                                    fontSize = 11.sp
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
