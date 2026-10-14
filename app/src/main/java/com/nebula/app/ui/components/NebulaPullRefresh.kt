package com.nebula.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nebula.app.theme.Theme

/**
 * One pull-to-refresh container shared by Home, Rooms, Chats, Friends and Profile.
 *
 * The indicator is drawn from the active theme rather than the Material default so
 * it matches light, dark, and both custom presets.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun NebulaPullRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val theme = Theme.current
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = onRefresh
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        content()

        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = theme.colors.surfaceElevated,
            contentColor = theme.colors.primaryAccent,
            scale = true
        )
    }
}

/**
 * Compact themed refresh affordance for screens that expose an explicit refresh
 * action instead of a pull gesture.
 */
@Composable
fun NebulaRefreshSpinner(
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    Box(
        modifier = modifier.size(18.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isRefreshing) {
            CircularProgressIndicator(
                modifier = Modifier.fillMaxSize(),
                color = theme.colors.primaryAccent,
                strokeWidth = 2.dp
            )
        } else {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(theme.colors.textTertiary)
            )
        }
    }
}
