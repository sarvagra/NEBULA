package com.nebula.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.domain.model.UserProfile
import com.nebula.app.theme.Theme

/**
 * NASidebar:
 * Modern left sliding navigation drawer for NEBULA.
 * Clean, restrained, Discord & Snapchat inspired profile drawer.
 * Contains:
 * - Profile header with avatar, display name, and @username
 * - Edit Profile
 * - Friends
 * - Settings
 * - Notifications
 * - Privacy
 * - AI Settings
 * - About
 * - Logout
 */
@Composable
fun NASidebar(
    isOpen: Boolean,
    userProfile: UserProfile,
    showInlineSuggestions: Boolean,
    onToggleInlineSuggestions: (Boolean) -> Unit,
    onEditProfile: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAISettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onLogoutClick: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(spring(stiffness = Spring.StiffnessMediumLow)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                )
        ) {
            AnimatedVisibility(
                visible = isOpen,
                enter = slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                ),
                exit = slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                ),
                modifier = Modifier
                    .fillMaxHeight()
                    .width(310.dp)
                    .align(Alignment.CenterStart)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(theme.colors.background)
                        .border(
                            width = 1.dp,
                            color = theme.colors.surfaceBorder,
                            shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { /* Prevent dismissing when clicking inside drawer */ }
                        )
                        .padding(top = statusBarTop + 8.dp, bottom = navBarBottom + 8.dp)
                ) {
                    // TOP BAR: Back button + Title
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close sidebar",
                                tint = theme.colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Text(
                            text = "NEBULA",
                            style = theme.typography.caption.copy(
                                color = theme.colors.textSecondary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                fontSize = 12.sp
                            ),
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // PROFILE HEADER: Avatar, Name, @username
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(theme.colors.surfaceElevated)
                                    .border(2.dp, theme.colors.primaryAccent.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = userProfile.avatarFallbackLetter,
                                    style = theme.typography.titleMedium.copy(
                                        color = theme.colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 24.sp
                                    )
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(theme.colors.primaryAccent)
                                    .clickable(onClick = onEditProfile),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Profile",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

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
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // SCROLLABLE SECTIONS
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // SECTION 1: Social & People
                        SidebarGroupCard(title = "Social") {
                            SidebarRow(
                                icon = Icons.Default.Edit,
                                label = "Edit Profile",
                                subtitle = "Name, avatar, status",
                                onClick = onEditProfile
                            )
                            SidebarDivider()
                            SidebarRow(
                                icon = Icons.Default.People,
                                label = "Friends",
                                subtitle = "Manage friends & invites",
                                onClick = onOpenFriends
                            )
                        }

                        // SECTION 2: AI Settings
                        SidebarGroupCard(title = "AI Engine") {
                            SidebarRow(
                                icon = Icons.Default.AutoAwesome,
                                label = "AI Settings",
                                subtitle = "Context suggestions & local NPU",
                                isAccent = true,
                                onClick = onOpenAISettings
                            )
                            SidebarDivider()
                            // Quick In-line Suggestions toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "In-line Suggestions",
                                        style = theme.typography.bodyMedium.copy(
                                            color = theme.colors.textPrimary,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp
                                        )
                                    )
                                    Text(
                                        text = if (showInlineSuggestions) "Active in chat" else "Disabled",
                                        style = theme.typography.caption.copy(
                                            color = theme.colors.textSecondary,
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                                Switch(
                                    checked = showInlineSuggestions,
                                    onCheckedChange = onToggleInlineSuggestions,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = theme.colors.primaryAccent,
                                        uncheckedThumbColor = theme.colors.textTertiary,
                                        uncheckedTrackColor = theme.colors.surfaceElevated
                                    )
                                )
                            }
                        }

                        // SECTION 3: System & Security
                        SidebarGroupCard(title = "Settings") {
                            SidebarRow(
                                icon = Icons.Default.Settings,
                                label = "Settings",
                                subtitle = "Preferences & appearance",
                                onClick = onOpenSettings
                            )
                            SidebarDivider()
                            SidebarRow(
                                icon = Icons.Default.Notifications,
                                label = "Notifications",
                                subtitle = "Room mentions & direct chats",
                                onClick = onOpenNotifications
                            )
                            SidebarDivider()
                            SidebarRow(
                                icon = Icons.Default.Info,
                                label = "About",
                                subtitle = "Official info & privacy policy",
                                onClick = onOpenAbout
                            )
                        }

                        // SECTION 4: Log Out
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
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Log out",
                                    tint = theme.colors.errorColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Log out",
                                    style = theme.typography.bodyMedium.copy(
                                        color = theme.colors.errorColor,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarGroupCard(
    title: String,
    content: @Composable () -> Unit
) {
    val theme = Theme.current

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = title,
            style = theme.typography.caption.copy(
                color = theme.colors.textSecondary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.5.sp
            ),
            modifier = Modifier.padding(start = 4.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(theme.colors.surface)
                .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
        ) {
            content()
        }
    }
}

@Composable
private fun SidebarRow(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    isAccent: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val theme = Theme.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isAccent) theme.colors.primaryAccent else theme.colors.textPrimary,
            modifier = Modifier.size(19.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = theme.typography.bodyMedium.copy(
                    color = if (isAccent) theme.colors.primaryAccent else theme.colors.textPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            )
            if (subtitle != null) {
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
}

@Composable
private fun SidebarDivider() {
    val theme = Theme.current
    HorizontalDivider(
        color = theme.colors.surfaceBorder.copy(alpha = 0.5f),
        thickness = 0.8.dp,
        modifier = Modifier.padding(horizontal = 12.dp)
    )
}
