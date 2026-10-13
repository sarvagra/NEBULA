package com.nebula.app.domain.model

data class NavSection(
    val id: String,
    val title: String,
    val iconKey: String,
    val description: String? = null,
    val badgeCount: Int = 0,
    val orderIndex: Int = 0,
    val isEnabled: Boolean = true
) {
    companion object {
        val DefaultSections = listOf(
            NavSection(
                id = "sec_memory",
                title = "Memory",
                iconKey = "auto_awesome",
                description = "Recall past conversations, facts, and context",
                orderIndex = 0
            ),
            NavSection(
                id = "sec_biography",
                title = "Biography",
                iconKey = "person_book",
                description = "Your timeline, identity, and profile records",
                orderIndex = 1
            ),
            NavSection(
                id = "sec_projects",
                title = "Projects",
                iconKey = "folder_special",
                description = "Active initiatives, goals, and roadmaps",
                orderIndex = 2
            ),
            NavSection(
                id = "sec_ideas",
                title = "Ideas",
                iconKey = "lightbulb",
                description = "Spark thoughts, brainstorming, drafts",
                orderIndex = 3
            ),
            NavSection(
                id = "sec_notes",
                title = "Notes",
                iconKey = "edit_note",
                description = "Quick captures and structured archives",
                orderIndex = 4
            )
        )
    }
}
