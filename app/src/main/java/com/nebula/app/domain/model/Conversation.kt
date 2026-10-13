package com.nebula.app.domain.model

data class Conversation(
    val id: String,
    val title: String,
    val lastMessagePreview: String,
    val timestamp: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage> = emptyList()
) {
    val groupCategory: HistoryGroup
        get() {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            val oneDay = 24 * 60 * 60 * 1000L
            return when {
                diff < oneDay -> HistoryGroup.TODAY
                diff < 2 * oneDay -> HistoryGroup.YESTERDAY
                diff < 7 * oneDay -> HistoryGroup.PREVIOUS_7_DAYS
                else -> HistoryGroup.OLDER
            }
        }
}

enum class HistoryGroup(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    PREVIOUS_7_DAYS("Previous 7 days"),
    OLDER("Older")
}
