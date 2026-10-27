package com.nebula.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.nebula.app.domain.model.NebulaSubscriptionTier
import com.nebula.app.domain.model.UserProfile
import com.nebula.app.theme.PAColors
import com.nebula.app.theme.Theme

/**
 * Banner fallback palettes shared by the public profile screen and this overlay,
 * so a profile without a banner image gets the exact same gradient in both places.
 */
internal val NebulaBannerPalettes: List<List<Color>> = listOf(
    listOf(Color(0xFF1E1B4B), Color(0xFF312E81)), // Deep Indigo
    listOf(Color(0xFF3B0764), Color(0xFF581C87)), // Nebula Violet
    listOf(Color(0xFF042F2E), Color(0xFF115E59)), // Midnight Teal
    listOf(Color(0xFF450A0A), Color(0xFF7F1D1D)), // Cosmic Crimson
    listOf(Color(0xFF090B10), Color(0xFF1E222D))  // Obsidian Glass
)

/**
 * LiquidGlassProfileOverlay:
 * Full-screen liquid glass overlay that completely overlays the screen when
 * user profile avatar is clicked. Replaces old sliding sidebar drawer.
 *
 * Includes:
 * - Profile header with clean banner
 * - View Public Profile action
 * - Nebula Membership entry (clean, no subtitles)
 * - Settings entry
 * - About NEBULA entry (clean, no subtitles)
 * - Log out action
 */
