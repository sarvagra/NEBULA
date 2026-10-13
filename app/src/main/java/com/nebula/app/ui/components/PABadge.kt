package com.nebula.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.theme.Theme

@Composable
fun PABadge(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    textColor: Color? = null,
    isPrimary: Boolean = false
) {
    val theme = Theme.current
    val bg = backgroundColor ?: if (isPrimary) {
        theme.colors.primaryAccent.copy(alpha = 0.15f)
    } else {
        theme.colors.badgeBackground
    }
    val fg = textColor ?: if (isPrimary) {
        theme.colors.primaryAccent
    } else {
        theme.colors.badgeText
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(theme.radii.pill))
            .background(bg)
            .border(
                width = 0.8.dp,
                color = if (isPrimary) theme.colors.primaryAccent.copy(alpha = 0.3f) else theme.colors.surfaceBorder,
                shape = RoundedCornerShape(theme.radii.pill)
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = theme.typography.caption.copy(
                color = fg,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}
