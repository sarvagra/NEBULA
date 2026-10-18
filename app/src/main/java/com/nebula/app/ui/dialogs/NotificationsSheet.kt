package com.nebula.app.ui.dialogs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.domain.model.NebulaNotification
import com.nebula.app.domain.model.NotificationType
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.NebulaAvatarImage
import com.nebula.app.ui.components.PABottomSheet
import kotlin.math.abs

/** Horizontal distance (dp) a card must travel before a swipe dismisses it. */
private const val SwipeDismissThresholdDp = 90f

/** Off-screen travel distance (dp) used by the dismiss animation. */
private const val SwipeDismissTravelDp = 600f

/** Distance (dp) over which a swiped card fully dissolves (alpha 1f -> 0f). */
private const val SwipeDissolveDistanceDp = 300f

/** Resting (collapsed) sheet height fraction of the window (N-025). */
private const val SheetCollapsedFraction = 0.88f

/** Expanded sheet height fraction (N-025). */
private const val SheetExpandedFraction = 1f

/**
 * NotificationsSheet:
 * Dedicated notification feed replacing the old History banner.
 * Displays:
 * - New friend requests
 * - Live room invitations & starting alerts
 * - System notices & announcements
 *
 * N-023: each row is swipe-to-dismiss (left or right), with no close/X affordance.
 * N-025: the sheet grows to the full window when the list is scrolled past its end and
 * returns to [SheetCollapsedFraction] of the window on a downward pull at the top.
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
    val listState = rememberLazyListState()

    // N-025: height fraction of the whole sheet, animated between 88% and 100% of the window.
    var isSheetExpanded by remember { mutableStateOf(false) }
    val sheetHeightFraction by animateFloatAsState(
        targetValue = if (isSheetExpanded) SheetExpandedFraction else SheetCollapsedFraction,
        animationSpec = tween(durationMillis = 240),
        label = "notificationsSheetHeight"
    )

    // N-025: nested-scroll bridge. The list keeps consuming its own scroll; we only observe the
    // part of the gesture it could not consume to grow/shrink the sheet.
    val expandOnScrollConnection = remember(listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Downward drag while the list is already at its top: collapse back to 88%.
                if (available.y > 0f && !listState.canScrollBackward) {
                    isSheetExpanded = false
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (available.y < 0f && !listState.canScrollForward) {
                    // Upward scroll the list could not consume (top reached / not scrollable):
                    // expand the sheet to fill the window.
                    isSheetExpanded = true
                } else if (available.y > 0f && !listState.canScrollBackward) {
                    // Downward scroll at the top of the list: restore the 88% height.
                    isSheetExpanded = false
                }
                return Offset.Zero
            }
        }
    }

    PABottomSheet(
        isVisible = isVisible,
        onDismissRequest = onDismiss,
        title = "Notifications",
        heightFraction = sheetHeightFraction
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val dayMs = 24 * 60 * 60 * 1000L
            val now = System.currentTimeMillis()
            val pendingRequests = notifications.filter {
                it.type == NotificationType.FRIEND_REQUEST && now - it.createdAtMs <= dayMs
            }
            val feed = notifications.filterNot { it in pendingRequests }
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
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .nestedScroll(expandOnScrollConnection),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (pendingRequests.isNotEmpty()) {
                        item(key = "pending-header") {
                            Text(
                                text = "REQUESTS",
                                style = theme.typography.caption.copy(
                                    color = theme.colors.textSecondary,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.1.sp,
                                    fontSize = 10.5.sp
                                ),
                                modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                            )
                        }
                        items(pendingRequests, key = { "pending-${it.id}" }) { notif ->
                            TelegramSwipeableNotificationItem(
                                notif = notif,
                                onAccept = { onAcceptNotification(notif) },
                                onDismiss = { onDismissNotification(notif.id) },
                                modifier = Modifier.animateItemPlacement()
                            )
                        }
                        if (feed.isNotEmpty()) {
                            item(key = "feed-header") {
                                Text(
                                    text = "EARLIER",
                                    style = theme.typography.caption.copy(
                                        color = theme.colors.textSecondary,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.1.sp,
                                        fontSize = 10.5.sp
                                    ),
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                                )
                            }
                        }
                    }
                    items(feed, key = { it.id }) { notif ->
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
    val density = LocalDensity.current
    val swipeThreshold = with(density) { SwipeDismissThresholdDp.dp.toPx() }
    val dismissTravel = with(density) { SwipeDismissTravelDp.dp.toPx() }
    val dissolveDistance = with(density) { SwipeDissolveDistanceDp.dp.toPx() }

    // Live drag offset. A plain snapshot state updated directly from the pointer-input callback:
    // no coroutine is launched per drag delta, so the card tracks the finger 1:1 and slow drags
    // (many tiny deltas) accumulate correctly instead of racing an animation.
    var dragOffset by remember(notif.id) { mutableFloatStateOf(0f) }

    // Non-null only while a release animation is pending: 0f = snap back,
    // +/- dismissTravel = slide off-screen and dismiss once.
    var settleTarget by remember(notif.id) { mutableStateOf<Float?>(null) }

    // A single Animatable drives the release animation, snapped to wherever the finger left the
    // card. It is never touched from onHorizontalDrag.
    val settleAnimation = remember(notif.id) { Animatable(0f) }

    LaunchedEffect(notif.id, settleTarget) {
        val target = settleTarget ?: return@LaunchedEffect
        settleAnimation.snapTo(dragOffset)
        settleAnimation.animateTo(
            targetValue = target,
            animationSpec = if (target == 0f) {
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            } else {
                tween(durationMillis = 180)
            }
        ) { dragOffset = value }

        if (target != 0f) {
            // Card is off-screen: dismiss exactly once, then rewind for the next item.
            onDismiss()
            dragOffset = 0f
        } else {
            dragOffset = 0f
        }
        settleTarget = null
    }

    val absOffset = abs(dragOffset)
    // Telegram-like dissolve: the card fades out as it is dragged away.
    val dissolveAlpha = (1f - (absOffset / dissolveDistance)).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(notif.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val releasedOffset = dragOffset
                        settleTarget = if (abs(releasedOffset) > swipeThreshold) {
                            // Both left and right swipes past the threshold dismiss this item.
                            if (releasedOffset > 0f) dismissTravel else -dismissTravel
                        } else {
                            0f
                        }
                    },
                    onDragCancel = { settleTarget = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        // Re-grabbing during a release animation cancels it (the LaunchedEffect
                        // restarts on a null target) so the finger owns the offset again.
                        if (settleTarget != null) settleTarget = null
                        dragOffset = (dragOffset + dragAmount)
                            .coerceIn(-dismissTravel, dismissTravel)
                    }
                )
            }
            .graphicsLayer {
                translationX = dragOffset
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

            if (notif.actorName != null) {
                // Who it came from matters more than the category icon.
                NebulaAvatarImage(
                    avatarUrl = notif.actorAvatarUrl,
                    fallbackLetter = notif.actorAvatarLetter,
                    modifier = Modifier.size(40.dp)
                )
            } else {
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
            }

            // Notification content. Invitation cards stay minimal: no X / close / dismiss button,
            // the only way to remove a row is the horizontal swipe handled above.
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

                // Single action button (accept) when the notification offers one.
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
