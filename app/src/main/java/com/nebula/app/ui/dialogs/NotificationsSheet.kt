package com.nebula.app.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.rememberSwipeableState
import androidx.compose.material.swipeable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.domain.model.NebulaNotification
import com.nebula.app.domain.model.NotificationType
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.NebulaAvatarImage
import com.nebula.app.ui.components.PABottomSheet
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * NotificationsSheet:
 * Modern, clean notification inbox for Nebula.
 * Displays:
 * - Friend requests with Accept / Dismiss
 * - Live room invites and notifications
 * - Direct message alerts
 * - Clear all notifications action & quick settings link
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun NotificationsSheet(
    isVisible: Boolean,
    notifications: List<NebulaNotification>,
    onAcceptNotification: (NebulaNotification) -> Unit,
    onDismissNotification: (String) -> Unit,
    onClearAll: (() -> Unit)? = null,
    onOpenUserProfile: ((String) -> Unit)? = null,
    onOpenPreferences: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val theme = Theme.current

    PABottomSheet(
        isVisible = isVisible,
        onDismissRequest = onDismiss,
        title = "Notifications",
        heightFraction = 0.88f
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header bar: Count / Clear All / Settings action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (notifications.isEmpty()) "INBOX" else "${notifications.size} NOTIFICATION${if (notifications.size > 1) "S" else ""}",
                    style = theme.typography.caption.copy(
                        color = theme.colors.textSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        fontSize = 11.sp
                    )
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (notifications.isNotEmpty() && onClearAll != null) {
                        TextButton(
                            onClick = onClearAll,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear all",
                                tint = theme.colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Clear All",
                                style = theme.typography.caption.copy(
                                    color = theme.colors.textSecondary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }

                    if (onOpenPreferences != null) {
                        IconButton(
                            onClick = onOpenPreferences,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Notification Settings",
                                tint = theme.colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            if (notifications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(theme.colors.surface)
                                .border(1.dp, theme.colors.surfaceBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsNone,
                                contentDescription = null,
                                tint = theme.colors.textTertiary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = "You're all caught up!",
                            style = theme.typography.bodyMedium.copy(
                                color = theme.colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        )
                        Text(
                            text = "New friend requests and room invites will appear here.",
                            style = theme.typography.caption.copy(
                                color = theme.colors.textSecondary,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notifications, key = { it.id }) { notif ->
                        NotificationCard(
                            notif = notif,
                            onAccept = { onAcceptNotification(notif) },
                            onDismiss = { onDismissNotification(notif.id) },
                            onOpenUserProfile = onOpenUserProfile
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun NotificationCard(
    notif: NebulaNotification,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    onOpenUserProfile: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val swipePx = with(density) { 100.dp.toPx() }

    val swipeableState = rememberSwipeableState(initialValue = 0)
    val anchors = mapOf(0f to 0, -swipePx to 1)

    LaunchedEffect(swipeableState.currentValue) {
        if (swipeableState.currentValue == 1) {
            onDismiss()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(theme.colors.errorColor.copy(alpha = 0.2f))
    ) {
        // Red background indicator for swipe dismiss
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(end = 20.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Delete",
                tint = theme.colors.errorColor,
                modifier = Modifier.size(20.dp)
            )
        }

        // Foreground Card
        Row(
            modifier = Modifier
                .offset { IntOffset(swipeableState.offset.value.roundToInt(), 0) }
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(theme.colors.surface)
                .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
                .swipeable(
                    state = swipeableState,
                    anchors = anchors,
                    thresholds = { _, _ -> FractionalThreshold(0.4f) },
                    orientation = Orientation.Horizontal
                )
                .clickable {
                    if (notif.actorId != null && onOpenUserProfile != null) {
                        onOpenUserProfile(notif.actorId)
                    }
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar or Type Icon
            if (notif.actorAvatarUrl != null || notif.actorAvatarLetter.isNotBlank()) {
                NebulaAvatarImage(
                    avatarUrl = notif.actorAvatarUrl,
                    fallbackLetter = notif.actorAvatarLetter,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(theme.colors.surfaceElevated)
                        .border(1.dp, theme.colors.surfaceBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (notif.type) {
                            NotificationType.FRIEND_REQUEST -> Icons.Default.PersonAdd
                            NotificationType.DIRECT_MESSAGE -> Icons.AutoMirrored.Filled.Send
                            NotificationType.ROOM_INVITE, NotificationType.ROOM_STARTED -> Icons.Default.Tv
                            NotificationType.NOTICE -> Icons.Default.Info
                        },
                        contentDescription = null,
                        tint = theme.colors.primaryAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Body
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = notif.title,
                    style = theme.typography.bodyMedium.copy(
                        color = theme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (notif.subtitle.isNotBlank()) {
                    Text(
                        text = notif.subtitle,
                        style = theme.typography.caption.copy(
                            color = theme.colors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right-side actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // If this is a friend request, provide Accept button
                if (notif.type == NotificationType.FRIEND_REQUEST) {
                    Button(
                        onClick = onAccept,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.colors.primaryAccent,
                            contentColor = theme.colors.onPrimaryAccent
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Accept",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Accept",
                            style = theme.typography.caption.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                } else if (notif.type == NotificationType.ROOM_INVITE || notif.type == NotificationType.ROOM_STARTED) {
                    Button(
                        onClick = onAccept,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.colors.primaryAccent,
                            contentColor = theme.colors.onPrimaryAccent
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(
                            text = "Join",
                            style = theme.typography.caption.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }

                // Explicit dismiss "X" button
                IconButton(
                    onClick = {
                        scope.launch {
                            swipeableState.animateTo(1)
                            onDismiss()
                        }
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = theme.colors.textTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
