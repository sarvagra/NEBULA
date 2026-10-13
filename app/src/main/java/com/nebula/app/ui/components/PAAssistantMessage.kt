package com.nebula.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.domain.model.AssistantStatus
import com.nebula.app.domain.model.ChatMessage
import com.nebula.app.theme.Theme

/**
 * PAAssistantMessage: Refined left-aligned assistant bubble with states and action controls.
 */
@Composable
fun PAAssistantMessage(
    message: ChatMessage,
    onCopy: () -> Unit,
    onSaveToMemory: () -> Unit,
    onShare: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    var showActions by remember { mutableStateOf(false) }

    // Bubble shape with smaller top-start corner
    val bubbleShape = RoundedCornerShape(
        topStart = theme.radii.userBubbleSmallCorner,
        topEnd = theme.radii.chatBubble,
        bottomStart = theme.radii.chatBubble,
        bottomEnd = theme.radii.chatBubble
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = theme.spacing.screenHorizontal, vertical = 3.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 310.dp)
                .clip(bubbleShape)
                .background(theme.colors.assistantBubbleBackground)
                .border(1.dp, theme.colors.assistantBubbleBorder, bubbleShape)
                .clickable { showActions = !showActions }
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            when (message.status) {
                AssistantStatus.THINKING -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PALoadingIndicator(dotSize = 6.dp)
                        Text(
                            text = "Thinking…",
                            style = theme.typography.bodySmall.copy(color = theme.colors.textSecondary)
                        )
                    }
                }
                AssistantStatus.ERROR -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Couldn't generate response.",
                            style = theme.typography.bodySmall.copy(color = theme.colors.errorColor)
                        )
                        Box(
                            modifier = Modifier
                                .clickable(onClick = onRetry)
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry",
                                tint = theme.colors.errorColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                else -> {
                    Text(
                        text = message.content,
                        style = theme.typography.bodyLarge.copy(
                            color = theme.colors.assistantBubbleText,
                            lineHeight = 22.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        Row(
            modifier = Modifier.padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = message.timeLabel,
                style = theme.typography.caption.copy(
                    color = theme.colors.textTertiary,
                    fontSize = 10.sp
                )
            )

            // Action triggers for assistant message
            if (message.status == AssistantStatus.COMPLETED) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Copy action
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = theme.colors.textTertiary,
                        modifier = Modifier
                            .size(12.dp)
                            .clickable(onClick = onCopy)
                    )

                    // Save to memory action
                    Icon(
                        imageVector = if (message.isSavedToMemory) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Save to Memory",
                        tint = if (message.isSavedToMemory) theme.colors.primaryAccent else theme.colors.textTertiary,
                        modifier = Modifier
                            .size(13.dp)
                            .clickable(onClick = onSaveToMemory)
                    )

                    // Share action
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = theme.colors.textTertiary,
                        modifier = Modifier
                            .size(12.dp)
                            .clickable(onClick = onShare)
                    )
                }
            }
        }
    }
}
