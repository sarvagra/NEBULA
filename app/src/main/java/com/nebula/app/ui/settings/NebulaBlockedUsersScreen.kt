package com.nebula.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.data.backend.NebulaApiClient
import com.nebula.app.theme.Theme
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * NebulaBlockedUsersScreen:
 * Dedicated settings screen listing every user the signed-in account has blocked.
 * Reuses the NebulaSettingsScreen visual language (surface cards, hairline dividers,
 * icon chips) and renders exclusively from theme tokens so all four themes
 * (Light, Dark, Lavender, Red-Hot-Pink) stay correct.
 */
@Composable
fun NebulaBlockedUsersScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    val scope = rememberCoroutineScope()

    // Keep the hardware/gesture back affordance consistent with the on-screen back arrow.
    BackHandler { onBack() }

    var blockedUsers by remember { mutableStateOf<List<BlockedUser>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var pendingUnblockId by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadToken) {
        isLoading = true
        loadError = null
        when (val response = NebulaApiClient.getBlockedUsers()) {
            is NebulaApiClient.ApiResponse.Success -> {
                blockedUsers = parseBlockedUsers(response.data)
                loadError = null
            }
            is NebulaApiClient.ApiResponse.Error -> {
                blockedUsers = emptyList()
                loadError = response.message
            }
        }
        isLoading = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.colors.background)
            .statusBarsPadding()
    ) {
        // TOP APP BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = theme.colors.textPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Text(
                text = "Blocked users",
                style = theme.typography.titleMedium.copy(
                    color = theme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            )
        }

        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .navigationBarsPadding(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = theme.colors.primaryAccent,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            loadError != null -> {
                BlockedUsersMessageState(
                    title = "Could not load blocked users",
                    description = loadError.orEmpty(),
                    isError = true,
                    actionLabel = "Retry",
                    onAction = {
                        actionError = null
                        reloadToken++
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            blockedUsers.isEmpty() -> {
                BlockedUsersMessageState(
                    title = "You have not blocked anyone.",
                    description = "People you block from their profile will appear here.",
                    isError = false,
                    modifier = Modifier.weight(1f)
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(start = 18.dp, top = 12.dp, end = 18.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (actionError != null) {
                        item {
                            Text(
                                text = actionError.orEmpty(),
                                style = theme.typography.bodySmall.copy(
                                    color = theme.colors.errorColor,
                                    fontSize = 12.5.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(theme.colors.surface)
                                .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(16.dp))
                        ) {
                            blockedUsers.forEachIndexed { index, user ->
                                if (index > 0) BlockedUsersDivider()

                                BlockedUserRow(
                                    user = user,
                                    isUnblocking = pendingUnblockId == user.id,
                                    enabled = pendingUnblockId == null,
                                    onUnblock = {
                                        actionError = null
                                        pendingUnblockId = user.id
                                        scope.launch {
                                            when (val response = NebulaApiClient.unblockUser(user.id)) {
                                                is NebulaApiClient.ApiResponse.Success -> {
                                                    blockedUsers = blockedUsers.filterNot { it.id == user.id }
                                                    actionError = null
                                                }
                                                is NebulaApiClient.ApiResponse.Error -> {
                                                    actionError = response.message
                                                }
                                            }
                                            pendingUnblockId = null
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
}

@Composable
private fun BlockedUserRow(
    user: BlockedUser,
    isUnblocking: Boolean,
    enabled: Boolean,
    onUnblock: () -> Unit
) {
    val theme = Theme.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = theme.colors.primaryAccent,
                    modifier = Modifier.size(19.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.displayLabel,
                    style = theme.typography.bodyMedium.copy(
                        color = theme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (user.handle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = user.handle,
                        style = theme.typography.caption.copy(
                            color = theme.colors.textSecondary,
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (isUnblocking) {
            CircularProgressIndicator(
                color = theme.colors.primaryAccent,
                strokeWidth = 2.dp,
                modifier = Modifier
                    .padding(horizontal = 18.dp)
                    .size(16.dp)
            )
        } else {
            TextButton(
                onClick = onUnblock,
                enabled = enabled
            ) {
                Text(
                    text = "Unblock",
                    style = theme.typography.button.copy(
                        color = if (enabled) theme.colors.primaryAccent else theme.colors.textTertiary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}

@Composable
private fun BlockedUsersMessageState(
    title: String,
    description: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val theme = Theme.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(theme.colors.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Block,
                contentDescription = null,
                tint = theme.colors.textSecondary,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = theme.typography.titleMedium.copy(color = theme.colors.textPrimary),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = description,
            style = theme.typography.bodySmall.copy(
                color = if (isError) theme.colors.errorColor else theme.colors.textSecondary
            ),
            textAlign = TextAlign.Center
        )

        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onAction) {
                Text(
                    text = actionLabel,
                    style = theme.typography.button.copy(
                        color = theme.colors.primaryAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}

@Composable
private fun BlockedUsersDivider() {
    val theme = Theme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(theme.colors.surfaceBorder)
    )
}

private data class BlockedUser(
    val id: String,
    val displayLabel: String,
    val handle: String?
)

private fun parseBlockedUsers(json: JSONArray): List<BlockedUser> {
    val users = mutableListOf<BlockedUser>()
    for (index in 0 until json.length()) {
        val raw = json.optJSONObject(index) ?: continue
        val id = raw.optString("id").takeIf { it.isNotBlank() } ?: continue
        val username = raw.optString("username").takeIf { it.isNotBlank() }
        val displayName = raw.optString("displayName").takeIf { it.isNotBlank() }
        val label = displayName ?: username ?: "User"
        val handle = username?.let { if (it.startsWith("@")) it else "@$it" }
        users += BlockedUser(id = id, displayLabel = label, handle = handle)
    }
    return users
}
