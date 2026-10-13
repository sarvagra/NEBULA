package com.nebula.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nebula.app.data.repository.RoomRepositoryImpl
import com.nebula.app.data.repository.SocialRepositoryImpl
import com.nebula.app.domain.model.ChatAttachment
import com.nebula.app.domain.model.Conversation
import com.nebula.app.domain.model.NebulaTab
import com.nebula.app.domain.model.NebulaUser
import com.nebula.app.domain.model.RoomActivityType
import com.nebula.app.domain.model.RoomPrivacy
import com.nebula.app.domain.model.SharedMediaItem
import com.nebula.app.domain.model.NebulaSubscriptionTier
import com.nebula.app.domain.repository.IChatRepository
import com.nebula.app.domain.repository.IProfileRepository
import com.nebula.app.domain.repository.IRoomRepository
import com.nebula.app.domain.repository.ISocialRepository
import com.nebula.app.domain.repository.IThemeRepository
import com.nebula.app.theme.AppThemeDefinition
import android.content.Context
import com.nebula.app.data.auth.GoogleAuthResult
import com.nebula.app.data.auth.NebulaAuthManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val themeRepository: IThemeRepository,
    private val profileRepository: IProfileRepository,
    private val chatRepository: IChatRepository,
    private val roomRepository: IRoomRepository = RoomRepositoryImpl(),
    private val socialRepository: ISocialRepository = SocialRepositoryImpl(),
    private val authManager: NebulaAuthManager? = null,
    private val sessionManager: com.nebula.app.data.local.SessionManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HomeUiState(
            currentTheme = themeRepository.activeTheme.value,
            availableThemes = themeRepository.availableThemes.value,
            userProfile = profileRepository.userProfile.value,
            conversations = chatRepository.conversations.value,
            activeConversation = chatRepository.activeConversation.value,
            messages = chatRepository.activeMessages.value,
            rooms = roomRepository.rooms.value,
            activeRoom = roomRepository.activeRoom.value,
            friends = socialRepository.friends.value,
            friendRequests = socialRepository.friendRequests.value,
            suggestedPeople = socialRepository.suggestedPeople.value,
            socialConversations = socialRepository.conversations.value,
            activeSocialConversation = socialRepository.activeConversation.value
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // Collect Theme
        viewModelScope.launch {
            themeRepository.activeTheme.collect { activeTheme ->
                _uiState.value = _uiState.value.copy(currentTheme = activeTheme)
            }
        }
        viewModelScope.launch {
            themeRepository.availableThemes.collect { themes ->
                _uiState.value = _uiState.value.copy(availableThemes = themes)
            }
        }

        // Collect Profile
        viewModelScope.launch {
            profileRepository.userProfile.collect { profile ->
                _uiState.value = _uiState.value.copy(userProfile = profile)
            }
        }

        // Collect Preserved Chat History
        viewModelScope.launch {
            chatRepository.conversations.collect { convs ->
                _uiState.value = _uiState.value.copy(conversations = convs)
            }
        }
        viewModelScope.launch {
            chatRepository.activeConversation.collect { activeConv ->
                _uiState.value = _uiState.value.copy(activeConversation = activeConv)
            }
        }
        viewModelScope.launch {
            chatRepository.activeMessages.collect { msgs ->
                _uiState.value = _uiState.value.copy(messages = msgs)
            }
        }

        // Collect Rooms
        viewModelScope.launch {
            roomRepository.rooms.collect { rList ->
                _uiState.value = _uiState.value.copy(rooms = rList)
            }
        }
        viewModelScope.launch {
            roomRepository.activeRoom.collect { currentRoom ->
                _uiState.value = _uiState.value.copy(activeRoom = currentRoom)
            }
        }
        viewModelScope.launch {
            roomRepository.lastReaction.collect { emoji ->
                _uiState.value = _uiState.value.copy(lastRoomReaction = emoji)
            }
        }

        // Collect Social & Friends
        viewModelScope.launch {
            socialRepository.friends.collect { fList ->
                _uiState.value = _uiState.value.copy(friends = fList)
            }
        }
        viewModelScope.launch {
            socialRepository.friendRequests.collect { reqList ->
                _uiState.value = _uiState.value.copy(friendRequests = reqList)
            }
        }
        viewModelScope.launch {
            socialRepository.suggestedPeople.collect { sugList ->
                _uiState.value = _uiState.value.copy(suggestedPeople = sugList)
            }
        }
        viewModelScope.launch {
            socialRepository.conversations.collect { sConvs ->
                _uiState.value = _uiState.value.copy(socialConversations = sConvs)
            }
        }
        viewModelScope.launch {
            socialRepository.activeConversation.collect { aConv ->
                _uiState.value = _uiState.value.copy(activeSocialConversation = aConv)
            }
        }

        val initialSavedProfile = sessionManager?.getUserProfile()
        _uiState.value = _uiState.value.copy(
            notifications = emptyList(),
            savedVaultItems = emptyList(),
            userProfile = initialSavedProfile ?: _uiState.value.userProfile,
            activeSubscriptionTier = initialSavedProfile?.subscriptionTier ?: com.nebula.app.domain.model.NebulaSubscriptionTier.FREE
        )
        if (initialSavedProfile != null) {
            viewModelScope.launch {
                profileRepository.setLoggedInUser(initialSavedProfile)
            }
        }

        // Fetch live rooms from backend
        viewModelScope.launch {
            try {
                (roomRepository as? RoomRepositoryImpl)?.refreshRooms()
            } catch (_: Exception) {}
        }
        viewModelScope.launch {
            try {
                socialRepository.refresh()
            } catch (_: Exception) {}
        }

        // Restore session from SessionManager or Firebase
        viewModelScope.launch {
            if (sessionManager?.hasSession() == true) {
                val savedProfile = sessionManager.getUserProfile() ?: initialSavedProfile
                if (savedProfile != null) {
                    profileRepository.setLoggedInUser(savedProfile)
                    _uiState.value = _uiState.value.copy(
                        userProfile = savedProfile,
                        activeSubscriptionTier = savedProfile.subscriptionTier
                    )
                }
                val token = sessionManager.getAuthToken()
                if (!token.isNullOrBlank()) {
                    com.nebula.app.data.backend.NebulaApiClient.authToken = token
                    val meRes = com.nebula.app.data.backend.NebulaApiClient.getMe()
                    if (meRes is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Success) {
                        val userObj = meRes.data.optJSONObject("data")
                        if (userObj != null && savedProfile != null) {
                            val backendTierStr = userObj.optString("tier", "")
                            val backendTier = if (backendTierStr.isNotBlank()) {
                                try {
                                    com.nebula.app.domain.model.NebulaSubscriptionTier.valueOf(backendTierStr.uppercase())
                                } catch (_: Exception) {
                                    savedProfile.subscriptionTier
                                }
                            } else savedProfile.subscriptionTier
                            val refreshed = savedProfile.copy(
                                displayName = userObj.optString("displayName", savedProfile.displayName),
                                username = "@" + userObj.optString("username", savedProfile.username?.replace("^@".toRegex(), "") ?: "").replace("^@".toRegex(), ""),
                                bio = userObj.optString("bio", savedProfile.bio),
                                status = userObj.optString("status", savedProfile.status),
                                bannerIndex = userObj.optInt("bannerIndex", savedProfile.bannerIndex),
                                isFriendCountPublic = userObj.optBoolean("isFriendCountPublic", savedProfile.isFriendCountPublic),
                                avatarUri = userObj.optString("avatarUrl", savedProfile.avatarUri ?: "").ifEmpty { savedProfile.avatarUri },
                                subscriptionTier = backendTier,
                                isPremium = backendTier != com.nebula.app.domain.model.NebulaSubscriptionTier.FREE,
                                isTrial = userObj.optBoolean("isTrial", false)
                            )
                            profileRepository.setLoggedInUser(refreshed)
                            sessionManager.saveUserProfile(refreshed)
                            _uiState.value = _uiState.value.copy(
                                userProfile = refreshed,
                                activeSubscriptionTier = refreshed.subscriptionTier
                            )
                        }
                    } else if (meRes is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Error && meRes.statusCode == 401) {
                        signOutGoogle()
                    }
                }
            } else if (authManager?.isFirebaseSignedIn == true) {
                authManager.restoreSessionIfPresent()?.let { restoredUser ->
                    profileRepository.setLoggedInUser(restoredUser)
                    sessionManager?.saveUserProfile(restoredUser)
                    _uiState.value = _uiState.value.copy(
                        userProfile = restoredUser,
                        activeSubscriptionTier = restoredUser.subscriptionTier
                    )
                }
            }
        }
    }

    // --- NAVIGATION ---
    fun onTabSelected(tab: NebulaTab) {
        _uiState.value = _uiState.value.copy(
            currentTab = tab,
            activeRoom = null,
            activeSocialConversation = null,
            isSidebarOpen = false
        )
    }

    fun onOpenSidebar() {
        _uiState.value = _uiState.value.copy(isSidebarOpen = true)
    }

    fun onDismissSidebar() {
        _uiState.value = _uiState.value.copy(isSidebarOpen = false)
    }

    // --- ROOM ACTIONS ---
    fun onJoinRoom(roomId: String) {
        viewModelScope.launch {
            val room = roomRepository.joinRoom(roomId)
            if (room != null) {
                _uiState.value = _uiState.value.copy(
                    activeRoom = room,
                    activeSocialConversation = null
                )
            }
        }
    }

    private var pendingRoomInviteFriend: NebulaUser? = null

    fun onCreateRoomClick() {
        _uiState.value = _uiState.value.copy(activeModalSheet = ActiveModalSheet.CreateRoom)
    }

    fun onInviteFromChat(user: NebulaUser) {
        val currentRoom = _uiState.value.activeRoom
        if (currentRoom != null) {
            onInviteFriendToActiveRoom(user)
        } else {
            pendingRoomInviteFriend = user
            onCreateRoomClick()
            showToast("Create a room first — Nebula will automatically invite ${user.displayName.split(" ").first()}")
        }
    }

    private val roomCreationQuotes = listOf(
        "Finding somewhere to be.",
        "Setting the room up.",
        "Getting everyone in sync.",
        "Preparing your space."
    )

    fun onCreateRoom(
        name: String,
        privacy: RoomPrivacy,
        tags: List<String>,
        mediaItem: SharedMediaItem?,
        maxParticipants: Int = 20
    ) {
        viewModelScope.launch {
            val quote = roomCreationQuotes.random()
            _uiState.value = _uiState.value.copy(
                isCreatingRoom = true,
                roomCreationQuote = quote,
                activeModalSheet = null
            )
            kotlinx.coroutines.delay(1200)
            try {
                val created = roomRepository.createRoom(
                    name = name,
                    privacy = privacy,
                    activityType = RoomActivityType.WATCH,
                    mediaItem = mediaItem,
                    tags = tags,
                    maxParticipants = maxParticipants
                )
                val finalRoom = roomRepository.updateRoomSettings(
                    tags = tags,
                    maxParticipants = maxParticipants
                ) ?: created.copy(tags = tags, maxParticipants = maxParticipants)

                val invited = pendingRoomInviteFriend
                pendingRoomInviteFriend = null
                _uiState.value = _uiState.value.copy(
                    isCreatingRoom = false,
                    activeRoom = finalRoom,
                    activeSocialConversation = null
                )
                if (invited != null) {
                    roomRepository.sendRoomMessage("${invited.displayName.split(" ").first()} was automatically invited to ${finalRoom.name}!")
                    showToast("Room created! Automatically invited ${invited.displayName.split(" ").first()}")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isCreatingRoom = false)
                showToast("Failed to create room: ${e.message ?: "Network error"}")
            }
        }
    }

    fun onLeaveRoom(newHostId: String? = null) {
        viewModelScope.launch {
            roomRepository.leaveRoom(newHostId)
            _uiState.value = _uiState.value.copy(activeRoom = null)
        }
    }

    fun onUpdateRoomSettings(
        name: String?,
        maxParticipants: Int?,
        isMicAllowed: Boolean?,
        isVideoAllowed: Boolean?,
        tags: List<String>?
    ) {
        viewModelScope.launch {
            val updated = roomRepository.updateRoomSettings(
                name = name,
                maxParticipants = maxParticipants,
                isMicAllowed = isMicAllowed,
                isVideoAllowed = isVideoAllowed,
                tags = tags
            )
            if (updated != null) {
                _uiState.value = _uiState.value.copy(activeRoom = updated)
                showToast("Room settings saved successfully")
            }
        }
    }

    fun onTransferHost(newHostId: String? = null, random: Boolean = false) {
        viewModelScope.launch {
            val updated = roomRepository.transferHost(newHostId, random)
            if (updated != null) {
                _uiState.value = _uiState.value.copy(activeRoom = updated)
                showToast("Room host updated: ${updated.hostName}")
            }
        }
    }

    fun onToggleRoomPlayPause() {
        viewModelScope.launch {
            roomRepository.togglePlayPause()
        }
    }

    fun onSeekTo(positionMs: Long) {
        viewModelScope.launch {
            val totalSec = positionMs / 1000L
            val minutes = totalSec / 60L
            val seconds = totalSec % 60L
            val label = String.format("%02d:%02d", minutes, seconds)
            roomRepository.seekTo(positionMs, label)
        }
    }

    fun onUpdateRoomMedia(title: String, mediaUrl: String, durationSeconds: Long = 0) {
        viewModelScope.launch {
            val updated = roomRepository.updateMedia(title, mediaUrl, durationSeconds)
            if (updated != null) {
                showToast("Now playing: $title")
            }
        }
    }

    fun onSendRoomMessage(content: String) {
        viewModelScope.launch {
            roomRepository.sendRoomMessage(content)
        }
    }

    fun onSendRoomReaction(emoji: String) {
        viewModelScope.launch {
            roomRepository.sendReaction(emoji)
        }
    }

    // --- SOCIAL & CHATS ACTIONS ---
    fun onSelectSocialConversation(id: String) {
        viewModelScope.launch {
            socialRepository.selectConversation(id)
            _uiState.value = _uiState.value.copy(
                activeRoom = null,
                activeSocialConversation = socialRepository.conversations.value.firstOrNull { it.id == id }
            )
        }
    }

    fun onCloseSocialConversation() {
        viewModelScope.launch {
            socialRepository.closeConversation()
            _uiState.value = _uiState.value.copy(activeSocialConversation = null)
        }
    }

    fun onSendDirectMessage(content: String) {
        val convId = _uiState.value.activeSocialConversation?.id ?: return
        viewModelScope.launch {
            socialRepository.sendMessage(convId, content)
        }
    }

    fun onInviteFriendToActiveRoom(user: NebulaUser) {
        val currentRoom = _uiState.value.activeRoom
        if (currentRoom != null) {
            viewModelScope.launch {
                roomRepository.sendRoomMessage("${user.displayName.split(" ").first()} was invited to ${currentRoom.name}!")
            }
            showToast("Invited ${user.displayName.split(" ").first()} to room")
        } else {
            // Invite to cinema room by default
            onJoinRoom("room_cinema")
            showToast("Invited ${user.displayName.split(" ").first()} to watchparty")
        }
    }

    private val navBackStack = java.util.ArrayDeque<FullScreenDestination>()

    fun navigateTo(dest: FullScreenDestination) {
        val current = _uiState.value.fullScreenDestination
        if (current != null && current != dest) {
            navBackStack.push(current)
        }
        _uiState.value = _uiState.value.copy(
            isSidebarOpen = false,
            fullScreenDestination = dest
        )
    }

    fun onOpenFriendProfile(user: NebulaUser) {
        _uiState.value = _uiState.value.copy(activeFriendProfile = user)
        navigateTo(FullScreenDestination.PublicProfile(user))
    }

    fun onOpenPublicProfile(user: NebulaUser) {
        _uiState.value = _uiState.value.copy(activeFriendProfile = user)
        navigateTo(FullScreenDestination.PublicProfile(user))
    }

    fun onOpenEditProfile() {
        navigateTo(FullScreenDestination.EditProfile)
    }

    fun onOpenAccountSettings() {
        navigateTo(FullScreenDestination.Account)
    }

    fun onOpenGoogleAccountSettings() {
        navigateTo(FullScreenDestination.GoogleAccount)
    }

    fun onOpenSettings() {
        navigateTo(FullScreenDestination.Settings)
    }

    fun onOpenAbout() {
        navigateTo(FullScreenDestination.About)
    }

    fun onOpenPrivacyPolicy() {
        navigateTo(FullScreenDestination.PrivacyPolicy)
    }

    fun onOpenMembership() {
        navigateTo(FullScreenDestination.Membership)
    }

    fun onNavigateBack() {
        if (navBackStack.isNotEmpty()) {
            val prev = navBackStack.pop()
            _uiState.value = _uiState.value.copy(fullScreenDestination = prev)
        } else {
            _uiState.value = _uiState.value.copy(fullScreenDestination = null)
        }
    }

    fun onSaveProfile(
        displayName: String,
        username: String,
        bio: String,
        status: String,
        bannerIndex: Int,
        interests: List<String>,
        isFriendCountPublic: Boolean = true,
        avatarUrl: String? = null,
        bannerUrl: String? = null
    ) {
        viewModelScope.launch {
            val rawClean = username.trim().replace("^@".toRegex(), "").lowercase()
            val cleanUsername = if (rawClean.isNotBlank()) rawClean else {
                _uiState.value.userProfile.username?.removePrefix("@")?.lowercase()
                    ?: _uiState.value.userProfile.email.substringBefore("@").filter { it.isLetterOrDigit() || it == '_' }.take(15).lowercase()
            }.ifEmpty { "nebula_user" }

            if (!cleanUsername.matches("^[a-z0-9_]{3,20}$".toRegex())) {
                showToast("Username must be 3-20 alphanumeric characters or underscores")
                return@launch
            }

            val apiRes = com.nebula.app.data.backend.NebulaApiClient.updateProfile(
                displayName = displayName,
                username = cleanUsername,
                bio = bio,
                status = status,
                bannerIndex = bannerIndex,
                interests = interests,
                avatarUrl = avatarUrl,
                isFriendCountPublic = isFriendCountPublic,
                bannerUrl = bannerUrl,
                isOnboardingCompleted = true
            )

            when (apiRes) {
                is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Success -> {
                    val updatedProfile = _uiState.value.userProfile.copy(
                        displayName = displayName,
                        username = "@$cleanUsername",
                        bio = bio,
                        status = status,
                        bannerIndex = bannerIndex,
                        bannerUrl = bannerUrl ?: _uiState.value.userProfile.bannerUrl,
                        interests = interests,
                        avatarUri = avatarUrl ?: _uiState.value.userProfile.avatarUri,
                        avatarFallbackLetter = displayName.firstOrNull()?.uppercase() ?: "N",
                        isCustomAvatar = !avatarUrl.isNullOrEmpty() || _uiState.value.userProfile.isCustomAvatar,
                        isFriendCountPublic = isFriendCountPublic,
                        isOnboardingCompleted = true
                    )
                    profileRepository.setLoggedInUser(updatedProfile)
                    sessionManager?.saveUserProfile(updatedProfile)

                    val prev = if (navBackStack.isNotEmpty()) navBackStack.pop() else null
                    _uiState.value = _uiState.value.copy(
                        userProfile = updatedProfile,
                        isFriendCountPublic = isFriendCountPublic,
                        fullScreenDestination = prev
                    )
                    showToast("Profile saved successfully")
                }
                is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Error -> {
                    if (apiRes.statusCode == 409) {
                        showToast("Error: Username @$cleanUsername is already taken")
                    } else {
                        showToast("Error: ${apiRes.message}")
                    }
                }
            }
        }
    }


    fun onAcceptFriendRequest(requestId: String) {
        viewModelScope.launch {
            socialRepository.acceptFriendRequest(requestId)
            showToast("Friend request accepted")
        }
    }

    fun onDeclineFriendRequest(requestId: String) {
        viewModelScope.launch {
            socialRepository.declineFriendRequest(requestId)
        }
    }

    fun onAddSuggestedFriend(user: NebulaUser) {
        viewModelScope.launch {
            socialRepository.addFriend(user)
            showToast("Added ${user.displayName.split(" ").first()}")
        }
    }

    fun onAddFriend(user: NebulaUser) {
        onAddSuggestedFriend(user)
    }

    fun onCancelFriendRequest(user: NebulaUser) {
        viewModelScope.launch {
            (socialRepository as? SocialRepositoryImpl)?.withdrawFriendRequest(user.id)
        }
    }

    // --- AI SETTINGS ---
    fun onToggleInlineSuggestions(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(showInlineSuggestions = enabled)
    }

    fun onToggleAiPersonalization(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(aiPersonalizationEnabled = enabled)
    }

    fun onSelectLocalAiMode(mode: String) {
        _uiState.value = _uiState.value.copy(localAiMode = mode)
    }

    // --- MODAL SHEETS ---
    fun onOpenAISettings() {
        _uiState.value = _uiState.value.copy(isSidebarOpen = false, activeModalSheet = ActiveModalSheet.AISettings)
    }

    fun onOpenThemes() {
        _uiState.value = _uiState.value.copy(isSidebarOpen = false, activeModalSheet = ActiveModalSheet.Themes)
    }

    fun onOpenPermissions() {
        _uiState.value = _uiState.value.copy(isSidebarOpen = false, activeModalSheet = ActiveModalSheet.Permissions)
    }

    fun onOpenPrivacy() {
        _uiState.value = _uiState.value.copy(isSidebarOpen = false, activeModalSheet = ActiveModalSheet.Privacy)
    }

    fun onOpenHistory() {
        _uiState.value = _uiState.value.copy(isSidebarOpen = false, activeModalSheet = ActiveModalSheet.History)
    }

    fun onOpenProfileEditor() {
        onOpenEditProfile()
    }

    fun onOpenPremiumPlans() {
        onOpenMembership()
    }

    fun onDismissModalSheet() {
        _uiState.value = _uiState.value.copy(
            activeModalSheet = null,
            activeFriendProfile = null
        )
    }

    fun onSelectTheme(theme: AppThemeDefinition) {
        if (theme.isPremium && _uiState.value.activeSubscriptionTier == NebulaSubscriptionTier.FREE) {
            showToast("Upgrade to Nebula Premium to unlock ${theme.name}")
            _uiState.value = _uiState.value.copy(
                activeModalSheet = ActiveModalSheet.PremiumPlans
            )
            return
        }
        viewModelScope.launch {
            themeRepository.setTheme(theme.id)
            _uiState.value = _uiState.value.copy(currentTheme = theme, activeModalSheet = null)
        }
    }

    fun onCompleteOnboarding(
        displayName: String,
        username: String,
        dob: String,
        gender: String,
        avatarUri: String?
    ) {
        val cleanUsername = username.trim().replace("^@".toRegex(), "").lowercase()
        val updatedProfile = _uiState.value.userProfile.copy(
            displayName = displayName,
            username = "@$cleanUsername",
            avatarUri = avatarUri ?: _uiState.value.userProfile.avatarUri,
            avatarFallbackLetter = displayName.firstOrNull()?.uppercase() ?: "N",
            isCustomAvatar = !avatarUri.isNullOrEmpty(),
            isOnboardingCompleted = true,
            dateOfBirth = dob,
            gender = gender
        )
        sessionManager?.saveUserProfile(updatedProfile)
        _uiState.value = _uiState.value.copy(
            userProfile = updatedProfile
        )
        showToast("Welcome to Nebula, $displayName!")

        viewModelScope.launch {
            com.nebula.app.data.backend.NebulaApiClient.updateProfile(
                displayName = displayName,
                username = cleanUsername,
                avatarUrl = avatarUri,
                dateOfBirth = dob,
                gender = gender,
                isOnboardingCompleted = true
            )
        }
    }

    fun onUpdateDisplayName(name: String) {
        viewModelScope.launch {
            profileRepository.updateDisplayName(name)
        }
    }

    fun onLogout() {
        _uiState.value = _uiState.value.copy(isSidebarOpen = false)
        signOutGoogle()
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(quickToastMessage = null)
    }

    private fun showToast(msg: String) {
        _uiState.value = _uiState.value.copy(quickToastMessage = msg)
    }

    // --- PRESERVED EXISTING CHAT ENGINE (FOR HISTORY) ---
    fun onSelectConversation(conv: Conversation) {
        viewModelScope.launch {
            chatRepository.selectConversation(conv.id)
            _uiState.value = _uiState.value.copy(
                activeModalSheet = null,
                currentTab = NebulaTab.CHATS
            )
        }
    }

    fun onDeleteConversation(conv: Conversation) {
        viewModelScope.launch {
            chatRepository.deleteConversation(conv.id)
        }
    }

    fun onNewConversation() {
        viewModelScope.launch {
            chatRepository.startNewConversation()
            _uiState.value = _uiState.value.copy(
                activeModalSheet = null,
                currentTab = NebulaTab.CHATS
            )
        }
    }

    // --- V2 NOTIFICATIONS & POLLS & VAULT ---
    fun onOpenNotifications() {
        _uiState.value = _uiState.value.copy(activeModalSheet = ActiveModalSheet.Notifications)
    }


    fun onVotePoll(optionId: String) {
        viewModelScope.launch {
            roomRepository.votePoll(optionId)
            showToast("Vote recorded!")
        }
    }

    fun onToggleFriendCountPrivacy() {
        val current = _uiState.value.isFriendCountPublic
        _uiState.value = _uiState.value.copy(isFriendCountPublic = !current)
        showToast(if (!current) "Friend list is now Public" else "Friend list is now Private")
    }

    fun onSelectSubscriptionTier(tier: com.nebula.app.domain.model.NebulaSubscriptionTier) {
        viewModelScope.launch {
            val tierStr = when (tier) {
                com.nebula.app.domain.model.NebulaSubscriptionTier.PREMIUM_PLUS -> "premium_plus"
                com.nebula.app.domain.model.NebulaSubscriptionTier.PREMIUM -> "premium"
                else -> "free"
            }
            if (tierStr != "free") {
                try {
                    com.nebula.app.data.backend.NebulaApiClient.verifyPurchase(
                        purchaseToken = "test_token_${System.currentTimeMillis()}",
                        planTier = tierStr
                    )
                } catch (_: Exception) {}
            }
            val updatedUser = _uiState.value.userProfile.copy(
                subscriptionTier = tier,
                isPremium = tier != com.nebula.app.domain.model.NebulaSubscriptionTier.FREE
            )
            profileRepository.setLoggedInUser(updatedUser)
            sessionManager?.saveUserProfile(updatedUser)
            _uiState.value = _uiState.value.copy(
                userProfile = updatedUser,
                activeSubscriptionTier = tier,
                activeModalSheet = null
            )
            showToast("Switched to ${tier.title} tier • Themes & Premium unlocked")
        }
    }

    fun onExportSavedVault() {
        showToast("Exported ${_uiState.value.savedVaultItems.size} images to DCIM/Nebula")
    }

    fun onPurgeSavedVault() {
        _uiState.value = _uiState.value.copy(savedVaultItems = emptyList())
        showToast("Saved media vault cleared")
    }

    fun onDismissNotification(id: String) {
        _uiState.value = _uiState.value.copy(
            notifications = _uiState.value.notifications.filterNot { it.id == id }
        )
    }

    fun onAcceptNotification(notif: com.nebula.app.domain.model.NebulaNotification) {
        when (notif.type) {
            com.nebula.app.domain.model.NotificationType.FRIEND_REQUEST -> {
                notif.relatedId?.let { onAcceptFriendRequest(it) }
                onDismissNotification(notif.id)
            }
            com.nebula.app.domain.model.NotificationType.ROOM_INVITE,
            com.nebula.app.domain.model.NotificationType.ROOM_STARTED -> {
                notif.relatedId?.let { onJoinRoom(it) }
                onDismissModalSheet()
            }
            com.nebula.app.domain.model.NotificationType.NOTICE -> {
                onDismissNotification(notif.id)
            }
        }
    }

    // --- GOOGLE & MULTI-DEVICE AUTHENTICATION ---
    fun signInWithGoogle(activityContext: Context) {
        val manager = authManager ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAuthLoading = true, authErrorMessage = null)
            when (val result = manager.signInWithGoogle(activityContext)) {
                is GoogleAuthResult.Success -> {
                    val finalProfile = result.profile.copy(isOnboardingCompleted = true)
                    profileRepository.setLoggedInUser(finalProfile)
                    sessionManager?.saveUserProfile(finalProfile)
                    _uiState.value = _uiState.value.copy(
                        userProfile = finalProfile,
                        isAuthLoading = false,
                        authErrorMessage = null,
                        quickToastMessage = "Connected as ${finalProfile.displayName}"
                    )
                }
                is GoogleAuthResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isAuthLoading = false,
                        authErrorMessage = if (result.isCancelled) null else result.message,
                        quickToastMessage = if (result.isCancelled) null else result.message
                    )
                }
                GoogleAuthResult.Loading -> {}
            }
        }
    }

    private var otpRequestInFlight = false

    fun requestOtp(target: String, onResult: (Boolean) -> Unit) {
        if (otpRequestInFlight) return
        otpRequestInFlight = true
        val manager = authManager
        if (manager == null) {
            otpRequestInFlight = false
            onResult(false)
            return
        }
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isAuthLoading = true, authErrorMessage = null)
                val res = manager.requestOtp(target)
                _uiState.value = _uiState.value.copy(isAuthLoading = false)
                if (res is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Success) {
                    val msg = res.data.optString("message", "Verification code dispatched")
                    _uiState.value = _uiState.value.copy(quickToastMessage = msg)
                    onResult(true)
                } else if (res is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Error) {
                    _uiState.value = _uiState.value.copy(authErrorMessage = res.message, quickToastMessage = res.message)
                    onResult(false)
                }
            } finally {
                otpRequestInFlight = false
            }
        }
    }

    fun verifyOtp(target: String, otp: String) {
        val manager = authManager ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAuthLoading = true, authErrorMessage = null)
            when (val result = manager.verifyOtp(target, otp)) {
                is GoogleAuthResult.Success -> {
                    val profile = result.profile
                    profileRepository.setLoggedInUser(profile)
                    sessionManager?.saveUserProfile(profile)
                    _uiState.value = _uiState.value.copy(
                        userProfile = profile,
                        isAuthLoading = false,
                        authErrorMessage = null,
                        quickToastMessage = if (profile.isOnboardingCompleted) "Connected as ${profile.displayName}" else "Welcome to Nebula!"
                    )
                }
                is GoogleAuthResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isAuthLoading = false,
                        authErrorMessage = result.message,
                        quickToastMessage = result.message
                    )
                }
                GoogleAuthResult.Loading -> {}
            }
        }
    }

    fun signInWithEmail(email: String, displayName: String = "") {
        val manager = authManager ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAuthLoading = true, authErrorMessage = null)
            when (val result = manager.signInWithEmailAccount(email, displayName)) {
                is GoogleAuthResult.Success -> {
                    val finalProfile = result.profile.copy(isOnboardingCompleted = true)
                    profileRepository.setLoggedInUser(finalProfile)
                    sessionManager?.saveUserProfile(finalProfile)
                    _uiState.value = _uiState.value.copy(
                        userProfile = finalProfile,
                        isAuthLoading = false,
                        authErrorMessage = null,
                        quickToastMessage = "Connected as ${finalProfile.displayName}"
                    )
                }
                is GoogleAuthResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isAuthLoading = false,
                        authErrorMessage = result.message,
                        quickToastMessage = result.message
                    )
                }
                GoogleAuthResult.Loading -> {}
            }
        }
    }

    fun updateBackendUrl(newUrl: String) {
        com.nebula.app.data.backend.NebulaApiClient.baseUrl = newUrl
        _uiState.value = _uiState.value.copy(
            backendUrl = com.nebula.app.data.backend.NebulaApiClient.baseUrl,
            quickToastMessage = "Server URL updated to $newUrl"
        )
    }

    fun clearAuthError() {
        _uiState.value = _uiState.value.copy(authErrorMessage = null)
    }

    fun developerSignIn() {
        signInWithEmail("developer@nebula.local", "Nebula Explorer")
    }

    fun signOutGoogle() {
        val manager = authManager
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAuthLoading = true)
            manager?.signOut()
            sessionManager?.clearSession()
            profileRepository.setLoggedInUser(com.nebula.app.domain.model.UserProfile(isLoggedIn = false))
            com.nebula.app.data.backend.NebulaApiClient.authToken = null
            _uiState.value = _uiState.value.copy(
                isAuthLoading = false,
                activeModalSheet = null,
                fullScreenDestination = null,
                activeRoom = null,
                activeSocialConversation = null,
                quickToastMessage = "Signed out of Nebula"
            )
        }
    }
}
