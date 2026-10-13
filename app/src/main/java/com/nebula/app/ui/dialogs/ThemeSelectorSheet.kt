package com.nebula.app.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nebula.app.domain.model.UserProfile
import com.nebula.app.theme.AppThemeDefinition
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.PABottomSheet
import com.nebula.app.ui.components.PAThemeSelector

/**
 * ThemeSelectorSheet:
 * Bottom sheet for switching among the Four Built-In Themes.
 */
@Composable
fun ThemeSelectorSheet(
    isVisible: Boolean,
    availableThemes: List<AppThemeDefinition>,
    activeTheme: AppThemeDefinition,
    userProfile: UserProfile,
    onSelectTheme: (AppThemeDefinition) -> Unit,
    onUpgradeRequested: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = Theme.current

    PABottomSheet(
        isVisible = isVisible,
        onDismissRequest = onDismiss,
        title = "Appearance & Themes"
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Choose your aesthetic. Free tier includes Light & Dark. Lavender & Red-Hot-Pink are included in Premium.",
                style = theme.typography.bodySmall.copy(color = theme.colors.textSecondary),
                modifier = Modifier.padding(horizontal = theme.spacing.screenHorizontal)
            )

            Spacer(modifier = Modifier.height(14.dp))

            PAThemeSelector(
                availableThemes = availableThemes,
                activeTheme = activeTheme,
                isUserPremium = userProfile.isPremium,
                onSelectTheme = { selected ->
                    onSelectTheme(selected)
                    onDismiss()
                },
                onUpgradeRequested = onUpgradeRequested
            )
        }
    }
}
