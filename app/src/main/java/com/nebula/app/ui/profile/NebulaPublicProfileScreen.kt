package com.nebula.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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
import com.nebula.app.ui.components.NebulaBannerPalettes

@Composable
fun NebulaPublicProfileScreen(
    user: NebulaUser,
    isCurrentUser: Boolean,
    onBack: () -> Unit,
    onMessage: (NebulaUser) -> Unit,
    onAddFriend: (NebulaUser) -> Unit = {},
    onCancelRequest: (NebulaUser) -> Unit = {},
    onRemoveFriend: (NebulaUser) -> Unit = {},
    onAcceptRequest: (NebulaUser) -> Unit = {},
    onBlockUser: (NebulaUser) -> Unit = {},
    onInviteToRoom: (NebulaUser) -> Unit,
    onOpenEditProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    var isFriendState by remember(user.id) { mutableStateOf(user.isFriend) }
    var isRequestPending by remember(user.id) { mutableStateOf(user.friendRequestPending) }
    var isRequestReceived by remember(user.id) { mutableStateOf(user.friendRequestReceived) }
    var showProfileMenu by remember { mutableStateOf(false) }
    var showBlockConfirmation by remember { mutableStateOf(false) }

    val bannerGradient = remember(user.bannerIndex) {
        val safeIndex = (user.bannerIndex).coerceIn(0, NebulaBannerPalettes.lastIndex)
        NebulaBannerPalettes[safeIndex]
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.colors.background)
            .statusBarsPadding()
    ) {
        // 1. TOP BAR: Back Navigation + Action
        // NOTE (N-033): there is deliberately NO centered username header here.
        // The username is rendered with the name block next to the avatar below.
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

            Box {
                IconButton(onClick = { showProfileMenu = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Settings, contentDescription = "Profile settings", tint = theme.colors.textSecondary, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = showProfileMenu, onDismissRequest = { showProfileMenu = false }) {
                    if (isCurrentUser) DropdownMenuItem(
                        text = { Text("Edit profile") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { showProfileMenu = false; onOpenEditProfile() }
                    ) else DropdownMenuItem(
                        text = { Text("Block user") },
                        leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                        onClick = { showProfileMenu = false; showBlockConfirmation = true }
                    )
                }
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
            // Fixed 164dp hero = 120dp banner + 44dp avatar overhang, so the
            // overlapping avatar is fully contained inside this item and can
            // never collide with (or be clipped by) the name/bio block below.
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(164.dp)
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

                        // 120dp banner + 44dp spacer = 164dp = hero height.
                        Spacer(modifier = Modifier.height(44.dp))
                    }

                    // Overlapping Avatar: its vertical centre sits exactly on the
                    // banner's bottom edge (120 - 44 = 76), so it overlaps the
                    // banner by 44dp and stays 44dp inside the hero box.
                    // x = 4dp (+ the 16dp hero padding) lines it up with the 20dp
                    // content gutter used by the name/bio/tag blocks.
                    Box(
                        modifier = Modifier
                            .offset(x = 4.dp, y = 76.dp)
                            .size(88.dp),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
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
                        }

                        // Status indicator dot — inset by 12dp on both axes so the
                        // whole 18dp dot (radius 9) stays inside the 88dp avatar
                        // circle: sqrt(23² + 23²) + 9 ≈ 41.5dp < 44dp radius.
                        // The 3dp background-coloured ring matches the avatar
                        // boundary ring and reads as attached in every theme.
                        val isUserOffline = user.status.equals("offline", ignoreCase = true) || user.presenceStatus == NebulaPresenceStatus.OFFLINE
                        val dotColor = if (isUserOffline) theme.colors.textTertiary else if (user.status.isNotBlank() && !user.status.equals("offline", ignoreCase = true)) theme.colors.successColor else Color(user.presenceStatus.hexColor)
                        Box(
                            modifier = Modifier
                                .offset(x = (-12).dp, y = (-12).dp)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                                .border(3.dp, theme.colors.background, CircleShape)
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
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
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
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Presence Badge Pill (supports custom text up to 8 chars for premium, defaults like online/offline)
                        val rawStatus = user.status.trim()
                        val isUserOffline = rawStatus.equals("offline", ignoreCase = true) || user.presenceStatus == NebulaPresenceStatus.OFFLINE
                        val displayStatus = when {
                            rawStatus.isNotBlank() -> rawStatus.take(80)
                            else -> user.presenceStatus.label
                        }
                        val statusColor = when {
                            isUserOffline -> theme.colors.textSecondary
                            rawStatus.isNotBlank() && !rawStatus.equals("online", ignoreCase = true) -> theme.colors.primaryAccent
                            else -> theme.colors.successColor
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Box(
                            modifier = Modifier
                                .widthIn(max = 132.dp)
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
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (user.bio.isNotBlank()) Text(
                        text = user.bio,
                        style = theme.typography.bodyMedium.copy(color = theme.colors.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "TAGS",
                        style = theme.typography.caption.copy(
                            color = theme.colors.textSecondary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            fontSize = 11.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (user.interests.isEmpty()) {
                        Text("No tags yet", style = theme.typography.caption.copy(color = theme.colors.textTertiary))
                    } else {
                        // FlowRow (same as the own-profile screen) so long or
                        // numerous tags wrap onto new lines instead of being
                        // clipped off the right edge.
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            user.interests.take(5).forEach { interest ->
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
                                        if (isFriendState) {
                                            isFriendState = false
                                            onRemoveFriend(user)
                                        } else if (isRequestReceived) {
                                            isRequestReceived = false
                                            isFriendState = true
                                            onAcceptRequest(user)
                                        } else if (!isRequestPending) {
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
                                            isFriendState -> "Remove Friend"
                                            isRequestReceived -> "Accept Request"
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

                            if (isFriendState) {
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
                        // Friends
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
                                    text = "Friends",
                                    style = theme.typography.caption.copy(
                                        color = theme.colors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                )
                                Text(
                                    text = user.friendsCount?.toString() ?: "—",
                                    style = theme.typography.titleLarge.copy(
                                        color = theme.colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp
                                    )
                                )
                                Text(
                                    text = if (user.friendsCount == null) "Private" else "People",
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

                    val hasActivity = false
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
                                text = "Top content will appear here when viewing history is available.",
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

    if (showBlockConfirmation) AlertDialog(
        onDismissRequest = { showBlockConfirmation = false },
        title = { Text("Block ${user.displayName}?") },
        text = { Text("They will no longer be able to find or contact you. You can unblock them from your blocked users list in settings.") },
        confirmButton = {
            TextButton(onClick = { showBlockConfirmation = false; onBlockUser(user) }) { Text("Block") }
        },
        dismissButton = {
            TextButton(onClick = { showBlockConfirmation = false }) { Text("Cancel") }
        }
    )
}
