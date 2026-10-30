package com.nebula.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.nebula.app.theme.Theme

/** Where an attachment is in its upload, before it becomes a message. */
internal enum class AttachmentUploadState {
    /** Uploading to storage; Send waits for this. */
    UPLOADING,
    /** Stored, with a URL the message can point at. */
    READY,
    /** The upload did not finish. Retry is offered instead of losing the picture. */
    FAILED,
    /** No network at pick time: nothing was attempted. */
    OFFLINE
}

/**
 * A picked photo or video on its way to becoming a message.
 *
 * The local URI is kept so the user sees *their* picture immediately, at full quality,
 * while the upload runs — the previous flow waited for the server's copy before showing
 * anything, so a slow connection looked like a failed pick. Nothing is sent until the
 * upload is READY, which is what stops an empty attachment from being posted.
 */
internal data class PendingAttachment(
    val localUri: String,
    val mimeType: String,
    val isVideo: Boolean,
    val state: AttachmentUploadState = AttachmentUploadState.UPLOADING,
    val remoteUrl: String? = null,
    val progress: Int = 0,
    val error: String? = null
) {
    val canSend: Boolean get() = state == AttachmentUploadState.READY && !remoteUrl.isNullOrBlank()

    /** Message type the server expects for this draft. */
    val messageType: String
        get() = when {
            isVideo -> "video"
            mimeType == "image/gif" -> "media"
            else -> "media"
        }
}

/**
 * The draft card above the composer: local preview, upload progress, failure and retry,
 * spoiler toggle, and a Send that is only enabled once the file actually exists on the
 * server.
 */
@Composable
internal fun AttachmentDraftCard(
    draft: PendingAttachment,
    isSpoiler: Boolean,
    isOnline: Boolean,
    onToggleSpoiler: (Boolean) -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    val message = when (draft.state) {
        AttachmentUploadState.UPLOADING -> "Uploading… ${draft.progress}%"
        AttachmentUploadState.READY -> "Ready to send"
        AttachmentUploadState.FAILED -> draft.error ?: "Upload failed"
        AttachmentUploadState.OFFLINE -> "You're offline"
    }

    androidx.compose.material3.Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = theme.colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.surfaceBorder)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.colors.surfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    if (draft.isVideo) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = theme.colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        // The picked file is shown from the device, so the preview is
                        // instant and independent of the upload.
                        SubcomposeAsyncImage(
                            model = draft.localUri,
                            contentDescription = "Photo ready to send",
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                            contentScale = ContentScale.Crop,
                            loading = { }
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (draft.isVideo) "Video" else "Photo",
                        style = theme.typography.bodySmall.copy(
                            color = theme.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        text = message,
                        style = theme.typography.caption.copy(
                            color = when (draft.state) {
                                AttachmentUploadState.FAILED, AttachmentUploadState.OFFLINE -> theme.colors.errorColor
                                AttachmentUploadState.READY -> theme.colors.successColor
                                AttachmentUploadState.UPLOADING -> theme.colors.textSecondary
                            },
                            fontSize = 11.5.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (draft.state == AttachmentUploadState.UPLOADING) {
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(theme.colors.surfaceBorder.copy(alpha = 0.5f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth((draft.progress.coerceIn(0, 100)) / 100f)
                                    .fillMaxHeight()
                                    .background(theme.colors.successColor)
                            )
                        }
                    }
                }
                IconButton(
                    onClick = onDiscard,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Discard attachment",
                        tint = theme.colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (draft.state == AttachmentUploadState.UPLOADING) {
                Text(
                    text = "Send unlocks when the upload finishes",
                    style = theme.typography.caption.copy(color = theme.colors.textTertiary, fontSize = 10.5.sp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Hide as spoiler",
                    style = theme.typography.caption.copy(color = theme.colors.textPrimary, fontSize = 12.sp)
                )
                Switch(
                    checked = isSpoiler,
                    onCheckedChange = onToggleSpoiler,
                    modifier = Modifier.scale(0.8f),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = theme.colors.onPrimaryAccent,
                        checkedTrackColor = theme.colors.primaryAccent,
                        uncheckedThumbColor = theme.colors.textSecondary,
                        uncheckedTrackColor = theme.colors.surfaceElevated,
                        uncheckedBorderColor = theme.colors.surfaceBorder
                    )
                )
                Spacer(Modifier.weight(1f))

                if (draft.state == AttachmentUploadState.FAILED || draft.state == AttachmentUploadState.OFFLINE) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(theme.colors.surfaceElevated)
                            .clickable(enabled = isOnline, onClick = onRetry)
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = if (isOnline) "Retry" else "Offline",
                            style = theme.typography.caption.copy(
                                color = if (isOnline) theme.colors.textPrimary else theme.colors.textTertiary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (draft.canSend) theme.colors.primaryAccent
                            else theme.colors.surfaceElevated
                        )
                        .clickable(enabled = draft.canSend, onClick = onSend)
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "Send",
                        style = theme.typography.caption.copy(
                            color = if (draft.canSend) theme.colors.onPrimaryAccent else theme.colors.textTertiary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}
