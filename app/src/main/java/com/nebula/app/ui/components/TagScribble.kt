package com.nebula.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.domain.model.NebulaTagCount
import com.nebula.app.theme.Theme

/**
 * The profile's top-content scribble.
 *
 * Every tag the person has room history in is drawn as a word whose size is its
 * share of that history: the tag they spend the most time in is the biggest word,
 * the least is the smallest. Zero-participation tags are filtered out before they
 * get here, so the cloud can never show a tag that means nothing.
 *
 * Rows are built by hand rather than with an experimental flow layout: the whole
 * point is the ragged, hand-placed look, so each row is deliberately nudged and
 * tilted a little instead of sitting on a strict grid.
 */
@Composable
fun TagScribble(
    tags: List<NebulaTagCount>,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    if (tags.isEmpty()) return

    // Most frequent first; cap the cloud so a long history stays readable.
    val ordered = tags
        .filter { it.tag.isNotBlank() && it.count > 0 }
        .sortedByDescending { it.count }
        .take(6)

    val maxCount = ordered.maxOf { it.count }.toFloat()
    val minCount = ordered.minOf { it.count }.toFloat()
    val span = (maxCount - minCount).coerceAtLeast(1f)

    fun sizeFor(count: Int): Float {
        val weight = ((count - minCount) / span).coerceIn(0f, 1f)
        return 13f + weight * 15f
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ordered.chunked(3).forEachIndexed { rowIndex, rowTags ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Alternate the lean of each row so the block reads as doodled.
                Spacer(modifier = Modifier.height(0.dp).padding(start = if (rowIndex % 2 == 1) 10.dp else 0.dp))
                rowTags.forEachIndexed { columnIndex, entry ->
                    val tilt = when {
                        (rowIndex + columnIndex) % 3 == 0 -> -3f
                        (rowIndex + columnIndex) % 3 == 1 -> 2f
                        else -> -1f
                    }
                    Text(
                        text = entry.tag,
                        style = theme.typography.titleMedium.copy(
                            color = if (entry.count == ordered.first().count) theme.colors.primaryAccent else theme.colors.textPrimary,
                            fontWeight = if (entry.count == ordered.first().count) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = sizeFor(entry.count).sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .rotate(tilt)
                            .alpha(0.75f + 0.25f * ((entry.count - minCount) / span))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Based on the rooms you join and host",
            style = theme.typography.caption.copy(color = theme.colors.textTertiary, fontSize = 10.sp)
        )
    }
}

/**
 * Compact numeric badge used by the profile headers and counters.
 * 999 stays "999"; 1_200 becomes "1.2K"; 3_400_000 becomes "3.4M".
 */
@Composable
fun ActivityPill(value: Int, label: String, modifier: Modifier = Modifier) {
    val theme = Theme.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(theme.colors.surfaceElevated)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = compactCount(value),
            style = theme.typography.bodyMedium.copy(
                color = theme.colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        )
        Text(
            text = label,
            style = theme.typography.caption.copy(color = theme.colors.textSecondary, fontSize = 11.sp)
        )
    }
}
