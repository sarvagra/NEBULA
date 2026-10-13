package com.nebula.app.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * CompositionLocal providing current AppThemeDefinition across the entire Compose hierarchy.
 */
val LocalAppTheme = compositionLocalOf<AppThemeDefinition> {
    error("No AppThemeDefinition provided. Wrap your UI hierarchy in ProvideAppTheme.")
}

/**
 * Convenience accessor object for current Theme tokens.
 */
object Theme {
    val current: AppThemeDefinition
        @Composable
        @ReadOnlyComposable
        get() = LocalAppTheme.current

    val colors: PAColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppTheme.current.colors

    val typography: PATypography
        @Composable
        @ReadOnlyComposable
        get() = LocalAppTheme.current.typography

    val spacing: PASpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalAppTheme.current.spacing

    val radii: PARadius
        @Composable
        @ReadOnlyComposable
        get() = LocalAppTheme.current.radii

    val shadow: PAShadow
        @Composable
        @ReadOnlyComposable
        get() = LocalAppTheme.current.shadow

    val animation: PAAnimation
        @Composable
        @ReadOnlyComposable
        get() = LocalAppTheme.current.animation
}

/**
 * Root theme provider with smooth animated color reveal/transitions across theme switches.
 */
@Composable
fun ProvideAppTheme(
    theme: AppThemeDefinition,
    content: @Composable () -> Unit
) {
    val animBg by animateColorAsState(theme.colors.background, tween(300), label = "themeBg")
    val animSurface by animateColorAsState(theme.colors.surface, tween(300), label = "themeSurf")
    val animSurfaceElevated by animateColorAsState(theme.colors.surfaceElevated, tween(300), label = "themeSurfElev")
    val animSurfaceBorder by animateColorAsState(theme.colors.surfaceBorder, tween(300), label = "themeBorder")
    val animPrimaryAccent by animateColorAsState(theme.colors.primaryAccent, tween(300), label = "themeAccent")
    val animTextPrimary by animateColorAsState(theme.colors.textPrimary, tween(300), label = "themeTextPrim")
    val animTextSecondary by animateColorAsState(theme.colors.textSecondary, tween(300), label = "themeTextSec")
    val animTextTertiary by animateColorAsState(theme.colors.textTertiary, tween(300), label = "themeTextTert")

    val animatedColors = theme.colors.copy(
        background = animBg,
        surface = animSurface,
        surfaceElevated = animSurfaceElevated,
        surfaceBorder = animSurfaceBorder,
        primaryAccent = animPrimaryAccent,
        textPrimary = animTextPrimary,
        textSecondary = animTextSecondary,
        textTertiary = animTextTertiary
    )
    val animatedTheme = theme.copy(colors = animatedColors)

    CompositionLocalProvider(
        LocalAppTheme provides animatedTheme,
        content = content
    )
}

/**
 * Surface modifier for standard card / elevated containers using centralized tokens.
 */
fun Modifier.paSurface(
    shape: Shape,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp
): Modifier = this
    .clip(shape)
    .then(
        if (backgroundColor != null) Modifier.background(backgroundColor) else Modifier
    )
    .then(
        if (borderColor != null) Modifier.border(borderWidth, borderColor, shape) else Modifier
    )
