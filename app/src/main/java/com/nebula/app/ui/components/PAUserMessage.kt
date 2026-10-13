package com.nebula.app.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.domain.model.AttachmentType
import com.nebula.app.domain.model.ChatMessage
import com.nebula.app.theme.Theme

/**
 * PAUserMessage: Snapchat-inspired lightweight right-aligned bubble.
 */
@Composable
fun PAUserMessage(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current

    // Bubble shape with distinct slightly smaller bottom-end corner
    val bubbleShape = RoundedCornerShape(
        topStart = theme.radii.chatBubble,
        topEnd = theme.radii.chatBubble,
        bottomStart = theme.radii.chatBubble,
        bottomEnd = theme.radii.userBubbleSmallCorner
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = theme.spacing.screenHorizontal, vertical = 3.dp),
        horizontalAlignment = Alignment.End
    ) {
        // Attachment previews if present
        if (message.attachments.isNotEmpty()) {
            message.attachments.forEach { attachment ->
                Row(
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .clip(RoundedCornerShape(theme.radii.md))
                        .background(theme.colors.surfaceElevated)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = if (attachment.type == AttachmentType.IMAGE) {
                        Icons.Default.Image
                    } else {
                        Icons.Default.Description
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = theme.colors.primaryAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = attachment.name,
                        style = theme.typography.caption.copy(color = theme.colors.textPrimary)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "• ${attachment.sizeLabel}",
                        style = theme.typography.caption.copy(color = theme.colors.textTertiary)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(bubbleShape)
                .background(theme.colors.userBubbleBackground)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = message.content,
                style = theme.typography.bodyLarge.copy(
                    color = theme.colors.userBubbleText,
                    lineHeight = 21.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = message.timeLabel,
            style = theme.typography.caption.copy(
                color = theme.colors.textTertiary,
                fontSize = 10.sp
            ),
            modifier = Modifier.padding(end = 4.dp)
        )
    }
}
