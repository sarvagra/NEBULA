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
    // Sharp bottom edge: a rounded banner read as a card, not a header.
    val bottomShape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp)

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
        // The frosted band only works over a dark background; in light themes it
        // washed the banner out, so it is skipped there.
        if (showBlurBand && theme.colors.isDark && isStaticImage(bannerUrl)) {
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
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    tierLabel: String? = null,
    presenceColor: Color? = null,
    topBarStart: (@Composable RowScope.() -> Unit)? = null,
    topBar: (@Composable RowScope.() -> Unit)? = null,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    trailingNameBadge: (@Composable () -> Unit)? = null
) {
    val theme = Theme.current
    val bannerHeight = if (compact) COMPACT_BANNER_HEIGHT else BANNER_HEIGHT
    val avatarSize = if (compact) COMPACT_AVATAR_SIZE else AVATAR_SIZE
    val overlap = if (compact) COMPACT_AVATAR_OVERLAP else AVATAR_OVERLAP
    val gutter = if (compact) 16.dp else 20.dp

    // Presence is carried by the badge on the avatar corner alone. Repeating it as
    // a dot plus a word under the handle said the same thing twice and pushed the
    // counters people actually read further down.
    val isOffline = user.status.equals("offline", ignoreCase = true) ||
        user.presenceStatus == NebulaPresenceStatus.OFFLINE ||
        !user.isOnline
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

            // Floating controls over the artwork, inset for the status bar. Leading
            // controls (back) and trailing ones (settings, overflow) are separate
            // slots so a back arrow can never drift to the right edge.
            if (topBar != null || topBarStart != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // A sheet already sits below the status bar, so only the
                        // full-screen headers inset their controls.
                        .then(if (compact) Modifier else Modifier.statusBarsPadding())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        content = topBarStart ?: {}
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        content = topBar ?: {}
                    )
                }
            }

            // Avatar hangs over the banner's bottom edge.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = gutter, y = overlap)
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(theme.colors.background)
                    .padding(3.5.dp)
                    .clip(CircleShape),
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

            // Presence dot pinned to the avatar's own bottom-right corner. Both the
            // avatar and the dot are bottom-aligned to the banner, so the dot's
            // offset is measured from that edge too: `overlap` is exactly how far
            // the avatar hangs below it. Using the avatar height here instead placed
            // the dot a full badge-width below the face.
            val dotSize = 20.dp
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = gutter + avatarSize - 20.dp, y = overlap - 16.dp)
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(dotColor)
                    .border(3.dp, theme.colors.background, CircleShape)
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

            Spacer(modifier = Modifier.height(10.dp))

            // Only mutual connections sit under the handle. A "N rooms" chip here
            // stacked a second counter directly beneath the name, and the friend-count
            // chip beside the name read as a stray number rather than a stat.
            if (user.mutualFriendsCount > 0) {
                CounterChip(text = "${user.mutualFriendsCount} mutual")
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


/** 999 -> "999", 1200 -> "1.2K", 3_400_000 -> "3.4M". */
internal fun compactCount(value: Int): String = when {
    value < 1000 -> value.toString()
    value < 1_000_000 -> {
        val thousands = value / 1000.0
        if (thousands >= 100) "${thousands.toInt()}K" else String.format(java.util.Locale.US, "%.1fK", thousands)
    }
    else -> {
        val millions = value / 1_000_000.0
        if (millions >= 100) "${millions.toInt()}M" else String.format(java.util.Locale.US, "%.1fM", millions)
    }
}
