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
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nebula.app.data.backend.NebulaApiClient
import com.nebula.app.data.local.SessionManager
import com.nebula.app.domain.model.NebulaSubscriptionTier
import com.nebula.app.domain.model.RoomActivityType
import com.nebula.app.domain.model.RoomPrivacy
import com.nebula.app.domain.model.SharedMediaItem
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.PABottomSheet
import kotlinx.coroutines.launch

// ============================================================================
// Shared playable-link rules (create-room sheet + in-room media switch sheet).
// These are cheap, offline pre-checks only: the server stays the authority and
// answers with HTTP 422 + one clear sentence for anything it rejects.
// ============================================================================

/** Shown for a link whose path ends in an image extension. */
const val IMAGE_LINK_ERROR = "That link points to an image, not a playable video."

/** Shown for any Google Drive link; the server rejects Drive with UNSUPPORTED_SOURCE. */
const val DRIVE_LINK_ERROR =
    "Google Drive links cannot be played in rooms. Use YouTube, Vimeo, or a direct video file."

/** Shown when the user submits an empty link field. */
const val EMPTY_LINK_ERROR = "Enter a video link to play in this room."

const val MAX_ROOM_TAGS = 3
const val MAX_ROOM_TAG_LENGTH = 30

/** Server-authoritative room participant caps. Every tier allows the same 24-seat room. */
const val BASIC_ROOM_PARTICIPANT_CAP = 24
const val PREMIUM_ROOM_PARTICIPANT_CAP = 24
const val PREMIUM_PLUS_ROOM_PARTICIPANT_CAP = 24

private val IMAGE_LINK_EXTENSIONS = listOf(
    ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".svg", ".avif", ".heic"
)

/**
 * Collapses a server message (or any message) onto a single line so it can be
 * shown as one concise inline line instead of a multi-line toast dump.
 */
fun singleLineLinkError(message: String): String =
    message.replace(Regex("\\s+"), " ").trim()

/**
 * Cheap offline pre-check for room media links. Returns the one-line error to
 * show, or null when the link is worth submitting to the server.
 *
 * Only an image file is rejected here: every other http(s) link (a page, a file, an
 * embeddable player) is left to the server, which classifies it and answers with a
 * 422 the caller shows verbatim.
 */
fun mediaLinkPrecheckError(rawUrl: String): String? {
    val trimmed = rawUrl.trim()
    if (trimmed.isEmpty()) return null

    val lower = trimmed.lowercase()
    val withoutScheme = lower.substringAfter("://", lower).removePrefix("//")

    // Mirrors the server: only the URL path decides, query/fragment are ignored.
    val path = withoutScheme.substringAfter('/', "").substringBefore('?').substringBefore('#')
    if (IMAGE_LINK_EXTENSIONS.any { path.endsWith(it) }) return IMAGE_LINK_ERROR

    return null
}

/** Server-authoritative participant cap for a subscription tier. */
fun roomParticipantCapForTier(tier: NebulaSubscriptionTier?): Int = when (tier) {
    NebulaSubscriptionTier.PREMIUM_PLUS -> PREMIUM_PLUS_ROOM_PARTICIPANT_CAP
    NebulaSubscriptionTier.PREMIUM -> PREMIUM_ROOM_PARTICIPANT_CAP
    else -> BASIC_ROOM_PARTICIPANT_CAP
}

private fun tierCapLabel(cap: Int): String = "Room cap"

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



/**
 * CreateRoomSheet — six-step room creation wizard, in this exact order:
 *
 * 1. Privacy           -> [PrivacyStep]        (public / friends / invite-only)
 * 2. Source            -> [SourceStep]         (YouTube / Vimeo / direct link / none-ambient)
 * 3. Source selection  -> [SourceSelectionStep](in-app YouTube search or an entered link)
 * 4. Participant limit -> [ParticipantLimitStep] (strictly clamped to the plan cap)
 * 5. Tags              -> [TagsStep]           (max 3 tags, 30 chars each)
 * 6. Create            -> [CreateStep]         (name + final review + create button)
 *
 * @param entitlementParticipantCap server entitlement payload value, when the caller already has it.
 *        When null the tier is read from the session/profile state and mapped to the
 *        server-authoritative caps (Basic 20 / Premium 25 / Premium Plus 50).
 * @param viewerTier optional explicit tier override for the cap lookup.
 * @param verifySourceLink optional server preflight for an entered link. It must return the
 *        `POST /rooms` / media-verification `ApiResponse`; an `ApiResponse.Error` with
 *        statusCode 422 renders the server's `error` as one inline line. The final create call
 *        remains the authority when no preflight is supplied.
 */
