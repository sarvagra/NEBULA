package com.nebula.app.domain.repository

import com.nebula.app.domain.model.NavSection
import kotlinx.coroutines.flow.StateFlow

interface INavigationRepository {
    val sections: StateFlow<List<NavSection>>
    suspend fun reorderSections(newOrderIds: List<String>)
    suspend fun toggleSectionVisibility(sectionId: String, isVisible: Boolean)
}
