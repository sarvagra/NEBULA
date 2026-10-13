package com.nebula.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.theme.AppThemeDefinition
import com.nebula.app.theme.Theme

/**
 * PAThemeSelector:
 * Clean interactive selector for the Four Theme System (Light, Dark, Lavender, Red-Hot-Pink),
 * showcasing visual color swatches, active indicator, and Free vs Premium indicators.
 */
@Composable
fun PAThemeSelector(
    availableThemes: List<AppThemeDefinition>,
    activeTheme: AppThemeDefinition,
    isUserPremium: Boolean,
    onSelectTheme: (AppThemeDefinition) -> Unit,
    onUpgradeRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTheme = Theme.current

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = currentTheme.spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(availableThemes) { themeItem ->
            val isSelected = themeItem.id == activeTheme.id
            val isLocked = themeItem.isPremium && !isUserPremium

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(currentTheme.radii.lg))
                    .background(
                        if (isSelected) {
                            currentTheme.colors.primaryAccent.copy(alpha = 0.08f)
                        } else {
                            currentTheme.colors.surfaceElevated
                        }
                    )
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) currentTheme.colors.primaryAccent else currentTheme.colors.surfaceBorder,
                        shape = RoundedCornerShape(currentTheme.radii.lg)
                    )
                    .clickable {
                        if (isLocked) {
                            onUpgradeRequested()
                        } else {
                            onSelectTheme(themeItem)
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Preview Palette Swatch
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(currentTheme.radii.sm))
                            .background(themeItem.colors.background)
                            .border(1.dp, currentTheme.colors.surfaceBorder, RoundedCornerShape(currentTheme.radii.sm))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(themeItem.colors.surface)
                        )
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(themeItem.colors.primaryAccent)
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = themeItem.name,
                                style = currentTheme.typography.titleMedium.copy(
                                    color = currentTheme.colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            if (themeItem.isPremium) {
                                PABadge(
                                    text = "PREMIUM",
                                    isPrimary = true
                                )
                            } else {
                                PABadge(
                                    text = "FREE"
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = themeItem.description,
                            style = currentTheme.typography.caption.copy(
                                color = currentTheme.colors.textSecondary
                            ),
                            maxLines = 1
                        )
                    }
                }

                // Status indicator: Checkmark or Lock
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isSelected -> currentTheme.colors.primaryAccent
                                isLocked -> currentTheme.colors.surface
                                else -> currentTheme.colors.surface
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) currentTheme.colors.primaryAccent else currentTheme.colors.surfaceBorder,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isSelected -> {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Active",
                                tint = currentTheme.colors.onPrimaryAccent,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        isLocked -> {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked",
                                tint = currentTheme.colors.textTertiary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
