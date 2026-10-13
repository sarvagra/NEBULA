package com.nebula.app.ui.membership

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.domain.model.NebulaSubscriptionTier
import com.nebula.app.theme.Theme

@Composable
fun NebulaMembershipScreen(
    currentTier: NebulaSubscriptionTier,
    onSelectTier: (NebulaSubscriptionTier) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.colors.background)
            .statusBarsPadding()
    ) {
        // TOP APP BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = theme.colors.textPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Text(
                text = "Nebula Membership",
                style = theme.typography.titleMedium.copy(
                    color = theme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, top = 12.dp, end = 18.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column {
                    Text(
                        text = "Customize your Nebula experience.",
                        style = theme.typography.titleLarge.copy(
                            color = theme.colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Transparent pricing, no ads on Premium, and zero artificial limits on basic rooms or chat.",
                        style = theme.typography.bodyMedium.copy(
                            color = theme.colors.textSecondary,
                            fontSize = 13.5.sp,
                            lineHeight = 18.sp
                        )
                    )
                }
            }

            items(NebulaSubscriptionTier.entries.toList()) { tier ->
                val isSelected = tier == currentTier

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) theme.colors.surfaceElevated else theme.colors.surface)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) theme.colors.primaryAccent else theme.colors.surfaceBorder,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { onSelectTier(tier) }
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (tier) {
                                                NebulaSubscriptionTier.FREE -> theme.colors.surfaceBorder
                                                NebulaSubscriptionTier.PREMIUM -> theme.colors.primaryAccent.copy(alpha = 0.2f)
                                                NebulaSubscriptionTier.PREMIUM_PLUS -> Color(0xFFF59E0B).copy(alpha = 0.2f)
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = when (tier) {
                                            NebulaSubscriptionTier.FREE -> theme.colors.textSecondary
                                            NebulaSubscriptionTier.PREMIUM -> theme.colors.primaryAccent
                                            NebulaSubscriptionTier.PREMIUM_PLUS -> Color(0xFFF59E0B)
                                        },
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = tier.title,
                                        style = theme.typography.titleMedium.copy(
                                            color = theme.colors.textPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp
                                        )
                                    )
                                    Text(
                                        text = tier.tagLine,
                                        style = theme.typography.caption.copy(
                                            color = theme.colors.textTertiary,
                                            fontSize = 11.5.sp
                                        )
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = tier.price,
                                    style = theme.typography.titleMedium.copy(
                                        color = if (isSelected) theme.colors.primaryAccent else theme.colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                )
                                Text(
                                    text = "/ ${tier.billingCycle}",
                                    style = theme.typography.caption.copy(
                                        color = theme.colors.textTertiary,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(theme.colors.surfaceBorder)
                        )

                        // Feature list
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            tier.features.forEach { feature ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (isSelected) theme.colors.primaryAccent else theme.colors.textSecondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = feature,
                                        style = theme.typography.bodyMedium.copy(
                                            color = theme.colors.textPrimary,
                                            fontSize = 13.sp
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Select / Active Button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) theme.colors.primaryAccent.copy(alpha = 0.15f)
                                    else theme.colors.primaryAccent
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) theme.colors.primaryAccent else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isSelected) "Current Plan" else "Select ${tier.title}",
                                style = theme.typography.bodyMedium.copy(
                                    color = if (isSelected) theme.colors.primaryAccent else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
