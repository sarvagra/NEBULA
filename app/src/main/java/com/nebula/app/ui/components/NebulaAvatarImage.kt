package com.nebula.app.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nebula.app.theme.Theme

@Composable
fun NebulaAvatarImage(
    avatarUrl: String?,
    fallbackLetter: String = "N",
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    fallbackTextSize: TextUnit = 18.sp
) {
    val theme = Theme.current
    val base64Bitmap = remember(avatarUrl) {
        if (!avatarUrl.isNullOrBlank() && avatarUrl.startsWith("data:image")) {
            try {
                val data = avatarUrl.substringAfter("base64,")
                val decoded = Base64.decode(data, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    Box(
        modifier = modifier.clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (base64Bitmap != null) {
            Image(
                bitmap = base64Bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().clip(CircleShape).background(theme.colors.surfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = fallbackLetter.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "N",
                    color = theme.colors.primaryAccent.takeUnless { it == Color.Transparent } ?: Color(0xFF7C5CFF),
                    fontSize = fallbackTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
