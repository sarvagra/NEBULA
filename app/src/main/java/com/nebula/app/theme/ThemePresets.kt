package com.nebula.app.theme

import androidx.compose.ui.graphics.Color

/**
 * Predefined Four-Theme System:
 * Theme 1: Light (Free)
 * Theme 2: Dark (Free)
 * Theme 3: Lavender (Premium)
 * Theme 4: Red-Hot-Pink (Premium)
 */
object ThemePresets {

    /**
     * THEME 1: LIGHT
     * Modern clean light interface. White / soft neutral surfaces.
     * Very subtle depth. Dark readable typography.
     */
    val Light = AppThemeDefinition(
        id = "theme_light",
        name = "Light",
        description = "Clean modern light interface with soft neutral surfaces and crisp typography",
        isPremium = false,
        colors = PAColors(
            isDark = false,
            background = Color(0xFFF9FAFB),          // Gray 50
            surface = Color(0xFFFFFFFF),             // White
            surfaceElevated = Color(0xFFFFFFFF),     // Pure white card
            surfaceBorder = Color(0xFFE5E7EB),       // Gray 200 hairline
            primaryAccent = Color(0xFF18181B),       // Zinc 900
            onPrimaryAccent = Color(0xFFFFFFFF),
            secondaryAccent = Color(0xFF3B82F6),     // Royal blue subtle
            textPrimary = Color(0xFF111827),         // Gray 900
            textSecondary = Color(0xFF374151),       // Gray 700 (crisp)
            textTertiary = Color(0xFF6B7280),        // Gray 500 (readable on white)
            chatInputBackground = Color(0xFFFFFFFF),
            chatInputBorder = Color(0xFFE5E7EB),
            chatInputPlaceholder = Color(0xFF9CA3AF),
            userBubbleBackground = Color(0xFF18181B), // Crisp dark bubble
            userBubbleText = Color(0xFFFFFFFF),
            assistantBubbleBackground = Color(0xFFFFFFFF),
            assistantBubbleText = Color(0xFF111827),
            assistantBubbleBorder = Color(0xFFE5E7EB),
            overlayScrim = Color(0x66000000),
            badgeBackground = Color(0xFFF3F4F6),
            badgeText = Color(0xFF374151)
        ),
        previewPalette = listOf(Color(0xFFF9FAFB), Color(0xFFFFFFFF), Color(0xFF18181B))
    )

    /**
     * THEME 2: DARK
     * Modern dark interface. Deep neutral background.
     * Subtle elevated surfaces. High-quality dark-mode contrast.
     */
    val Dark = AppThemeDefinition(
        id = "theme_dark",
        name = "Dark",
        description = "Deep neutral background with subtle elevated surfaces and refined contrast",
        isPremium = false,
        colors = PAColors(
            isDark = true,
            background = Color(0xFF0C0D11),          // Deep neutral slate/obsidian
            surface = Color(0xFF14171F),             // Neutral dark surface
            surfaceElevated = Color(0xFF1C202B),     // Elevated card
            surfaceBorder = Color(0xFF262C3A),       // Crisp dark divider
            primaryAccent = Color(0xFF4F46E5),       // Elegant Indigo
            onPrimaryAccent = Color(0xFFFFFFFF),
            secondaryAccent = Color(0xFF6366F1),     // Bright indigo
            textPrimary = Color(0xFFF8FAFC),         // Slate 50
            textSecondary = Color(0xFFCBD5E1),       // Slate 300 (crisp)
            textTertiary = Color(0xFF94A3B8),        // Slate 400 (legible)
            chatInputBackground = Color(0xFF14171F),
            chatInputBorder = Color(0xFF262C3A),
            chatInputPlaceholder = Color(0xFF64748B),
            userBubbleBackground = Color(0xFF4F46E5), // Indigo bubble
            userBubbleText = Color(0xFFFFFFFF),
            assistantBubbleBackground = Color(0xFF161922),
            assistantBubbleText = Color(0xFFF8FAFC),
            assistantBubbleBorder = Color(0xFF262C3A),
            overlayScrim = Color(0x80000000),
            badgeBackground = Color(0xFF1E2330),
            badgeText = Color(0xFF94A3B8)
        ),
        previewPalette = listOf(Color(0xFF0C0D11), Color(0xFF14171F), Color(0xFF4F46E5))
    )

