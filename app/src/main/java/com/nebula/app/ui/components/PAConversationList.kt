package com.nebula.app.ui.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.domain.model.Conversation
import com.nebula.app.domain.model.HistoryGroup
import com.nebula.app.theme.Theme

/**
 * PAConversationList:
 * Displays conversation history grouped by Today, Yesterday, Previous 7 days, Older.
 */
@Composable
fun PAConversationList(
    conversations: List<Conversation>,
    selectedId: String?,
    onSelectConversation: (Conversation) -> Unit,
    onDeleteConversation: (Conversation) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current

    val grouped = conversations.groupBy { it.groupCategory }
    val orderedGroups = listOf(
        HistoryGroup.TODAY,
        HistoryGroup.YESTERDAY,
        HistoryGroup.PREVIOUS_7_DAYS,
        HistoryGroup.OLDER
    )

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = theme.spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        orderedGroups.forEach { group ->
            val list = grouped[group] ?: emptyList()
            if (list.isNotEmpty()) {
                item {
                    PASectionHeader(title = group.label.uppercase())
                }

                items(list) { conv ->
                    val isSelected = conv.id == selectedId

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(theme.radii.md))
                            .background(
                                if (isSelected) {
                                    theme.colors.primaryAccent.copy(alpha = 0.1f)
                                } else {
                                    theme.colors.surfaceElevated
                                }
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) theme.colors.primaryAccent else theme.colors.surfaceBorder,
                                shape = RoundedCornerShape(theme.radii.md)
                            )
                            .clickable { onSelectConversation(conv) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(theme.radii.sm))
                                    .background(theme.colors.surface),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = theme.colors.primaryAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = conv.title,
                                    style = theme.typography.bodyLarge.copy(
                                        color = theme.colors.textPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    ),
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = conv.lastMessagePreview,
                                    style = theme.typography.caption.copy(
                                        color = theme.colors.textSecondary
                                    ),
                                    maxLines = 1
                                )
                            }
                        }

                        PAIconButton(
                            icon = Icons.Default.DeleteOutline,
                            contentDescription = "Delete conversation",
                            onClick = { onDeleteConversation(conv) },
                            size = 32.dp,
                            iconSize = 16.dp,
                            tint = theme.colors.textTertiary
                        )
                    }
                }
            }
        }
    }
}
