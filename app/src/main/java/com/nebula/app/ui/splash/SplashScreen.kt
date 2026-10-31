package com.nebula.app.ui.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.NebulaLogo
import kotlinx.coroutines.delay

/**
 * SplashScreen:
 * Polished native splash screen with branded monogram,
 * subtle scale/fade animation, short duration, and smooth transition into Home.
 */
@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    var isAnimationStarted by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isAnimationStarted) 1f else 0.85f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "splashScale"
    )

    val ringProgress by animateFloatAsState(
        targetValue = if (isAnimationStarted) 1f else 0f,
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "splashRingProgress"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isAnimationStarted) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "splashAlpha"
    )

    LaunchedEffect(Unit) {
        isAnimationStarted = true
        // Allow the animated ring completion to complete cleanly
        delay(1000)
        onSplashFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(theme.colors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Central Branded Monogram with animated completing planetary ring
            NebulaLogo(
                ringProgress = ringProgress,
                modifier = Modifier
                    .scale(scale)
                    .size(92.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "NEBULA",
                style = theme.typography.titleMedium.copy(
                    color = theme.colors.textPrimary.copy(alpha = alpha),
                    letterSpacing = 4.sp,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "shared spaces • watch together",
                style = theme.typography.caption.copy(
                    color = theme.colors.textSecondary.copy(alpha = alpha),
                    letterSpacing = 1.5.sp,
                    fontSize = 12.sp
                )
            )
        }
    }
}
