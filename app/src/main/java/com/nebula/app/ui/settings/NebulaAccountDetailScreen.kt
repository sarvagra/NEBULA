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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.nebula.app.domain.model.UserProfile
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.NebulaAvatarImage

private val BANNER_PALETTES = listOf(
    listOf(Color(0xFF1E1B4B), Color(0xFF312E81)),
    listOf(Color(0xFF3B0764), Color(0xFF581C87)),
    listOf(Color(0xFF042F2E), Color(0xFF115E59)),
    listOf(Color(0xFF450A0A), Color(0xFF7F1D1D)),
    listOf(Color(0xFF090B10), Color(0xFF1E222D))
)

/**
 * NebulaAccountDetailScreen:
 * Shows real authenticated account details, session status, and allows logout.
 */
@Composable
fun NebulaAccountDetailScreen(
    userProfile: UserProfile,
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current

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
                text = "Account",
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
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Identity Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(theme.colors.surface)
                        .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(16.dp))
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Profile Banner Preview in Account
                    val bannerColors = BANNER_PALETTES[userProfile.bannerIndex.coerceIn(0, BANNER_PALETTES.lastIndex)]
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (userProfile.bannerUrl.isNullOrBlank()) Modifier.background(Brush.horizontalGradient(bannerColors))
                                else Modifier.background(Color.Black)
                            )
                            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!userProfile.bannerUrl.isNullOrBlank()) {
                            val bannerUri = userProfile.bannerUrl!!
                            val base64Bitmap = remember(bannerUri) {
                                if (bannerUri.startsWith("data:image")) {
                                    try {
                                        val data = bannerUri.substringAfter("base64,")
                                        val decoded = Base64.decode(data, Base64.DEFAULT)
                                        BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
                                    } catch (e: Exception) {
                                        null
                                    }
                                } else null
                            }
                            if (base64Bitmap != null) {
                                Image(
                                    bitmap = base64Bitmap.asImageBitmap(),
                                    contentDescription = "Account Banner",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                AsyncImage(
                                    model = bannerUri,
                                    contentDescription = "Account Banner",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        } else {
                            Text(
                                text = "Profile Banner",
                                style = theme.typography.caption.copy(
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(theme.colors.surfaceElevated)
                            .border(2.dp, theme.colors.primaryAccent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        NebulaAvatarImage(
                            avatarUrl = userProfile.avatarUri,
                            fallbackLetter = userProfile.avatarFallbackLetter,
                            modifier = Modifier.fillMaxSize(),
                            fallbackTextSize = 28.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = userProfile.displayName,
                            style = theme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = theme.colors.textPrimary,
                                fontSize = 17.sp
                            )
                        )
                        Text(
                            text = userProfile.username ?: "@nebula_user",
                            style = theme.typography.caption.copy(
                                color = theme.colors.primaryAccent,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        )
                    }

                    Button(
                        onClick = onEditProfile,
                        modifier = Modifier.fillMaxWidth().height(40.dp),
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
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Edit Profile",
                                style = theme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }

            // Account Details
            item {
                val displayEmail = run {
                    val raw = userProfile.email
                    when {
                        raw.isNullOrBlank() -> "Connected via Google"
                        raw.contains("@nebula.local") -> "Connected via Google"
                        raw.contains("@") -> raw
                        else -> "Connected via Google"
                    }
                }
                val displayAccountId = userProfile.username?.takeIf { it.isNotBlank() }
                    ?: userProfile.id.takeIf { it.isNotBlank() && !it.startsWith("usr_dev") }
                    ?: "nebula_user"

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(theme.colors.surface)
                        .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(16.dp))
                ) {
                    AccountDetailRow("Email", displayEmail)
                    AccountDivider()
                    AccountDetailRow("Account", displayAccountId)
                    AccountDivider()
                    AccountDetailRow("Tier", userProfile.subscriptionTier.title)
                    AccountDivider()
                    AccountDetailRow("Banner", if (!userProfile.bannerUrl.isNullOrBlank()) "Custom Media" else "Palette ${userProfile.bannerIndex + 1}")
                    AccountDivider()
                    AccountDetailRow("Status", userProfile.status)
                }
            }

            // Sign Out
            item {
                Button(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.colors.errorColor.copy(alpha = 0.15f),
                        contentColor = theme.colors.errorColor
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Log Out",
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Log Out of Nebula",
                            style = theme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountDetailRow(label: String, value: String) {
    val theme = Theme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = theme.typography.bodyMedium.copy(
                color = theme.colors.textSecondary,
                fontSize = 14.sp
            )
        )
        Text(
            text = value,
            style = theme.typography.bodyMedium.copy(
                color = theme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        )
    }
}

@Composable
private fun AccountDivider() {
    val theme = Theme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(theme.colors.surfaceBorder)
    )
}
