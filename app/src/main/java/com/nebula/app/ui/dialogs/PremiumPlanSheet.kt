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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.domain.model.NebulaSubscriptionTier
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.PABottomSheet

/**
 * PremiumPlanSheet:
 * Transparent Freemium comparison modal for NEBULA:
 * - Free: Complete core social experience
 * - Premium: ₹99 / 2 months (Identity, customization, ad-free)
 * - Premium Plus: ₹399 / 4 months (Aspirational, exclusive cosmetics, host controls)
 * - Referral program: 1 month free per friend (up to 5 months)
 */
@Composable
fun PremiumPlanSheet(
    isVisible: Boolean,
    currentTier: NebulaSubscriptionTier = NebulaSubscriptionTier.FREE,
    isTrial: Boolean = false,
    onSelectTier: (NebulaSubscriptionTier) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = Theme.current
    val clipboard = LocalClipboardManager.current
    var selectedTier by remember { mutableStateOf(currentTier) }
    var codeCopied by remember { mutableStateOf(false) }

    PABottomSheet(
        isVisible = isVisible,
        onDismissRequest = onDismiss,
        title = "Nebula Membership"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (isTrial) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFFFB300).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFFFFB300).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "First 1,000 Early Adopter Reward",
                            style = theme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = theme.colors.textPrimary,
                                fontSize = 13.5.sp
                            )
                        )
                        Text(
                            text = "Active Status: 4 Months Free Premium Plus trial applied to your account!",
                            style = theme.typography.caption.copy(
                                color = theme.colors.textSecondary,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }
            }

            // 1. REFERRAL BANNER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(theme.colors.primaryAccent.copy(alpha = 0.12f))
                    .border(1.dp, theme.colors.primaryAccent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(theme.colors.primaryAccent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CardGiftcard,
                            contentDescription = null,
                            tint = theme.colors.primaryAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Referral Program • Free Premium",
                            style = theme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = theme.colors.textPrimary,
                                fontSize = 13.sp
                            )
                        )
                        Text(
                            text = "Get 1 month free per invited friend who hangs out for a week (up to 5 times).",
                            style = theme.typography.caption.copy(
                                color = theme.colors.textSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.colors.surface)
                        .clickable {
                            clipboard.setText(AnnotatedString("NEBULA-SARVAGRA-JOIN"))
                            codeCopied = true
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (codeCopied) "Copied!" else "Copy Code",
                        style = theme.typography.caption.copy(
                            fontWeight = FontWeight.Bold,
                            color = theme.colors.primaryAccent,
                            fontSize = 11.5.sp
                        )
                    )
                }
            }

            // 2. PLAN SELECTOR CARDS
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    NebulaSubscriptionTier.FREE,
                    NebulaSubscriptionTier.PREMIUM,
                    NebulaSubscriptionTier.PREMIUM_PLUS
                ).forEach { tier ->
                    val isSelected = selectedTier == tier
                    val borderColor = if (isSelected) theme.colors.primaryAccent else theme.colors.surfaceBorder
                    val bgColor = if (isSelected) theme.colors.surfaceElevated else theme.colors.surface

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(bgColor)
                            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
                            .clickable { selectedTier = tier }
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = tier.title,
                                    style = theme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = theme.colors.textPrimary,
                                        fontSize = 16.sp
                                    )
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(theme.colors.primaryAccent.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = tier.badgeLabel,
                                        style = theme.typography.caption.copy(
                                            color = theme.colors.primaryAccent,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = tier.price,
                                    style = theme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        color = theme.colors.textPrimary,
                                        fontSize = 17.sp
                                    )
                                )
                                Text(
                                    text = tier.billingCycle,
                                    style = theme.typography.caption.copy(
                                        color = theme.colors.textSecondary,
                                        fontSize = 10.5.sp
                                    )
                                )
                            }
                        }

                        Text(
                            text = tier.tagLine,
                            style = theme.typography.caption.copy(
                                color = theme.colors.primaryAccent,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.5.sp
                            )
                        )

                        // Feature bullet points
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            tier.features.take(3).forEach { feat ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = theme.colors.successColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = feat,
                                        style = theme.typography.caption.copy(
                                            color = theme.colors.textSecondary,
                                            fontSize = 11.5.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. ACTION BUTTON
            Button(
                onClick = { onSelectTier(selectedTier) },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.colors.primaryAccent,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = if (selectedTier == currentTier) "Current Plan" else "Select ${selectedTier.title} (${selectedTier.price})",
                    style = theme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp)
                )
            }
        }
    }
}
