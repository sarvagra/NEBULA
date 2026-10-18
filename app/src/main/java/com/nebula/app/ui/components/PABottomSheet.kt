package com.nebula.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nebula.app.theme.Theme

/**
 * PABottomSheet: Minimalist bottom sheet with fluid spring slide and clean drag handle.
 *
 * @param heightFraction optional fraction (0f..1f) of the window height the whole sheet occupies.
 *        When null the sheet wraps its content (default); when supplied the sheet is exactly that
 *        fraction of the window, which lets callers animate it (e.g. 0.88f -> 1f) while their own
 *        content uses `Modifier.weight(1f)` to fill the remaining space.
 */
@Composable
fun PABottomSheet(
    isVisible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    heightFraction: Float? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val theme = Theme.current
    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // System back must dismiss the sheet and leave the screen underneath untouched.
    BackHandler(enabled = isVisible) { onDismissRequest() }

    // Animate the whole sheet: backdrop fades, panel slides up on a spring. The
    // previous version snapped into place with no transition, which read as a jolt.
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(140)) +
            slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
        exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(120)) +
            slideOutVertically(
                targetOffsetY = { it },
                animationSpec = androidx.compose.animation.core.tween(160)
            )
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (heightFraction != null) Modifier.fillMaxHeight(heightFraction)
                        else Modifier
                    )
                    .imePadding()
                    .clip(RoundedCornerShape(topStart = theme.radii.xl, topEnd = theme.radii.xl))
                    .background(theme.colors.surfaceElevated)
                    .border(
                        width = 1.dp,
                        color = theme.colors.surfaceBorder,
                        shape = RoundedCornerShape(topStart = theme.radii.xl, topEnd = theme.radii.xl)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* consume */ }
                    )
                    .padding(bottom = navBottomPadding + 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Drag handle
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(theme.radii.pill))
                        .background(theme.colors.surfaceBorder)
                )

                if (title != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = title,
                        style = theme.typography.titleMedium.copy(
                            color = theme.colors.textPrimary
                        ),
                        modifier = Modifier.padding(horizontal = theme.spacing.screenHorizontal)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                content()
            }
        }
    }
}
