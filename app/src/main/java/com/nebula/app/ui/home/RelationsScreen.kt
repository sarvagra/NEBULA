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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.domain.model.PersonRelation
import com.nebula.app.domain.model.RelationTier
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.PABadge
import com.nebula.app.ui.components.PAButton

/**
 * RelationsScreen:
 * Relationship Intelligence & People Management.
 * Filter by High, Low, Non-related, track interaction frequency, and proactive prompts.
 */
@Composable
fun RelationsScreen(
    relations: List<PersonRelation>,
    onDraftMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    var selectedFilter by remember { mutableStateOf<RelationTier?>(null) }

    val filteredRelations = remember(relations, selectedFilter) {
        if (selectedFilter == null) relations else relations.filter { it.relationTier == selectedFilter }
    }

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
                    text = "Relationship Intel",
                    style = theme.typography.titleLarge.copy(
                        color = theme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                )
                Text(
                    text = "Cadence tracking & social memory",
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
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = "Add Contact",
                    tint = theme.colors.primaryAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Tier Filter Chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterPill(
                    label = "All (${relations.size})",
                    isSelected = selectedFilter == null,
                    onClick = { selectedFilter = null }
                )
            }
            items(RelationTier.entries) { tier ->
                val count = relations.count { it.relationTier == tier }
                FilterPill(
                    label = "${tier.label} ($count)",
                    isSelected = selectedFilter == tier,
                    onClick = { selectedFilter = tier }
                )
            }
        }

        // People List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredRelations, key = { it.id }) { person ->
                PersonRelationCard(
                    person = person,
                    onDraftMessage = {
                        onDraftMessage("Draft message for ${person.name} regarding: ${person.commonTopics.joinToString(", ")}")
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val theme = Theme.current

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(theme.radii.pill))
            .background(if (isSelected) theme.colors.primaryAccent else theme.colors.surface)
            .border(
                width = 1.dp,
                color = if (isSelected) theme.colors.primaryAccent else theme.colors.surfaceBorder,
                shape = RoundedCornerShape(theme.radii.pill)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = theme.typography.caption.copy(
                color = if (isSelected) theme.colors.onPrimaryAccent else theme.colors.textPrimary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        )
    }
}

@Composable
private fun PersonRelationCard(
    person: PersonRelation,
    onDraftMessage: () -> Unit
) {
    val theme = Theme.current
    val tierColor = Color(person.relationTier.colorHex)

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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Avatar with initial
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(theme.colors.surfaceElevated)
                        .border(1.dp, theme.colors.surfaceBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = person.avatarLetters,
                        style = theme.typography.titleMedium.copy(
                            color = theme.colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Column {
                    Text(
                        text = person.name,
                        style = theme.typography.bodyLarge.copy(
                            color = theme.colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    )
                    Text(
                        text = person.roleOrTag,
                        style = theme.typography.caption.copy(color = theme.colors.textSecondary)
                    )
                }
            }

            // Relationship Strength Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(theme.radii.pill))
                    .background(tierColor.copy(alpha = 0.14f))
                    .border(0.8.dp, tierColor.copy(alpha = 0.4f), RoundedCornerShape(theme.radii.pill))
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${person.relationTier.label} Strength",
                    style = theme.typography.caption.copy(
                        color = tierColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                )
            }
        }

        // Cadence & Last talk
        Text(
            text = "Last interacted: ${person.lastContactDaysAgo} days ago • ${person.cadenceDescription}",
            style = theme.typography.caption.copy(color = theme.colors.textSecondary)
        )

        // Common topics chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            person.commonTopics.forEach { topic ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(theme.radii.xs))
                        .background(theme.colors.surfaceElevated)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = topic,
                        style = theme.typography.caption.copy(
                            color = theme.colors.textTertiary,
                            fontSize = 10.5.sp
                        )
                    )
                }
            }
        }

        // Action banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(theme.radii.md))
                .background(theme.colors.surfaceElevated)
                .clickable(onClick = onDraftMessage)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = person.nextRecommendedAction,
                style = theme.typography.caption.copy(
                    color = theme.colors.textPrimary,
                    fontSize = 11.5.sp
                ),
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = Icons.Default.ChatBubbleOutline,
                contentDescription = "Draft",
                tint = theme.colors.primaryAccent,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
