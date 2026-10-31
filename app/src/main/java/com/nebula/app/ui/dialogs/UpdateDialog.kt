package com.nebula.app.ui.dialogs

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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nebula.app.theme.Theme
import com.nebula.app.update.AppUpdateInfo
import com.nebula.app.update.UpdateState
import java.io.File

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

        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(theme.colors.surface)
                .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(theme.colors.primaryAccent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    val icon = when (state) {
                        is UpdateState.Error -> Icons.Default.ErrorOutline
                        is UpdateState.Downloading -> Icons.Default.CloudDownload
                        else -> Icons.Default.SystemUpdate
                    }
                    val tint = when (state) {
                        is UpdateState.Error -> theme.colors.errorColor
                        else -> theme.colors.primaryAccent
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(28.dp)
                    )
                }

                when (state) {
                    is UpdateState.Available -> {
                        Text(
                            text = "New Update Available",
                            style = theme.typography.titleMedium.copy(
                                color = theme.colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(theme.colors.surfaceElevated)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Version ${state.info.versionName} • Build ${state.info.versionCode}",
                                style = theme.typography.caption.copy(
                                    color = theme.colors.primaryAccent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }

                        if (state.info.releaseNotes.isNotBlank()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(theme.colors.surfaceElevated)
                                    .padding(12.dp)
                                    .heightIn(max = 140.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = "What's New:",
                                    style = theme.typography.caption.copy(
                                        color = theme.colors.textSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = state.info.releaseNotes,
                                    style = theme.typography.bodySmall.copy(
                                        color = theme.colors.textPrimary,
                                        fontSize = 13.sp
                                    )
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
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Later", color = theme.colors.textSecondary)
                                }
                            }

                            Button(
                                onClick = { onStartDownload(state.info) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = theme.colors.primaryAccent
                                )
                            ) {
                                Text("Update Now", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    is UpdateState.Downloading -> {
                        Text(
                            text = "Downloading Update...",
                            style = theme.typography.titleMedium.copy(
                                color = theme.colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        )

                        val percent = (state.progress * 100).toInt()
                        LinearProgressIndicator(
                            progress = { state.progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = theme.colors.primaryAccent,
                            trackColor = theme.colors.surfaceBorder,
                        )

                        val downloadedMb = state.bytesDownloaded / 1024f / 1024f
                        val totalMb = if (state.totalBytes > 0) state.totalBytes / 1024f / 1024f else 0f
                        val progressLabel = if (totalMb > 0) {
                            "$percent% (%.1f MB / %.1f MB)".format(downloadedMb, totalMb)
                        } else {
                            "%.1f MB downloaded".format(downloadedMb)
                        }

                        Text(
                            text = progressLabel,
                            style = theme.typography.caption.copy(color = theme.colors.textSecondary)
                        )
                    }

                    is UpdateState.ReadyToInstall -> {
                        Text(
                            text = "Update Ready to Install",
                            style = theme.typography.titleMedium.copy(
                                color = theme.colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        )

                        Text(
                            text = "The latest Nebula update has been downloaded and is ready to install.",
                            style = theme.typography.bodySmall.copy(
                                color = theme.colors.textSecondary
                            )
                        )

                        Button(
                            onClick = { onInstall(state.apkFile) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.colors.primaryAccent
                            )
                        ) {
                            Text("Install Now", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    is UpdateState.Error -> {
                        Text(
                            text = "Update Failed",
                            style = theme.typography.titleMedium.copy(
                                color = theme.colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        )

                        Text(
                            text = state.message,
                            style = theme.typography.bodySmall.copy(
                                color = theme.colors.textSecondary
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Dismiss", color = theme.colors.textSecondary)
                            }
                        }
                    }

                    else -> Unit
                }
            }
        }
    }
}
