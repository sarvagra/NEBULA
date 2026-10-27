package com.nebula.app.ui.home

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
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
import coil.compose.AsyncImage
import com.nebula.app.domain.model.NebulaPresenceStatus
import com.nebula.app.domain.model.NebulaSubscriptionTier
import com.nebula.app.domain.model.NebulaUser
import com.nebula.app.theme.Theme

/**
 * Fixed, intentional banner height (dp). The banner is a proportional strip, not a stretched
 * 16:9 block: at this height the avatar still overlaps it by ~32dp while the name/status block
 * below starts immediately after the avatar without colliding.
 */
private val PROFILE_BANNER_HEIGHT = 156.dp

/**
 * User-selectable banner gradient palettes, indexed by the persisted `bannerIndex` payload field.
 * These are deliberately hardcoded artwork, not theme tokens: the palette is part of the saved
 * profile content (it travels with `bannerIndex`) and must look identical across all four themes.
 */
private val BANNER_PALETTES = com.nebula.app.ui.components.NebulaBannerThemes.ALL.map { it.colors }

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
 * - the dynamic mode -> the effective presence label (`NebulaPresenceStatus.label`: "Online", "In a room", ...)
 * - the offline mode -> "Offline"
 * - the custom mode  -> the user's own text, falling back to the presence label when blank
 * - anything else is already human-readable copy and is shown verbatim
 *
 * The raw mode token is never returned, so no screen can print it as a status label.
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
 * NebulaProfileScreen:
 * Full-screen social identity & usage analytics screen.
 * Communicates:
 * - Who am I? (@handle, avatar, bio, banner)
 * - What do I do on Nebula? (Rooms joined, hosted, in sync)
 * - What do I like? (Interests, top content)
 * - What have I done? (Public statistics, analytics)
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

    // Individual privacy switches for each analytics item
    var isActivityBreakdownPublic by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    var isRoomsHostedPublic by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    var isPollsVotedPublic by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var isReactionsPublic by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var isTopContentPublic by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    val isPremiumUser = activeTier != NebulaSubscriptionTier.FREE

    val bannerColors = BANNER_PALETTES.getOrElse(user.bannerIndex.coerceIn(0, BANNER_PALETTES.lastIndex)) { BANNER_PALETTES[0] }

    Column(modifier = modifier.fillMaxSize()) {
        // 1. FIXED STICKY TOP BAR: "NEBULA" + subscription badge, Settings action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.colors.background)
                .padding(horizontal = 18.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "NEBULA",
                    style = theme.typography.titleMedium.copy(
                        color = theme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = 2.sp
                    )
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(theme.colors.primaryAccent.copy(alpha = 0.15f))
                        .padding(horizontal = 7.dp, vertical = 2.5.dp)
                ) {
                    Text(
                        text = activeTier.badgeLabel,
                        style = theme.typography.caption.copy(
                            color = theme.colors.primaryAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            // Settings icon only
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = theme.colors.textPrimary,
                    modifier = Modifier.size(21.dp)
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 2. PROFILE HERO & STATS with a fixed-height banner strip
            item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(theme.colors.surface)
                    .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(16.dp))
            ) {
                // Background Banner behind the avatar: fixed proportional height, rounded by the
                // parent card clip. The banner carries no share affordance any more.
                com.nebula.app.ui.components.NebulaBannerView(
                    bannerUrl = user.bannerUrl,
                    bannerIndex = user.bannerIndex,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PROFILE_BANNER_HEIGHT),
                    shape = RoundedCornerShape(0.dp)
                )

                // Profile card content with spacious and de-congested layout
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 18.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-32).dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Avatar overlapping banner. No edit badge is drawn on top of the
                        // avatar; the edit entry point is the "Edit Profile" button below.
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(theme.colors.surfaceElevated)
                                .border(
                                    width = 1.dp,
                                    color = theme.colors.surface,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            com.nebula.app.ui.components.NebulaAvatarImage(
                                avatarUrl = user.avatarUrl,
                                fallbackLetter = user.avatarLetter,
                                contentDescription = user.displayName,
                                modifier = Modifier.fillMaxSize(),
                                fallbackTextSize = 26.sp
                            )
                        }

                    }

                    // Display Name + Handle underneath + Bio with clean breathing room
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = user.displayName,
                            style = theme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = theme.colors.textPrimary,
                                fontSize = 17.sp
                            )
                        )
                        Text(
                            text = user.username,
                            style = theme.typography.caption.copy(
                                color = theme.colors.primaryAccent,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        )
                        // Human-readable status: the raw persisted mode token is never rendered,
                        // it is always mapped through nebulaStatusDisplayText().
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(nebulaStatusIndicatorColor(user))
                            )
                            Text(
                                text = nebulaStatusDisplayText(user),
                                style = theme.typography.caption.copy(
                                    color = theme.colors.textSecondary,
                                    fontSize = 12.5.sp
                                )
                            )
                        }
                        if (user.bio.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = user.bio,
                                style = theme.typography.bodySmall.copy(
                                    color = theme.colors.textSecondary,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Interest tags with FlowRow for responsive wrapping
                    val displayInterests = user.interests.take(5)
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        displayInterests.forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(theme.colors.surfaceElevated)
                                    .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = tag,
                                    style = theme.typography.caption.copy(
                                        fontSize = 11.5.sp,
                                        color = theme.colors.textSecondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action buttons row: Edit Profile (Own profile view is the public view)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onOpenEditProfile,
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.colors.surfaceElevated,
                                contentColor = theme.colors.textPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.surfaceBorder),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = theme.colors.primaryAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Edit Profile",
                                    style = theme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. NEBULA ANALYTICS with individual item Public / Private switches
        item {
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

                        // Public / Private Switch for Breakdown
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
                    } else {
                    }
                }

                // Analytics metric tiles with specific individual item Public / Private switches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Rooms Hosted
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
                                Text("Rooms", style = theme.typography.caption.copy(color = theme.colors.textSecondary, fontSize = 10.sp))
                                PrivacyToggleBadge(
                                    isPublic = isRoomsHostedPublic,
                                    onToggle = { isRoomsHostedPublic = !isRoomsHostedPublic },
                                    compact = true
                                )
                            }
                            Text("${user.roomsHostedCount}", style = theme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = theme.colors.textPrimary))
                            Text("Hosted", style = theme.typography.caption.copy(color = theme.colors.textTertiary, fontSize = 9.sp))
                        }
                    }

                    // Polls Voted
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
                                Text("Rooms", style = theme.typography.caption.copy(color = theme.colors.textSecondary, fontSize = 10.sp))
                                PrivacyToggleBadge(
                                    isPublic = isActivityBreakdownPublic,
                                    onToggle = { isActivityBreakdownPublic = !isActivityBreakdownPublic },
                                    compact = true
                                )
                            }
                            Text("${user.roomsJoinedCount}", style = theme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = theme.colors.textPrimary))
                            Text("Joined", style = theme.typography.caption.copy(color = theme.colors.textTertiary, fontSize = 9.sp))
                        }
                    }

                    // Reactions Sent
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
                                Text("Sync", style = theme.typography.caption.copy(color = theme.colors.textSecondary, fontSize = 10.sp))
                                PrivacyToggleBadge(
                                    isPublic = isActivityBreakdownPublic,
                                    onToggle = { isActivityBreakdownPublic = !isActivityBreakdownPublic },
                                    compact = true
                                )
                            }
                            Text(String.format("%.1f", user.timeInSyncHours), style = theme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = theme.colors.textPrimary))
                            Text("Hours", style = theme.typography.caption.copy(color = theme.colors.textTertiary, fontSize = 9.sp))
                        }
                    }
                }
            }
        }

        // 4. TOP CONTENT SHOWCASE with public/private toggle
        item {
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
