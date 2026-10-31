package com.nebula.app.ui.dialogs

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.NebulaLogo
import com.nebula.app.update.AppUpdateInfo
import com.nebula.app.update.UpdateState
import java.io.File
import java.util.Locale

@Composable
fun UpdateDialog(
    state: UpdateState,
    onDismiss: () -> Unit,
    onStartDownload: (AppUpdateInfo) -> Unit,
    onInstall: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    if (state is UpdateState.Idle || state is UpdateState.Checking) return

    val isForced = when (state) {
        is UpdateState.Available -> state.info.forceUpdate
        is UpdateState.Downloading -> state.info.forceUpdate
        is UpdateState.ReadyToInstall -> state.info.forceUpdate
        else -> false
    }

    Dialog(
        onDismissRequest = { if (!isForced && state !is UpdateState.Downloading) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isForced && state !is UpdateState.Downloading,
            dismissOnClickOutside = !isForced && state !is UpdateState.Downloading
        )
    ) {
        val theme = Theme.current
        val borderGradient = Brush.linearGradient(
            colors = listOf(
                Color(0xFF8B5CF6).copy(alpha = 0.6f),
                Color(0xFF06B6D4).copy(alpha = 0.5f),
                Color(0xFF3B82F6).copy(alpha = 0.3f)
            )
        )

        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF0E1120))
                .border(1.2.dp, borderGradient, RoundedCornerShape(24.dp))
                .padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header Logo
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF161B30)),
                    contentAlignment = Alignment.Center
                ) {
                    when (state) {
                        is UpdateState.Error -> {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFF43F5E),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        is UpdateState.ReadyToInstall -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        is UpdateState.Downloading -> {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = Color(0xFF22D3EE),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        else -> {
                            NebulaLogo(Modifier.size(46.dp))
                        }
                    }
                }

                when (state) {
                    is UpdateState.Available -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "New Update Available",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(6.dp))
                            // Version Pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1E243E))
                                    .border(0.8.dp, Color(0xFF38BDF8).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF34D399))
                                    )
                                    Text(
                                        text = "v${state.info.versionName} • Build ${state.info.versionCode}",
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        if (state.info.releaseNotes.isNotBlank()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF141829))
                                    .border(0.8.dp, Color(0xFF262E4D), RoundedCornerShape(14.dp))
                                    .padding(14.dp)
                                    .heightIn(max = 160.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = "WHAT'S NEW",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = state.info.releaseNotes,
                                    color = Color(0xFFF1F5F9),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (!isForced) {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.weight(1f).height(46.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                                ) {
                                    Text("Later", color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Button(
                                onClick = { onStartDownload(state.info) },
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF7C3AED)
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RocketLaunch,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = Color.White
                                    )
                                    Text("Update", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    is UpdateState.Downloading -> {
                        val animatedProgress by animateFloatAsState(
                            targetValue = state.progress.coerceIn(0f, 1f),
                            animationSpec = tween(120),
                            label = "download_progress"
                        )
                        val percent = (animatedProgress * 100).toInt()

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Downloading Update",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Nebula v${state.info.versionName}",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }

                        // Big percentage display
                        Text(
                            text = "$percent%",
                            color = Color(0xFF38BDF8),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        )

                        // Real-time Progress bar
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp)),
                            color = Color(0xFF06B6D4),
                            trackColor = Color(0xFF1E293B)
                        )

                        // Real-time MB stats and speed
                        val downloadedMb = state.bytesDownloaded / 1024f / 1024f
                        val totalMb = if (state.totalBytes > 0) state.totalBytes / 1024f / 1024f else 0f

                        val speedStr = if (state.speedBytesPerSec > 1_000_000) {
                            String.format(Locale.US, "%.1f MB/s", state.speedBytesPerSec / 1024f / 1024f)
                        } else if (state.speedBytesPerSec > 0) {
                            String.format(Locale.US, "%d KB/s", state.speedBytesPerSec / 1024)
                        } else {
                            ""
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val progressText = if (totalMb > 0) {
                                String.format(Locale.US, "%.1f / %.1f MB", downloadedMb, totalMb)
                            } else {
                                String.format(Locale.US, "%.1f MB", downloadedMb)
                            }
                            Text(
                                text = progressText,
                                color = Color(0xFFCBD5E1),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (speedStr.isNotBlank()) {
                                Text(
                                    text = speedStr,
                                    color = Color(0xFF34D399),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    is UpdateState.ReadyToInstall -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Ready to Install",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Nebula v${state.info.versionName} has downloaded successfully. Proceed to install.",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }

                        Button(
                            onClick = { onInstall(state.apkFile) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981)
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Install Update",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        Text(
                            text = "If installation doesn't prompt automatically, tap the button above.",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    is UpdateState.Error -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Update Error",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = state.message,
                                color = Color(0xFFFCA5A5),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Text("Dismiss", color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    else -> Unit
                }
            }
        }
    }
}
