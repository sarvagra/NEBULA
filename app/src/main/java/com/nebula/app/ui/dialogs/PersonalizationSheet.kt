package com.nebula.app.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.domain.model.UserProfile
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.PABadge
import com.nebula.app.ui.components.PABottomSheet
import com.nebula.app.ui.components.PAButton

/**
 * PersonalizationSheet:
 * Custom instructions to tune Nebula's persona and context.
 * Strict char limit: 250 chars for Free users, 500 chars for Pro users (spaces included).
 */
@Composable
fun PersonalizationSheet(
    isVisible: Boolean,
    userProfile: UserProfile,
    onSaveInstructions: (String) -> Unit,
    onUpgradeRequested: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = Theme.current
    val maxChars = if (userProfile.isPremium) 500 else 250

    var text by remember(userProfile.customInstructions) {
        mutableStateOf(userProfile.customInstructions)
    }

    val currentLength = text.length
    val isOverLimit = currentLength > maxChars

    PABottomSheet(
        isVisible = isVisible,
        onDismissRequest = onDismiss,
        title = "Personalization"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = theme.spacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header explanation with Tier Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Custom Instructions",
                    style = theme.typography.titleMedium.copy(
                        color = theme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                )

                if (userProfile.isPremium) {
                    PABadge(text = "PRO 500 CHARS", isPrimary = true)
                } else {
                    PABadge(text = "FREE 250 CHARS", isPrimary = false)
                }
            }

            Text(
                text = "What would you like nabula to know about you to provide better recommendations and context? (e.g. communication style, projects, routines).",
                style = theme.typography.bodySmall.copy(
                    color = theme.colors.textSecondary,
                    lineHeight = 18.sp
                )
            )

            // Text Input Field
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(theme.radii.lg))
                    .background(theme.colors.surface)
                    .border(
                        width = 1.dp,
                        color = if (isOverLimit) theme.colors.errorColor else theme.colors.surfaceBorder,
                        shape = RoundedCornerShape(theme.radii.lg)
                    )
                    .padding(14.dp)
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { newText ->
                        // Allow typing up to max limit or strictly cap
                        if (newText.length <= maxChars) {
                            text = newText
                        }
                    },
                    textStyle = theme.typography.bodyMedium.copy(
                        color = theme.colors.textPrimary,
                        lineHeight = 22.sp
                    ),
                    cursorBrush = SolidColor(theme.colors.primaryAccent),
                    modifier = Modifier.fillMaxWidth()
                )

                if (text.isEmpty()) {
                    Text(
                        text = "e.g. I am a software engineer building native Android apps. Keep advice minimal and direct. Focus on clean architecture and habit tracking...",
                        style = theme.typography.bodyMedium.copy(
                            color = theme.colors.textTertiary,
                            lineHeight = 22.sp
                        )
                    )
                }
            }

            // Live Character Counter & Pro Upgrade Hint
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (!userProfile.isPremium) {
                    Text(
                        text = "Need more? Pro unlocks 500 characters",
                        style = theme.typography.caption.copy(color = theme.colors.primaryAccent)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                Text(
                    text = "$currentLength / $maxChars",
                    style = theme.typography.caption.copy(
                        color = if (currentLength >= maxChars) theme.colors.primaryAccent else theme.colors.textSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            PAButton(
                text = "Save Custom Instructions",
                onClick = {
                    onSaveInstructions(text)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}
