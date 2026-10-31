package com.nebula.app.ui.dialogs

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.PABottomSheet

private data class PermissionSpec(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val permissionString: String?
)

/**
 * PermissionsSheet:
 * Uniform permission manager with reactive in-app toggles.
 * Never violently exits the app; requests runtime permissions natively in-app.
 */
@Composable
fun PermissionsSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    val theme = Theme.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var refreshTrigger by remember { mutableIntStateOf(0) }
    var selectedPermissionToDisable by remember { mutableStateOf<PermissionSpec?>(null) }

    // Re-check permissions whenever the app returns from background / settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        refreshTrigger++
    }

    val permissionSpecs = remember {
        listOf(
            PermissionSpec(
                id = "notifications",
                name = "Notifications",
                icon = Icons.Default.Notifications,
                permissionString = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null
            ),
            PermissionSpec(
                id = "media",
                name = "Photos & Media",
                icon = Icons.Default.Image,
                permissionString = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
            )
        )
    }

    fun isPermissionGranted(spec: PermissionSpec): Boolean {
        // Read refreshTrigger so Compose re-evaluates when state changes
        if (refreshTrigger < 0) return false
        return if (spec.id == "notifications") {
            if (Build.VERSION.SDK_INT >= 33 && spec.permissionString != null) {
                ContextCompat.checkSelfPermission(context, spec.permissionString) == PackageManager.PERMISSION_GRANTED
            } else {
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        } else if (spec.permissionString != null) {
            ContextCompat.checkSelfPermission(context, spec.permissionString) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun safeOpenAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) {}
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
            permissionSpecs.forEach { spec ->
                val granted = isPermissionGranted(spec)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(theme.colors.surface)
                        .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
                        .clickable {
                            if (!granted) {
                                if (spec.permissionString != null) {
                                    launcher.launch(spec.permissionString)
                                } else {
                                    safeOpenAppSettings()
                                }
                            } else {
                                selectedPermissionToDisable = spec
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.colors.surfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = spec.icon,
                                contentDescription = null,
                                tint = if (granted) theme.colors.primaryAccent else theme.colors.textSecondary,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        Text(
                            text = spec.name,
                            style = theme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = theme.colors.textPrimary,
                                fontSize = 15.sp
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Switch(
                        checked = granted,
                        onCheckedChange = { next ->
                            if (next) {
                                if (spec.permissionString != null) {
                                    launcher.launch(spec.permissionString)
                                } else {
                                    safeOpenAppSettings()
                                }
                            } else {
                                selectedPermissionToDisable = spec
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = theme.colors.onPrimaryAccent,
                            checkedTrackColor = theme.colors.primaryAccent,
                            uncheckedThumbColor = theme.colors.textSecondary,
                            uncheckedTrackColor = theme.colors.surfaceElevated,
                            uncheckedBorderColor = theme.colors.surfaceBorder
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.colors.surfaceElevated,
                    contentColor = theme.colors.textPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Done",
                    style = theme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }

    if (selectedPermissionToDisable != null) {
        val target = selectedPermissionToDisable!!
        AlertDialog(
            onDismissRequest = { selectedPermissionToDisable = null },
            containerColor = theme.colors.surfaceElevated,
            titleContentColor = theme.colors.textPrimary,
            textContentColor = theme.colors.textSecondary,
            shape = RoundedCornerShape(18.dp),
            title = {
                Text(
                    text = "Manage ${target.name} Permission",
                    style = theme.typography.titleMedium.copy(
                        color = theme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            },
            text = {
                Text(
                    text = "Android requires permissions to be disabled in the system settings. Would you like to open App Settings to change this?",
                    style = theme.typography.bodySmall.copy(
                        color = theme.colors.textSecondary,
                        lineHeight = 18.sp
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedPermissionToDisable = null
                        safeOpenAppSettings()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.colors.primaryAccent,
                        contentColor = theme.colors.onPrimaryAccent
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Open Settings", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedPermissionToDisable = null }) {
                    Text("Keep Enabled", color = theme.colors.textSecondary)
                }
            }
        )
    }
}
