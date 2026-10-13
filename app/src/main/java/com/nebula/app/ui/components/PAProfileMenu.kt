package com.nebula.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.domain.model.ProfileMenuOption
import com.nebula.app.domain.model.UserProfile
import com.nebula.app.theme.Theme

/**
 * PAProfileMenu:
 * Spatially connected, fluid surface emerging directly from the profile avatar.
 * Replaces the old radial menu with a modern, high-precision menu.
 */
@Composable
fun PAProfileMenu(
    isOpen: Boolean,
    profile: UserProfile,
    options: List<ProfileMenuOption>,
    anchorOffset: Offset,
    onOptionSelected: (ProfileMenuOption) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // Smooth spring physics for scale and expansion
    val progress by animateFloatAsState(
        targetValue = if (isOpen) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "menuSpringProgress"
    )

    if (isOpen) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                )
        ) {
            // 1. Gently darkened scrim background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = progress * 0.55f))
            )

            // 2. Emerging connected surface anchored to profile button
            Box(
                modifier = Modifier
                    .padding(
                        top = statusBarTop + 8.dp,
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 24.dp
                    )
                    .graphicsLayer {
                        // Spatial origin at top-left where avatar is positioned
                        transformOrigin = TransformOrigin(0.08f, 0.03f)
                        scaleX = 0.85f + 0.15f * progress
                        scaleY = 0.85f + 0.15f * progress
                        alpha = progress.coerceIn(0f, 1f)
                    }
                    .clip(RoundedCornerShape(theme.radii.xl))
                    .background(theme.colors.surfaceElevated)
                    .border(
                        width = 1.dp,
                        color = theme.colors.surfaceBorder,
                        shape = RoundedCornerShape(theme.radii.xl)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* consume click */ }
                    )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Header: Profile Overview & Close Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            PAAvatar(profile = profile, size = 48.dp)

                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = profile.displayName,
                                        style = theme.typography.titleMedium.copy(
                                            color = theme.colors.textPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    if (profile.isPremium) {
                                        PABadge(
                                            text = "PRO",
                                            isPrimary = true
                                        )
                                    }
                                }
                                Text(
                                    text = profile.username ?: "@nebula_user",
                                    style = theme.typography.caption.copy(
                                        color = theme.colors.textSecondary
                                    )
                                )
                            }
                        }

                        PAIconButton(
                            icon = Icons.Default.Close,
                            contentDescription = "Close menu",
                            onClick = onDismissRequest,
                            size = 32.dp,
                            iconSize = 18.dp,
                            tint = theme.colors.textTertiary
                        )
                    }

                    HorizontalDivider(
                        color = theme.colors.surfaceBorder,
                        thickness = 0.8.dp
                    )

                    // Menu Rows: Perfectly aligned 10 destinations
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .padding(vertical = 6.dp)
                    ) {
                        items(options) { option ->
                            ProfileMenuRow(
                                option = option,
                                onClick = { onOptionSelected(option) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileMenuRow(
    option: ProfileMenuOption,
    onClick: () -> Unit
) {
    val theme = Theme.current
    val icon = resolveMenuIcon(option.iconKey)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Consistent icon container
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(theme.radii.sm))
                    .background(theme.colors.surface)
                    .border(0.8.dp, theme.colors.surfaceBorder, RoundedCornerShape(theme.radii.sm)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = theme.colors.primaryAccent,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.title,
                    style = theme.typography.bodyLarge.copy(
                        color = theme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.5.sp
                    )
                )
                Text(
                    text = option.subtitle,
                    style = theme.typography.caption.copy(
                        color = theme.colors.textSecondary,
                        fontSize = 11.5.sp
                    ),
                    maxLines = 1
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (option.badge != null) {
                PABadge(
                    text = option.badge,
                    isPrimary = option.badge == "Pro"
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = theme.colors.textTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private fun resolveMenuIcon(iconKey: String): ImageVector {
    return when (iconKey) {
        "person" -> Icons.Default.Person
        "settings" -> Icons.Default.Settings
        "security" -> Icons.Default.Security
        "lock" -> Icons.Default.Lock
        "palette" -> Icons.Default.Palette
        "psychology" -> Icons.Default.Psychology
        "notifications" -> Icons.Default.Notifications
        "extension" -> Icons.Default.Extension
        "star" -> Icons.Default.Star
        "info" -> Icons.Default.Info
        else -> Icons.Default.Settings
    }
}
