package com.nebula.app.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.nebula.app.domain.model.NebulaNotification
import com.nebula.app.domain.model.NotificationType
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.PABottomSheet
import kotlinx.coroutines.launch

/**
 * NotificationsSheet:
 * Dedicated notification feed replacing the old History banner.
 * Displays:
 * - New friend requests
 * - Live room invitations & starting alerts
 * - System notices & announcements
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotificationsSheet(
    isVisible: Boolean,
    notifications: List<NebulaNotification>,
    onAcceptNotification: (NebulaNotification) -> Unit,
    onDismissNotification: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = Theme.current

    PABottomSheet(
        isVisible = isVisible,
        onDismissRequest = onDismiss,
        title = "Notifications"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (notifications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "You're all caught up!",
                        style = theme.typography.bodyMedium.copy(color = theme.colors.textSecondary)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(notifications, key = { it.id }) { notif ->
                        TelegramSwipeableNotificationItem(
                            notif = notif,
                            onAccept = { onAcceptNotification(notif) },
                            onDismiss = { onDismissNotification(notif.id) },
                            modifier = Modifier.animateItemPlacement()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TelegramSwipeableNotificationItem(
    notif: NebulaNotification,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val swipeThreshold = 90f * density
    val targetDistance = 600f * density

    val offsetX = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    val currentOffset = offsetX.value
    val absOffset = kotlin.math.abs(currentOffset)
    // Telegram-like dissolve fade: as card slides, alpha smoothly dissolves
    val dissolveAlpha = (1f - (absOffset / (300f * density))).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(notif.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        coroutineScope.launch {
                            val releasedOffset = offsetX.value
                            if (kotlin.math.abs(releasedOffset) > swipeThreshold) {
                                // Swiped past threshold: dissolve off screen & dismiss synchronously
                                val targetX = if (releasedOffset > 0) targetDistance else -targetDistance
                                offsetX.animateTo(
                                    targetValue = targetX,
                                    animationSpec = androidx.compose.animation.core.tween(180)
                                )
                                onDismiss()
                            } else {
                                // Snap back with gentle spring
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = androidx.compose.animation.core.spring(
                                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount)
                        }
                    }
                )
            }
            .graphicsLayer {
                translationX = currentOffset
                alpha = dissolveAlpha
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(theme.colors.surface)
                .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon indicator
            val (iconVector, iconTint) = when (notif.type) {
                NotificationType.FRIEND_REQUEST -> Icons.Default.PersonAdd to theme.colors.primaryAccent
                NotificationType.DIRECT_MESSAGE -> Icons.AutoMirrored.Filled.Send to theme.colors.primaryAccent
                NotificationType.ROOM_INVITE, NotificationType.ROOM_STARTED -> Icons.Default.Tv to Color(0xFF38BDF8)
                NotificationType.NOTICE -> Icons.Default.Info to theme.colors.successColor
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Notification content
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notif.title,
                        style = theme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = theme.colors.textPrimary,
                            fontSize = 14.sp
                        )
                    )
                    Text(
                        text = notif.timeLabel,
                        style = theme.typography.caption.copy(
                            color = theme.colors.textSecondary,
                            fontSize = 11.sp
                        )
                    )
                }

                Text(
                    text = notif.subtitle,
                    style = theme.typography.bodySmall.copy(
                        color = theme.colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                )

                // Action button if available (Dismiss button removed, user slides to dismiss!)
                if (notif.actionLabel != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onAccept,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.colors.primaryAccent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = notif.actionLabel,
                            style = theme.typography.caption.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
