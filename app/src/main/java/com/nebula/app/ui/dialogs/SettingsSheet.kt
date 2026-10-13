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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.PABottomSheet

/**
 * SettingsSheet:
 * Assistant intelligence parameters, voice tone, and local model acceleration.
 */
@Composable
fun SettingsSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    val theme = Theme.current

    var proactiveSuggestions by remember { mutableStateOf(true) }
    var highPrecisionMemory by remember { mutableStateOf(true) }
    var edgeAcceleration by remember { mutableStateOf(false) }

    PABottomSheet(
        isVisible = isVisible,
        onDismissRequest = onDismiss,
        title = "Assistant Settings"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = theme.spacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingToggleRow(
                icon = Icons.Default.AutoAwesome,
                title = "Proactive Intelligence",
                subtitle = "Generate smart prompt suggestions from recent interactions",
                checked = proactiveSuggestions,
                onCheckedChange = { proactiveSuggestions = it }
            )

            SettingToggleRow(
                icon = Icons.Default.Psychology,
                title = "Long-Term Memory Recall",
                subtitle = "Retain biography details across conversations",
                checked = highPrecisionMemory,
                onCheckedChange = { highPrecisionMemory = it }
            )

            SettingToggleRow(
                icon = Icons.Default.Speed,
                title = "Edge NPU Acceleration",
                subtitle = "Utilize mobile neural processing unit for fast local inference",
                checked = edgeAcceleration,
                onCheckedChange = { edgeAcceleration = it }
            )
        }
    }
}

@Composable
private fun SettingToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val theme = Theme.current

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
                    imageVector = icon,
                    contentDescription = null,
                    tint = theme.colors.primaryAccent,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = theme.typography.titleMedium.copy(
                        color = theme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.5.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = theme.typography.caption.copy(color = theme.colors.textSecondary)
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = theme.colors.onPrimaryAccent,
                checkedTrackColor = theme.colors.primaryAccent,
                uncheckedThumbColor = theme.colors.textTertiary,
                uncheckedTrackColor = theme.colors.surfaceElevated
            )
        )
    }
}
