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
import androidx.compose.foundation.layout.aspectRatio
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
import com.nebula.app.domain.model.NebulaSubscriptionTier
import com.nebula.app.domain.model.NebulaUser
import com.nebula.app.theme.Theme

private val BANNER_PALETTES = listOf(
    listOf(Color(0xFF1E1B4B), Color(0xFF312E81)), // Deep Indigo
    listOf(Color(0xFF3B0764), Color(0xFF581C87)), // Nebula Violet
    listOf(Color(0xFF042F2E), Color(0xFF115E59)), // Midnight Teal
    listOf(Color(0xFF450A0A), Color(0xFF7F1D1D)), // Cosmic Crimson
    listOf(Color(0xFF090B10), Color(0xFF1E222D))  // Obsidian Glass
)

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
    var showQrDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
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
            // 2. PROFILE HERO & STATS with sleek 21:9 banner
            item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(theme.colors.surface)
                    .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(16.dp))
            ) {
                // Background Banner behind the avatar with sleek 16:9 aspect ratio
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(bannerColors)
                        )
                ) {
                    if (!user.bannerUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = user.bannerUrl,
                            contentDescription = "Profile banner",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                    // QR Code Share action button on banner
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            .clickable { showQrDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        // Stylized 4-square QR mini emblem
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                Box(Modifier.size(5.dp).border(1.2.dp, Color.White, RoundedCornerShape(1.dp)))
                                Box(Modifier.size(5.dp).border(1.2.dp, Color.White, RoundedCornerShape(1.dp)))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                Box(Modifier.size(5.dp).border(1.2.dp, Color.White, RoundedCornerShape(1.dp)))
                                Box(Modifier.size(5.dp).background(Color.White, RoundedCornerShape(1.dp)))
                            }
                        }
                    }
                }

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
                        // Avatar overlapping banner
                        Box(
                            modifier = Modifier.size(72.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isPremiumUser) {
                                // Multi-stop radiant aura ring
                                val auraGradient = Brush.sweepGradient(
                                    listOf(
                                        Color(0xFF8B5CF6),
                                        Color(0xFFEC4899),
                                        Color(0xFF3B82F6),
                                        Color(0xFF8B5CF6)
                                    )
                                )
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(auraGradient)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(if (isPremiumUser) 66.dp else 72.dp)
                                    .clip(CircleShape)
                                    .background(theme.colors.surfaceElevated)
                                    .border(
                                        width = if (isPremiumUser) 0.dp else 2.5.dp,
                                        color = theme.colors.primaryAccent,
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
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(theme.colors.primaryAccent)
                                        .clickable(onClick = onOpenEditProfile),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Avatar",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }

                        // Stats strip positioned neatly on card surface
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp)
                                .offset(y = 16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // Friends stat
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onNavigateFriends)
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isFriendCountPublic) "$friendsCount" else "—",
                                    style = theme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = theme.colors.textPrimary
                                    )
                                )
                                Text(
                                    text = "Friends",
                                    style = theme.typography.caption.copy(
                                        color = theme.colors.textSecondary,
                                        fontSize = 11.5.sp
                                    )
                                )
                            }

                            // Rooms Joined stat
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${user.roomsJoinedCount}",
                                    style = theme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = theme.colors.textPrimary
                                    )
                                )
                                Text(
                                    text = "Joined",
                                    style = theme.typography.caption.copy(
                                        color = theme.colors.textSecondary,
                                        fontSize = 11.5.sp
                                    )
                                )
                            }

                            // Rooms Hosted stat
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${user.roomsHostedCount}",
                                    style = theme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = theme.colors.textPrimary
                                    )
                                )
                                Text(
                                    text = "Hosted",
                                    style = theme.typography.caption.copy(
                                        color = theme.colors.textSecondary,
                                        fontSize = 11.5.sp
                                    )
                                )
                            }
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
                    val displayInterests = user.interests.ifEmpty { listOf("🎬 Sci-Fi", "🎧 Lo-Fi", "🍿 Watch Parties", "🌌 Ambient") }
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                        ) {
                            Box(modifier = Modifier.weight(0.64f).fillMaxSize().background(theme.colors.primaryAccent))
                            Box(modifier = Modifier.weight(0.24f).fillMaxSize().background(Color(0xFF38BDF8)))
                            Box(modifier = Modifier.weight(0.12f).fillMaxSize().background(Color(0xFFA855F7)))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(theme.colors.primaryAccent))
                                Text("Cinema 64%", style = theme.typography.caption.copy(fontSize = 10.5.sp, color = theme.colors.textSecondary))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF38BDF8)))
                                Text("Lo-Fi 24%", style = theme.typography.caption.copy(fontSize = 10.5.sp, color = theme.colors.textSecondary))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFA855F7)))
                                Text("Voice 12%", style = theme.typography.caption.copy(fontSize = 10.5.sp, color = theme.colors.textSecondary))
                            }
                        }
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
                                Text("Polls", style = theme.typography.caption.copy(color = theme.colors.textSecondary, fontSize = 10.sp))
                                PrivacyToggleBadge(
                                    isPublic = isPollsVotedPublic,
                                    onToggle = { isPollsVotedPublic = !isPollsVotedPublic },
                                    compact = true
                                )
                            }
                            Text("0", style = theme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = theme.colors.textPrimary))
                            Text("Voted", style = theme.typography.caption.copy(color = theme.colors.textTertiary, fontSize = 9.sp))
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
                                Text("Reactions", style = theme.typography.caption.copy(color = theme.colors.textSecondary, fontSize = 10.sp))
                                PrivacyToggleBadge(
                                    isPublic = isReactionsPublic,
                                    onToggle = { isReactionsPublic = !isReactionsPublic },
                                    compact = true
                                )
                            }
                            Text("0", style = theme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = theme.colors.textPrimary))
                            Text("Sent", style = theme.typography.caption.copy(color = theme.colors.textTertiary, fontSize = 9.sp))
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

                if (user.roomsHostedCount > 0) {
                    listOf(
                        Triple("01", "Ambient Space Odyssey", "Recent watchparty"),
                        Triple("02", "Cyberpunk: Edgerunners", "Recent session")
                    ).forEach { (rank, title, stat) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.colors.surfaceElevated)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = rank,
                                style = theme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = theme.colors.primaryAccent,
                                    fontSize = 16.sp
                                )
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = theme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = theme.colors.textPrimary,
                                        fontSize = 13.5.sp
                                    )
                                )
                                Text(
                                    text = stat,
                                    style = theme.typography.caption.copy(
                                        color = theme.colors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.colors.surfaceElevated)
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No shared watchparty history yet • Host a room to highlight your favorite media",
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

    if (showQrDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showQrDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(theme.colors.surface)
                    .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(20.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Nebula Profile QR",
                        style = theme.typography.titleMedium.copy(
                            color = theme.colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    )
                    Text(
                        text = "Scan to connect with ${user.displayName}",
                        style = theme.typography.bodySmall.copy(
                            color = theme.colors.textSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    )

                    // Stylized QR code representation
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Box(Modifier.size(36.dp).border(4.dp, Color.Black, RoundedCornerShape(4.dp)))
                                Box(Modifier.size(36.dp).border(4.dp, Color.Black, RoundedCornerShape(4.dp)))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = user.avatarLetter,
                                    style = theme.typography.titleLarge.copy(
                                        color = Color.Black,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 24.sp
                                    )
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Box(Modifier.size(36.dp).border(4.dp, Color.Black, RoundedCornerShape(4.dp)))
                                Box(Modifier.size(36.dp).background(Color.Black, RoundedCornerShape(4.dp)))
                            }
                        }
                    }

                    Text(
                        text = user.username,
                        style = theme.typography.bodyMedium.copy(
                            color = theme.colors.primaryAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    )

                    Button(
                        onClick = { showQrDialog = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.colors.surfaceElevated,
                            contentColor = theme.colors.textPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close")
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
