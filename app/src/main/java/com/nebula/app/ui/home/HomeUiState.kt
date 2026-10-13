package com.nebula.app.ui.home

import com.nebula.app.domain.model.ChatMessage
import com.nebula.app.domain.model.Conversation
import com.nebula.app.domain.model.NebulaConversation
import com.nebula.app.domain.model.NebulaFriendRequest
import com.nebula.app.domain.model.NebulaRoom
import com.nebula.app.domain.model.NebulaTab
import com.nebula.app.domain.model.NebulaUser
import com.nebula.app.domain.model.UserProfile
import com.nebula.app.theme.AppThemeDefinition

data class HomeUiState(
    val currentTheme: AppThemeDefinition,
    val availableThemes: List<AppThemeDefinition>,
    val userProfile: UserProfile,
    val currentTab: NebulaTab = NebulaTab.HOME,
    val isSidebarOpen: Boolean = false,

    // Rooms
    val rooms: List<NebulaRoom> = emptyList(),
    val activeRoom: NebulaRoom? = null,
    val roomChatInput: String = "",
    val lastRoomReaction: String? = null,

    // Social & Friends
    val friends: List<NebulaUser> = emptyList(),
    val friendRequests: List<NebulaFriendRequest> = emptyList(),
    val suggestedPeople: List<NebulaUser> = emptyList(),
    val activeFriendProfile: NebulaUser? = null,

    // Chats
    val socialConversations: List<NebulaConversation> = emptyList(),
    val activeSocialConversation: NebulaConversation? = null,
    val directChatInput: String = "",

    // Existing Chat Engine (Preserved for HistorySheet compatibility)
    val conversations: List<Conversation> = emptyList(),
    val activeConversation: Conversation? = null,
    val messages: List<ChatMessage> = emptyList(),
    val chatInputText: String = "",

    // AI Configuration
    val showInlineSuggestions: Boolean = true,
    val aiPersonalizationEnabled: Boolean = true,
    val localAiMode: String = "Automatic", // "Automatic", "Always On", "Off"

    // Modal Sheets
    val activeModalSheet: ActiveModalSheet? = null,
    val quickToastMessage: String? = null,

    val notifications: List<com.nebula.app.domain.model.NebulaNotification> = emptyList(),
    val savedVaultItems: List<com.nebula.app.domain.model.SavedVaultItem> = emptyList(),
    val isFriendCountPublic: Boolean = true,
    val activeSubscriptionTier: com.nebula.app.domain.model.NebulaSubscriptionTier = com.nebula.app.domain.model.NebulaSubscriptionTier.FREE,
    val isAuthLoading: Boolean = false,
    val authErrorMessage: String? = null,
    val backendUrl: String = com.nebula.app.data.backend.NebulaApiClient.baseUrl,
    val isCreatingRoom: Boolean = false,
    val isRefreshing: Boolean = false,
    val roomCreationQuote: String = "Finding somewhere to be.",
    val fullScreenDestination: FullScreenDestination? = null
)

sealed interface FullScreenDestination {
    data class PublicProfile(val user: NebulaUser) : FullScreenDestination
    data object EditProfile : FullScreenDestination
    data object Settings : FullScreenDestination
    data object Account : FullScreenDestination
    data object GoogleAccount : FullScreenDestination
    data object About : FullScreenDestination
    data object PrivacyPolicy : FullScreenDestination
    data object Membership : FullScreenDestination
}

sealed interface ActiveModalSheet {
    data object CreateRoom : ActiveModalSheet
    data class FriendProfile(val user: NebulaUser) : ActiveModalSheet
    data object Settings : ActiveModalSheet
    data object AISettings : ActiveModalSheet
    data object Themes : ActiveModalSheet
    data object PremiumPlans : ActiveModalSheet
    data object Personalization : ActiveModalSheet
    data object Profile : ActiveModalSheet
    data object Permissions : ActiveModalSheet
    data object Privacy : ActiveModalSheet
    data object PrivacyPolicy : ActiveModalSheet
    data object Notifications : ActiveModalSheet
    data object History : ActiveModalSheet
    data object About : ActiveModalSheet
    data object Onboarding : ActiveModalSheet
}
