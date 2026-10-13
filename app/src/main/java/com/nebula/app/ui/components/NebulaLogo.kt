package com.nebula.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip

@Composable
fun NebulaLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.42f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFB46CFF), Color(0xFF5B1FA8), Color(0xFF250A4A))
                ),
                radius = radius,
                center = center
            )

            val grains = listOf(
                0.20f to -0.18f, -0.25f to -0.20f, 0.08f to 0.10f,
                -0.12f to 0.26f, 0.30f to 0.20f, -0.34f to 0.08f,
                0.02f to -0.34f, 0.38f to -0.05f, -0.40f to -0.08f,
                0.16f to 0.34f, -0.18f to -0.36f
            )
            grains.forEachIndexed { index, (x, y) ->
                drawCircle(
                    color = if (index % 3 == 0) Color(0xFFFF6B5E) else Color(0xFFE83D73),
                    radius = size.minDimension * (0.018f + (index % 3) * 0.006f),
                    center = Offset(
                        center.x + x * size.minDimension,
                        center.y + y * size.minDimension
                    )
                )
            }
        }
    }
}
