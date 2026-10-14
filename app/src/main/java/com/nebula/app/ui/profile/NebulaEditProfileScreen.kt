package com.nebula.app.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
import com.nebula.app.domain.model.NebulaSubscriptionTier
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import android.media.MediaMetadataRetriever
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import com.nebula.app.data.backend.NebulaApiClient
import com.nebula.app.domain.model.UserProfile
import com.nebula.app.theme.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BANNER_PALETTES = listOf(
    "Deep Indigo" to listOf(Color(0xFF1E1B4B), Color(0xFF312E81)),
    "Nebula Violet" to listOf(Color(0xFF3B0764), Color(0xFF581C87)),
    "Midnight Teal" to listOf(Color(0xFF042F2E), Color(0xFF115E59)),
    "Cosmic Crimson" to listOf(Color(0xFF450A0A), Color(0xFF7F1D1D)),
    "Obsidian" to listOf(Color(0xFF090B10), Color(0xFF1E222D))
)

private val AVAILABLE_INTERESTS = listOf(
    "🎬 Sci-Fi", "🎧 Lo-Fi", "🍿 Watch Parties", "🌌 Ambient", "🎮 Gaming", "📚 Study", "🎧 Podcast", "🎨 Design"
)

data class ProfileAssetUiItem(
    val id: String,
    val assetType: String,
    val mediaUrl: String,
    val isCurrent: Boolean,
    val createdAt: String
) {
    val url: String get() = mediaUrl
}

sealed class UsernameValidationState {
    object Idle : UsernameValidationState()
    object Checking : UsernameValidationState()
    object Available : UsernameValidationState()
    data class Taken(val message: String) : UsernameValidationState()
    data class Invalid(val message: String) : UsernameValidationState()
}

