package com.nebula.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nebula.app.theme.Theme

/**
 * PALoadingIndicator: Subtle 3-dot pulsing assistant thinking indicator.
 */
@Composable
fun PALoadingIndicator(
    modifier: Modifier = Modifier,
    dotSize: Dp = 7.dp,
    spacing: Dp = 5.dp
) {
    val theme = Theme.current
    val infiniteTransition = rememberInfiniteTransition(label = "thinkingPulse")

    val scale1 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )

    val scale2 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 180),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )

    val scale3 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 360),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .scale(scale1)
                .background(theme.colors.primaryAccent, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(dotSize)
                .scale(scale2)
                .background(theme.colors.primaryAccent, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(dotSize)
                .scale(scale3)
                .background(theme.colors.primaryAccent, CircleShape)
        )
    }
}
