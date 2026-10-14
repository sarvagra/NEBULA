package com.nebula.app.ui.settings

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.nebula.app.data.local.SessionManager
import com.nebula.app.data.backend.NebulaApiClient
import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.theme.Theme

/**
 * NebulaSettingsScreen:
 * Authoritative settings hub structured in exact required order:
 * 1. Account
 * 2. Google Account
 * 3. Appearance
 * 4. Permissions
 * 5. Privacy Policy
 * 6. About
 * 7. Nebula Premium
 * 
 * Clean layout: strictly ICON + TITLE + navigation indicator (no subtitles).
 */
@Composable
fun NebulaSettingsScreen(
    onBack: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenGoogleAccount: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenNebulaPremium: () -> Unit,
    // Caller-supplied navigation for the "Blocked users" row. When null (default) the
    // screen opens NebulaBlockedUsersScreen in place so the row always works, even
    // before the hosting navigation graph is wired up.
    onOpenBlockedUsers: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    var showBlockedUsersInPlace by remember { mutableStateOf(false) }
    // Re-engagement messaging is opt-in: the switch reflects the server value and
    // is the only thing that turns it on.
    var reengagementOptIn by remember { mutableStateOf(false) }
    var reengagementLoading by remember { mutableStateOf(true) }
    var reengagementUpdating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        when (val res = NebulaApiClient.getReengagementPreferences()) {
            is NebulaApiClient.ApiResponse.Success -> {
                reengagementOptIn = res.data.optJSONObject("data")?.optBoolean("optIn", false) ?: false
            }
            is NebulaApiClient.ApiResponse.Error -> Unit
        }
        reengagementLoading = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.colors.background)
            .statusBarsPadding()
    ) {
        // TOP APP BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = theme.colors.textPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Text(
                text = "Settings",
                style = theme.typography.titleMedium.copy(
                    color = theme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, top = 12.dp, end = 18.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(theme.colors.surface)
                        .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(16.dp))
                ) {
                    // 1. Account
                    SettingNavRow(
                        icon = Icons.Default.Person,
                        title = "Account",
                        onClick = onOpenAccount
                    )

                    SettingsDivider()

                    // 2. Google Account
                    SettingNavRow(
                        icon = Icons.Default.Security,
                        title = "Google Account",
                        onClick = onOpenGoogleAccount
                    )

                    SettingsDivider()

                    // 3. Appearance
                    SettingNavRow(
                        icon = Icons.Default.Palette,
                        title = "Appearance",
                        onClick = onOpenAppearance
                    )

                    SettingsDivider()

                    // 4. Permissions
                    SettingNavRow(
                        icon = Icons.Default.Shield,
                        title = "Permissions",
                        onClick = onOpenPermissions
                    )

                    SettingsDivider()

                    // 5. Privacy Policy
                    SettingNavRow(
                        icon = Icons.Default.Security,
                        title = "Privacy Policy",
                        onClick = onOpenPrivacyPolicy
                    )

                    SettingsDivider()

                    SettingNavRow(
                        icon = Icons.Default.Block,
                        title = "Blocked users",
                        onClick = {
                            val navigate = onOpenBlockedUsers
                            if (navigate != null) navigate() else showBlockedUsersInPlace = true
                        }
                    )

                    SettingsDivider()

                    // 6. Re-engagement messaging (explicit consent, server-enforced)
                    SettingToggleRow(
                        icon = Icons.Default.NotificationsNone,
                        title = "Re-engagement messages",
                        subtitle = "Occasional reminders when you have been away. Off unless you turn it on.",
                        checked = reengagementOptIn,
                        enabled = !reengagementLoading && !reengagementUpdating,
                        onCheckedChange = { next ->
                            reengagementUpdating = true
                            scope.launch {
                                when (val res = NebulaApiClient.setReengagementOptIn(next)) {
                                    is NebulaApiClient.ApiResponse.Success -> {
                                        reengagementOptIn =
                                            res.data.optJSONObject("data")?.optBoolean("optIn", next) ?: next
                                        Toast.makeText(
                                            context,
                                            if (next) "Re-engagement messages on" else "Re-engagement messages off",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    is NebulaApiClient.ApiResponse.Error -> {
                                        Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                    }
                                }
                                reengagementUpdating = false
                            }
                        }
                    )

                    SettingsDivider()

                    // 7. About
                    SettingNavRow(
                        icon = Icons.Default.Info,
                        title = "About",
                        onClick = onOpenAbout
                    )

                    SettingsDivider()

                    // 8. Nebula Premium
                    SettingNavRow(
                        icon = Icons.Default.Star,
                        title = "Nebula Premium",
                        onClick = onOpenNebulaPremium
                    )
                }
            }
        }
    }

    // Dedicated settings screen (shares the host's layout slot, so it fully covers
    // this screen without requiring the caller to add a navigation destination).
    if (showBlockedUsersInPlace) {
        NebulaBlockedUsersScreen(
            onBack = { showBlockedUsersInPlace = false },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun SettingToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val theme = Theme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = theme.colors.textSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = theme.typography.bodyMedium.copy(
                    color = theme.colors.textPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.5.sp
                )
            )
            Text(
                text = subtitle,
                style = theme.typography.caption.copy(
                    color = theme.colors.textSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp
                )
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = theme.colors.onPrimaryAccent,
                checkedTrackColor = theme.colors.primaryAccent,
                uncheckedThumbColor = theme.colors.textSecondary,
                uncheckedTrackColor = theme.colors.surfaceElevated,
                uncheckedBorderColor = theme.colors.surfaceBorder
            )
        )
    }
}

@Composable
private fun SettingNavRow(
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
private fun SettingsDivider() {
    val theme = Theme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(theme.colors.surfaceBorder)
    )
}
