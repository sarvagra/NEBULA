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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.domain.model.NebulaPresenceStatus
import com.nebula.app.domain.model.NebulaSubscriptionTier
import com.nebula.app.domain.model.NebulaUser
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.NebulaProfileHeader

/**
 * Raw status modes persisted by the profile payload (mirrors `NebulaUser.status`).
 * These are internal enum tokens and must NEVER be rendered as user-facing copy.
 */
private const val STATUS_MODE_DYNAMIC = "dynamic"
private const val STATUS_MODE_OFFLINE = "offline"
private const val STATUS_MODE_CUSTOM = "custom"

/**
 * Maps the raw persisted status mode onto human-readable display text.
 *
 * - the dynamic mode -> the effective presence label
 * - the offline mode -> "Offline"
 * - the custom mode  -> the user's own text, falling back to the presence label
 * - anything else is already human-readable copy and is shown verbatim
 */
private fun nebulaStatusDisplayText(user: NebulaUser): String =
    when (user.status.trim().lowercase()) {
        STATUS_MODE_CUSTOM -> user.customStatus.trim().ifBlank { user.presenceStatus.label }
        STATUS_MODE_OFFLINE -> NebulaPresenceStatus.OFFLINE.label
        STATUS_MODE_DYNAMIC -> user.presenceStatus.label
        else -> user.status.trim().ifBlank { user.presenceStatus.label }
    }

/** Presence dot colour, derived from theme tokens only (never a hardcoded hex). */
@Composable
private fun nebulaStatusIndicatorColor(user: NebulaUser): Color {
    val theme = Theme.current
    return when {
        !user.isOnline -> theme.colors.textTertiary
        user.status.trim().equals(STATUS_MODE_OFFLINE, ignoreCase = true) -> theme.colors.textTertiary
        user.presenceStatus == NebulaPresenceStatus.IN_A_ROOM -> theme.colors.secondaryAccent
        user.presenceStatus == NebulaPresenceStatus.PRIVATE_CHAT -> theme.colors.primaryAccent
        else -> theme.colors.successColor
    }
}

/**
 * NebulaProfileScreen — the owner's own profile.
 *
 * The banner is full-bleed and immersive (Discord-style) rather than living
 * inside a padded card, so the artwork starts under the status bar. Social
 * counters, analytics and privacy switches sit below it on the page surface.
 */
