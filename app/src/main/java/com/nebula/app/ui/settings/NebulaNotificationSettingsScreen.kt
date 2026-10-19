package com.nebula.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.data.backend.NebulaApiClient
import com.nebula.app.theme.Theme
import kotlinx.coroutines.launch

/**
 * Notification settings.
 *
 * Every notification switch in the app lives here rather than being scattered
 * through the general settings list. All of them are opt-in and server-enforced:
 * the server is the source of truth for each value and rejects unknown keys.
 */
private data class NotifToggle(
    val key: String,
    val label: String,
    val description: String,
)

private val CATEGORY_TOGGLES = listOf(
    NotifToggle("directMessages", "Direct messages", "Alerts when someone messages you"),
    NotifToggle("friendRequests", "Friend requests", "Alerts for new friend requests"),
    NotifToggle("invites", "Room invites", "Alerts when you are invited to a room"),
    NotifToggle("textUpdates", "Text updates", "Important account and safety notices"),
    NotifToggle("emailUpdates", "Email updates", "Occasional account emails"),
    NotifToggle("promotions", "Promotions", "Offers and product news"),
)

@Composable
fun NebulaNotificationSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var reengagementOptIn by remember { mutableStateOf(false) }
    var categories by remember { mutableStateOf<org.json.JSONObject?>(null) }

    BackHandler { onBack() }

    LaunchedEffect(Unit) {
        when (val res = NebulaApiClient.getReengagementPreferences()) {
            is NebulaApiClient.ApiResponse.Success -> {
                val data = res.data.optJSONObject("data")
                reengagementOptIn = data?.optBoolean("optIn", false) ?: false
                categories = data?.optJSONObject("categories")
            }
            is NebulaApiClient.ApiResponse.Error ->
                Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
        }
        loading = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.colors.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = theme.colors.textPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = "Notifications",
                style = theme.typography.titleMedium.copy(
                    color = theme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            )
        }

        if (loading) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = theme.colors.primaryAccent
                )
            }
        } else {
            LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, top = 8.dp, end = 18.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "Choose what Nebula may notify you about. Everything here is off unless you turn it on, and you can change it at any time.",
                    style = theme.typography.caption.copy(
                        color = theme.colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                )
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(theme.colors.surface)
                        .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(16.dp))
                ) {
                    NotificationToggleRow(
                        icon = Icons.Default.NotificationsNone,
                        title = "Re-engagement messages",
                        subtitle = "Occasional reminders when you have been away",
                        checked = reengagementOptIn,
                        enabled = !saving,
                        onCheckedChange = { next ->
                            saving = true
                            scope.launch {
                                when (val res = NebulaApiClient.setReengagementOptIn(next)) {
                                    is NebulaApiClient.ApiResponse.Success -> {
                                        reengagementOptIn =
                                            res.data.optJSONObject("data")?.optBoolean("optIn", next) ?: next
                                        Toast.makeText(
                                            context,
                                            if (next) "Re-engagement on" else "Re-engagement off",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    is NebulaApiClient.ApiResponse.Error ->
                                        Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                }
                                saving = false
                            }
                        }
                    )

                    CATEGORY_TOGGLES.forEachIndexed { index, toggle ->
                        if (index > 0 || true) NotificationDivider()
                        val current = categories
                        val checked = current?.optBoolean(toggle.key, toggle.key == "textUpdates")
                            ?: (toggle.key == "textUpdates")
                        NotificationToggleRow(
                            icon = Icons.Default.NotificationsNone,
                            title = toggle.label,
                            subtitle = toggle.description,
                            checked = checked,
                            enabled = current != null && !saving,
                            onCheckedChange = { next ->
                                val draft = categories ?: org.json.JSONObject()
                                draft.put(toggle.key, next)
                                categories = draft
                                saving = true
                                scope.launch {
                                    when (val res = NebulaApiClient.updateNotificationPreferences(
                                        org.json.JSONObject().put(toggle.key, next)
                                    )) {
                                        is NebulaApiClient.ApiResponse.Success -> {
                                            categories = res.data.optJSONObject("data")
                                                ?.optJSONObject("categories") ?: draft
                                            Toast.makeText(
                                                context,
                                                "${toggle.label} ${if (next) "on" else "off"}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        is NebulaApiClient.ApiResponse.Error -> {
                                            draft.put(toggle.key, !next)
                                            categories = draft
                                            Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    saving = false
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun NotificationToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val theme = Theme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = theme.colors.textSecondary,
            modifier = Modifier.size(19.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = theme.typography.bodyMedium.copy(
                    color = theme.colors.textPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.5.sp
                )
            )
            Text(
                text = subtitle,
                style = theme.typography.caption.copy(
                    color = theme.colors.textSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp
                )
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
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

@Composable
private fun NotificationDivider() {
    val theme = Theme.current
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(theme.colors.surfaceBorder.copy(alpha = 0.4f))
    )
}
