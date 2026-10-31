package com.nebula.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.theme.Theme

/**
 * Public home of Nebula: marketing page, install guide and the APK download.
 * Change this one constant when the landing site moves to a custom domain.
 */
const val NEBULA_WEBSITE_URL = "https://nebula-73kb.onrender.com"

@Composable
fun NebulaAboutScreen(
    onBack: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    val context = LocalContext.current

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
                text = "About",
                style = theme.typography.titleMedium.copy(
                    color = theme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // MINIMAL PRODUCT HEADER (As specified in requirement 5)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(theme.colors.surface)
                    .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(16.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "NEBULA",
                        style = theme.typography.titleLarge.copy(
                            color = theme.colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            fontSize = 24.sp
                        )
                    )

                    Text(
                        text = "Find somewhere to be.",
                        style = theme.typography.bodyMedium.copy(
                            color = theme.colors.primaryAccent,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(theme.colors.surfaceElevated)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Version 1.1.0 • Build 2",
                            style = theme.typography.caption.copy(
                                color = theme.colors.textSecondary,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }
            }

            // OFFICIAL SUPPORT & INQUIRIES
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(theme.colors.surface)
                    .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.colors.surfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = theme.colors.primaryAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Official Support & Inquiries",
                            style = theme.typography.bodyMedium.copy(
                                color = theme.colors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Text(
                            text = "support@nebula.social",
                            style = theme.typography.caption.copy(
                                color = theme.colors.primaryAccent,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }

            // WEBSITE / DOWNLOAD ROW
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(theme.colors.surface)
                    .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
                    .clickable {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(NEBULA_WEBSITE_URL)
                                )
                            )
                        }
                    }
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.colors.primaryAccent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = theme.colors.primaryAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Visit our website",
                                style = theme.typography.bodyMedium.copy(
                                    color = theme.colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Text(
                                text = "Features, screenshots and the latest download",
                                style = theme.typography.caption.copy(
                                    color = theme.colors.textTertiary,
                                    fontSize = 11.5.sp
                                )
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = theme.colors.textTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // PRIVACY POLICY NAVIGATION ROW
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(theme.colors.surface)
                    .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
                    .clickable { onOpenPrivacyPolicy() }
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.colors.surfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = theme.colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Privacy Policy",
                                style = theme.typography.bodyMedium.copy(
                                    color = theme.colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Text(
                                text = "Data governance & retention terms",
                                style = theme.typography.caption.copy(
                                    color = theme.colors.textTertiary,
                                    fontSize = 11.5.sp
                                )
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = theme.colors.textTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
