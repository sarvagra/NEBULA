package com.nebula.app.data.repository

import com.nebula.app.domain.repository.IThemeRepository
import com.nebula.app.theme.AppThemeDefinition
import com.nebula.app.theme.ThemePresets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ThemeRepositoryImpl(
    initialTheme: AppThemeDefinition = ThemePresets.Dark
) : IThemeRepository {

    private val _availableThemes = MutableStateFlow(ThemePresets.AllThemes)
    override val availableThemes: StateFlow<List<AppThemeDefinition>> = _availableThemes.asStateFlow()

    private val _activeTheme = MutableStateFlow(initialTheme)
    override val activeTheme: StateFlow<AppThemeDefinition> = _activeTheme.asStateFlow()

    override suspend fun setTheme(themeId: String) {
        val found = _availableThemes.value.firstOrNull { it.id == themeId }
        if (found != null) {
            _activeTheme.value = found
        }
    }

    override suspend fun saveCustomTheme(theme: AppThemeDefinition) {
        val currentList = _availableThemes.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == theme.id }
        if (index >= 0) {
            currentList[index] = theme
        } else {
            currentList.add(theme)
        }
        _availableThemes.value = currentList
        _activeTheme.value = theme
    }
}
