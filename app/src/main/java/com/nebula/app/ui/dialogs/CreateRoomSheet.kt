package com.nebula.app.ui.dialogs

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nebula.app.domain.model.RoomActivityType
import com.nebula.app.domain.model.RoomPrivacy
import com.nebula.app.domain.model.SharedMediaItem
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.PABottomSheet

/**
 * Extracts YouTube video ID from URL if valid, otherwise null.
 */
fun extractYouTubeId(url: String): String? {
    val pattern = "(?:youtu\\.be/|youtube\\.com/(?:embed/|v/|shorts/|watch\\?v=|watch\\?.+&v=))([\\w-]{11})".toRegex()
    val match = pattern.find(url.trim())
    return match?.groupValues?.get(1)
}

/**
 * Checks if a direct link is a supported media stream (.mp4, .webm, .m3u8)
 */
fun isSupportedDirectStream(url: String): Boolean {
    val clean = url.trim().lowercase()
    return clean.endsWith(".mp4") || clean.endsWith(".webm") || clean.endsWith(".m3u8") || clean.contains(".mp4?")
}

@Composable
fun CreateRoomSheet(
    isVisible: Boolean,
    onCreateRoom: (name: String, privacy: RoomPrivacy, tags: List<String>, maxParticipants: Int, media: SharedMediaItem?) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = Theme.current

    var roomName by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var selectedPrivacy by remember { mutableStateOf(RoomPrivacy.FRIENDS) }
    var selectedMedia by remember { mutableStateOf<SharedMediaItem?>(null) }
    var selectedMaxParticipants by remember { mutableIntStateOf(20) }
    var selectedSource by remember { mutableStateOf("YouTube") }
    var showSourceDialog by remember { mutableStateOf(false) }
    var sourceUrl by remember { mutableStateOf("") }
    var sourceError by remember { mutableStateOf<String?>(null) }

    var selectedTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var isYouTubeBrowserOpen by remember { mutableStateOf(false) }

    PABottomSheet(
        isVisible = isVisible,
        onDismissRequest = onDismiss,
        title = "Create Room"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ROOM NAME INPUT
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "ROOM NAME",
                    style = theme.typography.caption.copy(
                        color = theme.colors.textSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontSize = 11.5.sp
                    )
                )

                OutlinedTextField(
                    value = roomName,
                    onValueChange = {
                        roomName = it.take(16)
                        nameError = null
                    },
                    placeholder = {
                        Text(
                            text = "e.g. Cinema Night, Lo-Fi Study Lounge",
                            style = theme.typography.bodyMedium.copy(color = theme.colors.textTertiary)
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = theme.colors.surface,
                        unfocusedContainerColor = theme.colors.surface,
                        focusedBorderColor = if (nameError != null) theme.colors.errorColor else theme.colors.primaryAccent,
                        unfocusedBorderColor = if (nameError != null) theme.colors.errorColor else theme.colors.surfaceBorder,
                        focusedTextColor = theme.colors.textPrimary,
                        unfocusedTextColor = theme.colors.textPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                nameError?.let { err ->
                    Text(
                        text = err,
                        style = theme.typography.caption.copy(
                            color = theme.colors.errorColor,
                            fontSize = 11.5.sp
                        )
                    )
                }
            }

            // PRIVACY SELECTOR
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "PRIVACY",
                    style = theme.typography.caption.copy(
                        color = theme.colors.textSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontSize = 11.5.sp
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PrivacyOption(
                        label = "Friends",
                        icon = Icons.Default.People,
                        isSelected = selectedPrivacy == RoomPrivacy.FRIENDS,
                        onClick = { selectedPrivacy = RoomPrivacy.FRIENDS },
                        modifier = Modifier.weight(1f)
                    )
                    PrivacyOption(
                        label = "Invite Only",
                        icon = Icons.Default.Lock,
                        isSelected = selectedPrivacy == RoomPrivacy.INVITE_ONLY,
                        onClick = { selectedPrivacy = RoomPrivacy.INVITE_ONLY },
                        modifier = Modifier.weight(1f)
                    )
                    PrivacyOption(
                        label = "Public",
                        icon = Icons.Default.Public,
                        isSelected = selectedPrivacy == RoomPrivacy.PUBLIC,
                        onClick = { selectedPrivacy = RoomPrivacy.PUBLIC },
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = when (selectedPrivacy) {
                        RoomPrivacy.FRIENDS -> "Only your friends can join."
                        RoomPrivacy.INVITE_ONLY -> "Only people you invite can join."
                        RoomPrivacy.PUBLIC -> "Anyone can find and join this room."
                    },
                    style = theme.typography.caption.copy(color = theme.colors.textTertiary, fontSize = 11.sp)
                )
            }

            // ROOM TAGS SELECTOR (up to 3 from 50+ predefined tags)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ROOM TAGS (${selectedTags.size}/3)",
                        style = theme.typography.caption.copy(
                            color = theme.colors.textSecondary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontSize = 11.5.sp
                        )
                    )
                    Text(
                        text = "Select up to 3 tags",
                        style = theme.typography.caption.copy(
                            color = theme.colors.textTertiary,
                            fontSize = 11.sp
                        )
                    )
                }

                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    com.nebula.app.ui.home.ALL_ROOM_TAGS.forEach { tag ->
                        val isSelected = selectedTags.contains(tag)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) theme.colors.primaryAccent.copy(alpha = 0.2f) else theme.colors.surface)
                                .border(
                                    1.dp,
                                    if (isSelected) theme.colors.primaryAccent else theme.colors.surfaceBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    selectedTags = if (isSelected) {
                                        selectedTags - tag
                                    } else {
                                        if (selectedTags.size < 3) selectedTags + tag else selectedTags
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = "#$tag",
                                style = theme.typography.caption.copy(
                                    color = if (isSelected) theme.colors.primaryAccent else theme.colors.textSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }

            // MAX PARTICIPANTS SELECTOR
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MAX PARTICIPANTS",
                        style = theme.typography.caption.copy(
                            color = theme.colors.textSecondary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontSize = 11.5.sp
                        )
                    )
                    Text(
                        text = "$selectedMaxParticipants members limit",
                        style = theme.typography.caption.copy(
                            color = theme.colors.primaryAccent,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    )
                }

                val participantLimits = listOf(5, 10, 20, 50)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    participantLimits.forEach { limit ->
                        val isSelected = selectedMaxParticipants == limit
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) theme.colors.primaryAccent.copy(alpha = 0.2f) else theme.colors.surface)
                                .border(
                                    1.dp,
                                    if (isSelected) theme.colors.primaryAccent else theme.colors.surfaceBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedMaxParticipants = limit }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$limit",
                                style = theme.typography.bodySmall.copy(
                                    color = if (isSelected) theme.colors.primaryAccent else theme.colors.textPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }

            // SYNCHRONIZED MEDIA SOURCE
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SELECT SOURCE",
                    style = theme.typography.caption.copy(
                        color = theme.colors.textSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontSize = 11.5.sp
                    )
                )

                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple("YouTube", Icons.Default.Tv, "Search videos"),
                            Triple("URL", Icons.Default.Link, "Direct media link"),
                            Triple("Drive", Icons.Default.Public, "Public file link")
                        ).forEach { (label, icon, subtitle) ->
                            ActivityOption(
                                label = label,
                                subtitle = subtitle,
                                icon = icon,
                                isSelected = selectedSource == label,
                                onClick = { selectedSource = label },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                if (selectedMedia != null) {
                    val media = selectedMedia!!
                    // Allotted video card with remove (X) button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(theme.colors.surfaceElevated)
                            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 72.dp, height = 42.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFCC0000)),
                                contentAlignment = Alignment.Center
                            ) {
                                val videoId = media.youtubeVideoId
                                if (!videoId.isNullOrBlank()) {
                                    AsyncImage(
                                        model = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                                        contentDescription = media.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                            Column {
                                Text(
                                    text = media.title,
                                    style = theme.typography.bodySmall.copy(
                                        color = theme.colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp
                                    ),
                                    maxLines = 1
                                )
                                Text(
                                    text = "${media.source} • Ready to play",
                                    style = theme.typography.caption.copy(
                                        color = theme.colors.primaryAccent,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        IconButton(
                            onClick = { selectedMedia = null },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove video",
                                tint = theme.colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else {
                    // Idle Room State card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(theme.colors.surface)
                            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(theme.colors.surfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = theme.colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Idle Room Position (No Video)",
                                style = theme.typography.bodySmall.copy(
                                    color = theme.colors.textPrimary,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            )
                            Text(
                                text = "Search YouTube below to allot a video, or start idle",
                                style = theme.typography.caption.copy(
                                    color = theme.colors.textSecondary,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }

                // Source action
                Button(
                    onClick = {
                        if (selectedSource == "YouTube") {
                            isYouTubeBrowserOpen = true
                        } else {
                            sourceUrl = ""
                            sourceError = null
                            showSourceDialog = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedSource == "YouTube") Color(0xFFCC0000) else theme.colors.primaryAccent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = when (selectedSource) {
                                "YouTube" -> if (selectedMedia != null) "Change Video (Search YouTube)" else "Search YouTube"
                                "Drive" -> "Add public Drive file"
                                else -> "Add media URL"
                            },
                            style = theme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // CREATE ACTION BUTTON
            Button(
                onClick = {
                    val trimmed = roomName.trim()
                    if (trimmed.isEmpty()) {
                        nameError = "Room name cannot be empty"
                        return@Button
                    }
                    if (trimmed.length < 3) {
                        nameError = "Room name must be at least 3 characters"
                        return@Button
                    }
                    if (trimmed.length > 16) {
                        nameError = "Room name must be 16 characters or fewer"
                        return@Button
                    }
                    if (!trimmed.matches("^[a-zA-Z0-9 ]+$".toRegex())) {
                        nameError = "Use English letters, numbers, and spaces only"
                        return@Button
                    }
                    nameError = null
                    onCreateRoom(trimmed, selectedPrivacy, selectedTags, selectedMaxParticipants, selectedMedia)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.colors.primaryAccent,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "Create Room",
                    style = theme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    YouTubeInAppBrowserDialog(
        isOpen = isYouTubeBrowserOpen,
        onVideoSelected = { videoId, title ->
            val cleanTitle = title.removeSuffix(" - YouTube").removeSuffix("- YouTube").trim().ifBlank { "YouTube Video" }
            selectedMedia = SharedMediaItem(
                id = "yt_picked_$videoId",
                title = cleanTitle,
                source = "YouTube",
                durationLabel = "Playable Video",
                durationMs = 0L,
                youtubeVideoId = videoId
            )
            isYouTubeBrowserOpen = false
        },
        onDismiss = { isYouTubeBrowserOpen = false }
    )

    if (showSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            title = { Text(if (selectedSource == "Drive") "Add Drive file" else "Add media URL") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (selectedSource == "Drive") {
                            "Paste a public Google Drive video link. The file must be accessible without signing in."
                        } else {
                            "Paste a direct video URL (for example, an MP4 or WebM file)."
                        }
                    )
                    OutlinedTextField(
                        value = sourceUrl,
                        onValueChange = { sourceUrl = it },
                        singleLine = true,
                        label = { Text("Media URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    sourceError?.let { Text(it, color = Color(0xFFFCA5A5)) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val url = sourceUrl.trim()
                    val valid = url.startsWith("https://") &&
                        (if (selectedSource == "Drive") {
                            url.contains("drive.google.com") || url.contains("docs.google.com")
                        } else {
                            url.substringAfter("://").contains(".")
                        })
                    if (!valid) {
                        sourceError = if (selectedSource == "Drive") {
                            "This Drive link is invalid or not public."
                        } else {
                            "Could not detect a valid web media URL."
                        }
                    } else {
                        selectedMedia = SharedMediaItem(
                            id = "media_${System.currentTimeMillis()}",
                            title = if (selectedSource == "Drive") "Drive video" else url.substringAfterLast('/').substringBefore('?').ifBlank { "Web video" },
                            source = selectedSource,
                            durationLabel = "Web",
                            durationMs = 0L,
                            mediaUrl = url
                        )
                        showSourceDialog = false
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showSourceDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PrivacyOption(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) theme.colors.primaryAccent.copy(alpha = 0.15f) else theme.colors.surface)
            .border(
                width = 1.dp,
                color = if (isSelected) theme.colors.primaryAccent else theme.colors.surfaceBorder,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) theme.colors.primaryAccent else theme.colors.textSecondary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = theme.typography.caption.copy(
                    color = if (isSelected) theme.colors.primaryAccent else theme.colors.textPrimary,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 11.5.sp
                )
            )
        }
    }
}

@Composable
private fun ActivityOption(
    label: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) theme.colors.primaryAccent.copy(alpha = 0.15f) else theme.colors.surface)
            .border(
                width = 1.dp,
                color = if (isSelected) theme.colors.primaryAccent else theme.colors.surfaceBorder,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) theme.colors.primaryAccent else theme.colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = theme.typography.bodySmall.copy(
                    color = if (isSelected) theme.colors.primaryAccent else theme.colors.textPrimary,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 12.sp
                )
            )
            Text(
                text = subtitle,
                style = theme.typography.caption.copy(
                    color = theme.colors.textTertiary,
                    fontSize = 10.sp
                )
            )
        }
    }
}

/**
 * YouTubeInAppBrowserDialog:
 * Locked in-app browser dialog for selecting YouTube videos.
 * - Opens YouTube search results or mobile YouTube homepage
 * - Browser locked strictly to youtube.com/youtu.be (blocks external navigations)
 * - Intercepts watch?v= / youtu.be / /shorts/ URLs
 * - Extracts 11-char video ID and page title
 * - Periodically checks current URL every 1000ms
 * - Back button returns directly to Room Creation screen
 */
@Composable
fun YouTubeInAppBrowserDialog(
    isOpen: Boolean,
    onVideoSelected: (videoId: String, title: String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return
    val theme = Theme.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                        .background(Color(0xFF0F1117))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {}
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    var currentWebView by remember { mutableStateOf<WebView?>(null) }

                    // Check URL periodically every 500ms
                    LaunchedEffect(isOpen) {
                        while (isOpen) {
                            kotlinx.coroutines.delay(500)
                            val url = currentWebView?.url
                            if (!url.isNullOrBlank()) {
                                val ytId = extractYouTubeId(url)
                                if (ytId != null && ytId.length == 11) {
                                    val rawTitle = currentWebView?.title ?: "YouTube Video"
                                    val cleanTitle = rawTitle.removeSuffix(" - YouTube").removeSuffix("- YouTube").trim()
                                    onVideoSelected(ytId, cleanTitle)
                                    break
                                }
                            }
                        }
                    }

                    // In-App WebView strictly locked to YouTube mobile homepage
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                currentWebView = this
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.mediaPlaybackRequiresUserGesture = false
                                settings.userAgentString =
                                    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val url = request?.url?.toString() ?: return false
                                        val host = request.url?.host ?: ""
                                        // Block external navigations
                                        if (!host.contains("youtube.com") && !host.contains("youtu.be")) {
                                            return true
                                        }

                                        val ytId = extractYouTubeId(url)
                                        if (ytId != null && ytId.length == 11) {
                                            val rawTitle = view?.title ?: "YouTube Video"
                                            val cleanTitle = rawTitle.removeSuffix(" - YouTube").removeSuffix("- YouTube").trim()
                                            onVideoSelected(ytId, cleanTitle)
                                            return true
                                        }
                                        return false
                                    }

                                    override fun onPageStarted(
                                        view: WebView?,
                                        url: String?,
                                        favicon: android.graphics.Bitmap?
                                    ) {
                                        super.onPageStarted(view, url, favicon)
                                        if (url != null) {
                                            val ytId = extractYouTubeId(url)
                                            if (ytId != null && ytId.length == 11) {
                                                val rawTitle = view?.title ?: "YouTube Video"
                                                val cleanTitle = rawTitle.removeSuffix(" - YouTube").removeSuffix("- YouTube").trim()
                                                onVideoSelected(ytId, cleanTitle)
                                            }
                                        }
                                    }
                                }

                                loadUrl("https://m.youtube.com")
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
