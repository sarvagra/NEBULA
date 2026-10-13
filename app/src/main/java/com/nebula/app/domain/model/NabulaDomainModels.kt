package com.nebula.app.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PeopleOutline
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.ui.graphics.vector.ImageVector

enum class NabulaTab(val label: String, val icon: ImageVector) {
    CHAT("Chat", Icons.Default.ChatBubbleOutline),
    RELATIONS("Relations", Icons.Default.PeopleOutline),
    HABITS("Habits", Icons.Default.TrackChanges),
    SUGGESTIONS("Suggestions", Icons.Default.Lightbulb)
}

enum class RelationTier(val label: String, val colorHex: Long) {
    HIGH("High", 0xFF10B981),        // Emerald
    LOW("Low", 0xFFF59E0B),          // Amber
    NON_RELATED("Non-Related", 0xFF6B7280) // Gray
}

data class PersonRelation(
    val id: String,
    val name: String,
    val avatarLetters: String,
    val relationTier: RelationTier,
    val roleOrTag: String,
    val lastContactDaysAgo: Int,
    val cadenceDescription: String,
    val commonTopics: List<String>,
    val nextRecommendedAction: String
) {
    companion object {
        val SampleRelations = listOf(
            PersonRelation(
                id = "rel_1",
                name = "Rahul",
                avatarLetters = "R",
                relationTier = RelationTier.HIGH,
                roleOrTag = "Close Friend • College",
                lastContactDaysAgo = 4,
                cadenceDescription = "Target cadence: Every 7 days (On Track)",
                commonTopics = listOf("Mobile Architecture", "Internships", "Weekend Catchup"),
                nextRecommendedAction = "Draft quick check-in about mobile project submission."
            ),
            PersonRelation(
                id = "rel_2",
                name = "Priya Sharma",
                avatarLetters = "P",
                relationTier = RelationTier.HIGH,
                roleOrTag = "Project Co-founder",
                lastContactDaysAgo = 2,
                cadenceDescription = "Daily active sync",
                commonTopics = listOf("Nabula AI Engine", "Local RAG Pipeline"),
                nextRecommendedAction = "Review offline token generation benchmark."
            ),
            PersonRelation(
                id = "rel_3",
                name = "Arjun Patel",
                avatarLetters = "A",
                relationTier = RelationTier.LOW,
                roleOrTag = "Former Colleague",
                lastContactDaysAgo = 21,
                cadenceDescription = "Cadence lapsed: Usually every 14 days",
                commonTopics = listOf("Tech Industry", "Startups"),
                nextRecommendedAction = "Send casual coffee invite or project congratulations."
            ),
            PersonRelation(
                id = "rel_4",
                name = "Kavita Rao",
                avatarLetters = "K",
                relationTier = RelationTier.NON_RELATED,
                roleOrTag = "Conference Contact",
                lastContactDaysAgo = 45,
                cadenceDescription = "Casual network",
                commonTopics = listOf("Android Dev Summit"),
                nextRecommendedAction = "Keep on radar for next developer event."
            )
        )
    }
}

data class HabitItem(
    val id: String,
    val title: String,
    val category: String,
    val targetDescription: String,
    val currentProgress: Int,
    val maxProgress: Int,
    val unit: String,
    val streakDays: Int,
    val isInterventionActive: Boolean = true,
    val aiRecommendation: String
) {
    companion object {
        val SampleHabits = listOf(
            HabitItem(
                id = "habit_1",
                title = "Reels & Shorts Limiter",
                category = "Digital Wellbeing",
                targetDescription = "Not more than 50 reels today • AI Screen Reader Active",
                currentProgress = 35,
                maxProgress = 50,
                unit = "reels",
                streakDays = 6,
                isInterventionActive = true,
                aiRecommendation = "15 reels remaining. Nabula will intercede when limit is reached."
            ),
            HabitItem(
                id = "habit_2",
                title = "Deep Coding Focus",
                category = "Hobby & Skill",
                targetDescription = "Dedicated offline architecture exploration",
                currentProgress = 120,
                maxProgress = 150,
                unit = "mins",
                streakDays = 14,
                isInterventionActive = false,
                aiRecommendation = "Great flow state detected. 30 mins left to hit daily milestone."
            ),
            HabitItem(
                id = "habit_3",
                title = "Technical Book Reading",
                category = "Habit",
                targetDescription = "Read Kotlin coroutines & systems architecture",
                currentProgress = 25,
                maxProgress = 30,
                unit = "mins",
                streakDays = 5,
                isInterventionActive = false,
                aiRecommendation = "Almost complete for today. Recommended chapter: Structured Concurrency."
            )
        )
    }
}

data class ProactiveSuggestion(
    val id: String,
    val title: String,
    val description: String,
    val tag: String,
    val actionPrompt: String
) {
    companion object {
        val SampleSuggestions = listOf(
            ProactiveSuggestion(
                id = "sug_1",
                title = "Relationship Cadence: Rahul",
                description = "4 days since last interaction. You typically catch up before the weekend.",
                tag = "Relationships",
                actionPrompt = "Draft a quick catch-up message to Rahul regarding our mobile project."
            ),
            ProactiveSuggestion(
                id = "sug_2",
                title = "Reels Threshold Approaching",
                description = "You have consumed 35 of 50 allowed reels today. Would you like a break reminder?",
                tag = "Digital Wellbeing",
                actionPrompt = "Turn on strict app blocker after 50 reels today."
            ),
            ProactiveSuggestion(
                id = "sug_3",
                title = "Calendar Sync: Morning Review",
                description = "2 open tasks pending in your local vault from yesterday.",
                tag = "Focus",
                actionPrompt = "Summarize my active priorities and schedule for today."
            )
        )
    }
}
