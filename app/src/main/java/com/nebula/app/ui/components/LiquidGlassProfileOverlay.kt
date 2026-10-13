package com.nebula.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.domain.model.NebulaSubscriptionTier
import com.nebula.app.domain.model.UserProfile
import com.nebula.app.theme.Theme

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
                .background(Color(0xE608090D)) // Liquid dark glass backdrop
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
                        .clip(RoundedCornerShape(20.dp))
                        .background(theme.colors.surface)
                        .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Banner Background
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF23283E), Color(0xFF141721))
                                    )
                                )
                        )

                        // Avatar & Name (overlapping banner)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp)
                                .padding(top = 0.dp, bottom = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
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

                            Spacer(modifier = Modifier.height(10.dp))

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
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        onDismissRequest()
                                        onViewPublicProfile()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = theme.colors.surfaceElevated,
                                        contentColor = theme.colors.textPrimary
                                    ),
                                    shape = RoundedCornerShape(10.dp)
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
                                                fontSize = 12.5.sp
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
                                        .height(40.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = theme.colors.primaryAccent.copy(alpha = 0.15f),
                                        contentColor = theme.colors.primaryAccent
                                    ),
                                    shape = RoundedCornerShape(10.dp)
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
                                                fontSize = 12.5.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. NEBULA MEMBERSHIP CARD (Clean: strictly Icon + Title + Indicator, no explanatory subtitles)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF4F46E5).copy(alpha = 0.22f),
                                    Color(0xFF7C3AED).copy(alpha = 0.12f)
                                )
                            )
                        )
                        .border(
                            1.2.dp,
                            Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFFA855F7))),
                            RoundedCornerShape(18.dp)
                        )
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
                                    .background(Color(0xFF6366F1).copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFA5B4FC),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = "Nebula Membership",
                                style = theme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color(0xFFA5B4FC),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // 3. ESSENTIAL SHORTCUTS (Settings & About NEBULA)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(theme.colors.surface)
                        .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(16.dp))
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
                        .background(theme.colors.errorColor.copy(alpha = 0.12f))
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
