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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.nebula.app.ui.components.TagScribble

/**
 * A profile opened from the outside: a friend, a stranger from a room, or the
 * viewer's own profile reached through "View public profile".
 *
 * The header is the same full-bleed component as the owner's own tab, so the
 * banner, avatar, counters and presence look identical from both sides. Only the
 * action row differs — friendship controls versus "Edit Profile".
 */
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
    onInviteToRoom: (NebulaUser) -> Unit = {},
    onOpenEditProfile: () -> Unit = {},
    /** Friend count of the signed-in viewer, used when this is their own profile. */
    viewerFriendsCount: Int? = null,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    var isFriendState by remember(user.id, user.isFriend) { mutableStateOf(user.isFriend) }
    var isRequestPending by remember(user.id, user.friendRequestPending) { mutableStateOf(user.friendRequestPending) }
    var isRequestReceived by remember(user.id, user.friendRequestReceived) { mutableStateOf(user.friendRequestReceived) }
    var showProfileMenu by remember { mutableStateOf(false) }
    var showBlockConfirmation by remember { mutableStateOf(false) }
    var showProfileInfo by remember { mutableStateOf(false) }

    LaunchedEffect(user.isFriend, user.friendRequestPending, user.friendRequestReceived) {
        isFriendState = user.isFriend
        isRequestPending = user.friendRequestPending
        isRequestReceived = user.friendRequestReceived
    }

    // Removing a friend is not undoable in one tap, so it asks first.
    var confirmRemoveFriend by remember(user.id) { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(theme.colors.background),
        contentPadding = PaddingValues(bottom = 112.dp)
    ) {
        // 1. IMMERSIVE HEADER
        item {                NebulaProfileHeader(
                user = user,
                topBarStart = {
                    CircleGlyphButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBack
                    )
                },
                topBar = {
                    CircleGlyphButton(
                        icon = Icons.Default.MoreHoriz,
                        contentDescription = "Profile options",
                        onClick = { showProfileMenu = true }
                    )
                },
                actions = {
                    if (isCurrentUser) {
                        // Your own profile as others see it: the two social buttons are
                        // drawn but inert, and the pencil takes the invite slot.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PreviewActionButton(
                                text = "Add Friend",
                                icon = Icons.Default.PersonAdd,
                                modifier = Modifier.weight(1f)
                            )
                            PreviewActionButton(
                                text = "Message",
                                icon = Icons.AutoMirrored.Filled.Chat,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(theme.colors.primaryAccent)
                                    .clickable { onOpenEditProfile() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit profile",
                                    tint = theme.colors.onPrimaryAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                FriendActionButton(
                                    isFriend = isFriendState,
                                    isRequestReceived = isRequestReceived,
                                    isRequestPending = isRequestPending,
                                    onToggle = {
                                        when {
                                            isFriendState -> confirmRemoveFriend = true
                                            isRequestReceived -> {
                                                isRequestReceived = false
                                                isFriendState = true
                                                onAcceptRequest(user)
                                            }
                                            isRequestPending -> {
                                                isRequestPending = false
                                                onCancelRequest(user)
                                            }
                                            else -> {
                                                isRequestPending = true
                                                onAddFriend(user)
                                            }
                                        }
                                    }
                                )
                            }

                            if (isFriendState) {
                                Box(modifier = Modifier.weight(1f)) {
                                    PrimaryActionButton(
                                        text = "Message",
                                        icon = Icons.AutoMirrored.Filled.Chat,
                                        onClick = { onMessage(user) }
                                    )
                                }
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
            )
        }

        // 2. ROOM ACTIVITY — joined and hosted only. The friend count moved next to
        // the name in the header, and the hours tile is gone from the product.
        // A private switch does not render an empty card: the whole block drops out
        // and top content, if it is public, moves up into its place.
        if (user.isRoomActivityPublic) {
            item {
                Column(modifier = Modifier.padding(horizontal = 18.dp).padding(top = 18.dp)) {
                    Text(
                        text = "ROOM ACTIVITY",
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
                        StatCard(
                            label = "Joined",
                            value = com.nebula.app.ui.components.compactCount(user.roomsJoinedCount),
                            caption = "Rooms"
                        )
                        StatCard(
                            label = "Hosted",
                            value = com.nebula.app.ui.components.compactCount(user.roomsHostedCount),
                            caption = "Rooms"
                        )
                    }

                    if (user.mutualFriendsCount > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.colors.surface)
                                .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${user.mutualFriendsCount} mutual connections",
                                style = theme.typography.caption.copy(
                                    color = theme.colors.textSecondary,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        // 3. TOP CONTENT — the tags of the rooms this person actually takes part in,
        // drawn as a scribble: the most-visited tag is the largest word and the least
        // is the smallest. Tags with no participation never appear at all.
        if (user.isTopContentPublic) {
            item {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 18.dp)
                        .padding(top = if (user.isRoomActivityPublic) 14.dp else 18.dp)
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

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(theme.colors.surface)
                            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
                            .padding(16.dp)
                    ) {
                        if (user.topTags.isEmpty()) {
                            Text(
                                text = "No room tags yet. Join a room and its tags will show up here.",
                                style = theme.typography.caption.copy(
                                    color = theme.colors.textSecondary,
                                    fontSize = 12.sp
                                )
                            )
                        } else {
                            TagScribble(tags = user.topTags)
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp).navigationBarsPadding()) }
    }

    ProfileActionSheet(
        isVisible = showProfileMenu,
        isCurrentUser = isCurrentUser,
        displayName = user.displayName,
        accountCreatedAt = user.accountCreatedAt,
        usernameChanges = user.usernameChanges,
        onOpenInfo = { showProfileMenu = false; showProfileInfo = true },
        onEditProfile = { showProfileMenu = false; onOpenEditProfile() },
        onBlock = { showProfileMenu = false; showBlockConfirmation = true },
        onDismiss = { showProfileMenu = false }
    )

    // Account details are their own page rather than a section wedged under the
    // action that opens them.
    if (showProfileInfo) {
        ProfileInfoPage(
            displayName = user.displayName,
            accountCreatedAt = user.accountCreatedAt,
            usernameChanges = user.usernameChanges,
            onBack = { showProfileInfo = false }
        )
    }

    if (showBlockConfirmation) {
        AlertDialog(
            onDismissRequest = { showBlockConfirmation = false },
            containerColor = theme.colors.surfaceElevated,
            titleContentColor = theme.colors.textPrimary,
            textContentColor = theme.colors.textSecondary,
            shape = RoundedCornerShape(20.dp),
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(theme.colors.errorColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = null,
                        tint = theme.colors.errorColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Block ${user.displayName}?",
                    style = theme.typography.titleMedium.copy(
                        color = theme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                )
            },
            text = {
                Text(
                    text = "They will no longer be able to message you, invite you to rooms, or find your profile. You can undo this at any time from Settings > Blocked users.",
                    style = theme.typography.bodySmall.copy(
                        color = theme.colors.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBlockConfirmation = false
                        onBlockUser(user)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.colors.errorColor,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Block", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirmation = false }) {
                    Text("Cancel", color = theme.colors.textSecondary)
                }
            }
        )
    }

    if (confirmRemoveFriend) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRemoveFriend = false },
            title = { Text("Remove ${user.displayName} from friends?") },
            text = { Text("You will stop sharing rooms and messages until you add each other again.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmRemoveFriend = false
                        isFriendState = false
                        onRemoveFriend(user)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.colors.errorColor,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Remove", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveFriend = false }) {
                    Text("Keep friend", color = theme.colors.textSecondary)
                }
            },
            containerColor = theme.colors.surfaceElevated
        )
    }
}

/** Circular translucent control for the banner's floating top bar. */
@Composable
private fun CircleGlyphButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(21.dp)
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.PreviewActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(theme.colors.surfaceElevated)
            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = theme.colors.textTertiary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                style = theme.typography.bodyMedium.copy(
                    color = theme.colors.textTertiary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp
                )
            )
        }
    }
}