@Composable
fun CreateRoomSheet(
    isVisible: Boolean,
    onCreateRoom: (name: String, privacy: RoomPrivacy, tags: List<String>, maxParticipants: Int, media: SharedMediaItem?) -> Unit,
    onDismiss: () -> Unit,
    entitlementParticipantCap: Int? = null,
    viewerTier: NebulaSubscriptionTier? = null,
    verifySourceLink: (suspend (String) -> NebulaApiClient.ApiResponse<*>)? = null
) {
    val theme = Theme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var roomName by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var selectedPrivacy by remember { mutableStateOf(RoomPrivacy.PUBLIC) }
    var selectedMedia by remember { mutableStateOf<SharedMediaItem?>(null) }
    var selectedMaxParticipants by remember { mutableIntStateOf(BASIC_ROOM_PARTICIPANT_CAP) }
    var selectedSource by remember { mutableStateOf(RoomMediaSource.YOUTUBE) }
    var startWithoutVideo by remember { mutableStateOf(false) }
    var sourceUrl by remember { mutableStateOf("") }
    var sourceError by remember { mutableStateOf<String?>(null) }
    var isVerifyingLink by remember { mutableStateOf(false) }

    var selectedTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var isYouTubeBrowserOpen by remember { mutableStateOf(false) }

    // Strict plan cap: entitlement payload first, otherwise the signed-in session tier.
    val participantCap = remember(isVisible, entitlementParticipantCap, viewerTier) {
        entitlementParticipantCap?.takeIf { it > 0 } ?: roomParticipantCapForTier(
            viewerTier
                ?: NebulaApiClient.sessionManager?.getUserProfile()?.subscriptionTier
                ?: runCatching { SessionManager.getInstance(context).getUserProfile()?.subscriptionTier }.getOrNull()
        )
    }

    // Clamp, so a value above the cap can never stay selected (and never be sent).
    LaunchedEffect(participantCap) {
        if (selectedMaxParticipants > participantCap) selectedMaxParticipants = participantCap
    }

    val sectionHeaderStyle = theme.typography.caption.copy(
        color = theme.colors.textSecondary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        fontSize = 11.5.sp
    )
    val hintStyle = theme.typography.caption.copy(color = theme.colors.textTertiary, fontSize = 11.sp)

    fun acceptSourceVideo(url: String) {
        selectedMedia = buildMediaItem(url, selectedSource)
        startWithoutVideo = false
        sourceUrl = ""
        sourceError = null
    }

    /** Submits/validates the entered link. Server 422 wins; the offline pre-check runs first. */
    fun submitSourceLink() {
        if (isVerifyingLink) return
        val url = sourceUrl.trim()
        if (url.isEmpty()) {
            sourceError = EMPTY_LINK_ERROR
            return
        }
        mediaLinkPrecheckError(url)?.let { precheck ->
            sourceError = precheck
            return
        }
        sourceError = null

        val verifier = verifySourceLink
        if (verifier == null) {
            acceptSourceVideo(url)
            return
        }
        scope.launch {
            isVerifyingLink = true
            val response = runCatching { verifier(url) }.getOrNull()
            isVerifyingLink = false
            when (response) {
                is NebulaApiClient.ApiResponse.Error ->
                    if (response.statusCode == 422) {
                        sourceError = singleLineLinkError(response.message)
                    } else {
                        acceptSourceVideo(url)
                    }
                else -> acceptSourceVideo(url)
            }
        }
    }

    val nameSuggestions = listOf("Cinema Night", "Study Chill", "Watch Party", "Gaming Hub")

    PABottomSheet(
        isVisible = isVisible,
        onDismissRequest = onDismiss,
        title = "Create Room"
    ) {
        BackHandler { onDismiss() }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. ROOM NAME
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "ROOM NAME", style = sectionHeaderStyle)
                    Text(
                        text = "${roomName.length}/16",
                        style = hintStyle
                    )
                }

                OutlinedTextField(
                    value = roomName,
                    onValueChange = {
                        roomName = it.take(16)
                        nameError = null
                    },
                    placeholder = {
                        Text(
                            text = "e.g. Cinema Night, Lo-Fi Chill",
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

                if (nameError != null) {
                    Text(
                        text = nameError.orEmpty(),
                        style = theme.typography.caption.copy(
                            color = theme.colors.errorColor,
                            fontSize = 11.5.sp
                        )
                    )
                }

                // Quick suggestions row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    nameSuggestions.forEach { suggestion ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(theme.colors.surface)
                                .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(8.dp))
                                .clickable {
                                    roomName = suggestion
                                    nameError = null
                                }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = suggestion,
                                style = theme.typography.caption.copy(
                                    color = if (roomName == suggestion) theme.colors.primaryAccent else theme.colors.textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (roomName == suggestion) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        }
                    }
                }
            }

            // 2. PRIVACY — public first, then friends, then invite-only.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "PRIVACY", style = sectionHeaderStyle)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PrivacyOption(
                        label = "Public",
                        icon = Icons.Default.Public,
                        isSelected = selectedPrivacy == RoomPrivacy.PUBLIC,
                        onClick = { selectedPrivacy = RoomPrivacy.PUBLIC },
                        modifier = Modifier.weight(1f)
                    )
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
                }
            }

            // 3. MEDIA & ACTIVITY (one picker shared with the in-room playlist)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "MEDIA / ACTIVITY", style = sectionHeaderStyle)
                    Text(
                        text = if (startWithoutVideo) "Voice + chat" else selectedSource.label,
                        style = hintStyle
                    )
                }

                if (startWithoutVideo) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.colors.surface)
                            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(10.dp))
                            .clickable {
                                startWithoutVideo = false
                                selectedSource = RoomMediaSource.YOUTUBE
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Ambient lounge (no video)",
                                style = theme.typography.bodySmall.copy(
                                    color = theme.colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            )
                            Text(text = "Tap to pick a video instead", style = hintStyle)
                        }
                        Text(
                            text = "Change",
                            style = theme.typography.caption.copy(
                                color = theme.colors.primaryAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                } else {
                    MediaSourcePicker(
                        selected = selectedSource,
                        onSelect = { source ->
                            selectedSource = source
                            sourceUrl = ""
                            sourceError = null
                        },
                        linkValue = sourceUrl,
                        onLinkChange = {
                            sourceUrl = it
                            sourceError = null
                            // Pasting a link from another host switches the tab so the
                            // label and the verification path always agree.
                            if (it.trim().length > 8) {
                                val detected = RoomMediaSource.detect(it)
                                if (detected != selectedSource && it.contains("://")) selectedSource = detected
                            }
                        },
                        error = sourceError,
                        isBusy = isVerifyingLink,
                        onOpenYouTube = { isYouTubeBrowserOpen = true },
                        onSubmit = { submitSourceLink() }
                    )

                    AmbientRoomRow(onClick = {
                        startWithoutVideo = true
                        selectedMedia = null
                        sourceUrl = ""
                        sourceError = null
                    })
                }
            }

                // Chosen video preview card
                selectedMedia?.let { media ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.colors.surfaceElevated)
                            .border(1.dp, theme.colors.primaryAccent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
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
                                    .size(width = 64.dp, height = 38.dp)
                                    .clip(RoundedCornerShape(6.dp))
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
                                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                            Column {
                                Text(
                                    text = media.title,
                                    style = theme.typography.bodySmall.copy(
                                        color = theme.colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${media.source} • Ready",
                                    style = theme.typography.caption.copy(
                                        color = theme.colors.primaryAccent,
                                        fontSize = 10.5.sp
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
                                contentDescription = "Remove",
                                tint = theme.colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

            // 4. PARTICIPANT LIMIT
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "PARTICIPANT LIMIT", style = sectionHeaderStyle)
                    Text(
                        text = "$selectedMaxParticipants of $participantCap seats",
                        style = theme.typography.caption.copy(
                            color = theme.colors.primaryAccent,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    )
                }

                // Six even steps up to the largest room this app supports (24 seats),
                // identical to the ladder the host can change later in room settings.
                val participantLimits = listOf(4, 8, 12, 16, 20, 24)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    participantLimits.forEach { limit ->
                        val isAllowed = limit <= participantCap
                        val isSelected = selectedMaxParticipants == limit
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .alpha(if (isAllowed) 1f else 0.4f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) theme.colors.primaryAccent.copy(alpha = 0.2f) else theme.colors.surface)
                                .border(
                                    1.dp,
                                    if (isSelected) theme.colors.primaryAccent else theme.colors.surfaceBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable(enabled = isAllowed) {
                                    selectedMaxParticipants = limit.coerceAtMost(participantCap)
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$limit",
                                style = theme.typography.bodySmall.copy(
                                    color = when {
                                        isSelected -> theme.colors.primaryAccent
                                        isAllowed -> theme.colors.textPrimary
                                        else -> theme.colors.textTertiary
                                    },
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }

            // 5. TAGS (Optional)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ROOM TAGS (${selectedTags.size}/$MAX_ROOM_TAGS)",
                        style = sectionHeaderStyle
                    )
                    Text(
                        text = "Optional",
                        style = hintStyle
                    )
                }

                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    com.nebula.app.ui.home.ALL_ROOM_TAGS.forEach { tag ->
                        val isSelected = selectedTags.contains(tag)
                        val isTooLong = tag.length > MAX_ROOM_TAG_LENGTH
                        val canSelect = isSelected || (selectedTags.size < MAX_ROOM_TAGS && !isTooLong)
                        Box(
                            modifier = Modifier
                                .height(30.dp)
                                .alpha(if (isTooLong) 0.4f else 1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) theme.colors.primaryAccent.copy(alpha = 0.2f) else theme.colors.surface)
                                .border(
                                    1.dp,
                                    if (isSelected) theme.colors.primaryAccent else theme.colors.surfaceBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable(enabled = canSelect) {
                                    selectedTags = if (isSelected) {
                                        selectedTags - tag
                                    } else {
                                        if (selectedTags.size < MAX_ROOM_TAGS && tag.length <= MAX_ROOM_TAG_LENGTH) {
                                            selectedTags + tag
                                        } else {
                                            selectedTags
                                        }
                                    }
                                }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "#$tag",
                                style = theme.typography.caption.copy(
                                    color = if (isSelected) theme.colors.primaryAccent else theme.colors.textSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.5.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 6. CREATE ROOM CTA BUTTON
            Button(
                onClick = {
                    val finalName = if (roomName.isBlank()) "Nebula Lounge" else roomName.trim()
                    if (finalName.length < 3) {
                        nameError = "Room name must be at least 3 characters"
                        return@Button
                    }
                    if (finalName.length > 16) {
                        nameError = "Room name must be 16 characters or fewer"
                        return@Button
                    }
                    if (!finalName.matches("^[a-zA-Z0-9 ]+$".toRegex())) {
                        nameError = "Use English letters, numbers, and spaces only"
                        return@Button
                    }
                    nameError = null
                    val safeTags = selectedTags
                        .filter { it.isNotBlank() && it.length <= MAX_ROOM_TAG_LENGTH }
                        .distinct()
                        .take(MAX_ROOM_TAGS)
                    val safeMaxParticipants = selectedMaxParticipants.coerceIn(2, participantCap)
                    onCreateRoom(
                        finalName,
                        selectedPrivacy,
                        safeTags,
                        safeMaxParticipants,
                        if (startWithoutVideo) null else selectedMedia
                    )
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
                        text = "Create & Enter Room",
                        style = theme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
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
            sourceError = null
            isYouTubeBrowserOpen = false
        },
        onDismiss = { isYouTubeBrowserOpen = false }
    )
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
            .padding(vertical = 10.dp, horizontal = 4.dp),
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
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = theme.typography.caption.copy(
                    color = theme.colors.textTertiary,
                    fontSize = 9.5.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
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
