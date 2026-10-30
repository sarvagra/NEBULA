package com.nebula.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nebula.app.domain.model.NebulaPresenceStatus
import com.nebula.app.domain.model.NebulaUser
import com.nebula.app.theme.Theme

/**
 * Discord-style immersive profile header.
 *
 * The banner runs edge to edge and only its bottom corners are rounded, so the
 * artwork starts under the status bar instead of sitting inside a padded card.
 * Because the artwork now sits behind the system bars, every control drawn on
 * top of it needs its own contrast: the top scrim, the frosted pill behind each
 * icon and the softened top band keep back/settings legible over a photo while
 * still letting the image read as full-bleed.
 *
 * One component serves the owner's profile, a stranger's profile, the quick
 * friend sheet and the live preview in the editor, so a profile can never look
 * like two different products depending on who opened it.
 */

private val BANNER_HEIGHT = 208.dp
private val COMPACT_BANNER_HEIGHT = 156.dp
private val AVATAR_SIZE = 92.dp
private val COMPACT_AVATAR_SIZE = 74.dp

/** How far the avatar hangs below the banner. */
private val AVATAR_OVERLAP = 46.dp
private val COMPACT_AVATAR_OVERLAP = 37.dp

private fun isStaticImage(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    if (url.startsWith("data:video")) return false
    val lower = url.lowercase()
    if (lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mov")) return false
    return true
}

/**
 * Full-bleed banner with the top scrim and a real blurred band behind the
 * system-bar row. The blurred band is only drawn for still images: duplicating a
 * looping video would mean decoding it twice on every profile open.
 */
@Composable
fun NebulaImmersiveBanner(
    bannerUrl: String?,
    bannerIndex: Int,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = BANNER_HEIGHT,
    cornerRadius: androidx.compose.ui.unit.Dp = 26.dp,
    showBlurBand: Boolean = true
) {
    val theme = Theme.current
    val bottomShape = RoundedCornerShape(bottomStart = cornerRadius, bottomEnd = cornerRadius)

    Box(modifier = modifier.fillMaxWidth().height(height)) {
        NebulaBannerView(
            bannerUrl = bannerUrl,
            bannerIndex = bannerIndex,
            modifier = Modifier.fillMaxSize(),
            shape = bottomShape
        )

        // Frosted band: a blurred copy of the same still image clipped to the top
        // third of the banner. The copy is drawn at the banner's full height and
        // then cropped by its container, so it stays pixel-aligned with the sharp
        // artwork underneath instead of being scaled into a different framing.
        if (showBlurBand && isStaticImage(bannerUrl)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height * 0.38f)
                    .clipToBounds()
            ) {
                AsyncImage(
                    model = bannerUrl,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(height)
                        .blur(28.dp, edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded)
                        .alpha(0.55f)
                )
            }
        }

        // Top scrim: dark at the very top, gone by 60% down the banner.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.62f),
                        0.28f to Color.Black.copy(alpha = 0.28f),
                        0.6f to Color.Transparent
                    )
                )
        )

        // Bottom fade into the page so the artwork meets the surface without a hard seam.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(height / 4)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to theme.colors.background.copy(alpha = 0.92f)
                    )
                )
        )
    }
}

/**
 * The whole identity block: banner, overlapping avatar, name, presence, social
 * counters, bio and tags, with slots for the floating top-bar actions and the
 * row of primary buttons.
 */
