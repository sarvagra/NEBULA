package com.nebula.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nebula.app.theme.Theme

enum class PAButtonVariant {
    PRIMARY,
    SECONDARY,
    OUTLINE,
    GHOST
}

@Composable
fun PAButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: PAButtonVariant = PAButtonVariant.PRIMARY,
    icon: ImageVector? = null,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 48.dp
) {
    val theme = Theme.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1f,
        label = "buttonScale"
    )

    val (bg, fg, border) = when (variant) {
        PAButtonVariant.PRIMARY -> Triple(
            theme.colors.primaryAccent,
            theme.colors.onPrimaryAccent,
            null
        )
        PAButtonVariant.SECONDARY -> Triple(
            theme.colors.surfaceElevated,
            theme.colors.textPrimary,
            theme.colors.surfaceBorder
        )
        PAButtonVariant.OUTLINE -> Triple(
            Color.Transparent,
            theme.colors.textPrimary,
            theme.colors.surfaceBorder
        )
        PAButtonVariant.GHOST -> Triple(
            Color.Transparent,
            theme.colors.textSecondary,
            null
        )
    }

    Box(
        modifier = modifier
            .scale(scale)
            .height(height)
            .clip(RoundedCornerShape(theme.radii.lg))
            .background(if (enabled) bg else bg.copy(alpha = 0.5f))
            .then(
                if (border != null) {
                    Modifier.border(1.dp, border, RoundedCornerShape(theme.radii.lg))
                } else Modifier
            )
            .clickable(
                enabled = enabled && !isLoading,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = fg,
                strokeWidth = 2.dp
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = fg,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = theme.typography.button.copy(color = fg)
                )
            }
        }
    }
}
