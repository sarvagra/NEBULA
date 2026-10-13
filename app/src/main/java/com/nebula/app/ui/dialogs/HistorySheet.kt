package com.nebula.app.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nebula.app.domain.model.Conversation
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.PAButton
import com.nebula.app.ui.components.PAConversationList
import com.nebula.app.ui.components.PABottomSheet

/**
 * HistorySheet:
 * Bottom sheet displaying full conversation history grouped by Today, Yesterday, Previous 7 days, and Older.
 */
@Composable
fun HistorySheet(
    isVisible: Boolean,
    conversations: List<Conversation>,
    activeConversationId: String?,
    onSelectConversation: (Conversation) -> Unit,
    onDeleteConversation: (Conversation) -> Unit,
    onNewConversation: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = Theme.current

    PABottomSheet(
        isVisible = isVisible,
        onDismissRequest = onDismiss,
        title = "Conversation History"
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // New Conversation Action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = theme.spacing.screenHorizontal),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "All chats saved on-device",
                    style = theme.typography.bodySmall.copy(color = theme.colors.textSecondary)
                )

                PAButton(
                    text = "New Chat",
                    icon = Icons.Default.Add,
                    height = 36.dp,
                    onClick = {
                        onNewConversation()
                        onDismiss()
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            PAConversationList(
                conversations = conversations,
                selectedId = activeConversationId,
                onSelectConversation = { conv ->
                    onSelectConversation(conv)
                    onDismiss()
                },
                onDeleteConversation = onDeleteConversation
            )
        }
    }
}
