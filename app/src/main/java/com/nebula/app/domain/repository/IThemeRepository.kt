package com.nebula.app.domain.repository

import com.nebula.app.theme.AppThemeDefinition
import kotlinx.coroutines.flow.StateFlow

interface IThemeRepository {
    val activeTheme: StateFlow<AppThemeDefinition>
    val availableThemes: StateFlow<List<AppThemeDefinition>>
    suspend fun setTheme(themeId: String)
    suspend fun saveCustomTheme(theme: AppThemeDefinition)
}