@Composable
fun NebulaEditProfileScreen(
    userProfile: UserProfile,
    onSave: (displayName: String, username: String, bio: String, status: String, bannerIndex: Int, interests: List<String>, isFriendCountPublic: Boolean, avatarUrl: String?, bannerUrl: String?, customStatus: String) -> Unit,
    onBack: () -> Unit,
    onOpenMembership: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var displayName by remember { mutableStateOf(userProfile.displayName) }
    var username by remember { mutableStateOf(userProfile.username?.removePrefix("@") ?: "") }
    var bio by remember { mutableStateOf(userProfile.bio) }
    var statusText by remember { mutableStateOf(userProfile.status.lowercase().let { if (it == "online") "dynamic" else it }) }
    var customStatusText by remember { mutableStateOf(userProfile.customStatus) }
    var selectedBannerIndex by remember { mutableIntStateOf(userProfile.bannerIndex) }
    var customBannerUri by remember { mutableStateOf(userProfile.bannerUrl) }
    var selectedInterests by remember { mutableStateOf(userProfile.interests.toSet()) }

    var isFriendCountPublic by remember { mutableStateOf(userProfile.isFriendCountPublic) }
    var isOnlinePresencePublic by remember { mutableStateOf(true) }

    var currentAvatarUri by remember { mutableStateOf(userProfile.avatarUri) }
    var isUploadingAvatar by remember { mutableStateOf(false) }

    var pendingBannerCropBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var showBannerCropDialog by remember { mutableStateOf(false) }
    var isUploadingBanner by remember { mutableStateOf(false) }

    val bannerPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            scope.launch {
                val mimeType = context.contentResolver.getType(selectedUri) ?: ""
                val isVideo = mimeType.startsWith("video")

                if (isVideo) {
                    var durationMs = 0L
                    var frameBmp: android.graphics.Bitmap? = null
                    try {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(context, selectedUri)
                        val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        durationMs = durStr?.toLongOrNull() ?: 0L
                        frameBmp = retriever.getFrameAtTime(0)
                        retriever.release()
                    } catch (e: Exception) {
                        android.util.Log.e("NebulaBanner", "Error checking video duration", e)
                    }

                    if (durationMs > 5500L) {
                        Toast.makeText(context, "Video banner must be 5 seconds or shorter (${(durationMs + 999) / 1000}s selected)", Toast.LENGTH_LONG).show()
                        return@launch
                    }

                    val fileSize = withContext(Dispatchers.IO) {
                        try {
                            context.contentResolver.openFileDescriptor(selectedUri, "r")?.use { it.statSize } ?: 0L
                        } catch (e: Exception) { 0L }
                    }
                    if (fileSize > 30 * 1024 * 1024L) {
                        Toast.makeText(context, "Video file must be under 30MB", Toast.LENGTH_LONG).show()
                        return@launch
                    }

                    if (frameBmp != null) {
                        pendingBannerCropBitmap = frameBmp
                        showBannerCropDialog = true
                    } else {
                        customBannerUri = selectedUri.toString()
                    }
                } else {
                    val fileSize = withContext(Dispatchers.IO) {
                        try {
                            context.contentResolver.openFileDescriptor(selectedUri, "r")?.use { it.statSize } ?: 0L
                        } catch (e: Exception) { 0L }
                    }
                    if (fileSize > 5 * 1024 * 1024L) {
                        Toast.makeText(context, "Image file must be under 5MB", Toast.LENGTH_LONG).show()
                        return@launch
                    }

                    val loadedBmp = withContext(Dispatchers.IO) {
                        try {
                            context.contentResolver.openInputStream(selectedUri)?.use { stream ->
                                val bmp = BitmapFactory.decodeStream(stream)
                                if (bmp != null) {
                                    val maxDim = maxOf(bmp.width, bmp.height)
                                    if (maxDim > 1280) {
                                        val scale = 1280f / maxDim
                                        android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
                                    } else {
                                        bmp
                                    }
                                } else null
                            }
                        } catch (t: Throwable) {
                            android.util.Log.e("NebulaBanner", "Error loading banner", t)
                            null
                        }
                    }
                    if (loadedBmp != null) {
                        pendingBannerCropBitmap = loadedBmp
                        showBannerCropDialog = true
                    } else {
                        customBannerUri = selectedUri.toString()
                    }
                }
            }
        }
    }

    var usernameState by remember { mutableStateOf<UsernameValidationState>(UsernameValidationState.Idle) }

    LaunchedEffect(username) {
        val clean = username.trim().removePrefix("@").lowercase()
        val currentClean = userProfile.username?.trim()?.removePrefix("@")?.lowercase()
        if (clean.isEmpty()) {
            usernameState = UsernameValidationState.Idle
            return@LaunchedEffect
        }
        if (clean == currentClean) {
            usernameState = UsernameValidationState.Idle
            return@LaunchedEffect
        }
        if (clean.length < 3 || clean.length > 20) {
            usernameState = UsernameValidationState.Invalid("Must be 3-20 characters")
            return@LaunchedEffect
        }
        if (!clean.matches("^[a-z0-9_]{3,20}$".toRegex())) {
            usernameState = UsernameValidationState.Invalid("Letters, numbers, and underscores only")
            return@LaunchedEffect
        }

        usernameState = UsernameValidationState.Checking
        kotlinx.coroutines.delay(350)
        when (val res = NebulaApiClient.checkUsername(clean)) {
            is NebulaApiClient.ApiResponse.Success -> {
                val isAvail = res.data.optJSONObject("data")?.optBoolean("isAvailable", true) ?: true
                usernameState = if (isAvail) {
                    UsernameValidationState.Available
                } else {
                    UsernameValidationState.Taken("@$clean is already taken")
                }
            }
            is NebulaApiClient.ApiResponse.Error -> {
                usernameState = UsernameValidationState.Idle
            }
        }
    }

    // Profile Asset History state
    var assetHistory by remember { mutableStateOf<List<ProfileAssetUiItem>>(emptyList()) }
    var isAssetLoading by remember { mutableStateOf(false) }
    var assetToDelete by remember { mutableStateOf<ProfileAssetUiItem?>(null) }

    fun refreshAssets() {
        scope.launch {
            isAssetLoading = true
            when (val res = NebulaApiClient.getProfileAssets()) {
                is NebulaApiClient.ApiResponse.Success -> {
                    val rawList = res.data
                    val items = mutableListOf<ProfileAssetUiItem>()
                    for (i in 0 until rawList.length()) {
                        val obj = rawList.getJSONObject(i)
                        items.add(
                            ProfileAssetUiItem(
                                id = obj.optString("id", obj.optString("_id")),
                                assetType = obj.optString("assetType", "avatar"),
                                mediaUrl = obj.optString("url", obj.optString("mediaUrl", "")),
                                isCurrent = obj.optBoolean("isCurrent", false),
                                createdAt = obj.optString("createdAt", "").take(10)
                            )
                        )
                    }
                    assetHistory = items
                }
                else -> {}
            }
            isAssetLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshAssets()
    }

    var pendingCropBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var showCropDialog by remember { mutableStateOf(false) }

    // Modern Android Photo Picker (zero broad storage permission required)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val loadedBmp = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(it)?.use { stream ->
                            val bmp = android.graphics.BitmapFactory.decodeStream(stream)
                            if (bmp != null) {
                                val maxDim = maxOf(bmp.width, bmp.height)
                                if (maxDim > 1080) {
                                    val scale = 1080f / maxDim
                                    android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
                                } else {
                                    bmp
                                }
                            } else null
                        }
                    } catch (t: Throwable) {
                        android.util.Log.e("NebulaCrop", "Error loading photo picker uri", t)
                        null
                    }
                }
                if (loadedBmp != null) {
                    pendingCropBitmap = loadedBmp
                    showCropDialog = true
                } else {
                    currentAvatarUri = it.toString()
                }
            }
        }
    }

    if (showCropDialog && pendingCropBitmap != null) {
        Custom1to1CropDialog(
            bitmap = pendingCropBitmap!!,
            onConfirmCrop = { cropped ->
                showCropDialog = false
                scope.launch {
                    try {
                        isUploadingAvatar = true
                        val outputStream = java.io.ByteArrayOutputStream()
                        cropped.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outputStream)
                        val compressedBytes = outputStream.toByteArray()
                        val base64 = android.util.Base64.encodeToString(compressedBytes, android.util.Base64.NO_WRAP)
                        val dataUrl = "data:image/jpeg;base64,$base64"

                        currentAvatarUri = dataUrl

                        NebulaApiClient.uploadProfileAsset(
                            assetType = "avatar",
                            url = dataUrl,
                            name = "avatar_${System.currentTimeMillis()}.jpg"
                        )
                        refreshAssets()
                    } catch (e: Exception) {
                        // fallback
                    } finally {
                        isUploadingAvatar = false
                        pendingCropBitmap = null
                    }
                }
            },
            onDismiss = {
                showCropDialog = false
                pendingCropBitmap = null
            }
        )
    }

    if (showBannerCropDialog && pendingBannerCropBitmap != null) {
        Custom16to9CropDialog(
            bitmap = pendingBannerCropBitmap!!,
            onConfirmCrop = { cropped ->
                showBannerCropDialog = false
                scope.launch {
                    try {
                        isUploadingBanner = true
                        val outputStream = java.io.ByteArrayOutputStream()
                        cropped.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outputStream)
                        val compressedBytes = outputStream.toByteArray()
                        val base64 = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
                        val dataUrl = "data:image/jpeg;base64,$base64"

                        customBannerUri = dataUrl

                        NebulaApiClient.uploadProfileAsset(
                            assetType = "banner",
                            url = dataUrl,
                            name = "banner_${System.currentTimeMillis()}.jpg"
                        )
                        refreshAssets()
                    } catch (e: Exception) {
                        android.util.Log.e("NebulaBanner", "Error uploading banner", e)
                    } finally {
                        isUploadingBanner = false
                        pendingBannerCropBitmap = null
                    }
                }
            },
            onDismiss = {
                showBannerCropDialog = false
                pendingBannerCropBitmap = null
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.colors.background)
            .statusBarsPadding()
            .imePadding()
    ) {
        // TOP APP BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Cancel",
                    tint = theme.colors.textPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Text(
                text = "Edit Profile",
                style = theme.typography.titleMedium.copy(
                    color = theme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            )

            // Save Action Button
            val canSave = usernameState !is UsernameValidationState.Taken && usernameState !is UsernameValidationState.Invalid
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (canSave) theme.colors.primaryAccent else theme.colors.primaryAccent.copy(alpha = 0.4f))
                    .clickable(enabled = canSave) {
                        onSave(
                            displayName,
                            username,
                            bio,
                            statusText,
                            selectedBannerIndex,
                            selectedInterests.toList(),
                            isFriendCountPublic,
                            currentAvatarUri,
                            customBannerUri,
                            customStatusText
                        )
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Save",
                    style = theme.typography.bodyMedium.copy(
                        color = if (canSave) Color.White else Color.White.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                )
            }
        }

        // FORM FIELDS
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. BANNER & AVATAR PICKER (Fixed 16:9 Display Ratio documented)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(theme.colors.surface)
                        .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PROFILE BANNER",
                            style = theme.typography.caption.copy(
                                color = theme.colors.textSecondary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                fontSize = 11.sp
                            )
                        )
                        Text(
                            text = "16:9 Aspect Ratio",
                            style = theme.typography.caption.copy(
                                color = theme.colors.primaryAccent,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.5.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Banner Preview with fixed 16:9 ratio
                    val currentColors = BANNER_PALETTES[selectedBannerIndex.coerceIn(0, BANNER_PALETTES.lastIndex)].second
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.horizontalGradient(currentColors))
                            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(12.dp))
                            .clickable {
                                bannerPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (!customBannerUri.isNullOrBlank()) {
                            val bUri = customBannerUri!!
                            val base64Bitmap = remember(bUri) {
                                if (bUri.startsWith("data:image")) {
                                    try {
                                        val data = bUri.substringAfter("base64,")
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
                                    contentDescription = "Custom Media Banner",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                coil.compose.AsyncImage(
                                    model = bUri,
                                    contentDescription = "Custom Media Banner",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        } else {
                            Text(
                                text = BANNER_PALETTES[selectedBannerIndex.coerceIn(0, BANNER_PALETTES.lastIndex)].first,
                                style = theme.typography.bodyMedium.copy(
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Banner Palette Selector Chips + Custom Media Banner
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Custom Media Banner Chip
                        item {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (customBannerUri != null) theme.colors.primaryAccent.copy(alpha = 0.25f)
                                        else theme.colors.surfaceElevated
                                    )
                                    .border(
                                        width = if (customBannerUri != null) 2.dp else 1.dp,
                                        color = if (customBannerUri != null) theme.colors.primaryAccent else theme.colors.surfaceBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        bannerPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                        )
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = theme.colors.primaryAccent,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "Custom Media Banner",
                                        style = theme.typography.caption.copy(
                                            color = theme.colors.textPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }

                        if (customBannerUri != null) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(theme.colors.surfaceElevated)
                                        .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(8.dp))
                                        .clickable { customBannerUri = null }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Reset to Palette",
                                        style = theme.typography.caption.copy(
                                            color = theme.colors.textSecondary,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }

                        itemsIndexed(BANNER_PALETTES) { index, (name, colors) ->
                            val isSelected = selectedBannerIndex == index && customBannerUri == null
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Brush.horizontalGradient(colors))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) theme.colors.primaryAccent else theme.colors.surfaceBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        selectedBannerIndex = index
                                        customBannerUri = null
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = name,
                                    style = theme.typography.caption.copy(
                                        color = Color.White,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Avatar photo picker row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                 .size(64.dp)
                                 .clip(CircleShape)
                                 .background(theme.colors.surfaceElevated)
                                 .border(2.dp, theme.colors.surfaceBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            com.nebula.app.ui.components.NebulaAvatarImage(
                                avatarUrl = currentAvatarUri,
                                fallbackLetter = displayName.firstOrNull()?.uppercase() ?: userProfile.avatarFallbackLetter,
                                contentDescription = displayName,
                                modifier = Modifier.size(64.dp),
                                fallbackTextSize = 24.sp
                            )
                            if (isUploadingAvatar) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = theme.colors.primaryAccent,
                                    strokeWidth = 2.dp
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(theme.colors.surfaceElevated)
                                    .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(10.dp))
                                    .clickable {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoLibrary,
                                        contentDescription = null,
                                        tint = theme.colors.textPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Upload Image",
                                        style = theme.typography.caption.copy(
                                            color = theme.colors.textPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. DISPLAY NAME
            item {
                EditField(
                    label = "Display Name",
                    value = displayName,
                    onValueChange = { displayName = it },
                    singleLine = true
                )
            }

            // 3. USERNAME / HANDLE
            item {
                EditField(
                    label = "Username",
                    value = username,
                    onValueChange = { input ->
                        val clean = input.removePrefix("@").lowercase().filter { it.isLetterOrDigit() || it == '_' }.take(20)
                        username = clean
                    },
                    singleLine = true,
                    statusContent = {
                        when (val state = usernameState) {
                            UsernameValidationState.Checking -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.5.dp,
                                        color = theme.colors.primaryAccent
                                    )
                                    Text(
                                        text = "Checking availability...",
                                        style = theme.typography.caption.copy(
                                            color = theme.colors.textTertiary,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                            UsernameValidationState.Available -> {
                                Text(
                                    text = "✓ Username is available",
                                    style = theme.typography.caption.copy(
                                        color = Color(0xFF10B981),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                            is UsernameValidationState.Taken -> {
                                Text(
                                    text = "✕ ${state.message}",
                                    style = theme.typography.caption.copy(
                                        color = theme.colors.errorColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                            is UsernameValidationState.Invalid -> {
                                Text(
                                    text = "✕ ${state.message}",
                                    style = theme.typography.caption.copy(
                                        color = theme.colors.errorColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                            UsernameValidationState.Idle -> {}
                        }
                    }
                )
            }

            // 4. BIOGRAPHY (500-char limit with counter)
            item {
                EditField(
                    label = "Biography",
                    value = bio,
                    onValueChange = { bio = it },
                    singleLine = false,
                    minLines = 3,
                    maxLength = 500
                )
            }

            // 5. STATUS / ROOM PRESENCE (Online/Offline defaults, custom up to 8 chars for premium)
            item {
                val isPremiumUser = userProfile.subscriptionTier != NebulaSubscriptionTier.FREE
                val isPremiumPlus = userProfile.subscriptionTier == NebulaSubscriptionTier.PREMIUM_PLUS
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(theme.colors.surface)
                        .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "STATUS / ROOM PRESENCE",
                            style = theme.typography.caption.copy(
                                color = theme.colors.textSecondary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.1.sp,
                                fontSize = 10.5.sp
                            )
                        )
                        if (isPremiumPlus) Text("${customStatusText.length}/80", style = theme.typography.caption.copy(color = theme.colors.textTertiary, fontSize = 10.5.sp))
                    }

                    // Default presets (Online, Offline)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (if (isPremiumPlus) listOf("dynamic", "offline", "custom") else listOf("dynamic", "offline")).forEach { preset ->
                            val isSelected = statusText.equals(preset, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) theme.colors.primaryAccent.copy(alpha = 0.2f) else theme.colors.surfaceElevated)
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) theme.colors.primaryAccent else theme.colors.surfaceBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        if (preset == "offline" && !isPremiumUser || preset == "custom" && !isPremiumPlus) onOpenMembership()
                                        else statusText = preset
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (preset == "dynamic") theme.colors.successColor
                                                else theme.colors.textTertiary
                                            )
                                    )
                                    Text(
                                        text = when (preset) { "dynamic" -> "Dynamic"; "offline" -> "Offline"; else -> "Custom" },
                                        style = theme.typography.caption.copy(
                                            color = if (isSelected) theme.colors.primaryAccent else theme.colors.textPrimary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Custom status input (up to 8 chars)
                    if (isPremiumPlus && statusText == "custom") {
                        BasicTextField(
                            value = customStatusText,
                            onValueChange = {
                                if (it.length <= 80) customStatusText = it
                            },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = theme.typography.bodyMedium.copy(
                                color = theme.colors.textPrimary,
                                fontSize = 14.5.sp
                            ),
                            cursorBrush = SolidColor(theme.colors.primaryAccent),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                Box {
                                    if (statusText.isEmpty()) {
                                        Text(
                                            text = "Custom status (max 80 characters)",
                                            style = theme.typography.bodyMedium.copy(
                                                color = theme.colors.textTertiary,
                                                fontSize = 14.sp
                                            )
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    } else if (!isPremiumPlus) {
                        Text(
                            text = "Offline status is Premium. Custom status is Premium Plus.",
                            style = theme.typography.caption.copy(
                                color = theme.colors.textTertiary,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            // 6. INTERESTS & ROOM BADGES
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(theme.colors.surface)
                        .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = "INTERESTS & ROOM BADGES",
                        style = theme.typography.caption.copy(
                            color = theme.colors.textSecondary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            fontSize = 11.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AVAILABLE_INTERESTS.take(4).forEach { interest ->
                            val isSelected = selectedInterests.contains(interest)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) theme.colors.primaryAccent.copy(alpha = 0.2f) else theme.colors.surfaceElevated)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) theme.colors.primaryAccent else theme.colors.surfaceBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        selectedInterests = if (isSelected) {
                                            selectedInterests - interest
                                        } else if (selectedInterests.size < 5) {
                                            selectedInterests + interest
                                        } else {
                                            selectedInterests
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = interest,
                                    style = theme.typography.caption.copy(
                                        color = if (isSelected) theme.colors.primaryAccent else theme.colors.textPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.5.sp
                                    )
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AVAILABLE_INTERESTS.drop(4).forEach { interest ->
                            val isSelected = selectedInterests.contains(interest)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) theme.colors.primaryAccent.copy(alpha = 0.2f) else theme.colors.surfaceElevated)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) theme.colors.primaryAccent else theme.colors.surfaceBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        selectedInterests = if (isSelected) {
                                            selectedInterests - interest
                                        } else if (selectedInterests.size < 5) {
                                            selectedInterests + interest
                                        } else {
                                            selectedInterests
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = interest,
                                    style = theme.typography.caption.copy(
                                        color = if (isSelected) theme.colors.primaryAccent else theme.colors.textPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.5.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 7. PRIVACY CONTROLS (Only in Edit Profile, correctly persisting)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(theme.colors.surface)
                        .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = "PRIVACY CONTROLS",
                        style = theme.typography.caption.copy(
                            color = theme.colors.textSecondary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            fontSize = 11.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Show Friend Count on Profile",
                                style = theme.typography.bodyMedium.copy(
                                    color = theme.colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Text(
                                text = "Display your mutual friends publicly",
                                style = theme.typography.caption.copy(
                                    color = theme.colors.textTertiary,
                                    fontSize = 11.5.sp
                                )
                            )
                        }
                        Switch(
                            checked = isFriendCountPublic,
                            onCheckedChange = { isFriendCountPublic = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = theme.colors.primaryAccent
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Online Presence in Rooms",
                                style = theme.typography.bodyMedium.copy(
                                    color = theme.colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Text(
                                text = "Let friends see which room you are in",
                                style = theme.typography.caption.copy(
                                    color = theme.colors.textTertiary,
                                    fontSize = 11.5.sp
                                )
                            )
                        }
                        Switch(
                            checked = isOnlinePresencePublic,
                            onCheckedChange = { isOnlinePresencePublic = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = theme.colors.primaryAccent
                            )
                        )
                    }
                }
            }

            // 8. PROFILE ASSET HISTORY / PROFILE MEDIA LIBRARY
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(theme.colors.surface)
                        .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PROFILE ASSET HISTORY",
                            style = theme.typography.caption.copy(
                                color = theme.colors.textSecondary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                fontSize = 11.sp
                            )
                        )
                        Text(
                            text = "${assetHistory.size} assets",
                            style = theme.typography.caption.copy(
                                color = theme.colors.primaryAccent,
                                fontSize = 11.sp
                            )
                        )
                    }

                    if (isAssetLoading) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = theme.colors.primaryAccent
                            )
                        }
                    } else if (assetHistory.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.colors.surfaceElevated)
                                .padding(14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No past profile assets found. Upload images to maintain history.",
                                style = theme.typography.caption.copy(
                                    color = theme.colors.textSecondary,
                                    fontSize = 11.5.sp
                                )
                            )
                        }
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(assetHistory) { asset ->
                                val isSelectedDraft = (asset.assetType == "avatar" && currentAvatarUri == asset.url) ||
                                        (asset.assetType == "banner" && customBannerUri == asset.url)
                                Box(
                                    modifier = Modifier
                                        .width(110.dp)
                                        .height(76.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(theme.colors.surfaceElevated)
                                        .border(
                                            width = if (isSelectedDraft) 2.dp else 1.dp,
                                            color = if (isSelectedDraft) theme.colors.primaryAccent else theme.colors.surfaceBorder,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            // DRAFT SELECTION: Updates preview in editor, does NOT commit to server immediately
                                            if (asset.assetType == "avatar") {
                                                currentAvatarUri = asset.url
                                            } else if (asset.assetType == "banner") {
                                                customBannerUri = asset.url
                                            }
                                        }
                                ) {
                                    val assetBitmap = remember(asset.url) {
                                        if (asset.url.startsWith("data:image")) {
                                            try {
                                                val data = asset.url.substringAfter("base64,")
                                                val decoded = Base64.decode(data, Base64.DEFAULT)
                                                BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
                                            } catch (e: Exception) {
                                                null
                                            }
                                        } else null
                                    }

                                    if (assetBitmap != null) {
                                        Image(
                                            bitmap = assetBitmap.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        coil.compose.AsyncImage(
                                            model = asset.url,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }

                                    // Delete icon overlay in top right
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.65f))
                                            .clickable { assetToDelete = asset },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Delete asset",
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }

                                    // Draft Selected indicator tag
                                    if (isSelectedDraft) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(4.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(theme.colors.primaryAccent)
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "Selected",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
            }
        }
        }

        if (assetToDelete != null) {
            val asset = assetToDelete!!
            AlertDialog(
                onDismissRequest = { assetToDelete = null },
                title = {
                    Text(
                        text = "Delete Asset?",
                        style = theme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = theme.colors.textPrimary
                        )
                    )
                },
                text = {
                    Text(
                        text = "This will permanently remove this ${asset.assetType} asset from server storage.",
                        style = theme.typography.bodyMedium.copy(color = theme.colors.textSecondary)
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val toDeleteId = asset.id
                            assetToDelete = null
                            scope.launch {
                                NebulaApiClient.deleteProfileAsset(toDeleteId)
                                refreshAssets()
                            }
                        }
                    ) {
                        Text("Delete", color = theme.colors.errorColor, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { assetToDelete = null }) {
                        Text("Cancel", color = theme.colors.textSecondary)
                    }
                },
                containerColor = theme.colors.surfaceElevated,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
private fun EditField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLength: Int? = null,
    statusContent: (@Composable () -> Unit)? = null
) {
    val theme = Theme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(theme.colors.surface)
            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label.uppercase(),
                style = theme.typography.caption.copy(
                    color = theme.colors.textSecondary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                    fontSize = 10.5.sp
                )
            )
            if (maxLength != null) {
                Text(
                    text = "${value.length}/$maxLength",
                    style = theme.typography.caption.copy(
                        color = if (value.length >= maxLength) theme.colors.errorColor else theme.colors.textTertiary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        BasicTextField(
            value = value,
            onValueChange = {
                if (maxLength == null || it.length <= maxLength) {
                    onValueChange(it)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            textStyle = theme.typography.bodyMedium.copy(
                color = theme.colors.textPrimary,
                fontSize = 14.5.sp
            ),
            cursorBrush = SolidColor(theme.colors.primaryAccent),
            singleLine = singleLine,
            minLines = minLines
        )
        if (statusContent != null) {
            Spacer(modifier = Modifier.height(4.dp))
            statusContent()
        }
    }
}

@Composable
fun Custom1to1CropDialog(
    bitmap: android.graphics.Bitmap,
    onConfirmCrop: (croppedBitmap: android.graphics.Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = Theme.current
    var zoom by remember { mutableFloatStateOf(1.0f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }

    val minDim = minOf(bitmap.width, bitmap.height)
    val cropSize = (minDim / zoom).toInt().coerceIn(20, minDim)
    val maxOffsetX = (bitmap.width - cropSize) / 2
    val maxOffsetY = (bitmap.height - cropSize) / 2
    val startX = (maxOffsetX + (panX * maxOffsetX)).toInt().coerceIn(0, maxOf(0, bitmap.width - cropSize))
    val startY = (maxOffsetY + (panY * maxOffsetY)).toInt().coerceIn(0, maxOf(0, bitmap.height - cropSize))

    val previewBitmap = remember(bitmap, zoom, panX, panY) {
        try {
            val cropped = android.graphics.Bitmap.createBitmap(bitmap, startX, startY, cropSize, cropSize)
            android.graphics.Bitmap.createScaledBitmap(cropped, 320, 320, true)
        } catch (e: Exception) {
            bitmap
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.colors.surfaceElevated,
        titleContentColor = theme.colors.textPrimary,
        textContentColor = theme.colors.textSecondary,
        title = {
            Text(
                text = "1:1 Square Crop",
                style = theme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Adjust zoom and pan to align your square 1:1 avatar profile picture.",
                    style = theme.typography.caption.copy(color = theme.colors.textSecondary)
                )

                // 1:1 Square Viewport
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black)
                        .border(2.dp, theme.colors.primaryAccent, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = "Crop Preview",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Zoom Slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Zoom", style = theme.typography.caption.copy(fontWeight = FontWeight.SemiBold))
                        Text(String.format("%.1fx", zoom), style = theme.typography.caption.copy(color = theme.colors.primaryAccent))
                    }
                    Slider(
                        value = zoom,
                        onValueChange = { zoom = it },
                        valueRange = 1.0f..3.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = theme.colors.primaryAccent,
                            activeTrackColor = theme.colors.primaryAccent
                        )
                    )
                }

                // Horizontal Pan Slider
                if (bitmap.width > cropSize) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Horizontal Position", style = theme.typography.caption.copy(fontWeight = FontWeight.SemiBold))
                        }
                        Slider(
                            value = panX,
                            onValueChange = { panX = it },
                            valueRange = -1.0f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = theme.colors.primaryAccent,
                                activeTrackColor = theme.colors.primaryAccent
                            )
                        )
                    }
                }

                // Vertical Pan Slider
                if (bitmap.height > cropSize) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Vertical Position", style = theme.typography.caption.copy(fontWeight = FontWeight.SemiBold))
                        }
                        Slider(
                            value = panY,
                            onValueChange = { panY = it },
                            valueRange = -1.0f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = theme.colors.primaryAccent,
                                activeTrackColor = theme.colors.primaryAccent
                            )
                        )
                    }
                }

                // Reset button
                TextButton(
                    onClick = {
                        zoom = 1.0f
                        panX = 0f
                        panY = 0f
                    }
                ) {
                    Text("Reset to Center", color = theme.colors.primaryAccent, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val cropped = android.graphics.Bitmap.createBitmap(bitmap, startX, startY, cropSize, cropSize)
                        val scaled = android.graphics.Bitmap.createScaledBitmap(cropped, 512, 512, true)
                        onConfirmCrop(scaled)
                    } catch (e: Exception) {
                        onConfirmCrop(bitmap)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.colors.primaryAccent),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Confirm 1:1 Crop", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = theme.colors.textSecondary)
            }
        }
    )
}

@Composable
fun Custom16to9CropDialog(
    bitmap: android.graphics.Bitmap,
    onConfirmCrop: (croppedBitmap: android.graphics.Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = Theme.current
    var zoom by remember { mutableFloatStateOf(1.0f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }

    val targetAspect = 16f / 9f
    val imgAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
    val baseWidth: Int
    val baseHeight: Int
    if (imgAspect > targetAspect) {
        baseHeight = bitmap.height
        baseWidth = (bitmap.height * targetAspect).toInt().coerceIn(1, bitmap.width)
    } else {
        baseWidth = bitmap.width
        baseHeight = (bitmap.width / targetAspect).toInt().coerceIn(1, bitmap.height)
    }

    val cropWidth = (baseWidth / zoom).toInt().coerceIn(20, baseWidth)
    val cropHeight = (baseHeight / zoom).toInt().coerceIn(20, baseHeight)

    val maxOffsetX = (bitmap.width - cropWidth) / 2
    val maxOffsetY = (bitmap.height - cropHeight) / 2
    val startX = (maxOffsetX + (panX * maxOffsetX)).toInt().coerceIn(0, maxOf(0, bitmap.width - cropWidth))
    val startY = (maxOffsetY + (panY * maxOffsetY)).toInt().coerceIn(0, maxOf(0, bitmap.height - cropHeight))

    val previewBitmap = remember(bitmap, zoom, panX, panY) {
        try {
            val cropped = android.graphics.Bitmap.createBitmap(bitmap, startX, startY, cropWidth, cropHeight)
            android.graphics.Bitmap.createScaledBitmap(cropped, 480, 270, true)
        } catch (e: Exception) {
            bitmap
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.colors.surfaceElevated,
        titleContentColor = theme.colors.textPrimary,
        textContentColor = theme.colors.textSecondary,
        title = {
            Text(
                text = "16:9 Banner Crop",
                style = theme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Adjust zoom and pan to align your 16:9 widescreen profile banner.",
                    style = theme.typography.caption.copy(color = theme.colors.textSecondary)
                )

                // 16:9 Viewport
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                        .border(2.dp, theme.colors.primaryAccent, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = "Banner Crop Preview",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Zoom Slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Zoom", style = theme.typography.caption.copy(fontWeight = FontWeight.SemiBold))
                        Text(String.format("%.1fx", zoom), style = theme.typography.caption.copy(color = theme.colors.primaryAccent))
                    }
                    Slider(
                        value = zoom,
                        onValueChange = { zoom = it },
                        valueRange = 1.0f..3.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = theme.colors.primaryAccent,
                            activeTrackColor = theme.colors.primaryAccent
                        )
                    )
                }

                // Horizontal Pan Slider
                if (bitmap.width > cropWidth) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Horizontal Position", style = theme.typography.caption.copy(fontWeight = FontWeight.SemiBold))
                        }
                        Slider(
                            value = panX,
                            onValueChange = { panX = it },
                            valueRange = -1.0f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = theme.colors.primaryAccent,
                                activeTrackColor = theme.colors.primaryAccent
                            )
                        )
                    }
                }

                // Vertical Pan Slider
                if (bitmap.height > cropHeight) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Vertical Position", style = theme.typography.caption.copy(fontWeight = FontWeight.SemiBold))
                        }
                        Slider(
                            value = panY,
                            onValueChange = { panY = it },
                            valueRange = -1.0f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = theme.colors.primaryAccent,
                                activeTrackColor = theme.colors.primaryAccent
                            )
                        )
                    }
                }

                // Reset button
                TextButton(
                    onClick = {
                        zoom = 1.0f
                        panX = 0f
                        panY = 0f
                    }
                ) {
                    Text("Reset to Center", color = theme.colors.primaryAccent, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val cropped = android.graphics.Bitmap.createBitmap(bitmap, startX, startY, cropWidth, cropHeight)
                        val scaled = android.graphics.Bitmap.createScaledBitmap(cropped, 640, 360, true)
                        onConfirmCrop(scaled)
                    } catch (e: Exception) {
                        onConfirmCrop(bitmap)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.colors.primaryAccent),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Confirm 16:9 Banner", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = theme.colors.textSecondary)
            }
        }
    )
}
