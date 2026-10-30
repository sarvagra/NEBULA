package com.nebula.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.domain.model.RoomQueueItem
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.PABottomSheet

/**
 * RoomQueueSheet:
 * What the room has agreed to watch later.
 *
 * Two actions are deliberately separate, because conflating them is what made the
 * first version of this feature change what everyone was watching:
 *  - "Skip" is a vote. It needs a strict majority of the room and only ever removes
 *    the item from the queue.
 *  - "Play now" is the host's alone and advances the room onto the item.
 */
@Composable
fun RoomQueueSheet(
    isVisible: Boolean,
    queue: List<RoomQueueItem>,
    currentUserId: String,
    isHost: Boolean,
    onAddToQueue: (title: String, mediaUrl: String) -> Unit,
    onVoteSkip: (String) -> Unit,
    onAdvance: () -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = Theme.current
    var showAddField by remember { mutableStateOf(false) }
    var linkText by remember { mutableStateOf("") }
    var titleText by remember { mutableStateOf("") }

    PABottomSheet(isVisible = isVisible, onDismissRequest = onDismiss, content = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Up next",
                        style = theme.typography.titleLarge.copy(
                            color = theme.colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = if (queue.isEmpty()) {
                            "Nothing queued yet"
                        } else {
                            "${queue.size} waiting · the host starts each one"
                        },
                        style = theme.typography.bodySmall.copy(color = theme.colors.textSecondary)
                    )
                }
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close queue",
                    tint = theme.colors.textSecondary,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { onDismiss() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.colors.surface.copy(alpha = 0.6f))
                        .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Add a YouTube link or an .m3u8/.mp4 stream",
                        style = theme.typography.bodySmall.copy(color = theme.colors.textSecondary),
                        fontSize = 12.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(queue, key = { _, item -> item.id }) { index, item ->
                        QueueRow(
                            item = item,
                            position = index + 1,
                            isHost = isHost,
                            hasVoted = item.hasVoted(currentUserId),
                            isOwnItem = item.addedByUserId == currentUserId,
                            onVoteSkip = { onVoteSkip(item.id) },
                            onAdvance = onAdvance,
                            onRemove = { onRemove(item.id) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!showAddField) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.colors.surfaceElevated)
                        .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(12.dp))
                        .clickable { showAddField = true },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = theme.colors.primaryAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add something to watch",
                        style = theme.typography.bodyMedium.copy(
                            color = theme.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QueueTextField(
                        value = titleText,
                        placeholder = "Title (optional)",
                        onChange = { titleText = it }
                    )
                    QueueTextField(
                        value = linkText,
                        placeholder = "YouTube or .mp4 / .m3u8 link",
                        onChange = { linkText = it }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(10.dp))
                                .clickable {
                                    showAddField = false
                                    linkText = ""
                                    titleText = ""
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Cancel",
                                style = theme.typography.bodyMedium.copy(color = theme.colors.textSecondary)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (linkText.isBlank()) {
                                        theme.colors.primaryAccent.copy(alpha = 0.4f)
                                    } else {
                                        theme.colors.primaryAccent
                                    }
                                )
                                .clickable(enabled = linkText.isNotBlank()) {
                                    val url = linkText.trim()
                                    val title = titleText.trim().ifBlank {
                                        url.substringAfterLast('/').ifBlank { "Queued video" }
                                    }
                                    onAddToQueue(title, url)
                                    showAddField = false
                                    linkText = ""
                                    titleText = ""
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Add",
                                style = theme.typography.bodyMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }
            }
        }
    })
}

@Composable
private fun QueueRow(
    item: RoomQueueItem,
    position: Int,
    isHost: Boolean,
    hasVoted: Boolean,
    isOwnItem: Boolean,
    onVoteSkip: () -> Unit,
    onAdvance: () -> Unit,
    onRemove: () -> Unit
) {
    val theme = Theme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.colors.surface.copy(alpha = 0.65f))
            .border(1.dp, theme.colors.surfaceBorder.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(theme.colors.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$position",
                style = theme.typography.caption.copy(
                    color = theme.colors.textSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = theme.typography.bodyMedium.copy(
                    color = theme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Queued by ${item.addedByName}",
                style = theme.typography.caption.copy(color = theme.colors.textSecondary),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isHost) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(theme.colors.primaryAccent)
                    .clickable { onAdvance() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play this now",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        // A vote is the only way to drop someone else's item, and it shows its own
        // progress so nobody has to guess whether it counted.
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (hasVoted) theme.colors.primaryAccent.copy(alpha = 0.22f)
                    else theme.colors.surfaceElevated
                )
                .clickable { if (isHost || !isOwnItem || !hasVoted) onVoteSkip() else onRemove() }
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                text = when {
                    isOwnItem && !isHost -> "Remove"
                    else -> "Skip ${item.skipVoteCount}/${item.skipVotesRequired}"
                },
                style = theme.typography.caption.copy(
                    color = if (hasVoted) theme.colors.primaryAccent else theme.colors.textSecondary,
                    fontWeight = FontWeight.SemiBold
                ),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun QueueTextField(value: String, placeholder: String, onChange: (String) -> Unit) {
    val theme = Theme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(theme.colors.surface)
            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = theme.typography.bodySmall.copy(color = theme.colors.textSecondary),
                fontSize = 12.sp
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = theme.typography.bodyMedium.copy(color = theme.colors.textPrimary),
            cursorBrush = SolidColor(theme.colors.primaryAccent),
            modifier = Modifier.fillMaxSize().padding(top = 12.dp)
        )
    }
}
