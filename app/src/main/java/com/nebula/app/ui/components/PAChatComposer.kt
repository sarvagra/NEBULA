package com.nebula.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.domain.model.AttachmentType
import com.nebula.app.domain.model.ChatAttachment
import com.nebula.app.theme.Theme

/**
 * PAChatComposer:
 * The dominant, minimal, IME-aware assistant input.
 * Supports compact attachment chips, voice toggle, and clean send action.
 */
@Composable
fun PAChatComposer(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachmentClick: () -> Unit,
    onVoiceClick: () -> Unit,
    attachments: List<ChatAttachment> = emptyList(),
    onRemoveAttachment: ((ChatAttachment) -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    placeholder: String = "Ask anything…"
) {
    val theme = Theme.current
    val hasContent = text.trim().isNotEmpty() || attachments.isNotEmpty()

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Active Attachment Chips Row
        if (attachments.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(attachments) { attachment ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(theme.radii.pill))
                            .background(theme.colors.surfaceElevated)
                            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(theme.radii.pill))
                            .padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
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
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = attachment.name,
                            style = theme.typography.caption.copy(color = theme.colors.textPrimary),
                            maxLines = 1
                        )
                        if (onRemoveAttachment != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(theme.colors.surfaceBorder)
                                    .clickable { onRemoveAttachment(attachment) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = theme.colors.textSecondary,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Primary Input Pill Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(theme.radii.pill))
                .background(theme.colors.chatInputBackground)
                .border(
                    width = 1.dp,
                    color = theme.colors.chatInputBorder,
                    shape = RoundedCornerShape(theme.radii.pill)
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Attachment Button
            PAIconButton(
                icon = Icons.Default.Add,
                contentDescription = "Add attachment",
                onClick = onAttachmentClick,
                size = 38.dp,
                iconSize = 20.dp,
                tint = theme.colors.textSecondary
            )

            // Middle Text Input
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = theme.typography.chatInput.copy(
                            color = theme.colors.chatInputPlaceholder
                        )
                    )
                }

                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = theme.typography.chatInput.copy(
                        color = theme.colors.textPrimary,
                        lineHeight = 20.sp
                    ),
                    cursorBrush = SolidColor(theme.colors.primaryAccent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 24.dp, max = 96.dp)
                        .onFocusChanged { focusState ->
                            onFocusChanged?.invoke(focusState.isFocused)
                        }
                )
            }

            // Right Button: Voice or Send Action
            if (hasContent) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(theme.colors.primaryAccent)
                        .clickable(onClick = onSend),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = theme.colors.onPrimaryAccent,
                        modifier = Modifier.size(17.dp)
                    )
                }
            } else {
                PAIconButton(
                    icon = Icons.Default.Mic,
                    contentDescription = "Voice input",
                    onClick = onVoiceClick,
                    size = 38.dp,
                    iconSize = 19.dp,
                    tint = theme.colors.textSecondary
                )
            }
        }
    }
}
