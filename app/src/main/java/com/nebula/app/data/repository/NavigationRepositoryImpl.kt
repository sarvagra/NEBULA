package com.nebula.app.data.repository

import com.nebula.app.domain.model.NavSection
import com.nebula.app.domain.repository.INavigationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NavigationRepositoryImpl(
    initialSections: List<NavSection> = NavSection.DefaultSections
) : INavigationRepository {

    private val _sections = MutableStateFlow(initialSections)
    override val sections: StateFlow<List<NavSection>> = _sections.asStateFlow()

    override suspend fun reorderSections(newOrderIds: List<String>) {
        val current = _sections.value
        val map = current.associateBy { it.id }
        val reordered = newOrderIds.mapIndexedNotNull { index, id ->
            map[id]?.copy(orderIndex = index)
        }
        _sections.value = reordered
    }

    override suspend fun toggleSectionVisibility(sectionId: String, isVisible: Boolean) {
        _sections.value = _sections.value.map {
            if (it.id == sectionId) it.copy(isEnabled = isVisible) else it
        }
    }
}
