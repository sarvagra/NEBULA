package com.nebula.app.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nebula.app.domain.model.UserProfile
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.PAAvatar
import com.nebula.app.ui.components.PAButton
import com.nebula.app.ui.components.PABottomSheet

/**
 * Banner preview aspect ratio. There is exactly ONE aspect source in this sheet (the two numbers
 * below): the preview shape and the aspect caption are BOTH derived from it, so no second
 * aspect-ratio control can disagree with - or silently reset - the selected banner.
 */
private const val BANNER_PREVIEW_ASPECT_WIDTH = 16f
private const val BANNER_PREVIEW_ASPECT_HEIGHT = 9f
private const val BANNER_PREVIEW_ASPECT = BANNER_PREVIEW_ASPECT_WIDTH / BANNER_PREVIEW_ASPECT_HEIGHT
private val BANNER_PREVIEW_ASPECT_LABEL =
    "${BANNER_PREVIEW_ASPECT_WIDTH.toInt()}:${BANNER_PREVIEW_ASPECT_HEIGHT.toInt()} preview"

/**
 * Banner artwork for the preview, in the exact same order as the persisted `bannerIndex` payload
 * field used by the profile screens. Deliberately hardcoded artwork rather than theme tokens:
 * the palette is saved profile content and must look identical in every theme.
 */
private val BANNER_PREVIEW_PALETTES = listOf(
    listOf(Color(0xFF1E1B4B), Color(0xFF312E81)), // 0 Deep Indigo
    listOf(Color(0xFF3B0764), Color(0xFF581C87)), // 1 Nebula Violet
    listOf(Color(0xFF042F2E), Color(0xFF115E59)), // 2 Midnight Teal
    listOf(Color(0xFF450A0A), Color(0xFF7F1D1D)), // 3 Cosmic Crimson
    listOf(Color(0xFF090B10), Color(0xFF1E222D))  // 4 Obsidian
)

/**
 * Raw status tokens persisted by the profile payload. These are backend contract values and are
 * never used as user-facing copy: the selector below labels them "Automatic"/"Offline"/"Custom".
 */
private const val PROFILE_STATUS_DYNAMIC = "dynamic"
private const val PROFILE_STATUS_OFFLINE = "offline"
private const val PROFILE_STATUS_CUSTOM = "custom"

/** Maps the persisted raw status token onto this sheet's editing mode. */
private fun profileStatusMode(profile: UserProfile): String =
    when (profile.status.trim().lowercase()) {
        PROFILE_STATUS_OFFLINE -> PROFILE_STATUS_OFFLINE
        PROFILE_STATUS_CUSTOM -> PROFILE_STATUS_CUSTOM
        PROFILE_STATUS_DYNAMIC, "online", "" -> PROFILE_STATUS_DYNAMIC
        // Legacy human-readable status text is preserved as custom status instead of being lost.
        else -> PROFILE_STATUS_CUSTOM
    }

/** Seeds the custom-status field from the persisted custom text, preserving legacy status text. */
private fun profileStatusCustomText(profile: UserProfile): String {
    if (profile.customStatus.isNotBlank()) return profile.customStatus
    val raw = profile.status.trim()
    val isRawToken = raw.lowercase() in setOf(
        PROFILE_STATUS_DYNAMIC,
        PROFILE_STATUS_OFFLINE,
        PROFILE_STATUS_CUSTOM,
        "online"
    )
    return if (isRawToken) "" else raw
}

