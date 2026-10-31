package com.nebula.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate

/**
 * NebulaLogo:
 * Authoritative branded logo matching website and launcher vector geometry:
 * Central glowing celestial orb with an orbital planetary ring tilted at -24 degrees.
 * Supports smooth ring completion animation (ringProgress from 0f to 1f).
 */
@Composable
fun NebulaLogo(
    modifier: Modifier = Modifier,
    ringProgress: Float = 1f
) {
    Box(modifier = modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val center = Offset(cx, cy)
            val orbRadius = size.minDimension * 0.25f

            // 1. Ambient Cosmic Glow behind Jupiter
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFF59E0B).copy(alpha = 0.45f),
                        Color(0xFFB45309).copy(alpha = 0.22f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = orbRadius * 1.8f
                ),
                radius = orbRadius * 1.8f,
                center = center
            )

            // 2. Full Planetary Ring (-24 degrees tilt, back + full path)
            val rx = size.minDimension * 0.44f
            val ry = size.minDimension * 0.155f
            val strokeW = size.minDimension * 0.042f
            val progressClamped = ringProgress.coerceIn(0f, 1f)

            rotate(degrees = -24f, pivot = center) {
                val ringBrush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFDE68A),
                        Color(0xFFF59E0B),
                        Color(0xFFEA580C)
                    ),
                    start = Offset(cx - rx, cy - ry),
                    end = Offset(cx + rx, cy + ry)
                )

                // The ring gets completed smoothly based on ringProgress
                if (progressClamped > 0f) {
                    drawArc(
                        brush = ringBrush,
                        startAngle = 180f,
                        sweepAngle = 360f * progressClamped,
                        useCenter = false,
                        topLeft = Offset(cx - rx, cy - ry),
                        size = Size(rx * 2f, ry * 2f),
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                }
            }

            // 3. Central Jupiter Celestial Sphere
            val highlightCenter = Offset(cx - orbRadius * 0.28f, cy - orbRadius * 0.32f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFFBEB),
                        Color(0xFFFDE68A),
                        Color(0xFFD97706),
                        Color(0xFFB45309),
                        Color(0xFF78350F)
                    ),
                    center = highlightCenter,
                    radius = orbRadius * 1.35f
                ),
                radius = orbRadius,
                center = center
            )

            // 4. Subtle Jupiter atmospheric band lines & rim reflection
            drawCircle(
                color = Color(0xFFFEF3C7).copy(alpha = 0.35f),
                radius = orbRadius,
                center = center,
                style = Stroke(width = size.minDimension * 0.016f)
            )

            // 5. Foreground Ring Arc (Over the planet for true 3D orbital depth)
            if (progressClamped >= 0.5f) {
                rotate(degrees = -24f, pivot = center) {
                    val ringBrush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFDE68A),
                            Color(0xFFF59E0B),
                            Color(0xFFEA580C)
                        ),
                        start = Offset(cx - rx, cy - ry),
                        end = Offset(cx + rx, cy + ry)
                    )
                    val fgSweep = (progressClamped - 0.5f) * 2f * 180f
                    drawArc(
                        brush = ringBrush,
                        startAngle = 0f,
                        sweepAngle = fgSweep,
                        useCenter = false,
                        topLeft = Offset(cx - rx, cy - ry),
                        size = Size(rx * 2f, ry * 2f),
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}
