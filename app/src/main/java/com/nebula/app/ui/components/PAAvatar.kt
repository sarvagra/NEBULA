package com.nebula.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nebula.app.domain.model.UserProfile
import com.nebula.app.theme.Theme

/**
 * PAAvatar: Reusable avatar component respecting Theme design tokens,
 * supporting image URI, letter fallback, and premium badge indicator.
 */
@Composable
fun PAAvatar(
    profile: UserProfile,
    size: Dp = 44.dp,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    val isPremium = profile.isPremium

    val borderModifier = if (isPremium) {
        Modifier.border(
            width = 2.dp,
            brush = Brush.sweepGradient(
                listOf(
                    theme.colors.primaryAccent,
                    theme.colors.secondaryAccent,
                    theme.colors.primaryAccent
                )
            ),
            shape = CircleShape
        )
    } else {
        Modifier.border(
            width = 1.dp,
            color = theme.colors.surfaceBorder,
            shape = CircleShape
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .then(borderModifier)
                .clip(CircleShape)
                .background(theme.colors.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            if (!profile.avatarUri.isNullOrEmpty()) {
                AsyncImage(
                    model = profile.avatarUri,
                    contentDescription = profile.displayName,
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = profile.avatarFallbackLetter,
                    style = theme.typography.titleMedium.copy(
                        color = if (isPremium) theme.colors.primaryAccent else theme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = (size.value * 0.42f).sp
                    )
                )
            }
        }

        // Small corner badge for premium status
        if (isPremium) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .clip(CircleShape)
                    .background(theme.colors.primaryAccent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Premium",
                    tint = theme.colors.onPrimaryAccent,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}
