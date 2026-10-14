package com.nebula.app.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.domain.model.SharedMediaItem
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.PABottomSheet
import kotlinx.coroutines.launch

/**
 * InRoomMediaSearchSheet:
 * Lets the room host switch what everyone is watching.
 *
 * - The in-app YouTube browser popup still queues a discovered video.
 * - A pasted YouTube / Vimeo / direct video link is submitted to the server-authoritative
 *   room media update. An HTTP 422 rejection shows the server's sentence as one inline line
 *   under the field, and the room's local now-playing state is left untouched.
 * - Image links and Google Drive links are rejected on-device before any network call.
 *
 * @param onSubmitMediaLink suspend submitter supplied by the room screen. It returns the
 *        single-line error to display, or null when the server accepted the switch. The
 *        caller is responsible for refreshing the room state only after a confirmed update.
 */
@Composable
fun InRoomMediaSearchSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onSubmitMediaLink: suspend (SharedMediaItem) -> String?
) {
    val theme = Theme.current
    val scope = rememberCoroutineScope()
    var isYouTubeBrowserOpen by remember { mutableStateOf(false) }
    var linkInput by remember { mutableStateOf("") }
    var linkError by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    /**
     * Submits one concrete video to the server-authoritative room media update.
     * Only a confirmed success dismisses the sheet and updates the room.
     */
    fun submitMedia(item: SharedMediaItem) {
        if (isSubmitting) return
        val url = item.youtubeVideoId?.takeIf { it.isNotBlank() }
            ?.let { "https://www.youtube.com/watch?v=$it" }
            ?: item.mediaUrl.orEmpty()

        // Offline pre-check: obvious non-videos never leave the device.
        mediaLinkPrecheckError(url)?.let { precheck ->
            linkError = precheck
            return
        }

        linkError = null
        scope.launch {
            isSubmitting = true
            val rejection = runCatching { onSubmitMediaLink(item) }.getOrNull()
            isSubmitting = false
            if (rejection.isNullOrBlank()) {
                linkInput = ""
                linkError = null
                onDismiss()
            } else {
                // Server authority (HTTP 422): exactly one inline line under the field and
                // no change to the local now-playing state.
                linkError = singleLineLinkError(rejection)
            }
        }
    }

    PABottomSheet(
        isVisible = isVisible,
        onDismissRequest = onDismiss,
        title = "Queue Video"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFCC0000)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Switch the room video",
                    style = theme.typography.titleMedium.copy(
                        color = theme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Search YouTube in the popup, or paste a YouTube, Vimeo, or direct video link. Every switch is verified by the server before it plays.",
                    style = theme.typography.caption.copy(
                        color = theme.colors.textSecondary,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            Button(
                onClick = { isYouTubeBrowserOpen = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFCC0000),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Search YouTube",
                        style = theme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    )
                }
            }

            Text(
                text = "OR PASTE A LINK",
                style = theme.typography.caption.copy(
                    color = theme.colors.textSecondary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontSize = 11.sp
                ),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = linkInput,
                onValueChange = {
                    linkInput = it
                    // Any change clears the previous rejection immediately.
                    linkError = null
                },
                placeholder = {
                    Text(
                        text = "https://youtube.com/watch?v=... · vimeo.com/... · video.mp4",
                        style = theme.typography.bodyMedium.copy(color = theme.colors.textTertiary)
                    )
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = theme.colors.surface,
                    unfocusedContainerColor = theme.colors.surface,
                    focusedBorderColor = if (linkError != null) theme.colors.errorColor else theme.colors.primaryAccent,
                    unfocusedBorderColor = if (linkError != null) theme.colors.errorColor else theme.colors.surfaceBorder,
                    focusedTextColor = theme.colors.textPrimary,
                    unfocusedTextColor = theme.colors.textPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // One inline line directly under the link field — never a dialog or a toast dump.
            linkError?.let { err ->
                Text(
                    text = singleLineLinkError(err),
                    style = theme.typography.caption.copy(
                        color = theme.colors.errorColor,
                        fontSize = 11.5.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                onClick = {
                    val url = linkInput.trim()
                    if (url.isEmpty()) {
                        linkError = EMPTY_LINK_ERROR
                        return@Button
                    }
                    submitMedia(
                        SharedMediaItem(
                            id = "media_${System.currentTimeMillis()}",
                            title = url.substringAfterLast('/').substringBefore('?').ifBlank { "Web video" },
                            source = when {
                                url.contains("youtube.com") || url.contains("youtu.be") -> "YouTube"
                                url.contains("vimeo.com") -> "Vimeo"
                                else -> "URL"
                            },
                            durationLabel = "Stream",
                            durationMs = 0L,
                            mediaUrl = url,
                            youtubeVideoId = extractYouTubeId(url)
                        )
                    )
                },
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.colors.primaryAccent,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (isSubmitting) "Checking link…" else "Switch room video",
                        style = theme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    YouTubeInAppBrowserDialog(
        isOpen = isYouTubeBrowserOpen,
        onVideoSelected = { videoId, title ->
            val cleanTitle = title.removeSuffix(" - YouTube").removeSuffix("- YouTube").trim().ifBlank { "YouTube Video" }
            isYouTubeBrowserOpen = false
            submitMedia(
                SharedMediaItem(
                    id = "yt_picked_$videoId",
                    title = cleanTitle,
                    source = "YouTube",
                    durationLabel = "Stream",
                    durationMs = 0L,
                    youtubeVideoId = videoId
                )
            )
        },
        onDismiss = { isYouTubeBrowserOpen = false }
    )
}
