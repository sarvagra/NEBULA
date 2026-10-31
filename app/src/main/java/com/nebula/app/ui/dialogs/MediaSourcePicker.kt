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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.domain.model.SharedMediaItem
import com.nebula.app.theme.Theme

/**
 * The media sources a room can play.
 *
 * Rooms used to present "YouTube" as if it were the only source. Every surface that
 * asks for media — room creation, the host's "switch video" sheet, the playlist —
 * now shows the same four choices, and the server verifies whichever link is given.
 */
enum class RoomMediaSource(val label: String, val shortHint: String) {
    YOUTUBE("YouTube", "Search or paste a YouTube link"),
    VIMEO("Vimeo", "Paste a Vimeo link"),
    PRIME("Prime Video", "Paste a Prime Video link"),
    LINK("Link", "Any video page or file link");

    val placeholder: String
        get() = when (this) {
            YOUTUBE -> "https://youtube.com/watch?v=…"
            VIMEO -> "https://vimeo.com/…"
            PRIME -> "https://primevideo.com/detail/…"
            LINK -> "https://…"
        }

    companion object {
        /** Best-effort source for a pasted link, so the right tab lights up. */
        fun detect(url: String): RoomMediaSource {
            val lower = url.trim().lowercase()
            return when {
                lower.contains("youtube.com") || lower.contains("youtu.be") -> YOUTUBE
                lower.contains("vimeo.com") -> VIMEO
                lower.contains("primevideo.com") || lower.contains("amazon.com") -> PRIME
                else -> LINK
            }
        }
    }
}

/** Source label stored on a room's media item, e.g. "Vimeo". */
fun RoomMediaSource.mediaLabel(): String = when (this) {
    RoomMediaSource.YOUTUBE -> "YouTube"
    RoomMediaSource.VIMEO -> "Vimeo"
    RoomMediaSource.PRIME -> "Prime Video"
    RoomMediaSource.LINK -> "Link"
}

/**
 * Builds the room media item for a pasted link.
 *
 * The server normalises the link to its playable form; this only carries the label and
 * the YouTube id the client needs to pick a surface optimistically.
 */
fun buildMediaItem(
    url: String,
    source: RoomMediaSource = RoomMediaSource.detect(url),
    title: String? = null
): SharedMediaItem {
    val trimmed = url.trim()
    val youtubeId = if (source == RoomMediaSource.YOUTUBE) extractYouTubeId(trimmed) else null
    val fallbackTitle = trimmed.substringAfterLast('/').substringBefore('?').substringBefore('#')
        .replace('-', ' ').replace('_', ' ').ifBlank { source.mediaLabel() }
        .take(120)
    return SharedMediaItem(
        id = "media_${System.currentTimeMillis()}",
        title = title?.takeIf { it.isNotBlank() } ?: fallbackTitle,
        source = source.mediaLabel(),
        durationLabel = "Stream",
        durationMs = 0L,
        mediaUrl = trimmed,
        youtubeVideoId = youtubeId
    )
}

private fun RoomMediaSource.icon(): ImageVector = when (this) {
    RoomMediaSource.YOUTUBE -> Icons.Default.PlayArrow
    RoomMediaSource.VIMEO -> Icons.Default.Tv
    RoomMediaSource.PRIME -> Icons.Default.PlayArrow
    RoomMediaSource.LINK -> Icons.Default.Link
}

/** The four source chips. Identical everywhere media is chosen. */
@Composable
fun MediaSourceTabs(
    selected: RoomMediaSource,
    onSelect: (RoomMediaSource) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RoomMediaSource.entries.forEach { source ->
            val isSelected = source == selected
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSelected) theme.colors.primaryAccent.copy(alpha = 0.16f) else theme.colors.surface)
                    .border(
                        1.dp,
                        if (isSelected) theme.colors.primaryAccent else theme.colors.surfaceBorder,
                        RoundedCornerShape(9.dp)
                    )
                    .clickable { onSelect(source) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = source.icon(),
                    contentDescription = source.label,
                    tint = if (isSelected) theme.colors.primaryAccent else theme.colors.textSecondary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = source.label,
                    style = theme.typography.caption.copy(
                        color = if (isSelected) theme.colors.primaryAccent else theme.colors.textSecondary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 10.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}

/**
 * Source tabs + the matching input row.
 *
 * YouTube keeps both routes it is expected to offer: open YouTube in the in-app
 * browser (search and pick) and paste a link. Every other source is a paste field.
 */
@Composable
fun MediaSourcePicker(
    selected: RoomMediaSource,
    onSelect: (RoomMediaSource) -> Unit,
    linkValue: String,
    onLinkChange: (String) -> Unit,
    error: String?,
    isBusy: Boolean,
    onOpenYouTube: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MediaSourceTabs(selected = selected, onSelect = onSelect)

        if (selected == RoomMediaSource.YOUTUBE) {
            Button(
                onClick = onOpenYouTube,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFCC0000),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Text(
                        text = "Open YouTube",
                        style = theme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = linkValue,
                onValueChange = onLinkChange,
                placeholder = {
                    Text(
                        text = selected.placeholder,
                        style = theme.typography.bodySmall.copy(color = theme.colors.textTertiary, fontSize = 12.sp)
                    )
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = theme.colors.surface,
                    unfocusedContainerColor = theme.colors.surface,
                    focusedBorderColor = if (error != null) theme.colors.errorColor else theme.colors.primaryAccent,
                    unfocusedBorderColor = if (error != null) theme.colors.errorColor else theme.colors.surfaceBorder,
                    focusedTextColor = theme.colors.textPrimary,
                    unfocusedTextColor = theme.colors.textPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = onSubmit,
                enabled = !isBusy,
                modifier = Modifier.height(44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.colors.primaryAccent,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = if (isBusy) "…" else "Add",
                    style = theme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        Text(
            text = selected.shortHint,
            style = theme.typography.caption.copy(color = theme.colors.textTertiary, fontSize = 10.5.sp)
        )

        error?.let { err ->
            Text(
                text = singleLineLinkError(err),
                style = theme.typography.caption.copy(color = theme.colors.errorColor, fontSize = 11.5.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