    /**
     * THEME 3: LAVENDER
     * Elegant lavender/purple atmosphere.
     * Extremely restrained. Soft but professional. Not childish. Not neon. Not overly fantasy-like.
     */
    val Lavender = AppThemeDefinition(
        id = "theme_lavender",
        name = "Lavender",
        description = "Restrained, elegant lavender atmosphere with subtle heather tones",
        isPremium = true,
        colors = PAColors(
            isDark = true,
            background = Color(0xFF121019),          // Deep heather black
            surface = Color(0xFF1A1725),             // Muted twilight purple
            surfaceElevated = Color(0xFF231F32),     // Elevated surface
            surfaceBorder = Color(0xFF322C46),       // Restrained lavender-gray border
            primaryAccent = Color(0xFF9D8EC9),       // Soft, muted lavender
            onPrimaryAccent = Color(0xFF13111C),
            secondaryAccent = Color(0xFFB8A9E3),     // Light lavender
            textPrimary = Color(0xFFF6F4FB),         // Warm soft white
            textSecondary = Color(0xFFD4CEE8),       // Clear lavender white
            textTertiary = Color(0xFFA39BB8),        // Visible heather gray
            chatInputBackground = Color(0xFF1A1725),
            chatInputBorder = Color(0xFF322C46),
            chatInputPlaceholder = Color(0xFF736C87),
            userBubbleBackground = Color(0xFF7E6FA8), // Soft restrained lavender bubble
            userBubbleText = Color(0xFFFFFFFF),
            assistantBubbleBackground = Color(0xFF1F1C2C),
            assistantBubbleText = Color(0xFFF6F4FB),
            assistantBubbleBorder = Color(0xFF322C46),
            overlayScrim = Color(0x8A08070D),
            badgeBackground = Color(0xFF2A243B),
            badgeText = Color(0xFFB8A9E3)
        ),
        previewPalette = listOf(Color(0xFF121019), Color(0xFF1A1725), Color(0xFF9D8EC9))
    )

    /**
     * THEME 4: RED-HOT-PINK
     * Sophisticated reddish-pink accent system.
     * Deep neutral base with controlled hot-pink/red highlights. High-end rather than cyberpunk.
     */
    val RedHotPink = AppThemeDefinition(
        id = "theme_red_hot_pink",
        name = "Red-Hot-Pink",
        description = "Deep obsidian base with sophisticated, high-end crimson & hot-pink accents",
        isPremium = true,
        colors = PAColors(
            isDark = true,
            background = Color(0xFF0F0B0E),          // Deep dark neutral
            surface = Color(0xFF181216),             // Subtly tinted dark surface
            surfaceElevated = Color(0xFF221920),     // Elevated card
            surfaceBorder = Color(0xFF33202C),       // Fine rose-wine border
            primaryAccent = Color(0xFFE11D48),       // Rose / Hot-pink premium accent
            onPrimaryAccent = Color(0xFFFFFFFF),
            secondaryAccent = Color(0xFFF43F5E),     // Vibrant rose
            textPrimary = Color(0xFFFDF2F4),         // Crisp light rose-white
            textSecondary = Color(0xFFE2CED4),       // Crisp rose-white (high contrast)
            textTertiary = Color(0xFFB89CA5),        // Visible plum slate (high contrast)
            chatInputBackground = Color(0xFF181216),
            chatInputBorder = Color(0xFF33202C),
            chatInputPlaceholder = Color(0xFF6F5E64),
            userBubbleBackground = Color(0xFFE11D48), // Sophisticated hot pink bubble
            userBubbleText = Color(0xFFFFFFFF),
            assistantBubbleBackground = Color(0xFF1D151B),
            assistantBubbleText = Color(0xFFFDF2F4),
            assistantBubbleBorder = Color(0xFF33202C),
            overlayScrim = Color(0x940B0709),
            badgeBackground = Color(0xFF2A1722),
            badgeText = Color(0xFFF43F5E)
        ),
        previewPalette = listOf(Color(0xFF0F0B0E), Color(0xFF181216), Color(0xFFE11D48))
    )

    val AllThemes: List<AppThemeDefinition> = listOf(
        Light,
        Dark,
        Lavender,
        RedHotPink
    )
}
