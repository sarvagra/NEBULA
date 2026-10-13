package com.nebula.app.ui.dialogs

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.nebula.app.ui.components.PABottomSheet

/**
 * NebulaAISettingsSheet:
 * Dedicated AI settings sheet.
 * - Context-aware suggestions (ON/OFF)
 * - AI Personalization (ON/OFF)
 * - Local AI processing where available (Automatic / Always On / Off)
 */
@Composable
fun NebulaAISettingsSheet(
    isVisible: Boolean,
    showInlineSuggestions: Boolean,
    aiPersonalizationEnabled: Boolean,
    localAiMode: String,
    onToggleInlineSuggestions: (Boolean) -> Unit,
    onTogglePersonalization: (Boolean) -> Unit,
    onSelectLocalAiMode: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = Theme.current

    PABottomSheet(
        isVisible = isVisible,
        onDismissRequest = onDismiss,
        title = "AI Engine Settings"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // SETTING 1: CONTEXT-AWARE SUGGESTIONS
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(theme.colors.surface)
                    .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Context-aware suggestions",
                            style = theme.typography.bodyLarge.copy(
                                color = theme.colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Analyze current room & chat context to generate proactive reply chips and activity ideas.",
                            style = theme.typography.bodySmall.copy(
                                color = theme.colors.textSecondary,
                                fontSize = 12.5.sp,
                                lineHeight = 17.sp
                            )
                        )
                    }

                    Switch(
                        checked = showInlineSuggestions,
                        onCheckedChange = onToggleInlineSuggestions,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = theme.colors.primaryAccent
                        )
                    )
                }
            }

            // SETTING 2: AI PERSONALIZATION
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(theme.colors.surface)
                    .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI Personalization",
                            style = theme.typography.bodyLarge.copy(
                                color = theme.colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Allow Nebula to tailor media recommendations based on your shared room history.",
                            style = theme.typography.bodySmall.copy(
                                color = theme.colors.textSecondary,
                                fontSize = 12.5.sp,
                                lineHeight = 17.sp
                            )
                        )
                    }

                    Switch(
                        checked = aiPersonalizationEnabled,
                        onCheckedChange = onTogglePersonalization,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = theme.colors.primaryAccent
                        )
                    )
                }
            }

            // SETTING 3: LOCAL AI PROCESSING
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(theme.colors.surface)
                    .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column {
                    Text(
                        text = "Local AI where available",
                        style = theme.typography.bodyLarge.copy(
                            color = theme.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Run inference on-device using mobile NPU when available for zero latency and privacy.",
                        style = theme.typography.bodySmall.copy(
                            color = theme.colors.textSecondary,
                            fontSize = 12.5.sp
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Automatic", "Always On", "Off").forEach { mode ->
                        val isSelected = localAiMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) theme.colors.primaryAccent else theme.colors.surfaceElevated)
                                .clickable { onSelectLocalAiMode(mode) }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode,
                                style = theme.typography.caption.copy(
                                    color = if (isSelected) Color.White else theme.colors.textPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}
