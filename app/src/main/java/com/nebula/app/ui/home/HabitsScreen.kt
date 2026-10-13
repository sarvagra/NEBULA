package com.nebula.app.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.domain.model.HabitItem
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.PABadge

/**
 * HabitsScreen:
 * Habits, Hobbies progression, and Ambient App Tracking.
 * Features AI Screen Reader Limiter (e.g. "Not more than 50 reels today"), focus streaks, and daily progression.
 */
@Composable
fun HabitsScreen(
    habits: List<HabitItem>,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = theme.spacing.screenHorizontal)
    ) {
        // Top Heading
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Habits & Guardrails",
                    style = theme.typography.titleLarge.copy(
                        color = theme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                )
                Text(
                    text = "Phone awareness • Screen limits • Routine goals",
                    style = theme.typography.caption.copy(color = theme.colors.textSecondary)
                )
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(theme.colors.surface)
                    .border(1.dp, theme.colors.surfaceBorder, CircleShape)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Habit",
                    tint = theme.colors.primaryAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Ambient Screen Limiter Card Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(theme.radii.lg))
                .background(theme.colors.surfaceElevated)
                .border(1.dp, theme.colors.primaryAccent.copy(alpha = 0.3f), RoundedCornerShape(theme.radii.lg))
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(theme.colors.primaryAccent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = theme.colors.primaryAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = "AI Screen Reader & Limiter Active",
                        style = theme.typography.bodyMedium.copy(
                            color = theme.colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Bans infinite scrolling past 50 reels. Provides floating recommendations while browsing & texting.",
                        style = theme.typography.caption.copy(
                            color = theme.colors.textSecondary,
                            lineHeight = 15.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Habits List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(habits, key = { it.id }) { habit ->
                HabitProgressionCard(habit = habit)
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun HabitProgressionCard(habit: HabitItem) {
    val theme = Theme.current
    val progressFraction = (habit.currentProgress.toFloat() / habit.maxProgress.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(targetValue = progressFraction, label = "progress")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(theme.radii.lg))
            .background(theme.colors.surface)
            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(theme.radii.lg))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = habit.title,
                    style = theme.typography.titleMedium.copy(
                        color = theme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                )
                Text(
                    text = habit.category,
                    style = theme.typography.caption.copy(color = theme.colors.textSecondary)
                )
            }

            // Streak Badge
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(theme.radii.pill))
                    .background(theme.colors.surfaceElevated)
                    .border(0.8.dp, theme.colors.surfaceBorder, RoundedCornerShape(theme.radii.pill))
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = "Streak",
                    tint = theme.colors.primaryAccent,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "${habit.streakDays}d streak",
                    style = theme.typography.caption.copy(
                        color = theme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                )
            }
        }

        Text(
            text = habit.targetDescription,
            style = theme.typography.bodySmall.copy(
                color = theme.colors.textPrimary,
                fontSize = 12.5.sp
            )
        )

        // Progress Bar
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${habit.currentProgress} / ${habit.maxProgress} ${habit.unit}",
                    style = theme.typography.caption.copy(
                        color = theme.colors.primaryAccent,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "${(progressFraction * 100).toInt()}%",
                    style = theme.typography.caption.copy(color = theme.colors.textSecondary)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(theme.radii.pill))
                    .background(theme.colors.surfaceElevated)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(6.dp)
                        .clip(RoundedCornerShape(theme.radii.pill))
                        .background(theme.colors.primaryAccent)
                )
            }
        }

        // AI Ambient Recommendation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(theme.radii.sm))
                .background(theme.colors.surfaceElevated)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = theme.colors.primaryAccent,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = habit.aiRecommendation,
                style = theme.typography.caption.copy(
                    color = theme.colors.textSecondary,
                    fontSize = 11.5.sp
                )
            )
        }
    }
}
