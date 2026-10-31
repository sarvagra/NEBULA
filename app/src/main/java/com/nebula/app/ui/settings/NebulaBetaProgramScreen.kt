package com.nebula.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.data.local.SessionManager
import com.nebula.app.theme.Theme

/**
 * NebulaBetaProgramScreen:
 * Full-screen dedicated settings page for the Nebula Beta Program and Premium feature toggles.
 * Every feature that was previously behind paywalls is available for free during open beta.
 */
@Composable
fun NebulaBetaProgramScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    val context = LocalContext.current
    val sessionManager = remember { SessionManager.getInstance(context) }

    BackHandler { onBack() }

    var featureCustomStatus by remember {
        mutableStateOf(sessionManager.isFeatureEnabled(SessionManager.FEATURE_CUSTOM_STATUS))
    }
    var featureCustomBanner by remember {
        mutableStateOf(sessionManager.isFeatureEnabled(SessionManager.FEATURE_CUSTOM_BANNER))
    }
    var featureVideoBanner by remember {
        mutableStateOf(sessionManager.isFeatureEnabled(SessionManager.FEATURE_VIDEO_BANNER))
    }
    var featureAllTags by remember {
        mutableStateOf(sessionManager.isFeatureEnabled(SessionManager.FEATURE_ALL_TAGS))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.colors.background)
    ) {
        // TOP APP BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = theme.colors.textPrimary
                )
            }
            Text(
                text = "Beta Program",
                style = theme.typography.titleMedium.copy(
                    color = theme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // INFO BANNER
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(theme.colors.primaryAccent.copy(alpha = 0.1f))
                        .border(1.dp, theme.colors.primaryAccent.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.colors.primaryAccent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = theme.colors.primaryAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "All Features Unlocked",
                                style = theme.typography.bodyLarge.copy(
                                    color = theme.colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Nebula is in open beta. All premium perks, customization options, and advanced room features are 100% free and unlocked for all participants.",
                                style = theme.typography.bodySmall.copy(
                                    color = theme.colors.textSecondary,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            )
                        }
                    }
                }
            }

            // FEATURE SWITCHES CARD
            item {
                Text(
                    text = "FEATURE PREFERENCES",
                    style = theme.typography.caption.copy(
                        color = theme.colors.textTertiary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    ),
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(theme.colors.surface)
                        .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(16.dp))
                ) {
                    BetaFeatureItem(
                        icon = Icons.Default.Tune,
                        title = "Custom status",
                        subtitle = "Write your own status text instead of preset labels",
                        checked = featureCustomStatus,
                        onCheckedChange = {
                            featureCustomStatus = it
                            sessionManager.setFeatureEnabled(SessionManager.FEATURE_CUSTOM_STATUS, it)
                        }
                    )

                    BetaDivider()

                    BetaFeatureItem(
                        icon = Icons.Default.Image,
                        title = "Custom banner",
                        subtitle = "Upload custom images or animated artwork for your profile banner",
                        checked = featureCustomBanner,
                        onCheckedChange = {
                            featureCustomBanner = it
                            sessionManager.setFeatureEnabled(SessionManager.FEATURE_CUSTOM_BANNER, it)
                        }
                    )

                    BetaDivider()

                    BetaFeatureItem(
                        icon = Icons.Default.Movie,
                        title = "Video banner",
                        subtitle = "Loop short high-definition clips in your profile header",
                        checked = featureVideoBanner,
                        onCheckedChange = {
                            featureVideoBanner = it
                            sessionManager.setFeatureEnabled(SessionManager.FEATURE_VIDEO_BANNER, it)
                        }
                    )

                    BetaDivider()

                    BetaFeatureItem(
                        icon = Icons.Default.Sell,
                        title = "Full profile tags",
                        subtitle = "Show all interests and community badges on your public profile",
                        checked = featureAllTags,
                        onCheckedChange = {
                            featureAllTags = it
                            sessionManager.setFeatureEnabled(SessionManager.FEATURE_ALL_TAGS, it)
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun BetaDivider() {
    val theme = Theme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(theme.colors.surfaceBorder)
    )
}

@Composable
private fun BetaFeatureItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val theme = Theme.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(theme.colors.primaryAccent.copy(alpha = 0.12f)),
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
                text = title,
                style = theme.typography.bodyMedium.copy(
                    color = theme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = theme.typography.caption.copy(
                    color = theme.colors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = theme.colors.primaryAccent,
                checkedTrackColor = theme.colors.primaryAccent.copy(alpha = 0.35f),
                uncheckedThumbColor = theme.colors.textTertiary,
                uncheckedTrackColor = theme.colors.surfaceElevated
            )
        )
    }
}
