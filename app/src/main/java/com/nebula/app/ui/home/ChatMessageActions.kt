package com.nebula.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.filled.Close
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Done
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import com.nebula.app.domain.model.ChatMessage
import com.nebula.app.domain.model.MessageRole
import com.nebula.app.domain.model.MessageSendState
import com.nebula.app.domain.model.NebulaConversation
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.NebulaAvatarImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent

/**
 * The long-press actions on a message the user sent: edit and unsend, both inside
 * their two-minute window and both drawn with a countdown.
 */

@Composable
internal fun MessageActionSheet(
    message: ChatMessage,
    onEdit: () -> Unit,
    onUnsend: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = Theme.current
    var remainingFraction by remember(message.id) {
        mutableFloatStateOf(remainingFractionFor(message.timestamp))
    }
    androidx.compose.runtime.LaunchedEffect(message.id) {
        while (remainingFraction > 0f) {
            kotlinx.coroutines.delay(250)
            remainingFraction = remainingFractionFor(message.timestamp)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = theme.colors.surfaceElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, theme.colors.surfaceBorder),
            modifier = Modifier.widthIn(min = 240.dp, max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Message",
                    style = theme.typography.caption.copy(
                        color = theme.colors.textSecondary,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.6.sp
                    ),
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                )
                CountdownAction(
                    label = "Edit",
                    subtitle = if (remainingFraction > 0f) "Send the corrected text" else "Edit window closed",
                    fraction = remainingFraction,
                    accent = theme.colors.successColor,
                    enabled = remainingFraction > 0f,
                    onClick = onEdit
                )
                CountdownAction(
                    label = "Unsend",
                    subtitle = if (remainingFraction > 0f) "Remove for everyone" else "Unsend window closed",
                    fraction = remainingFraction,
                    accent = theme.colors.errorColor,
                    enabled = remainingFraction > 0f,
                    onClick = onUnsend
                )
            }
        }
    }
}

/** 1f at send time, draining to 0f when the two-minute window expires. */
private fun remainingFractionFor(timestampMs: Long): Float {
    val elapsed = System.currentTimeMillis() - timestampMs
    return (1f - (elapsed.toFloat() / (2 * 60 * 1000f))).coerceIn(0f, 1f)
}

@Composable
internal fun CountdownAction(
    label: String,
    subtitle: String,
    fraction: Float,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val theme = Theme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.colors.surface)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = theme.typography.bodyMedium.copy(
                color = if (enabled) theme.colors.textPrimary else theme.colors.textTertiary,
                fontWeight = FontWeight.SemiBold
            )
        )
        Text(
            text = subtitle,
            style = theme.typography.caption.copy(
                color = theme.colors.textTertiary,
                fontSize = 11.sp
            )
        )
        // Depleting bar: full accent at send time, empty when the window closes.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(theme.colors.surfaceBorder.copy(alpha = 0.4f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(if (enabled) accent else theme.colors.textTertiary.copy(alpha = 0.4f))
            )
        }
    }
}
