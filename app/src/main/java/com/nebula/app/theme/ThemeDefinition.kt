package com.nebula.app.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * AppThemeDefinition:
 * Pure data token representation of a complete application theme.
 * Supports runtime theme swapping and future theme packs without changing UI components.
 */
@Immutable
data class AppThemeDefinition(
    val id: String,
    val name: String,
    val description: String,
    val isPremium: Boolean,
    val colors: PAColors,
    val typography: PATypography = PATypography(),
    val spacing: PASpacing = PASpacing(),
    val radii: PARadius = PARadius(),
    val shadow: PAShadow = PAShadow(),
    val animation: PAAnimation = PAAnimation(),
    val previewPalette: List<Color> = listOf(colors.background, colors.surface, colors.primaryAccent)
)
