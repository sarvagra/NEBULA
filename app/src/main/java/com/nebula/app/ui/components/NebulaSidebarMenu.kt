package com.nebula.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.theme.Theme

/**
 * NebulaSidebarMenu: the app menu behind the avatar in the Home header.
 *
 * This is deliberately a plain list on the standard surface tokens - the same shape
 * every other menu in the app uses. The earlier version was a full-screen liquid
 * glass card that repeated the profile (banner, avatar, name, follow counters) and
 * carried its own edit-profile shortcut, which made a menu look like a second
 * profile page and gave the same destination two different entry points.
 */
@Composable
fun NebulaSidebarMenu(
    isOpen: Boolean,
    onOpenAccount: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenAbout: () -> Unit,
    onLogoutClick: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val theme = Theme.current

    BackHandler(enabled = isOpen) { onDismissRequest() }

    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(140))
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
                    .padding(horizontal = theme.spacing.screenHorizontal)
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "NEBULA",
                        style = theme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = theme.colors.textPrimary,
                            letterSpacing = 2.4.sp,
                            fontSize = 14.sp
                        )
                    )
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(theme.colors.surfaceElevated)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close menu",
                            tint = theme.colors.textPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(theme.radii.lg))
                        .background(theme.colors.surface)
                ) {
                    MenuRow(
                        icon = Icons.Default.Person,
                        label = "Account",
                        onClick = onOpenAccount
                    )
                    MenuDivider()
                    MenuRow(
                        icon = Icons.Default.Settings,
                        label = "Settings",
                        onClick = onOpenSettings
                    )
                    MenuDivider()
                    MenuRow(
                        icon = Icons.Default.Info,
                        label = "About NEBULA",
                        onClick = onOpenAbout
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(theme.radii.lg))
                        .background(theme.colors.surface)
                ) {
                    MenuRow(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        label = "Log out",
                        showChevron = false,
                        tint = theme.colors.errorColor,
                        onClick = onLogoutClick
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    showChevron: Boolean = true,
    tint: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit
) {
    val theme = Theme.current
    val contentColor = tint ?: theme.colors.textPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.size(14.dp))
        Text(
            text = label,
            style = theme.typography.bodyMedium.copy(
                color = contentColor,
                fontWeight = FontWeight.Medium
            ),
            modifier = Modifier.weight(1f)
        )
        if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = theme.colors.textTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun MenuDivider() {
    val theme = Theme.current
    HorizontalDivider(
        modifier = Modifier.padding(start = 50.dp),
        thickness = 1.dp,
        color = theme.colors.surfaceBorder.copy(alpha = 0.6f)
    )
}