@Composable
fun LiquidGlassProfileOverlay(
    isOpen: Boolean,
    userProfile: UserProfile,
    subscriptionTier: NebulaSubscriptionTier = NebulaSubscriptionTier.FREE,
    onViewPublicProfile: () -> Unit,
    onEditProfile: () -> Unit = {},
    onOpenPremiumPlans: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenAbout: () -> Unit,
    onLogoutClick: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val theme = Theme.current

    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(tween(250)) + scaleIn(initialScale = 0.95f, animationSpec = tween(250)),
        exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.95f, animationSpec = tween(200))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(theme.colors.background)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top close action row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "NEBULA PROFILE",
                        style = theme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = theme.colors.textPrimary,
                            letterSpacing = 1.8.sp,
                            fontSize = 14.sp
                        )
                    )

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(theme.colors.surfaceElevated)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = theme.colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // 1. PROFILE IDENTITY CARD WITH CUSTOMIZABLE BANNER
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlassSurface(RoundedCornerShape(20.dp), theme.colors)
                ) {
                    // The banner is the card's background: it spans the full card,
                    // and the avatar/name/actions are content drawn on top of it.
                    ProfileOverlayBanner(
                        bannerUrl = userProfile.bannerUrl,
                        bannerIndex = userProfile.bannerIndex,
                        modifier = Modifier.matchParentSize()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to theme.colors.background.copy(alpha = 0.10f),
                                    0.45f to theme.colors.background.copy(alpha = 0.25f),
                                    1f to theme.colors.background.copy(alpha = 0.82f)
                                )
                            )
                    )

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 150.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .offset(y = 38.dp)
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .background(theme.colors.surfaceElevated)
                                    .border(3.dp, theme.colors.surface, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                NebulaAvatarImage(
                                    avatarUrl = userProfile.avatarUri,
                                    fallbackLetter = userProfile.avatarFallbackLetter,
                                    contentDescription = userProfile.displayName,
                                    modifier = Modifier.fillMaxSize(),
                                    fallbackTextSize = 26.sp
                                )
                            }
                        }

                        // Avatar & Name (avatar overlaps the banner above)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp)
                                // 38dp avatar overhang + 10dp gap keeps the name
                                // clear of the avatar for every font scale.
                                .padding(top = 48.dp, bottom = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = userProfile.displayName,
                                style = theme.typography.titleMedium.copy(
                                    color = theme.colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            )

                            Text(
                                text = userProfile.username ?: "@nebula_user",
                                style = theme.typography.bodySmall.copy(
                                    color = theme.colors.textSecondary,
                                    fontSize = 13.sp
                                )
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Profile Action Buttons (Public View & Edit Profile)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        onDismissRequest()
                                        onViewPublicProfile()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = theme.colors.surfaceElevated,
                                        contentColor = theme.colors.textPrimary
                                    ),
                                    border = BorderStroke(1.dp, theme.colors.surfaceBorder.copy(alpha = 0.65f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Visibility,
                                            contentDescription = null,
                                            tint = theme.colors.primaryAccent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Public View",
                                            style = theme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        onDismissRequest()
                                        onEditProfile()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = theme.colors.surfaceElevated,
                                        contentColor = theme.colors.textPrimary
                                    ),
                                    border = BorderStroke(1.dp, theme.colors.surfaceBorder.copy(alpha = 0.65f)),
                                    shape = RoundedCornerShape(12.dp)
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
                                            style = theme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. NEBULA MEMBERSHIP CARD (Uniform greyish liquidGlassSurface matching other options)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlassSurface(RoundedCornerShape(16.dp), theme.colors)
                        .clickable {
                            onDismissRequest()
                            onOpenPremiumPlans()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(theme.colors.primaryAccent.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = theme.colors.primaryAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = "Nebula Membership",
                                style = theme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = theme.colors.textPrimary,
                                    fontSize = 15.sp
                                )
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = theme.colors.primaryAccent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // 3. ESSENTIAL SHORTCUTS (Settings & About NEBULA)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlassSurface(RoundedCornerShape(16.dp), theme.colors)
                ) {
                    LiquidGlassActionRow(
                        icon = Icons.Default.Settings,
                        label = "Settings",
                        onClick = {
                            onDismissRequest()
                            onOpenSettings()
                        }
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(theme.colors.surfaceBorder)
                    )

                    LiquidGlassActionRow(
                        icon = Icons.Default.Info,
                        label = "About NEBULA",
                        onClick = {
                            onDismissRequest()
                            onOpenAbout()
                        }
                    )
                }

                // 4. LOG OUT BUTTON
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(theme.colors.surfaceElevated)
                        .border(
                            1.dp,
                            theme.colors.surfaceBorder.copy(alpha = 0.65f),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable(onClick = onLogoutClick)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Log out",
                            tint = theme.colors.errorColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Log out",
                            style = theme.typography.bodyMedium.copy(
                                color = theme.colors.errorColor,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun LiquidGlassActionRow(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(theme.colors.primaryAccent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = theme.colors.primaryAccent,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column {
                Text(
                    text = label,
                    style = theme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = theme.colors.textPrimary,
                        fontSize = 14.sp
                    )
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = theme.typography.caption.copy(
                            color = theme.colors.textSecondary,
                            fontSize = 11.5.sp
                        )
                    )
                }
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = theme.colors.textTertiary,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * Liquid-glass card surface: a semi-transparent theme surface, a soft white sheen
 * across the top third and a hairline highlight border, all clipped to [shape].
 * The sheen/highlight intensities follow [PAColors.isDark], so the same recipe
 * reads correctly on the light theme and on all three dark themes.
 */
private fun Modifier.liquidGlassSurface(shape: Shape, colors: PAColors): Modifier {
    val sheenAlpha = if (colors.isDark) 0.10f else 0.55f
    val highlightAlpha = if (colors.isDark) 0.30f else 0.95f

    return this
        .clip(shape)
        // Opaque theme surface: the translucent version let the banner bleed
        // through the menu rows and looked unreadable next to the rest of the app.
        .background(colors.surfaceElevated)
        .background(
            Brush.verticalGradient(
                0.0f to Color.White.copy(alpha = sheenAlpha),
                0.45f to Color.Transparent
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = highlightAlpha),
                1f to colors.surfaceBorder.copy(alpha = 0.55f)
            ),
            shape = shape
        )
}

/**
 * Banner renderer for the overlay. The gradient fallback is always painted first,
 * so a null URL, a failed base64 decode or a Coil load error simply reveals the
 * brand gradient for [bannerIndex] instead of an empty box.
 */
@Composable
private fun ProfileOverlayBanner(
    bannerUrl: String?,
    bannerIndex: Int,
    modifier: Modifier = Modifier
) {
    val palette = remember(bannerIndex) {
        NebulaBannerPalettes[bannerIndex.coerceIn(0, NebulaBannerPalettes.lastIndex)]
    }

    // Vertical gradient across the whole card: the previous horizontal band left a
    // hard seam where it stopped, which looked like a broken header image.
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = palette + listOf(palette.last().copy(alpha = 0.85f)),
                startY = 0f,
                endY = Float.POSITIVE_INFINITY
            )
        )
    ) {
        if (!bannerUrl.isNullOrBlank()) {
            if (isVideoBanner(bannerUrl)) {
                VideoBannerPlayer(
                    videoUrl = bannerUrl,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val base64Bitmap = remember(bannerUrl) { decodeBase64Image(bannerUrl) }
                if (base64Bitmap != null) {
                    Image(
                        bitmap = base64Bitmap.asImageBitmap(),
                        contentDescription = "Profile banner",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    SubcomposeAsyncImage(
                        model = bannerUrl,
                        contentDescription = "Profile banner",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        loading = { },
                        error = { },
                        success = { SubcomposeAsyncImageContent() }
                    )
                }
            }
        }
    }
}

/** Decodes an inline `data:image/...;base64,...` banner, or returns null. */
private fun decodeBase64Image(value: String): Bitmap? {
    if (!value.startsWith("data:image")) return null
    return try {
        val decoded = Base64.decode(value.substringAfter("base64,"), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
    } catch (e: Exception) {
        null
    }
}
