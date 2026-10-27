package com.nebula.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

enum class BannerPatternType {
    CLEAN_GRADIENT,
    FLOATING_CIRCLES,
    CYBER_GRID,
    HEXAGONS,
    DIAMONDS,
    WAVES,
    EMOJIS
}

data class NebulaBannerThemeItem(
    val id: Int,
    val name: String,
    val category: String,
    val colors: List<Color>,
    val patternType: BannerPatternType = BannerPatternType.CLEAN_GRADIENT,
    val emojis: List<String> = emptyList()
)

object NebulaBannerThemes {
    val ALL: List<NebulaBannerThemeItem> = listOf(
        // --- Classic Gradients ---
        NebulaBannerThemeItem(0, "Deep Indigo", "Gradients", listOf(Color(0xFF1E1B4B), Color(0xFF312E81))),
        NebulaBannerThemeItem(1, "Nebula Violet", "Gradients", listOf(Color(0xFF3B0764), Color(0xFF581C87))),
        NebulaBannerThemeItem(2, "Midnight Teal", "Gradients", listOf(Color(0xFF042F2E), Color(0xFF115E59))),
        NebulaBannerThemeItem(3, "Cosmic Crimson", "Gradients", listOf(Color(0xFF450A0A), Color(0xFF7F1D1D))),
        NebulaBannerThemeItem(4, "Obsidian Glass", "Gradients", listOf(Color(0xFF090B10), Color(0xFF1E222D))),
        NebulaBannerThemeItem(5, "Solar Gold", "Gradients", listOf(Color(0xFF78350F), Color(0xFFB45309), Color(0xFFF59E0B))),
        NebulaBannerThemeItem(6, "Electric Royal", "Gradients", listOf(Color(0xFF0F172A), Color(0xFF1E3A8A), Color(0xFF3B82F6))),
        NebulaBannerThemeItem(7, "Emerald Jade", "Gradients", listOf(Color(0xFF064E3B), Color(0xFF059669), Color(0xFF10B981))),

        // --- Geometric & Modern Shapes ---
        NebulaBannerThemeItem(8, "Floating Orbs", "Shapes", listOf(Color(0xFF4C1D95), Color(0xFF7C3AED), Color(0xFFEC4899)), BannerPatternType.FLOATING_CIRCLES),
        NebulaBannerThemeItem(9, "Cyber Grid", "Shapes", listOf(Color(0xFF0F172A), Color(0xFF0284C7)), BannerPatternType.CYBER_GRID),
        NebulaBannerThemeItem(10, "Neon Hexagons", "Shapes", listOf(Color(0xFF14532D), Color(0xFF16A34A), Color(0xFF22C55E)), BannerPatternType.HEXAGONS),
        NebulaBannerThemeItem(11, "Prism Diamonds", "Shapes", listOf(Color(0xFF701A75), Color(0xFFC026D3), Color(0xFFF472B6)), BannerPatternType.DIAMONDS),
        NebulaBannerThemeItem(12, "Cosmic Waves", "Shapes", listOf(Color(0xFF172554), Color(0xFF2563EB), Color(0xFF06B6D4)), BannerPatternType.WAVES),
        NebulaBannerThemeItem(13, "Solar Rings", "Shapes", listOf(Color(0xFF881337), Color(0xFFE11D48), Color(0xFFF97316)), BannerPatternType.FLOATING_CIRCLES),

        // --- Emoji & Playful Vibes ---
        NebulaBannerThemeItem(14, "Cosmic Galaxy", "Emojis", listOf(Color(0xFF0B0F19), Color(0xFF2E1065)), BannerPatternType.EMOJIS, listOf("🌌", "✨", "🪐", "⭐")),
        NebulaBannerThemeItem(15, "Retro Arcade", "Emojis", listOf(Color(0xFF18181B), Color(0xFF6B21A8)), BannerPatternType.EMOJIS, listOf("🎮", "👾", "🕹️", "⚡")),
        NebulaBannerThemeItem(16, "Lo-Fi Beats", "Emojis", listOf(Color(0xFF2E1065), Color(0xFF581C87)), BannerPatternType.EMOJIS, listOf("🎧", "🎵", "🎶", "💜")),
        NebulaBannerThemeItem(17, "Super Energy", "Emojis", listOf(Color(0xFF7C2D12), Color(0xFFDC2626)), BannerPatternType.EMOJIS, listOf("🔥", "⚡", "✨", "💫")),
        NebulaBannerThemeItem(18, "Sakura Bloom", "Emojis", listOf(Color(0xFF500724), Color(0xFF9D174D)), BannerPatternType.EMOJIS, listOf("🌸", "🌺", "🍃", "✨")),
        NebulaBannerThemeItem(19, "Midnight Coffee", "Emojis", listOf(Color(0xFF1C1917), Color(0xFF44403C)), BannerPatternType.EMOJIS, listOf("☕", "🌙", "📚", "✨")),
        NebulaBannerThemeItem(20, "Warp Speed", "Emojis", listOf(Color(0xFF030712), Color(0xFF1E1B4B)), BannerPatternType.EMOJIS, listOf("🚀", "🌠", "🛸", "🌌")),
        NebulaBannerThemeItem(21, "Tropical Wave", "Emojis", listOf(Color(0xFF083344), Color(0xFF0E7490)), BannerPatternType.EMOJIS, listOf("🌊", "🐬", "🌴", "✨")),
        NebulaBannerThemeItem(22, "Neon City", "Emojis", listOf(Color(0xFF09090B), Color(0xFF4C0519)), BannerPatternType.EMOJIS, listOf("🌆", "🤖", "⚡", "🌃")),
        NebulaBannerThemeItem(23, "Royal Trophy", "Emojis", listOf(Color(0xFF451A03), Color(0xFF78350F)), BannerPatternType.EMOJIS, listOf("👑", "🏆", "⭐", "💎"))
    )

