package com.nebula.app.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * PAColors: Centralized color tokens for the Nebula design system.
 */
@Immutable
data class PAColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceBorder: Color,
    val primaryAccent: Color,
    val onPrimaryAccent: Color,
    val secondaryAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val chatInputBackground: Color,
    val chatInputBorder: Color,
    val chatInputPlaceholder: Color,
    val userBubbleBackground: Color,
    val userBubbleText: Color,
    val assistantBubbleBackground: Color,
    val assistantBubbleText: Color,
    val assistantBubbleBorder: Color,
    val overlayScrim: Color,
    val badgeBackground: Color,
    val badgeText: Color,
    val errorColor: Color = Color(0xFFEF4444),
    val successColor: Color = Color(0xFF10B981)
)

/**
 * PATypography: Scaled, harmonious typography tokens.
 */
@Immutable
data class PATypography(
    val fontFamily: FontFamily = FontFamily.Default,
    val heroGreeting: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 28.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.4).sp
    ),
    val heroSubtitle: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.1).sp
    ),
    val titleLarge: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp
    ),
    val titleMedium: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp
    ),
    val bodyLarge: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp
    ),
    val bodyMedium: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp
    ),
    val bodySmall: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.1.sp
    ),
    val caption: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp
    ),
    val button: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp
    ),
    val chatInput: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp
    )
)

/**
 * PASpacing: Strict 4-point spacing scale.
 */
@Immutable
data class PASpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
    val screenHorizontal: Dp = 20.dp,
    val composerDockBottom: Dp = 12.dp
)

/**
 * PARadius: Corner radius scale.
 */
@Immutable
data class PARadius(
    val xs: Dp = 6.dp,
    val sm: Dp = 10.dp,
    val md: Dp = 14.dp,
    val lg: Dp = 20.dp,
    val xl: Dp = 28.dp,
    val pill: Dp = 999.dp,
    val chatBubble: Dp = 18.dp,
    val userBubbleSmallCorner: Dp = 4.dp
)

/**
 * PAShadow: Subtle, refined elevations.
 */
@Immutable
data class PAShadow(
    val none: Dp = 0.dp,
    val subtle: Dp = 2.dp,
    val card: Dp = 6.dp,
    val modal: Dp = 16.dp
)

/**
 * PAAnimation: Coherent, responsive spring-based animation tokens.
 */
@Immutable
data class PAAnimation(
    val springStiffness: Float = Spring.StiffnessMediumLow,
    val springDampingRatio: Float = Spring.DampingRatioLowBouncy,
    val fastDurationMs: Int = 200,
    val normalDurationMs: Int = 300,
    val slowDurationMs: Int = 450,
    val easing: Easing = FastOutSlowInEasing
)