/**
 * ProfileEditorSheet:
 * Management sheet for avatar, banner, display name, username, status and subscription tier.
 * Profile premium section directly opens the detailed plan comparison.
 *
 * State ownership:
 * - `selectedBannerIndex` is the single source of truth for the banner preview, the palette
 *   selector and the aspect caption. No other control in this sheet writes it.
 * - `statusMode` / `customStatusInput` are separate state and never touch the banner.
 * - Persistence happens through exactly one call to [onSaveDetails], so the caller keeps sole
 *   ownership of the stored value (this sheet never writes a second copy anywhere).
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
    onSaveDetails: (
        displayName: String,
        bio: String,
        status: String,
        customStatus: String,
        bannerIndex: Int
    ) -> Unit = { displayName, _, _, _, _ -> onUpdateName(displayName) },
    onDismiss: () -> Unit
) {
    val theme = Theme.current

    // Each draft field is remembered against its persisted source value, so a profile update can
    // never leave a stale duplicate behind and two screens cannot fight over the same value.
    var nameInput by remember(profile.displayName) { mutableStateOf(profile.displayName) }
    var bioInput by remember(profile.bio) { mutableStateOf(profile.bio) }

    // Status draft: ONE mode + ONE custom text. Nobody else in this sheet writes these.
    var statusMode by remember(profile.status) { mutableStateOf(profileStatusMode(profile)) }
    var customStatusInput by remember(profile.customStatus, profile.status) {
        mutableStateOf(profileStatusCustomText(profile))
    }

    // Banner draft: the ONE selected-banner state variable. The preview, the palette selector and
    // the aspect caption all read from it; changing name/bio/status can never reset it.
    var selectedBannerIndex by remember(profile.bannerIndex) {
        mutableIntStateOf(profile.bannerIndex.coerceIn(0, BANNER_PREVIEW_PALETTES.lastIndex))
    }
    val bannerPreviewBrush = Brush.horizontalGradient(BANNER_PREVIEW_PALETTES[selectedBannerIndex])

    var selectedInterests by remember(profile.interests) { mutableStateOf(profile.interests.toSet()) }

    // Keep this long sheet inside the viewport instead of letting controls (and the save button)
    // overflow off-screen.
    val maxContentHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.66f)

    PABottomSheet(
        isVisible = isVisible,
        onDismissRequest = onDismiss,
        title = "Edit Profile"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxContentHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = theme.spacing.screenHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---- Banner preview (single source of truth: selectedBannerIndex) ----------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(BANNER_PREVIEW_ASPECT)
                    .clip(RoundedCornerShape(theme.radii.lg))
                    .background(bannerPreviewBrush)
                    .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(theme.radii.lg))
            ) {
                if (!profile.bannerUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = profile.bannerUrl,
                        contentDescription = "Banner preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Text(
                    text = BANNER_PREVIEW_ASPECT_LABEL,
                    style = theme.typography.caption.copy(
                        color = theme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.5.sp
                    ),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(theme.radii.xs))
                        .background(theme.colors.surface.copy(alpha = 0.72f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Banner palette selector: writes ONLY selectedBannerIndex.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BANNER_PREVIEW_PALETTES.forEachIndexed { index, palette ->
                    val isSelected = index == selectedBannerIndex
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(palette))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) theme.colors.primaryAccent else theme.colors.surfaceBorder,
                                shape = CircleShape
                            )
                            .clickable { selectedBannerIndex = index }
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Banner ${selectedBannerIndex + 1} of ${BANNER_PREVIEW_PALETTES.size}",
                    style = theme.typography.caption.copy(
                        color = theme.colors.textTertiary,
                        fontSize = 10.5.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

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

            // Status mode selector: one owner for the status draft, independent of the banner.
            Text(
                text = "Status",
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
                StatusModeChip(
                    label = "Automatic",
                    isSelected = statusMode == PROFILE_STATUS_DYNAMIC,
                    onClick = { statusMode = PROFILE_STATUS_DYNAMIC },
                    modifier = Modifier.weight(1f)
                )
                StatusModeChip(
                    label = "Offline",
                    isSelected = statusMode == PROFILE_STATUS_OFFLINE,
                    onClick = { statusMode = PROFILE_STATUS_OFFLINE },
                    modifier = Modifier.weight(1f)
                )
                StatusModeChip(
                    label = "Custom",
                    isSelected = statusMode == PROFILE_STATUS_CUSTOM,
                    onClick = { statusMode = PROFILE_STATUS_CUSTOM },
                    modifier = Modifier.weight(1f)
                )
            }

            if (statusMode == PROFILE_STATUS_CUSTOM) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = customStatusInput,
                    onValueChange = { customStatusInput = it },
                    label = { Text("Custom status") },
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
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = when (statusMode) {
                    PROFILE_STATUS_OFFLINE -> "Your profile will always show Offline."
                    PROFILE_STATUS_CUSTOM -> "Your profile will show the custom text above."
                    else -> "Your profile will show your live presence automatically."
                },
                style = theme.typography.caption.copy(
                    color = theme.colors.textTertiary,
                    fontSize = 10.5.sp
                ),
                modifier = Modifier.align(Alignment.Start)
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

            // Save Action: exactly one write path, so the caller remains the sole owner of the
            // persisted profile values.
            PAButton(
                text = "Save Changes",
                onClick = {
                    onSaveDetails(
                        nameInput,
                        bioInput,
                        statusMode,
                        if (statusMode == PROFILE_STATUS_CUSTOM) customStatusInput.trim() else "",
                        selectedBannerIndex
                    )
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StatusModeChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(theme.radii.sm))
            .background(if (isSelected) theme.colors.primaryAccent.copy(alpha = 0.18f) else theme.colors.surface)
            .border(
                width = 1.dp,
                color = if (isSelected) theme.colors.primaryAccent else theme.colors.surfaceBorder,
                shape = RoundedCornerShape(theme.radii.sm)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = theme.typography.caption.copy(
                color = if (isSelected) theme.colors.primaryAccent else theme.colors.textSecondary,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 11.5.sp
            )
        )
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