@Composable
fun NebulaProfileHeader(
    user: NebulaUser,
    friendsCount: Int?,
    isFriendCountPublic: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    tierLabel: String? = null,
    statusText: String? = null,
    presenceColor: Color? = null,
    topBar: (@Composable RowScope.() -> Unit)? = null,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    trailingNameBadge: (@Composable () -> Unit)? = null
) {
    val theme = Theme.current
    val bannerHeight = if (compact) COMPACT_BANNER_HEIGHT else BANNER_HEIGHT
    val avatarSize = if (compact) COMPACT_AVATAR_SIZE else AVATAR_SIZE
    val overlap = if (compact) COMPACT_AVATAR_OVERLAP else AVATAR_OVERLAP
    val gutter = if (compact) 16.dp else 20.dp

    val isOffline = user.status.equals("offline", ignoreCase = true) ||
        user.presenceStatus == NebulaPresenceStatus.OFFLINE ||
        !user.isOnline
    val status = statusText?.takeIf { it.isNotBlank() } ?: run {
        when {
            isOffline -> "Offline"
            user.presenceStatus == NebulaPresenceStatus.IN_A_ROOM -> "In a room"
            user.presenceStatus == NebulaPresenceStatus.PRIVATE_CHAT -> "In a private chat"
            else -> "Online"
        }
    }
    val dotColor = presenceColor ?: when {
        isOffline -> theme.colors.textTertiary
        user.presenceStatus == NebulaPresenceStatus.IN_A_ROOM -> theme.colors.secondaryAccent
        user.presenceStatus == NebulaPresenceStatus.PRIVATE_CHAT -> theme.colors.primaryAccent
        else -> theme.colors.successColor
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(bannerHeight)) {
            NebulaImmersiveBanner(
                bannerUrl = user.bannerUrl,
                bannerIndex = user.bannerIndex,
                height = bannerHeight
            )

            // Floating controls over the artwork, inset for the status bar.
            if (topBar != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    content = topBar
                )
            }

            // Avatar hangs over the banner's bottom edge.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = gutter, y = overlap)
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(theme.colors.background)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .border(1.dp, theme.colors.surfaceBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                NebulaAvatarImage(
                    avatarUrl = user.avatarUrl,
                    fallbackLetter = user.avatarLetter.ifBlank { user.displayName.take(1) },
                    contentDescription = user.displayName,
                    modifier = Modifier.fillMaxSize(),
                    fallbackTextSize = if (compact) 24.sp else 30.sp
                )
            }

            // Presence dot pinned to the avatar, not the banner, so it stays glued
            // to the face when the layout is compact.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = gutter + avatarSize - 24.dp, y = overlap + avatarSize - 24.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(dotColor)
                    .border(4.dp, theme.colors.background, CircleShape)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = gutter, end = gutter)
                .padding(top = overlap + 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = user.displayName,
                    style = theme.typography.titleLarge.copy(
                        color = theme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (compact) 19.sp else 23.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (tierLabel != null) {
                    Spacer(modifier = Modifier.size(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(theme.colors.primaryAccent.copy(alpha = 0.16f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = tierLabel,
                            style = theme.typography.caption.copy(
                                color = theme.colors.primaryAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.5.sp
                            )
                        )
                    }
                }
                if (trailingNameBadge != null) {
                    Spacer(modifier = Modifier.size(8.dp))
                    trailingNameBadge()
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (user.username.startsWith("@")) user.username else "@${user.username}",
                    style = theme.typography.bodyMedium.copy(
                        color = theme.colors.textSecondary,
                        fontSize = if (compact) 12.5.sp else 13.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Presence line, Discord-style: a coloured dot then the status text.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = status,
                    style = theme.typography.caption.copy(
                        color = if (isOffline) theme.colors.textSecondary else theme.colors.textPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 220.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Social counters stay visible on every profile — including the
            // owner's — because they are the reason people open this screen.
            val friendsLabel = when {
                !isFriendCountPublic -> "Friends private"
                friendsCount == null -> "Friends private"
                friendsCount == 1 -> "1 friend"
                else -> "$friendsCount friends"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                CounterChip(text = friendsLabel)
                val rooms = user.roomsHostedCount + user.roomsJoinedCount
                if (rooms > 0) {
                    Spacer(modifier = Modifier.size(8.dp))
                    CounterChip(text = if (rooms == 1) "1 room" else "$rooms rooms")
                }
                if (user.mutualFriendsCount > 0) {
                    Spacer(modifier = Modifier.size(8.dp))
                    CounterChip(text = "${user.mutualFriendsCount} mutual")
                }
            }

            if (user.bio.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = user.bio,
                    style = theme.typography.bodyMedium.copy(
                        color = theme.colors.textPrimary,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp
                    )
                )
            }

            if (user.interests.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    user.interests.take(compactForInterests(compact)).forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(theme.colors.surfaceElevated)
                                .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(9.dp))
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = tag,
                                style = theme.typography.caption.copy(
                                    color = theme.colors.textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (actions != null) {
                Spacer(modifier = Modifier.height(16.dp))
                actions()
            }
        }
    }
}

private fun compactForInterests(compact: Boolean): Int = if (compact) 3 else 5

/** Small neutral pill used for the friend/room counters under the handle. */
@Composable
private fun CounterChip(text: String) {
    val theme = Theme.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(theme.colors.surface)
            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = theme.typography.caption.copy(
                color = theme.colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1
        )
    }
}
