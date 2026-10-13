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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.PABadge
import com.nebula.app.ui.components.PABottomSheet
import com.nebula.app.ui.components.PAIconButton

data class MemoryFact(
    val id: String,
    val category: String,
    val content: String,
    val confidence: String,
    val icon: ImageVector
)

/**
 * MemorySheet:
 * Personal knowledge graph exposing facts, relationships, preferences, and saved items.
 */
@Composable
fun MemorySheet(
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    val theme = Theme.current

    val sampleFacts = remember {
        listOf(
            MemoryFact(
                id = "f1",
                category = "Relationship",
                content = "Rahul: Close collaborator on mobile architectures. Sync interval: weekly.",
                confidence = "High Recall",
                icon = Icons.Default.People
            ),
            MemoryFact(
                id = "f2",
                category = "Preference",
                content = "Prefers calm, dark interfaces with zero visual noise and high contrast.",
                confidence = "Permanent",
                icon = Icons.Default.Lightbulb
            ),
            MemoryFact(
                id = "f3",
                category = "Project",
                content = "Building native Android Nebula OS with Jetpack Compose.",
                confidence = "Active Loop",
                icon = Icons.Default.Bookmark
            ),
            MemoryFact(
                id = "f4",
                category = "Security",
                content = "Zero cloud telemetry rule enforced. Local device SQLite vault active.",
                confidence = "System Rule",
                icon = Icons.Default.Lightbulb
            )
        )
    }

    PABottomSheet(
        isVisible = isVisible,
        onDismissRequest = onDismiss,
        title = "Memory & Knowledge Graph"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = theme.spacing.screenHorizontal)
        ) {
            Text(
                text = "Facts and relationships inferred from conversations. You have complete control to inspect or revoke any memory.",
                style = theme.typography.bodySmall.copy(color = theme.colors.textSecondary)
            )

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(sampleFacts) { fact ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(theme.radii.lg))
                            .background(theme.colors.surface)
                            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(theme.radii.lg))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(theme.radii.sm))
                                    .background(theme.colors.surfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = fact.icon,
                                    contentDescription = null,
                                    tint = theme.colors.primaryAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = fact.category,
                                        style = theme.typography.caption.copy(
                                            color = theme.colors.primaryAccent,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                    PABadge(text = fact.confidence)
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = fact.content,
                                    style = theme.typography.bodySmall.copy(
                                        color = theme.colors.textPrimary
                                    )
                                )
                            }
                        }

                        PAIconButton(
                            icon = Icons.Default.DeleteOutline,
                            contentDescription = "Delete memory",
                            onClick = { /* delete fact */ },
                            size = 32.dp,
                            iconSize = 16.dp,
                            tint = theme.colors.textTertiary
                        )
                    }
                }
            }
        }
    }
}
