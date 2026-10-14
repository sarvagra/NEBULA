package com.nebula.app.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.nebula.app.ui.components.PABottomSheet

/**
 * NebulaSettingsSheet:
 * Authoritative settings hub structured in exact required order:
 * 1. Account
 * 2. Google Account
 * 3. Appearance & Themes
 * 4. Permissions
 * 5. Privacy Policy
 * 6. About
 * 7. Nebula Premium
 *
 * Clean layout: strictly ICON + TITLE (no subtitles).
 *
 * State ownership: this sheet intentionally holds NO profile/banner/status state. It only routes
 * to the single profile editor (Account), so the persisted banner/status values keep exactly one
 * owner and cannot be written from two places.
 */
@Composable
fun NebulaSettingsSheet(
    isVisible: Boolean,
    onOpenAccount: () -> Unit,
    onOpenGoogleAccount: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenNebulaPremium: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = Theme.current

    PABottomSheet(
        isVisible = isVisible,
        onDismissRequest = onDismiss,
        title = "Settings"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(theme.colors.surface)
                    .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
            ) {
                // 1. Account
                SettingsItemRow(
                    icon = Icons.Default.Person,
                    title = "Account",
                    onClick = onOpenAccount
                )
                SettingsRowDivider()

                // 2. Google Account
                SettingsItemRow(
                    icon = Icons.Default.Security,
                    title = "Google Account",
                    onClick = onOpenGoogleAccount
                )
                SettingsRowDivider()

                // 3. Appearance & Themes
                SettingsItemRow(
                    icon = Icons.Default.Palette,
                    title = "Appearance & Themes",
                    onClick = onOpenAppearance
                )
                SettingsRowDivider()

                // 4. Permissions
                SettingsItemRow(
                    icon = Icons.Default.Shield,
                    title = "Permissions",
                    onClick = onOpenPermissions
                )
                SettingsRowDivider()

                // 5. Privacy Policy
                SettingsItemRow(
                    icon = Icons.Default.Security,
                    title = "Privacy Policy",
                    onClick = onOpenPrivacyPolicy
                )
                SettingsRowDivider()

                // 6. About
                SettingsItemRow(
                    icon = Icons.Default.Info,
                    title = "About",
                    onClick = onOpenAbout
                )
                SettingsRowDivider()

                // 7. Nebula Premium
                SettingsItemRow(
                    icon = Icons.Default.Star,
                    title = "Nebula Premium",
                    onClick = onOpenNebulaPremium
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun SettingsItemRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    val theme = Theme.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(theme.colors.surfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = theme.colors.primaryAccent,
                    modifier = Modifier.size(19.dp)
                )
            }

            Text(
                text = title,
                style = theme.typography.bodyMedium.copy(
                    color = theme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = theme.colors.textTertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SettingsRowDivider() {
    val theme = Theme.current
    HorizontalDivider(
        color = theme.colors.surfaceBorder.copy(alpha = 0.5f),
        thickness = 0.8.dp,
        modifier = Modifier.padding(horizontal = 14.dp)
    )
}

