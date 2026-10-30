package com.nebula.app.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.domain.model.SharedMediaItem
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.PABottomSheet
import kotlinx.coroutines.launch

/**
 * InRoomMediaSearchSheet:
 * Lets anyone in the room point the room at a video.
 *
 * It offers exactly the sources room creation offers — YouTube (open the in-app
 * browser to search, or paste a link), Vimeo, Prime Video and any other link — because
 * "add video" and "playlist" used to open a YouTube-only surface while creation
 * already supported more.
 *
 * @param title sheet heading; the caller uses "Play now" or "Add to playlist".
 * @param onSubmitMediaLink suspend submitter supplied by the room screen. It returns the
 *        single-line error to display, or null when the server accepted the media. The
 *        caller is responsible for refreshing room state only after a confirmed update.
 */
@Composable
fun InRoomMediaSearchSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onSubmitMediaLink: suspend (SharedMediaItem) -> String?,
    title: String = "Play something",
    submitLabel: String = "Play"
) {
    val theme = Theme.current
    val scope = rememberCoroutineScope()
    var isYouTubeBrowserOpen by remember { mutableStateOf(false) }
    var linkInput by remember { mutableStateOf("") }
    var linkError by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var selectedSource by remember { mutableStateOf(RoomMediaSource.YOUTUBE) }

    /** Submits one concrete video to the server-authoritative room media update. */
    fun submitMedia(item: SharedMediaItem) {
        if (isSubmitting) return
        val url = item.youtubeVideoId?.takeIf { it.isNotBlank() }
            ?.let { "https://www.youtube.com/watch?v=$it" }
            ?: item.mediaUrl.orEmpty()

        // Offline pre-check: an image link never leaves the device.
        mediaLinkPrecheckError(url)?.let { precheck ->
            linkError = precheck
            return
        }

        linkError = null
        scope.launch {
            isSubmitting = true
            val outcome = runCatching { onSubmitMediaLink(item) }
            isSubmitting = false
            val rejection = outcome.getOrNull()
            when {
                outcome.isFailure ->
                    linkError = "Could not reach the server. Check your connection and try again."
                rejection.isNullOrBlank() -> {
                    linkInput = ""
                    linkError = null
                    onDismiss()
                }
                else -> linkError = singleLineLinkError(rejection)
            }
        }
    }

    PABottomSheet(
        isVisible = isVisible,
        onDismissRequest = onDismiss,
        title = title
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = theme.typography.titleMedium.copy(
                        color = theme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                )
                Text(
                    text = "YouTube, Vimeo, Prime Video or any other link. The server checks every link before it plays.",
                    style = theme.typography.caption.copy(
                        color = theme.colors.textSecondary,
                        fontSize = 12.sp
                    )
                )
            }

            MediaSourcePicker(
                selected = selectedSource,
                onSelect = { source ->
                    selectedSource = source
                    linkError = null
                },
                linkValue = linkInput,
                onLinkChange = {
                    linkInput = it
                    linkError = null
                    if (it.trim().length > 8 && it.contains("://")) {
                        val detected = RoomMediaSource.detect(it)
                        if (detected != selectedSource) selectedSource = detected
                    }
                },
                error = linkError,
                isBusy = isSubmitting,
                onOpenYouTube = { isYouTubeBrowserOpen = true },
                onSubmit = {
                    val url = linkInput.trim()
                    if (url.isEmpty()) {
                        linkError = EMPTY_LINK_ERROR
                        return@MediaSourcePicker
                    }
                    submitMedia(buildMediaItem(url, selectedSource))
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tap a source tab to switch where the link comes from.",
                    style = theme.typography.caption.copy(color = theme.colors.textTertiary, fontSize = 10.5.sp)
                )
                Text(
                    text = submitLabel,
                    style = theme.typography.caption.copy(
                        color = theme.colors.primaryAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }

    YouTubeInAppBrowserDialog(
        isOpen = isYouTubeBrowserOpen,
        onVideoSelected = { videoId, titleText ->
            val cleanTitle = titleText.removeSuffix(" - YouTube").removeSuffix("- YouTube").trim().ifBlank { "YouTube video" }
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