    fun getTheme(index: Int): NebulaBannerThemeItem {
        val safeIndex = index.coerceIn(0, ALL.lastIndex)
        return ALL[safeIndex]
    }
}

/**
 * Renders the decorative background with patterns, geometric shapes, or emojis
 */
@Composable
fun NebulaThemedBannerBackground(
    bannerIndex: Int,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    borderColor: Color = Color.Transparent
) {
    val themeItem = remember(bannerIndex) { NebulaBannerThemes.getTheme(bannerIndex) }

    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.horizontalGradient(themeItem.colors))
            .border(1.dp, borderColor, shape)
    ) {
        when (themeItem.patternType) {
            BannerPatternType.CLEAN_GRADIENT -> {
                // Clean gradient: subtle soft lighting sweep
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.08f),
                        radius = size.width * 0.45f,
                        center = Offset(size.width * 0.85f, size.height * 0.2f)
                    )
                }
            }

            BannerPatternType.FLOATING_CIRCLES -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    drawCircle(Color.White.copy(alpha = 0.12f), radius = h * 0.7f, center = Offset(w * 0.15f, h * 0.5f))
                    drawCircle(Color.White.copy(alpha = 0.08f), radius = h * 0.4f, center = Offset(w * 0.45f, h * 0.2f))
                    drawCircle(Color.White.copy(alpha = 0.15f), radius = h * 0.9f, center = Offset(w * 0.82f, h * 0.7f))
                    drawCircle(Color.White.copy(alpha = 0.05f), radius = h * 1.3f, center = Offset(w * 0.95f, h * 0.3f), style = Stroke(width = 3.dp.toPx()))
                    drawCircle(Color.White.copy(alpha = 0.08f), radius = h * 0.35f, center = Offset(w * 0.65f, h * 0.8f), style = Stroke(width = 2.dp.toPx()))
                }
            }

            BannerPatternType.CYBER_GRID -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val step = 28.dp.toPx()
                    val gridColor = Color.White.copy(alpha = 0.10f)

                    var x = 0f
                    while (x < w) {
                        drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1.dp.toPx())
                        x += step
                    }
                    var y = 0f
                    while (y < h) {
                        drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
                        y += step
                    }
                    // Cyber accent glow
                    drawCircle(
                        Brush.radialGradient(listOf(Color.Cyan.copy(alpha = 0.25f), Color.Transparent)),
                        radius = h * 1.2f,
                        center = Offset(w * 0.75f, h * 0.5f)
                    )
                }
            }

            BannerPatternType.HEXAGONS -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val h = size.height
                    val w = size.width
                    val hexColor = Color.White.copy(alpha = 0.12f)
                    val r = h * 0.42f

                    fun drawHex(cx: Float, cy: Float) {
                        val path = Path()
                        for (i in 0 until 6) {
                            val angle = Math.toRadians((60 * i - 30).toDouble())
                            val px = (cx + r * cos(angle)).toFloat()
                            val py = (cy + r * sin(angle)).toFloat()
                            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                        }
                        path.close()
                        drawPath(path, hexColor, style = Stroke(width = 2.dp.toPx()))
                    }

                    drawHex(w * 0.18f, h * 0.5f)
                    drawHex(w * 0.52f, h * 0.4f)
                    drawHex(w * 0.85f, h * 0.6f)
                }
            }

            BannerPatternType.DIAMONDS -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val strokeColor = Color.White.copy(alpha = 0.14f)

                    fun drawDiamond(cx: Float, cy: Float, sizePx: Float) {
                        val path = Path().apply {
                            moveTo(cx, cy - sizePx)
                            lineTo(cx + sizePx, cy)
                            lineTo(cx, cy + sizePx)
                            lineTo(cx - sizePx, cy)
                            close()
                        }
                        drawPath(path, strokeColor, style = Stroke(width = 2.dp.toPx()))
                    }

                    drawDiamond(w * 0.2f, h * 0.5f, h * 0.4f)
                    drawDiamond(w * 0.5f, h * 0.35f, h * 0.25f)
                    drawDiamond(w * 0.78f, h * 0.55f, h * 0.55f)
                }
            }

            BannerPatternType.WAVES -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val waveColor = Color.White.copy(alpha = 0.15f)

                    val path = Path().apply {
                        moveTo(0f, h * 0.7f)
                        cubicTo(w * 0.25f, h * 0.3f, w * 0.55f, h * 0.9f, w, h * 0.4f)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(path, waveColor)

                    val path2 = Path().apply {
                        moveTo(0f, h * 0.85f)
                        cubicTo(w * 0.35f, h * 0.5f, w * 0.7f, h * 0.95f, w, h * 0.65f)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(path2, Color.White.copy(alpha = 0.08f))
                }
            }

            BannerPatternType.EMOJIS -> {
                // Subtle floating atmospheric emoji row with opacity
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    themeItem.emojis.forEachIndexed { i, emoji ->
                        val align = when (i % 4) {
                            0 -> Alignment.CenterStart
                            1 -> Alignment.TopCenter
                            2 -> Alignment.BottomCenter
                            else -> Alignment.CenterEnd
                        }
                        val fontSize = when (i % 3) {
                            0 -> 34.sp
                            1 -> 28.sp
                            else -> 38.sp
                        }
                        Text(
                            text = emoji,
                            fontSize = fontSize,
                            modifier = Modifier
                                .align(align)
                                .padding(
                                    start = (i * 28).dp,
                                    end = ((3 - i) * 16).dp
                                )
                        )
                    }
                }
            }
        }
    }
}
