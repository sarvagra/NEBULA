package com.nebula.app.ui.dialogs

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.PABottomSheet

data class PermissionItem(
    val name: String,
    val icon: ImageVector,
    val androidPermission: String?,
    val isSystemOnly: Boolean = false
)

/**
 * PermissionsSheet:
 * Concise permission hub showing strictly:
 * Permission Name + Action/Toggle control on the right.
 * No explanatory copy or clutter.
 * Tapping control takes user directly to system app settings.
 */
@Composable
fun PermissionsSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    val theme = Theme.current
    val context = LocalContext.current

    val permissions = remember {
        listOf(
            PermissionItem(
                name = "Microphone",
                icon = Icons.Default.Mic,
                androidPermission = Manifest.permission.RECORD_AUDIO
            ),
            PermissionItem(
                name = "Camera",
                icon = Icons.Default.Videocam,
                androidPermission = Manifest.permission.CAMERA
            ),
            PermissionItem(
                name = "Notifications",
                icon = Icons.Default.Notifications,
                androidPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null
            ),
            PermissionItem(
                name = "Photos & Media",
                icon = Icons.Default.Image,
                androidPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
            )
        )
    }

    // Refresh state when sheet appears
    var checkTrigger by remember { mutableStateOf(0) }

    fun openAppSystemSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun openPermissionSettings(itemName: String) {
        try {
            if (itemName == "Notifications" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            }
            openAppSystemSettings()
        } catch (_: Exception) {
            openAppSystemSettings()
        }
    }

    PABottomSheet(
        isVisible = isVisible,
        onDismissRequest = onDismiss,
        title = "Permissions"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            permissions.forEach { item ->
                val isGranted = item.androidPermission?.let {
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                } ?: true

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.colors.surface)
                        .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(12.dp))
                        .clickable { openPermissionSettings(item.name) }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(theme.colors.surfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = theme.colors.primaryAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Text(
                            text = item.name,
                            style = theme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = theme.colors.textPrimary,
                                fontSize = 14.5.sp
                            )
                        )
                    }

                    // Action / Status Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isGranted) theme.colors.successColor.copy(alpha = 0.15f)
                                else theme.colors.surfaceElevated
                            )
                            .border(
                                1.dp,
                                if (isGranted) theme.colors.successColor.copy(alpha = 0.4f)
                                else theme.colors.surfaceBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { openPermissionSettings(item.name) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isGranted) "Granted" else "Manage",
                            style = theme.typography.caption.copy(
                                color = if (isGranted) theme.colors.successColor else theme.colors.primaryAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.colors.surfaceElevated,
                    contentColor = theme.colors.textPrimary
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "Done",
                    style = theme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