@Composable
private fun PrimaryActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val theme = Theme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(theme.colors.primaryAccent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = theme.colors.onPrimaryAccent, modifier = Modifier.size(16.dp))
            Text(
                text = text,
                style = theme.typography.bodyMedium.copy(
                    color = theme.colors.onPrimaryAccent,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

@Composable
private fun FriendActionButton(
    isFriend: Boolean,
    isRequestReceived: Boolean,
    isRequestPending: Boolean,
    onToggle: () -> Unit
) {
    val theme = Theme.current
    val label = when {
        isFriend -> "Friends"
        isRequestReceived -> "Accept Request"
        isRequestPending -> "Withdraw Request"
        else -> "Add Friend"
    }
    val filled = !isFriend && !isRequestPending

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (filled) theme.colors.primaryAccent else theme.colors.surfaceElevated)
            .border(1.dp, if (filled) Color.Transparent else theme.colors.surfaceBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = when {
                    isFriend -> Icons.Default.Check
                    isRequestReceived -> Icons.Default.Check
                    isRequestPending -> Icons.Default.MoreHoriz
                    else -> Icons.Default.PersonAdd
                },
                contentDescription = null,
                tint = if (filled) theme.colors.onPrimaryAccent else theme.colors.textPrimary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = theme.typography.bodyMedium.copy(
                    color = if (filled) theme.colors.onPrimaryAccent else theme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp
                )
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StatCard(label: String, value: String, caption: String) {
    val theme = Theme.current
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(theme.colors.surface)
            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column {
            Text(text = label, style = theme.typography.caption.copy(color = theme.colors.textSecondary, fontSize = 11.sp))
            Text(
                text = value,
                style = theme.typography.titleLarge.copy(
                    color = theme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            )
            Text(text = caption, style = theme.typography.caption.copy(color = theme.colors.textTertiary, fontSize = 10.sp))
        }
    }
}

/**
 * Profile options sheet, rendering through Nebula's unified PABottomSheet
 * matching all other action sheets across the application.
 */
@Composable
private fun ProfileActionSheet(
    isVisible: Boolean,
    isCurrentUser: Boolean,
    displayName: String,
    accountCreatedAt: String?,
    usernameChanges: List<com.nebula.app.domain.model.NebulaUsernameChange>,
    onOpenInfo: () -> Unit,
    onEditProfile: () -> Unit,
    onBlock: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = Theme.current

    /** "12 Mar 2026" from an ISO timestamp; blank when the server sent nothing usable. */
    fun shortDate(iso: String?): String? {
        if (iso.isNullOrBlank()) return null
        return try {
            val normalised = iso.replace(" ", "T").substringBefore('.').removeSuffix("Z")
            val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val date = parser.parse(normalised) ?: return null
            val out = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.US)
            out.timeZone = java.util.TimeZone.getDefault()
            out.format(date)
        } catch (_: Exception) {
            null
        }
    }

    PABottomSheet(
        isVisible = isVisible,
        onDismissRequest = onDismiss,
        title = if (isCurrentUser) "Profile Options" else "User Options"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(theme.colors.surface)
                    .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(16.dp))
            ) {
                ProfileActionRow(
                    icon = Icons.Default.Info,
                    title = "Profile info",
                    subtitle = "Account age, username changes",
                    tint = theme.colors.primaryAccent,
                    onClick = onOpenInfo
                )
                if (isCurrentUser) {
                    ProfileActionRow(
                        icon = Icons.Default.Edit,
                        title = "Edit Profile",
                        subtitle = "Customize your avatar, banner, bio, and interests",
                        tint = theme.colors.primaryAccent,
                        onClick = onEditProfile
                    )
                } else {
                    ProfileActionRow(
                        icon = Icons.Default.Block,
                        title = "Block ${displayName.substringBefore(' ')}",
                        subtitle = "They won't be able to message you or invite you to rooms",
                        tint = theme.colors.errorColor,
                        titleColor = theme.colors.errorColor,
                        onClick = onBlock
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

/**
 * The account record behind a profile: when the account was made, how many times the
 * username changed and when it last changed — not the trail of old handles themselves.
 */
@Composable
private fun ProfileInfoPage(
    displayName: String,
    accountCreatedAt: String?,
    usernameChanges: List<com.nebula.app.domain.model.NebulaUsernameChange>,
    onBack: () -> Unit
) {
    val theme = Theme.current

    /** "12 Mar 2026" from an ISO timestamp; blank when the server sent nothing usable. */
    fun shortDate(iso: String?): String? {
        if (iso.isNullOrBlank()) return null
        return try {
            val normalised = iso.replace(" ", "T").substringBefore('.').removeSuffix("Z")
            val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val date = parser.parse(normalised) ?: return null
            val out = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.US)
            out.timeZone = java.util.TimeZone.getDefault()
            out.format(date)
        } catch (_: Exception) {
            null
        }
    }

    val lastChange = usernameChanges.lastOrNull()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.colors.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 48.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircleGlyphButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBack
                    )
                    Text(
                        text = "Profile info",
                        modifier = Modifier.padding(start = 10.dp),
                        style = theme.typography.titleMedium.copy(
                            color = theme.colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(theme.colors.surface)
                        .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = displayName,
                        style = theme.typography.bodyMedium.copy(
                            color = theme.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    )

                    InfoLine(label = "Member since", value = shortDate(accountCreatedAt) ?: "Unknown")
                    InfoLine(
                        label = "Username changes",
                        value = when (usernameChanges.size) {
                            0 -> "Never changed"
                            1 -> "1 time"
                            else -> "${usernameChanges.size} times"
                        }
                    )
                    InfoLine(
                        label = "Last username change",
                        value = shortDate(lastChange?.changedAt) ?: "Unknown"
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    val theme = Theme.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = theme.typography.bodySmall.copy(color = theme.colors.textSecondary, fontSize = 12.5.sp)
        )
        if (value.isNotBlank()) {
            Text(
                text = value,
                style = theme.typography.bodySmall.copy(
                    color = theme.colors.textPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.5.sp
                )
            )
        }
    }
}

@Composable
private fun ProfileActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    tint: Color,
    titleColor: Color = Theme.current.colors.textPrimary,
    onClick: () -> Unit
) {
    val theme = Theme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (tint == theme.colors.errorColor) theme.colors.errorColor.copy(alpha = 0.12f) else theme.colors.surfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(19.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = theme.typography.bodyMedium.copy(
                        color = titleColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = theme.typography.caption.copy(
                            color = theme.colors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    )
                }
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = theme.colors.textTertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}