@Composable
fun NebulaProfileScreen(
    user: NebulaUser = NebulaUser.CurrentUser,
    friendsCount: Int,
    isFriendCountPublic: Boolean,
    onToggleFriendPrivacy: () -> Unit,
    onNavigateFriends: () -> Unit,
    onOpenEditProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    onOpenVault: () -> Unit = {},
    onOpenPremiumPlans: () -> Unit = {},
    onViewPublicProfile: () -> Unit = {},
    onSignInWithGoogle: () -> Unit = {},
    onSignOutGoogle: () -> Unit = {},
    isAuthLoading: Boolean = false,
    activeTier: NebulaSubscriptionTier = NebulaSubscriptionTier.FREE,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current

    // Individual privacy switches for each analytics item.
    var isActivityBreakdownPublic by remember { mutableStateOf(true) }
    var isRoomsHostedPublic by remember { mutableStateOf(true) }
    var isPollsVotedPublic by remember { mutableStateOf(false) }
    var isReactionsPublic by remember { mutableStateOf(false) }
    var isTopContentPublic by remember { mutableStateOf(true) }
    val isPremiumUser = activeTier != NebulaSubscriptionTier.FREE

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(theme.colors.background),
        // The floating tab bar overlays the list, so the last card needs room to
        // scroll clear of it instead of ending underneath the pill.
        contentPadding = PaddingValues(bottom = 112.dp)
    ) {
        // 1. IMMERSIVE HEADER: full-bleed banner, floating settings action,
        // overlapping avatar and the owner's own social counters.
        item {
            NebulaProfileHeader(
                user = user,
                friendsCount = friendsCount,
                isFriendCountPublic = isFriendCountPublic,
                tierLabel = activeTier.badgeLabel,
                presenceColor = nebulaStatusIndicatorColor(user),
                topBar = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.32f))
                            .clickable(onClick = onOpenSettings),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                },
                actions = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onOpenEditProfile,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.colors.primaryAccent,
                                contentColor = theme.colors.onPrimaryAccent
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Edit Profile",
                                    style = theme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.colors.surfaceElevated)
                                .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(12.dp))
                                .clickable(onClick = onViewPublicProfile),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "View public profile",
                                tint = theme.colors.textPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (activeTier == NebulaSubscriptionTier.FREE) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.colors.surface)
                                .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(12.dp))
                                .clickable(onClick = onOpenPremiumPlans)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = theme.colors.primaryAccent,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = "Unlock custom status, more tags and longer history",
                                style = theme.typography.caption.copy(color = theme.colors.textSecondary, fontSize = 11.5.sp),
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = theme.colors.textTertiary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    if (isAuthLoading) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = theme.colors.primaryAccent,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = "Syncing your profile…",
                                style = theme.typography.caption.copy(color = theme.colors.textSecondary, fontSize = 11.5.sp)
                            )
                        }
                    }
                }
            )
        }

        // 2. FRIENDS ROW — the counter is also a door into the friends screen.
        item {
            Column(modifier = Modifier.padding(horizontal = 18.dp).padding(top = 18.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(theme.colors.surface)
                        .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
                        .clickable(onClick = onNavigateFriends)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.colors.primaryAccent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = theme.colors.primaryAccent,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Friends",
                            style = theme.typography.bodyMedium.copy(
                                color = theme.colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp
                            )
                        )
                        Text(
                            text = if (!isFriendCountPublic) {
                                "Hidden from your public profile"
                            } else if (friendsCount == 1) {
                                "1 friend"
                            } else {
                                "$friendsCount friends"
                            },
                            style = theme.typography.caption.copy(color = theme.colors.textSecondary, fontSize = 11.5.sp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isFriendCountPublic) theme.colors.primaryAccent.copy(alpha = 0.12f) else theme.colors.surfaceElevated)
                            .clickable(onClick = onToggleFriendPrivacy)
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(
                                imageVector = if (isFriendCountPublic) Icons.Default.Public else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (isFriendCountPublic) theme.colors.primaryAccent else theme.colors.textSecondary,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = if (isFriendCountPublic) "Public" else "Private",
                                style = theme.typography.caption.copy(
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isFriendCountPublic) theme.colors.primaryAccent else theme.colors.textSecondary
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = theme.colors.textTertiary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        // 3. NEBULA ANALYTICS with individual item Public / Private switches
        item {
            Column(modifier = Modifier.padding(horizontal = 18.dp).padding(top = 14.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(theme.colors.surface)
                        .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "NEBULA ANALYTICS",
                            style = theme.typography.caption.copy(
                                color = theme.colors.textSecondary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                fontSize = 11.sp
                            )
                        )
                        Text(
                            text = "This Month",
                            style = theme.typography.caption.copy(
                                color = theme.colors.primaryAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }

                    // Activity breakdown with public/private toggle
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Activity Breakdown",
                                style = theme.typography.caption.copy(
                                    color = theme.colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            )
                            PrivacyToggleBadge(
                                isPublic = isActivityBreakdownPublic,
                                onToggle = { isActivityBreakdownPublic = !isActivityBreakdownPublic }
                            )
                        }

                        val totalRooms = user.roomsHostedCount + user.roomsJoinedCount
                        if (totalRooms == 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(theme.colors.surfaceElevated)
                                    .padding(vertical = 12.dp, horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No room sync sessions recorded yet this month",
                                    style = theme.typography.caption.copy(
                                        color = theme.colors.textSecondary,
                                        fontSize = 11.5.sp
                                    )
                                )
                            }
                        }
                    }

                    // Analytics metric tiles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricTile(
                            label = "Hosted",
                            value = "${user.roomsHostedCount}",
                            icon = Icons.Default.Star,
                            isPublic = isRoomsHostedPublic,
                            onTogglePrivacy = { isRoomsHostedPublic = !isRoomsHostedPublic }
                        )
                        MetricTile(
                            label = "Joined",
                            value = "${user.roomsJoinedCount}",
                            icon = Icons.Default.Public,
                            isPublic = isActivityBreakdownPublic,
                            onTogglePrivacy = { isActivityBreakdownPublic = !isActivityBreakdownPublic }
                        )
                        MetricTile(
                            label = "Hours",
                            value = String.format("%.1f", user.timeInSyncHours),
                            icon = Icons.Default.AccessTime,
                            isPublic = isActivityBreakdownPublic,
                            onTogglePrivacy = { isActivityBreakdownPublic = !isActivityBreakdownPublic }
                        )
                    }
                }
            }
        }

        // 4. TOP CONTENT SHOWCASE with public/private toggle
        item {
            Column(modifier = Modifier.padding(horizontal = 18.dp).padding(top = 14.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(theme.colors.surface)
                        .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
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
                        PrivacyToggleBadge(
                            isPublic = isTopContentPublic,
                            onToggle = { isTopContentPublic = !isTopContentPublic }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.colors.surfaceElevated)
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No top content yet. Your watched videos will appear here when available.",
                            style = theme.typography.bodySmall.copy(
                                color = theme.colors.textSecondary,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.MetricTile(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPublic: Boolean,
    onTogglePrivacy: () -> Unit
) {
    val theme = Theme.current
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(theme.colors.surfaceElevated)
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = theme.colors.textTertiary,
                    modifier = Modifier.size(12.dp)
                )
                PrivacyToggleBadge(isPublic = isPublic, onToggle = onTogglePrivacy, compact = true)
            }
            Text(
                text = value,
                style = theme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = theme.colors.textPrimary)
            )
            Text(text = label, style = theme.typography.caption.copy(color = theme.colors.textTertiary, fontSize = 9.sp))
        }
    }
}

@Composable
private fun PrivacyToggleBadge(
    isPublic: Boolean,
    onToggle: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isPublic) theme.colors.primaryAccent.copy(alpha = 0.12f) else theme.colors.surfaceElevated)
            .clickable(onClick = onToggle)
            .padding(horizontal = if (compact) 4.dp else 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = if (isPublic) Icons.Default.Public else Icons.Default.Lock,
            contentDescription = null,
            tint = if (isPublic) theme.colors.primaryAccent else theme.colors.textSecondary,
            modifier = Modifier.size(if (compact) 8.dp else 10.dp)
        )
        if (!compact) {
            Text(
                text = if (isPublic) "Public" else "Private",
                style = theme.typography.caption.copy(
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isPublic) theme.colors.primaryAccent else theme.colors.textSecondary
                )
            )
        }
    }
}
