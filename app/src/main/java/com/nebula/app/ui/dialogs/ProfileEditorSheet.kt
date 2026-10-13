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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.domain.model.UserProfile
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.PAAvatar
import com.nebula.app.ui.components.PAButton
import com.nebula.app.ui.components.PABottomSheet

/**
 * ProfileEditorSheet:
 * Management sheet for avatar, display name, username, and subscription status.
 * Profile premium section directly opens the detailed plan comparison.
 */
@Composable
fun ProfileEditorSheet(
    isVisible: Boolean,
    profile: UserProfile,
    onUpdateName: (String) -> Unit,
    onTogglePremium: (Boolean) -> Unit,
    onOpenPremiumPlans: () -> Unit = {},
    onPickGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = Theme.current
    var nameInput by remember(profile.displayName) { mutableStateOf(profile.displayName) }
    var bioInput by remember { mutableStateOf("Building ambient systems & shared spaces on Android.") }
    var statusInput by remember { mutableStateOf("Building ambient systems") }
    var selectedInterests by remember { mutableStateOf(setOf("🎬 Sci-Fi", "🎧 Lo-Fi", "🍿 Watch Parties", "🌌 Ambient")) }

    PABottomSheet(
        isVisible = isVisible,
        onDismissRequest = onDismiss,
        title = "Edit Profile"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = theme.spacing.screenHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Large Avatar Preview
            PAAvatar(profile = profile, size = 72.dp)

            Spacer(modifier = Modifier.height(16.dp))

            // Photo Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PhotoActionButton(
                    icon = Icons.Default.CameraAlt,
                    label = "Camera",
                    onClick = onTakePhoto,
                    modifier = Modifier.weight(1f)
                )
                PhotoActionButton(
                    icon = Icons.Default.PhotoLibrary,
                    label = "Gallery",
                    onClick = onPickGallery,
                    modifier = Modifier.weight(1f)
                )
                if (profile.avatarUri != null) {
                    PhotoActionButton(
                        icon = Icons.Default.Delete,
                        label = "Remove",
                        onClick = onRemovePhoto,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Display Name Edit Field
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text("Display Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(theme.radii.md),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = theme.colors.primaryAccent,
                    unfocusedBorderColor = theme.colors.surfaceBorder,
                    focusedTextColor = theme.colors.textPrimary,
                    unfocusedTextColor = theme.colors.textPrimary,
                    focusedLabelColor = theme.colors.primaryAccent,
                    unfocusedLabelColor = theme.colors.textSecondary
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Bio Edit Field
            OutlinedTextField(
                value = bioInput,
                onValueChange = { bioInput = it },
                label = { Text("Biography") },
                maxLines = 3,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(theme.radii.md),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = theme.colors.primaryAccent,
                    unfocusedBorderColor = theme.colors.surfaceBorder,
                    focusedTextColor = theme.colors.textPrimary,
                    unfocusedTextColor = theme.colors.textPrimary,
                    focusedLabelColor = theme.colors.primaryAccent,
                    unfocusedLabelColor = theme.colors.textSecondary
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Status Edit Field
            OutlinedTextField(
                value = statusInput,
                onValueChange = { statusInput = it },
                label = { Text("Status / Room Presence") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(theme.radii.md),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = theme.colors.primaryAccent,
                    unfocusedBorderColor = theme.colors.surfaceBorder,
                    focusedTextColor = theme.colors.textPrimary,
                    unfocusedTextColor = theme.colors.textPrimary,
                    focusedLabelColor = theme.colors.primaryAccent,
                    unfocusedLabelColor = theme.colors.textSecondary
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Interest category chips
            Text(
                text = "Interests & Room Badges",
                style = theme.typography.caption.copy(
                    color = theme.colors.textSecondary,
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("🎬 Sci-Fi", "🎧 Lo-Fi", "🍿 Watch Parties", "🌌 Ambient").forEach { tag ->
                    val isSelected = selectedInterests.contains(tag)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) theme.colors.primaryAccent.copy(alpha = 0.2f) else theme.colors.surface)
                            .border(1.dp, if (isSelected) theme.colors.primaryAccent else theme.colors.surfaceBorder, RoundedCornerShape(6.dp))
                            .clickable {
                                selectedInterests = if (isSelected) selectedInterests - tag else selectedInterests + tag
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = tag,
                            style = theme.typography.caption.copy(
                                fontSize = 11.sp,
                                color = if (isSelected) theme.colors.primaryAccent else theme.colors.textSecondary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Subscription Plan Row (Clicking directly opens Premium Comparison!)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(theme.radii.lg))
                    .background(theme.colors.surface)
                    .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(theme.radii.lg))
                    .clickable {
                        onDismiss()
                        onOpenPremiumPlans()
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(theme.colors.primaryAccent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = theme.colors.primaryAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Membership: ${profile.subscriptionTier.title}",
                            style = theme.typography.titleMedium.copy(
                                color = theme.colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.5.sp
                            )
                        )
                        Text(
                            text = "Tap to view Basic, Lite & Pro comparisons",
                            style = theme.typography.caption.copy(color = theme.colors.primaryAccent)
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "View plans",
                    tint = theme.colors.textTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Save Action
            PAButton(
                text = "Save Changes",
                onClick = {
                    onUpdateName(nameInput)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PhotoActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(theme.radii.md))
            .background(theme.colors.surface)
            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(theme.radii.md))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = theme.colors.primaryAccent,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = theme.typography.caption.copy(
                color = theme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}
