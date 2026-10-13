package com.nebula.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.domain.model.ProactiveSuggestion
import com.nebula.app.theme.Theme

/**
 * SuggestionsScreen:
 * Context-aware proactive recommendations.
 * Ambient recommendations from screen readers, relationship cadences, and daily planner.
 */
@Composable
fun SuggestionsScreen(
    suggestions: List<ProactiveSuggestion>,
    onActOnSuggestion: (String) -> Unit,
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
                    text = "Proactive Feed",
                    style = theme.typography.titleLarge.copy(
                        color = theme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                )
                Text(
                    text = "Contextual recommendations based on habits & contacts",
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
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Active Intelligence",
                    tint = theme.colors.primaryAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Suggestions List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(suggestions, key = { it.id }) { item ->
                SuggestionCard(
                    suggestion = item,
                    onClick = { onActOnSuggestion(item.actionPrompt) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestion: ProactiveSuggestion,
    onClick: () -> Unit
) {
    val theme = Theme.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(theme.radii.lg))
            .background(theme.colors.surface)
            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(theme.radii.lg))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = suggestion.title,
                style = theme.typography.titleMedium.copy(
                    color = theme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(theme.radii.pill))
                    .background(theme.colors.primaryAccent.copy(alpha = 0.12f))
                    .border(0.8.dp, theme.colors.primaryAccent.copy(alpha = 0.35f), RoundedCornerShape(theme.radii.pill))
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                Text(
                    text = suggestion.tag,
                    style = theme.typography.caption.copy(
                        color = theme.colors.primaryAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                )
            }
        }

        Text(
            text = suggestion.description,
            style = theme.typography.bodySmall.copy(
                color = theme.colors.textSecondary,
                lineHeight = 18.sp
            )
        )

        // One-tap action prompt pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(theme.radii.md))
                .background(theme.colors.surfaceElevated)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = suggestion.actionPrompt,
                style = theme.typography.caption.copy(
                    color = theme.colors.textPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                ),
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "Send",
                tint = theme.colors.primaryAccent,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}
