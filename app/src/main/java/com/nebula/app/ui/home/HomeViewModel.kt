package com.nebula.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nebula.app.data.repository.RoomRepositoryImpl
import com.nebula.app.data.repository.SocialRepositoryImpl
import com.nebula.app.domain.model.ChatAttachment
import com.nebula.app.domain.model.Conversation
import com.nebula.app.domain.model.NebulaTab
import com.nebula.app.domain.model.NebulaUser
import com.nebula.app.domain.model.NebulaConversation
import com.nebula.app.domain.model.RoomActivityType
import com.nebula.app.domain.model.RoomPrivacy
import com.nebula.app.domain.model.SharedMediaItem
import com.nebula.app.domain.model.NebulaSubscriptionTier
import com.nebula.app.domain.model.NebulaNotification
import com.nebula.app.domain.model.NotificationType
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    private var presencePulseJob: Job? = null
    private var conversationSyncJob: Job? = null

    private val _showWelcomeBack = MutableStateFlow(false)
    val showWelcomeBack: StateFlow<Boolean> = _showWelcomeBack.asStateFlow()

    fun dismissWelcomeBack() {
        _showWelcomeBack.value = false
    }

    init {
        (roomRepository as? RoomRepositoryImpl)?.onSocialChanged = {
            viewModelScope.launch { socialRepository.refresh() }
        }
    }

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

    /**
     * Syncs every list.
     *
     * [showIndicator] is false by default: opening the app, resuming it, or
     * completing an action must not flash a spinner. Only a real pull-to-refresh
     * gesture asks for the indicator.
     */
    /** "just now" / "12m" / "3h" / "2d" — a real age instead of a fixed label. */
    private fun relativeNotificationTime(timestampMs: Long): String {
        val delta = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
        val minutes = delta / 60_000
        val hours = minutes / 60
        val days = hours / 24
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m"
            hours < 24 -> "${hours}h"
            days < 7 -> "${days}d"
            else -> java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault())
                .format(java.util.Date(timestampMs))
        }
    }

    fun refreshAll(showIndicator: Boolean = false) {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            if (showIndicator) _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                // Idempotent: connects once the session exists and no-ops afterwards.
                socialRepository.startRealtime()
                (roomRepository as? RoomRepositoryImpl)?.refreshRooms()
                socialRepository.refresh()
                val notificationResult = com.nebula.app.data.backend.NebulaApiClient.getNotifications()
                if (notificationResult is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Success) {
                    _uiState.value = _uiState.value.copy(
                        notifications = (0 until notificationResult.data.length()).mapNotNull { index ->
                            notificationResult.data.optJSONObject(index)?.let { item ->
                                val action = item.optJSONObject("actionData")
                                val type = when (item.optString("type")) {
                                    "friend_request" -> NotificationType.FRIEND_REQUEST
                                    "direct_message" -> NotificationType.DIRECT_MESSAGE
                                    "room_invite" -> NotificationType.ROOM_INVITE
                                    "room_activity" -> NotificationType.ROOM_STARTED
                                    else -> NotificationType.NOTICE
                                }
                                val actionLabel = when (type) {
                                    NotificationType.FRIEND_REQUEST -> "Accept"
                                    NotificationType.DIRECT_MESSAGE -> "Open chat"
                                    NotificationType.ROOM_INVITE, NotificationType.ROOM_STARTED -> "Open"
                                    NotificationType.NOTICE -> null
                                }
                                val actor = item.optJSONObject("actor")
                                NebulaNotification(
                                    id = item.optString("id"),
                                    type = type,
                                    actorId = actor?.optString("id")?.ifBlank { null },
                                    actorName = actor?.optString("displayName")?.ifBlank { null },
                                    actorAvatarUrl = actor?.optString("avatarUrl")?.ifBlank { null },
                                    actorAvatarLetter = actor?.optString("avatarLetter")?.ifBlank { "U" } ?: "U",
                                    createdAtMs = item.optString("createdAt").takeIf { it.isNotBlank() }
                                        ?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
                                        ?: System.currentTimeMillis(),
                                    title = item.optString("title"),
                                    subtitle = item.optString("body"),
                                    timeLabel = relativeNotificationTime(
                                        item.optString("createdAt").takeIf { it.isNotBlank() }
                                            ?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
                                            ?: System.currentTimeMillis()
                                    ),
                                    actionLabel = actionLabel,
                                    relatedId = action?.optString("friendshipId")?.ifBlank { null }
                                        ?: action?.optString("conversationId")?.ifBlank { null }
                                        ?: action?.optString("roomId")?.ifBlank { null },
                                    isRead = item.optBoolean("isRead", false)
                                )
                            }
                        }
                    )
                }
                val token = sessionManager?.getAuthToken()
                if (!token.isNullOrBlank()) {
                    com.nebula.app.data.backend.NebulaApiClient.authToken = token
                    val profile = profileRepository.userProfile.value
                    if (profile.isLoggedIn) {
                        val result = com.nebula.app.data.backend.NebulaApiClient.getMe()
                        if (result is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Success) {
                            val user = result.data.optJSONObject("data")
                            if (user != null) {
                                val tier = when (user.optString("tier").lowercase()) {
                                    "premium_plus", "plus" -> NebulaSubscriptionTier.PREMIUM_PLUS
                                    "premium", "pro" -> NebulaSubscriptionTier.PREMIUM
                                    else -> NebulaSubscriptionTier.FREE
                                }
                                val stats = user.optJSONObject("stats")
                                val refreshed = profile.copy(
                                    displayName = user.optString("displayName", profile.displayName),
                                    username = user.optString("username", profile.username),
                                    email = user.optString("email", profile.email),
                                    bio = user.optString("bio", profile.bio),
                                    status = user.optString("status", profile.status).lowercase().let { if (it in setOf("dynamic", "offline", "custom")) it else "dynamic" },
                                    customStatus = user.optString("customStatus", profile.customStatus),
                                    avatarUri = user.optString("avatarUrl").ifBlank { null },
                                    bannerUrl = user.optString("bannerUrl").ifBlank { null },
                                    bannerIndex = user.optInt("bannerIndex", profile.bannerIndex).let { serverIdx ->
                                        if (profile.bannerIndex >= 5 && serverIdx in 0..4) profile.bannerIndex else serverIdx
                                    },
                                    interests = user.optJSONArray("interests")?.let { values ->
                                        List(values.length()) { index -> values.optString(index) }
                                    } ?: profile.interests,
                                    isFriendCountPublic = user.optBoolean("isFriendCountPublic", profile.isFriendCountPublic),
                                    isPremium = tier != NebulaSubscriptionTier.FREE,
                                    subscriptionTier = tier,
                                    isTrial = user.optBoolean("isTrial", false),
                                    roomsJoinedCount = stats?.optInt("roomsJoinedCount", profile.roomsJoinedCount) ?: profile.roomsJoinedCount,
                                    roomsHostedCount = stats?.optInt("roomsHostedCount", profile.roomsHostedCount) ?: profile.roomsHostedCount,
                                    timeInSyncHours = stats?.optDouble("timeInSyncHours", profile.timeInSyncHours) ?: profile.timeInSyncHours,
                                    isOnboardingCompleted = user.optBoolean("isOnboardingCompleted", profile.isOnboardingCompleted),
                                    dateOfBirth = user.optString("dateOfBirth").ifBlank { null },
                                    gender = user.optString("gender").ifBlank { null }
                                )
                                profileRepository.setLoggedInUser(refreshed)
                                sessionManager?.saveUserProfile(refreshed)
                                _uiState.value = _uiState.value.copy(
                                    userProfile = refreshed,
                                    activeSubscriptionTier = tier,
                                    isFriendCountPublic = refreshed.isFriendCountPublic,
                                    isRoomActivityPublic = user.optBoolean("isRoomActivityPublic", true),
                                    isTopContentPublic = user.optBoolean("isTopContentPublic", true)
                                )
                                // The owner's own tag scribble is served by the public profile
                                // endpoint, which is the only place the rollup is computed.
                                val ownTags = runCatching {
                                    socialRepository.loadPublicProfileById(refreshed.id)?.topTags
                                }.getOrNull()
                                if (ownTags != null) {
                                    _uiState.value = _uiState.value.copy(myTopTags = ownTags)
                                }
                            }

                        }
                    }
                }
            } finally {
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            }
        }
    }

    /** True while the realtime socket is open; presence is maintained by it. */
    private fun isRealtimeConnected(): Boolean =
        (roomRepository as? com.nebula.app.data.repository.RoomRepositoryImpl)?.isRealtimeConnected() == true

    fun onAppForegrounded() {
        presencePulseJob?.cancel()
        presencePulseJob = viewModelScope.launch {
            while (true) {
                // Presence is maintained by the socket's authenticated connection;
                // this HTTP pulse only exists for the rare foreground session without
                // one. At 25 seconds it cost 40 writes/second at a thousand users.
                if (_uiState.value.userProfile.isLoggedIn && !isRealtimeConnected()) {
                    com.nebula.app.data.backend.NebulaApiClient.updateAppPresence(true)
                }
                delay(240_000)
            }
        }
        viewModelScope.launch {
            (roomRepository as? RoomRepositoryImpl)?.reconnectActiveRoom()
            (roomRepository as? RoomRepositoryImpl)?.setAppForegrounded(true)
            refreshAll(showIndicator = false)
        }
    }

    fun onAppBackgrounded() {
        presencePulseJob?.cancel()
        presencePulseJob = null
        if (_uiState.value.userProfile.isLoggedIn) {
            viewModelScope.launch { com.nebula.app.data.backend.NebulaApiClient.updateAppPresence(false) }
        }
        (roomRepository as? RoomRepositoryImpl)?.setAppForegrounded(false)
    }

    fun refreshActiveRoom() {
        viewModelScope.launch { roomRepository.syncActiveRoom() }
    }

    init {
        // A session revoked from another device (or an immediate remote logout) makes
        // every later request fail. Leave for the sign-in screen on the first refusal
        // instead of leaving a signed-in shell full of dead data on screen.
        com.nebula.app.data.backend.NebulaApiClient.onSessionRevoked = { reason ->
            viewModelScope.launch { handleForcedSignOut(reason) }
        }

        // Follow whichever thread the repository considers open, no matter which
        // navigation path opened it, and keep it synced while it stays open.
        // This must live here: the state holder above has to exist first.
        viewModelScope.launch {
            socialRepository.activeConversation.collect { open ->
                _uiState.value = _uiState.value.copy(activeSocialConversation = open)
                if (open == null) {
                    conversationSyncJob?.cancel()
                    conversationSyncJob = null
                } else {
                    startConversationSync()
                }
            }
        }

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
        viewModelScope.launch {
            roomRepository.queue.collect { items ->
                _uiState.value = _uiState.value.copy(roomQueue = items)
            }
        }

        // Connectivity is part of the UI state so the chat can say it is offline instead
        // of leaving a spinner running, and so the moment it comes back the unsent
        // messages go out without the user tapping anything.
        viewModelScope.launch {
            var wasOnline = com.nebula.app.data.local.ConnectivityMonitor.isOnlineNow()
            com.nebula.app.data.local.ConnectivityMonitor.isOnline.collect { online ->
                _uiState.value = _uiState.value.copy(isOnline = online)
                if (online && !wasOnline) {
                    socialRepository.flushPendingNow()
                }
                wasOnline = online
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
            socialRepository.searchResults.collect { results ->
                _uiState.value = _uiState.value.copy(searchResults = results)
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
                                status = userObj.optString("status", savedProfile.status).lowercase().let { if (it in setOf("dynamic", "offline", "custom")) it else "dynamic" },
                                customStatus = userObj.optString("customStatus", savedProfile.customStatus),
                                bannerIndex = userObj.optInt("bannerIndex", savedProfile.bannerIndex).let { serverIdx ->
                                    if (savedProfile.bannerIndex >= 5 && serverIdx in 0..4) savedProfile.bannerIndex else serverIdx
                                },
                                bannerUrl = userObj.optString("bannerUrl", savedProfile.bannerUrl ?: "").ifEmpty { savedProfile.bannerUrl },
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
        if (tab == NebulaTab.FRIENDS || tab == NebulaTab.CHATS || tab == NebulaTab.HOME) viewModelScope.launch { socialRepository.refresh() }
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
    /**
     * Joins a room by its shareable code, which is what every invite surface carries.
     * Resolution happens on the server so a link can never smuggle in a room id the
     * user was not handed.
     */
    fun onJoinRoomByCode(code: String) {
        val trimmed = code.trim().uppercase()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val resolved = com.nebula.app.data.backend.NebulaApiClient.resolveRoomInvite(trimmed)
            val roomId = (resolved as? com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Success)
                ?.data?.optJSONObject("data")?.optString("id")
                ?.takeIf { it.isNotBlank() }
            if (roomId != null) onJoinRoom(roomId) else showToast("That watch party has ended.")
        }
    }

    /**
     * Sends the active room into a friend's direct messages as a card.
     *
     * The card carries the room's shareable code rather than its id, matching every
     * other invite surface. The server validates the code before storing it, so a
     * card can never point at a room that does not exist.
     */
    fun onShareRoomIntoDm(friend: NebulaUser) {
        val room = _uiState.value.activeRoom
        val code = room?.inviteCode
        if (room == null || code.isNullOrBlank()) {
            showToast("This room has no shareable link yet.")
            return
        }
        viewModelScope.launch {
            val sent = socialRepository.sendMessage(
                conversationId = friend.id,
                content = room.name,
                mediaUrl = "nebula://room/$code",
                messageType = "room_card"
            )
            showToast(if (sent) "Invite sent to ${friend.displayName}" else "Could not send that invite.")
        }
    }

    fun onJoinRoom(roomId: String) {
        viewModelScope.launch {
            val room = roomRepository.joinRoom(roomId)
            if (room != null) {
                _uiState.value = _uiState.value.copy(
                    activeRoom = room,
                    activeSocialConversation = null
                )
            } else {
                val blockReason = (roomRepository as? RoomRepositoryImpl)?.lastJoinBlockReason
                if (!blockReason.isNullOrBlank() && blockReason.contains("device", ignoreCase = true)) {
                    // One device per account in a room. This is a rule, not a glitch, so
                    // it gets a dialog that says why instead of a passing toast.
                    _uiState.value = _uiState.value.copy(deviceConflictMessage = blockReason)
                } else {
                    showToast(blockReason?.takeIf { it.isNotBlank() } ?: "Could not join this room. Check access and try again.")
                }
            }
        }
    }

    fun dismissDeviceConflict() {
        _uiState.value = _uiState.value.copy(deviceConflictMessage = null)
    }

    /**
     * Refreshes the cooldown/price shown next to "Switch Google Account". The rules live
     * on the server, so the label is read rather than calculated here.
     */
    fun refreshEmailChangeStatus() {
        viewModelScope.launch {
            val res = com.nebula.app.data.backend.NebulaApiClient.getEmailChangeStatus()
            if (res is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Success) {
                val data = res.data.optJSONObject("data") ?: return@launch
                _uiState.value = _uiState.value.copy(
                    emailChangeFreeNow = data.optBoolean("isFreeNow", true),
                    emailChangeAvailableFrom = data.optString("canChangeFreeAt")
                        .takeIf { it.isNotBlank() && it != "null" },
                    emailChangePriceInr = data.optInt("priceInr", 0)
                )
            }
        }
    }

    /**
     * Replaces the Google identity behind this account. Data is untouched (rooms,
     * messages and stored files are keyed by the account id); only the identity that
     * may sign in changes.
     */
    fun onSwitchGoogleAccount(activityContext: Context) {
        val manager = authManager ?: return
        if (_uiState.value.isRebindingEmail) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRebindingEmail = true)
            try {
                when (val identity = manager.acquireIdentityToken(activityContext)) {
                    is com.nebula.app.data.auth.NebulaAuthManager.IdentityTokenResult.Error -> {
                        if (!identity.isCancelled) showToast(identity.message)
                    }
                    is com.nebula.app.data.auth.NebulaAuthManager.IdentityTokenResult.Success -> {
                        val currentEmail = _uiState.value.userProfile.email
                        if (!identity.email.isNullOrBlank() &&
                            identity.email.equals(currentEmail, ignoreCase = true)
                        ) {
                            showToast("That is already your linked Google account.")
                        } else {
                            when (val res = com.nebula.app.data.backend.NebulaApiClient.rebindEmail(identity.idToken)) {
                                is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Success -> {
                                    val email = res.data.optJSONObject("data")?.optString("email").orEmpty()
                                    val updated = _uiState.value.userProfile.copy(
                                        email = email.ifBlank { identity.email ?: currentEmail }
                                    )
                                    profileRepository.setLoggedInUser(updated)
                                    sessionManager?.saveUserProfile(updated)
                                    _uiState.value = _uiState.value.copy(userProfile = updated)
                                    showToast("Google account changed. Your rooms, chats and media are unchanged.")
                                    refreshEmailChangeStatus()
                                }
                                is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Error -> {
                                    when (res.statusCode) {
                                        402 -> showToast(res.message)
                                        409 -> showToast(res.message)
                                        else -> showToast(res.message)
                                    }
                                    refreshEmailChangeStatus()
                                }
                            }
                        }
                    }
                }
            } finally {
                _uiState.value = _uiState.value.copy(isRebindingEmail = false)
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
            showToast("Create a room first, then you can invite ${user.displayName.split(" ").first()}.")
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
            try {
                val created = roomRepository.createRoom(
                    name = name,
                    privacy = privacy,
                    activityType = RoomActivityType.WATCH,
                    mediaItem = mediaItem,
                    tags = tags,
                    maxParticipants = maxParticipants
                )
                val finalRoom = created

                val invited = pendingRoomInviteFriend
                pendingRoomInviteFriend = null
                _uiState.value = _uiState.value.copy(
                    isCreatingRoom = false,
                    activeRoom = finalRoom,
                    activeSocialConversation = null
                )
                if (invited != null) {
                    when (com.nebula.app.data.backend.NebulaApiClient.inviteToRoom(finalRoom.id, invited.id)) {
                        is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Success ->
                            showToast("Room created and invitation sent to ${invited.displayName.split(" ").first()}.")
                        is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Error ->
                            showToast("Room created, but the invitation could not be sent.")
                    }
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
            val remaining = roomRepository.activeRoom.value
            _uiState.value = _uiState.value.copy(activeRoom = remaining)
            if (remaining != null) showToast("Could not leave the room. Check your connection and try again.")
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
            } else {
                showToast("Room settings could not be saved. Check your connection and permissions.")
            }
        }
    }

    fun onTransferHost(newHostId: String? = null, random: Boolean = false) {
        viewModelScope.launch {
            val updated = roomRepository.transferHost(newHostId, random)
            if (updated != null) {
                _uiState.value = _uiState.value.copy(activeRoom = updated)
                showToast("Room host updated: ${updated.hostName}")
            } else {
                showToast("Room host could not be changed. Check your connection and permissions.")
            }
        }
    }

    fun onModerateRoomParticipant(userId: String, isMuted: Boolean?, isCameraOff: Boolean?) {
        val roomId = _uiState.value.activeRoom?.id ?: return
        viewModelScope.launch {
            val response = com.nebula.app.data.backend.NebulaApiClient.moderateRoomParticipant(
                roomId, userId, isMuted, isCameraOff
            )
            if (response !is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Success) {
                showToast("Participant controls could not be updated.")
            }
        }
    }

    fun onKickRoomParticipant(userId: String) {
        val roomId = _uiState.value.activeRoom?.id ?: return
        viewModelScope.launch {
            val response = com.nebula.app.data.backend.NebulaApiClient.kickRoomParticipant(roomId, userId)
            if (response !is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Success) {
                showToast("Participant could not be removed from the room.")
            }
        }
    }

    fun onToggleRoomPlayPause() {
        viewModelScope.launch {
            roomRepository.togglePlayPause()
        }
    }

    fun onHostPlaybackSample(positionMs: Long, isPlaying: Boolean) {
        roomRepository.reportHostPlayerSample(positionMs, isPlaying)
    }

    fun onHostVideoEnded(positionMs: Long) {
        roomRepository.reportVideoEnded(positionMs)
    }

    fun onRestartRoomVideo() {
        roomRepository.restartVideo()
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

    // --- ROOM QUEUE ---

    fun onAddToRoomQueue(title: String, mediaUrl: String, subtitle: String = "", durationSeconds: Long = 0L) {
        viewModelScope.launch {
            val ok = roomRepository.addToQueue(title, mediaUrl, subtitle, durationSeconds)
            showToast(
                if (ok) "Added to the queue"
                else "Could not add that link. It may not be playable."
            )
        }
    }

    fun onVoteSkipQueueItem(itemId: String) {
        viewModelScope.launch { roomRepository.voteSkipQueueItem(itemId) }
    }

    fun onAdvanceRoomQueue() {
        viewModelScope.launch {
            val ok = roomRepository.advanceQueue()
            if (!ok) showToast("The queue is empty")
        }
    }

    fun onRemoveQueueItem(itemId: String) {
        viewModelScope.launch { roomRepository.removeQueueItem(itemId) }
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
    private var lastConversationClickTime = 0L

    fun onSelectSocialConversation(id: String) {
        val now = System.currentTimeMillis()
        if (now - lastConversationClickTime < 400L && _uiState.value.activeSocialConversation?.id == id) {
            return
        }
        lastConversationClickTime = now

        // STEP 1: Instant optimistic UI opening (0ms - zero delay before screen transition)
        val instantTarget = socialRepository.conversations.value.firstOrNull { it.id == id }
            ?: _uiState.value.socialConversations.firstOrNull { it.id == id }

        if (instantTarget != null) {
            _uiState.value = _uiState.value.copy(
                activeRoom = null,
                activeSocialConversation = instantTarget
            )
        }

        // STEP 2: Asynchronously select, sync read status, and start live polling
        viewModelScope.launch {
            // Opening the thread is what clears its notifications: one row per sender
            // disappears from the inbox the moment the conversation is on screen.
            _uiState.value.notifications
                .filter { it.type == NotificationType.DIRECT_MESSAGE && it.actorId == id }
                .forEach { notif ->
                    com.nebula.app.data.backend.NebulaApiClient.markNotificationRead(notif.id)
                    _uiState.value = _uiState.value.copy(
                        notifications = _uiState.value.notifications.filterNot { it.id == notif.id }
                    )
                }
            socialRepository.selectConversation(id)
            val updated = socialRepository.activeConversation.value
                ?: socialRepository.conversations.value.firstOrNull { it.id == id }
            if (updated != null && (_uiState.value.activeSocialConversation?.id == id || _uiState.value.activeSocialConversation == null)) {
                _uiState.value = _uiState.value.copy(
                    activeRoom = null,
                    activeSocialConversation = updated
                )
            }
            startConversationSync()
        }
    }

    /**
     * Keeps the open thread live: read receipts, edits and messages the other
     * side sends arrive without the user leaving the screen. Cancelled as soon
     * as the conversation closes so nothing polls in the background.
     */
    private fun startConversationSync() {
        conversationSyncJob?.cancel()
        conversationSyncJob = viewModelScope.launch {
            while (true) {
                // Messages, read receipts and edits arrive over the DM socket, so this
                // is only a safety net for a socket that silently died. At four
                // seconds it was the single largest source of HTTP load once a few
                // hundred people sat in open threads at the same time.
                delay(20_000)
                val open = socialRepository.activeConversation.value ?: break
                socialRepository.refreshActiveConversation()
                _uiState.value = _uiState.value.copy(
                    activeSocialConversation = socialRepository.activeConversation.value
                        ?: socialRepository.conversations.value.firstOrNull { it.id == open.id }
                )
            }
        }
    }

    fun onOpenNotificationDestination(type: String, id: String) {
        when (type) {
            "dm" -> viewModelScope.launch {
                val instant = socialRepository.conversations.value.firstOrNull { it.id == id }
                if (instant != null) {
                    _uiState.value = _uiState.value.copy(
                        currentTab = NebulaTab.CHATS,
                        activeRoom = null,
                        activeSocialConversation = instant
                    )
                }
                if (socialRepository.conversations.value.none { it.id == id }) socialRepository.refresh()
                socialRepository.selectConversation(id)
                val conversation = socialRepository.activeConversation.value
                if (conversation != null) {
                    _uiState.value = _uiState.value.copy(
                        currentTab = NebulaTab.CHATS,
                        activeRoom = null,
                        activeSocialConversation = conversation
                    )
                } else if (instant == null) {
                    showToast("That conversation is no longer available.")
                }
                startConversationSync()
            }
            "room_invite", "room_activity" -> onJoinRoom(id)
            // A shared link carries the room's code, not its database id.
            "room_invite_code" -> viewModelScope.launch {
                val resolved = com.nebula.app.data.backend.NebulaApiClient.resolveRoomInvite(id)
                val roomId = (resolved as? com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Success)
                    ?.data?.optJSONObject("data")?.optString("id")
                    ?.takeIf { it.isNotBlank() }
                if (roomId != null) onJoinRoom(roomId) else showToast("That invite link is no longer valid.")
            }
            "friend_request" -> onTabSelected(NebulaTab.FRIENDS)
        }
    }

    fun onMessageFriend(user: NebulaUser) {
        val now = System.currentTimeMillis()
        if (now - lastConversationClickTime < 400L && _uiState.value.activeSocialConversation?.partnerUser?.id == user.id) {
            return
        }
        lastConversationClickTime = now

        val conversation = _uiState.value.socialConversations.firstOrNull { it.partnerUser?.id == user.id }
            ?: socialRepository.conversations.value.firstOrNull { it.partnerUser?.id == user.id }
            ?: NebulaConversation(
                id = user.id,
                title = user.displayName,
                isGroup = false,
                partnerUser = user,
                participants = listOf(NebulaUser.CurrentUser, user),
                lastMessageText = "",
                lastMessageTime = ""
            )

        // Instant 0ms transition:
        _uiState.value = _uiState.value.copy(
            activeRoom = null,
            activeSocialConversation = conversation
        )

        viewModelScope.launch {
            if (_uiState.value.socialConversations.any { it.id == conversation.id }) {
                socialRepository.selectConversation(conversation.id)
            }
            startConversationSync()
        }
    }

    fun onCloseSocialConversation() {
        conversationSyncJob?.cancel()
        conversationSyncJob = null
        viewModelScope.launch {
            socialRepository.closeConversation()
            _uiState.value = _uiState.value.copy(activeSocialConversation = null)
        }
    }

    fun onSendDirectMessage(content: String) {
        val convId = _uiState.value.activeSocialConversation?.id ?: return
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        // Validate locally so an over-length message is never sent and the user is
        // told exactly what is wrong, instead of a generic connectivity message.
        if (trimmed.length > MAX_DIRECT_MESSAGE_CHARS) {
            showToast("Message is too long. Keep it under $MAX_DIRECT_MESSAGE_CHARS characters.")
            return
        }
        viewModelScope.launch {
            if (!socialRepository.sendMessage(convId, trimmed)) {
                // Be specific about which failure this is. "Not sent" while offline is a
                // different instruction from "rejected", and the user acts on it differently.
                showToast(
                    if (com.nebula.app.data.local.ConnectivityMonitor.isOnlineNow()) {
                        "Message not sent. Tap the red ! to retry."
                    } else {
                        "You're offline. Saved — it will send when you reconnect."
                    }
                )
            }
        }
    }

    fun onRetryDirectMessage(clientKey: String) {
        viewModelScope.launch {
            if (!socialRepository.retryMessage(clientKey)) {
                showToast("Still offline. The message will stay unsent until you retry.")
            }
        }
    }

    fun onSendDirectAttachment(text: String, mediaUrl: String, messageType: String, isSpoiler: Boolean, mediaMimeType: String) {
        val conversationId = _uiState.value.activeSocialConversation?.id ?: return
        viewModelScope.launch {
            if (!socialRepository.sendMessage(conversationId, text, mediaUrl, messageType, isSpoiler, mediaMimeType)) showToast("Attachment could not be sent.")
        }
    }

    fun onSendDirectAttachments(text: String, mediaUrls: List<String>, messageType: String, isSpoiler: Boolean, mediaMimeType: String) {
        val conversationId = _uiState.value.activeSocialConversation?.id ?: return
        if (mediaUrls.isEmpty()) return
        viewModelScope.launch {
            val sent = socialRepository.sendMessage(
                conversationId = conversationId,
                content = text,
                mediaUrl = mediaUrls.first(),
                messageType = messageType,
                isSpoiler = isSpoiler,
                mediaMimeType = mediaMimeType,
                mediaUrls = mediaUrls
            )
            if (!sent) showToast("Photos could not be sent.")
        }
    }

    fun onEditDirectMessage(messageId: String, text: String) {
        viewModelScope.launch { if (!socialRepository.editMessage(messageId, text)) showToast("Message could not be edited.") }
    }

    fun onUnsendDirectMessage(messageId: String) {
        viewModelScope.launch { if (!socialRepository.unsendMessage(messageId)) showToast("This message can no longer be unsent.") }
    }

    fun onInviteFriendToActiveRoom(user: NebulaUser) {
        val currentRoom = _uiState.value.activeRoom
        if (currentRoom != null) {
            viewModelScope.launch {
                when (com.nebula.app.data.backend.NebulaApiClient.inviteToRoom(currentRoom.id, user.id)) {
                    is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Success ->
                        showToast("Invitation sent to ${user.displayName.split(" ").first()}.")
                    is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Error ->
                        showToast("Invitation could not be sent. Only accepted friends can be invited.")
                }
            }
        } else {
            pendingRoomInviteFriend = user
            onCreateRoomClick()
            showToast("Create a room first, then you can invite ${user.displayName.split(" ").first()}.")
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

    fun resolveUserRelationship(user: NebulaUser): NebulaUser {
        val currentProfile = _uiState.value.userProfile
        val isSelf = user.id == currentProfile.id || user.username.equals(currentProfile.username, ignoreCase = true)
        if (isSelf) return user
        val isFriend = _uiState.value.friends.any { it.id == user.id }
        val incoming = _uiState.value.friendRequests.firstOrNull { it.sender.id == user.id }
        val outgoing = _uiState.value.suggestedPeople.find { it.id == user.id }?.friendRequestPending == true || user.friendRequestPending
        return user.copy(
            isFriend = isFriend,
            friendRequestReceived = !isFriend && incoming != null,
            friendRequestId = incoming?.id ?: user.friendRequestId,
            friendRequestPending = !isFriend && incoming == null && outgoing
        )
    }

    fun onOpenFriendProfile(user: NebulaUser) {
        val resolved = resolveUserRelationship(user)
        _uiState.value = _uiState.value.copy(activeFriendProfile = resolved)
        navigateTo(FullScreenDestination.PublicProfile(resolved))
        if (user.id != _uiState.value.userProfile.id) viewModelScope.launch {
            socialRepository.loadPublicProfileById(user.id)?.let { loaded ->
                val resolvedLoaded = resolveUserRelationship(loaded)
                _uiState.value = _uiState.value.copy(activeFriendProfile = resolvedLoaded, fullScreenDestination = FullScreenDestination.PublicProfile(resolvedLoaded))
            }
        }
    }

    fun onSearchPeople(query: String) {
        viewModelScope.launch { socialRepository.searchPeople(query) }
    }

    fun onRefreshSuggestions() {
        viewModelScope.launch {
            if (socialRepository.refreshSuggestions()) showToast("Suggestions refreshed")
            else showToast("Daily refresh limit reached or suggestions are unavailable")
        }
    }

    fun onBlockUser(user: NebulaUser) {
        viewModelScope.launch {
            if (socialRepository.blockUser(user.id)) {
                refreshAll()
                _uiState.value = _uiState.value.copy(activeFriendProfile = null, fullScreenDestination = null)
                showToast("User blocked")
            } else showToast("User could not be blocked. Try again.")
        }
    }

    fun onOpenPublicProfile(user: NebulaUser) {
        val resolved = resolveUserRelationship(user)
        _uiState.value = _uiState.value.copy(activeFriendProfile = resolved)
        navigateTo(FullScreenDestination.PublicProfile(resolved))
        if (user.id != _uiState.value.userProfile.id) viewModelScope.launch {
            socialRepository.loadPublicProfileById(user.id)?.let { loaded ->
                val resolvedLoaded = resolveUserRelationship(loaded)
                _uiState.value = _uiState.value.copy(activeFriendProfile = resolvedLoaded, fullScreenDestination = FullScreenDestination.PublicProfile(resolvedLoaded))
            }
        }
    }

    fun onOpenUserProfileById(userId: String, displayName: String? = null, avatarUrl: String? = null, avatarLetter: String? = null) {
        val existing = _uiState.value.friends.firstOrNull { it.id == userId }
            ?: _uiState.value.friendRequests.firstOrNull { it.sender.id == userId }?.sender
            ?: _uiState.value.suggestedPeople.firstOrNull { it.id == userId }
            ?: NebulaUser(
                id = userId,
                username = "@user",
                displayName = displayName ?: "User",
                avatarLetter = avatarLetter ?: "U",
                avatarUrl = avatarUrl,
                bio = "",
                status = "Offline"
            )
        onOpenPublicProfile(existing)
    }

    fun onOpenEditProfile() {
        navigateTo(FullScreenDestination.EditProfile)
    }

    fun onOpenAccountSettings() {
        navigateTo(FullScreenDestination.Account)
    }

    /**
     * The app menu's "Account" row lands straight on session management, which is
     * the whole point of that destination: devices signed in, revoke, sign out.
     */
    fun onOpenAccountDetail() {
        navigateTo(FullScreenDestination.Account)
    }

    /**
     * Horizontal swipe across the tab shell. Tabs are stepped in the same order the
     * bottom bar draws them, so the gesture and the bar can never disagree about
     * what "next" means. Swiping past either end simply stays put.
     */
    fun onSwipeToAdjacentTab(forward: Boolean) {
        val tabs = NebulaTab.entries
        val currentIndex = tabs.indexOf(_uiState.value.currentTab)
        if (currentIndex < 0) return
        val targetIndex = (currentIndex + if (forward) 1 else -1).coerceIn(0, tabs.lastIndex)
        if (targetIndex != currentIndex) onTabSelected(tabs[targetIndex])
    }

    fun onOpenGoogleAccountSettings() {
        navigateTo(FullScreenDestination.GoogleAccount)
    }

    fun onOpenBlockedUsers() {
        navigateTo(FullScreenDestination.BlockedUsers)
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

    fun updateOptimisticProfile(
        bannerIndex: Int? = null,
        bannerUrl: String? = null,
        avatarUrl: String? = null
    ) {
        val current = _uiState.value.userProfile
        val updated = current.copy(
            bannerIndex = bannerIndex ?: current.bannerIndex,
            bannerUrl = if (bannerIndex != null && bannerUrl == null) null else (bannerUrl ?: current.bannerUrl),
            avatarUri = avatarUrl ?: current.avatarUri,
            isCustomAvatar = if (avatarUrl != null) true else current.isCustomAvatar
        )
        sessionManager?.saveUserProfile(updated)
        _uiState.value = _uiState.value.copy(userProfile = updated)
        viewModelScope.launch {
            profileRepository.setLoggedInUser(updated)
        }
    }

    /**
     * Persists a profile edit.
     *
     * The screen only closes once the server has accepted the write. The old flow
     * navigated back and printed "Profile saved" before the request finished, so a
     * rejected edit (taken username, tier-gated status, too many tags) looked like
     * a success until the next launch restored the old values from the server.
     */
    fun onSaveProfile(
        displayName: String,
        username: String,
        bio: String,
        status: String,
        bannerIndex: Int,
        interests: List<String>,
        isFriendCountPublic: Boolean = true,
        avatarUrl: String? = null,
        bannerUrl: String? = null,
        customStatus: String = _uiState.value.userProfile.customStatus,
        onResult: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val rawClean = username.trim().replace("^@".toRegex(), "").lowercase()
            val cleanUsername = if (rawClean.isNotBlank()) rawClean else {
                _uiState.value.userProfile.username?.removePrefix("@")?.lowercase()
                    ?: _uiState.value.userProfile.email.substringBefore("@").filter { it.isLetterOrDigit() || it == '_' }.take(15).lowercase()
            }.ifEmpty { "nebula_user" }

            if (cleanUsername.length !in 3..16 || !cleanUsername.matches("^[a-z0-9][a-z0-9_]*[a-z0-9]$".toRegex())) {
                showToast("Username must be 3-16 characters, with no leading or trailing underscore")
                onResult(false)
                return@launch
            }

            val current = _uiState.value.userProfile

            // Optimistic paint of the editor's own values so the profile behind it
            // already looks right, but the route is only popped after the server
            // confirms — see the success branch below.
            val optimisticProfile = current.copy(
                displayName = displayName,
                username = "@$cleanUsername",
                bio = bio,
                status = status,
                customStatus = customStatus,
                bannerIndex = bannerIndex,
                bannerUrl = bannerUrl,
                interests = interests,
                avatarUri = avatarUrl ?: current.avatarUri,
                avatarFallbackLetter = displayName.firstOrNull()?.uppercase() ?: "N",
                isCustomAvatar = !avatarUrl.isNullOrEmpty() || current.isCustomAvatar,
                isFriendCountPublic = isFriendCountPublic,
                isOnboardingCompleted = true
            )
            profileRepository.setLoggedInUser(optimisticProfile)
            sessionManager?.saveUserProfile(optimisticProfile)
            _uiState.value = _uiState.value.copy(
                userProfile = optimisticProfile,
                isFriendCountPublic = isFriendCountPublic
            )

            // Only send the tier-gated presence fields when they actually changed.
            // Otherwise an untouched "offline" value blocks every other edit on a
            // Basic account, which is exactly what users were hitting.
            val statusChanged = !status.equals(current.status, ignoreCase = true)
            val customStatusChanged = customStatus != current.customStatus
            suspend fun push(sendStatus: Boolean, sendCustom: Boolean): com.nebula.app.data.backend.NebulaApiClient.ApiResponse<org.json.JSONObject> =
                com.nebula.app.data.backend.NebulaApiClient.updateProfile(
                    displayName = displayName,
                    username = cleanUsername,
                    bio = bio,
                    status = if (sendStatus) status else null,
                    customStatus = if (sendCustom) customStatus else null,
                    bannerIndex = bannerIndex,
                    interests = interests,
                    avatarUrl = avatarUrl,
                    isFriendCountPublic = isFriendCountPublic,
                    bannerUrl = bannerUrl,
                    isOnboardingCompleted = true
                )

            var apiRes = push(statusChanged, customStatusChanged)

            // A lapsed subscription can leave the stored status on a tier the account
            // no longer has. The server rejects the whole document for it, so retry
            // once without the presence fields instead of losing the name, bio and
            // artwork the user actually came to edit.
            if (apiRes is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Error &&
                apiRes.statusCode == 403 &&
                (statusChanged || customStatusChanged) &&
                apiRes.message.contains("status", ignoreCase = true)
            ) {
                apiRes = push(sendStatus = false, sendCustom = false)
            }

            when (apiRes) {
                is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Success -> {
                    // Trust the server's copy: it clamps the banner index, trims tags to
                    // the tier allowance and is the only source of the final URLs.
                    val saved = apiRes.data.optJSONObject("data")
                    val serverProfile = optimisticProfile.copy(
                        displayName = saved?.optString("displayName")?.takeIf { it.isNotBlank() } ?: displayName,
                        username = saved?.optString("username")?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: optimisticProfile.username,
                        bio = saved?.optString("bio") ?: bio,
                        bannerIndex = if (saved?.has("bannerIndex") == true) saved.optInt("bannerIndex", bannerIndex) else bannerIndex,
                        bannerUrl = saved?.optString("bannerUrl")?.takeIf { it.isNotBlank() } ?: bannerUrl,
                        avatarUri = saved?.optString("avatarUrl")?.takeIf { it.isNotBlank() } ?: (avatarUrl ?: current.avatarUri),
                        interests = saved?.optJSONArray("interests")?.let { arr ->
                            (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
                        } ?: interests
                    )
                    profileRepository.setLoggedInUser(serverProfile)
                    sessionManager?.saveUserProfile(serverProfile)

                    val prev = if (navBackStack.isNotEmpty()) navBackStack.pop() else null
                    _uiState.value = _uiState.value.copy(
                        userProfile = serverProfile,
                        isFriendCountPublic = isFriendCountPublic,
                        fullScreenDestination = prev
                    )
                    showToast("Profile saved")
                    refreshAll(showIndicator = false)
                    onResult(true)
                }
                is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Error -> {
                    // Put the old values back so nothing presents an unsaved edit as real.
                    profileRepository.setLoggedInUser(current)
                    sessionManager?.saveUserProfile(current)
                    _uiState.value = _uiState.value.copy(
                        userProfile = current,
                        isFriendCountPublic = current.isFriendCountPublic
                    )
                    val message = when {
                        apiRes.statusCode == 409 -> "@$cleanUsername is already taken"
                        apiRes.statusCode == 403 -> apiRes.message
                        apiRes.statusCode == 400 -> apiRes.message
                        apiRes.statusCode == 401 -> "Your session expired. Sign in again."
                        else -> apiRes.message.ifBlank { "Could not reach Nebula. Check your connection." }
                    }
                    showToast(message)
                    onResult(false)
                }
            }
        }
    }


    fun onAcceptFriendRequest(requestId: String, onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            if (socialRepository.acceptFriendRequest(requestId)) {
                refreshAll()
                showToast("Friend request accepted")
                onSuccess?.invoke()
            } else {
                showToast("Friend request could not be accepted. Try again.")
            }
        }
    }

    fun onDeclineFriendRequest(requestId: String) {
        viewModelScope.launch {
            if (!socialRepository.declineFriendRequest(requestId)) {
                showToast("Friend request could not be declined. Try again.")
            } else refreshAll()
        }
    }

    /**
     * Keeps an open profile route in step with a relationship change. The profile screen
     * renders the route's user, so patching only the repository lists would leave the
     * button on the old label until the screen is reopened.
     */
    private fun patchProfileRelationship(userId: String, pending: Boolean) {
        fun NebulaUser.patched(): NebulaUser = if (id != userId) this else copy(
            friendRequestPending = pending,
            friendRequestReceived = false
        )
        _uiState.value.activeFriendProfile?.let { active ->
            if (active.id == userId) {
                _uiState.value = _uiState.value.copy(activeFriendProfile = active.patched())
            }
        }
        val dest = _uiState.value.fullScreenDestination
        if (dest is FullScreenDestination.PublicProfile && dest.user.id == userId) {
            _uiState.value = _uiState.value.copy(
                fullScreenDestination = FullScreenDestination.PublicProfile(dest.user.patched())
            )
        }
    }

    fun onAddSuggestedFriend(user: NebulaUser) {
        // Optimistic: the row flips at once, then rolls back if the server refuses.
        patchProfileRelationship(user.id, pending = true)
        viewModelScope.launch {
            if (socialRepository.addFriend(user)) {
                refreshAll()
                showToast("Friend request sent to ${user.displayName.split(" ").first()}")
            } else {
                patchProfileRelationship(user.id, pending = false)
                showToast("Friend request could not be sent. Try again.")
            }
        }
    }

    fun onAddFriend(user: NebulaUser) {
        onAddSuggestedFriend(user)
    }

    fun onCancelFriendRequest(user: NebulaUser) {
        patchProfileRelationship(user.id, pending = false)
        viewModelScope.launch {
            if (!socialRepository.withdrawFriendRequest(user.id)) {
                patchProfileRelationship(user.id, pending = true)
                showToast("Friend request could not be cancelled. Try again.")
            } else refreshAll()
        }
    }

    fun onRemoveFriend(user: NebulaUser) {
        viewModelScope.launch {
            if (socialRepository.removeFriend(user.id)) showToast("${user.displayName} removed from friends")
            else showToast("Friend could not be removed. Try again.")
        }
    }

    fun onAcceptProfileRequest(user: NebulaUser) {
        val requestId = user.friendRequestId ?: return showToast("Request is no longer available")
        onAcceptFriendRequest(requestId)
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
        refreshAll(showIndicator = false)
        markVisibleNotificationsSeen()
    }

    /**
     * Seen notifications clear themselves: once the sheet has been open long
     * enough for a row to be read, it is marked read and dropped from the list.
     * Friend requests stay because they are still actionable.
     */
    private fun markVisibleNotificationsSeen() {
        viewModelScope.launch {
            delay(2200)
            if (_uiState.value.activeModalSheet != ActiveModalSheet.Notifications) return@launch
            // Anything the user has had on screen counts as seen, whether or not
            // it was already read. Actions on friend requests stay put.
            val seen = _uiState.value.notifications.filter { !it.isRead }
            for (notif in seen) {
                val marked = com.nebula.app.data.backend.NebulaApiClient.markNotificationRead(notif.id)
                if (marked is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Success) {
                    _uiState.value = _uiState.value.copy(
                        notifications = if (notif.type == NotificationType.FRIEND_REQUEST) {
                            // A seen request moves to the archived section instead of
                            // vanishing: it is still actionable from the friends page.
                            _uiState.value.notifications.map {
                                if (it.id == notif.id) it.copy(isRead = true) else it
                            }
                        } else {
                            _uiState.value.notifications.filterNot { it.id == notif.id }
                        }
                    )
                }
            }
        }
    }


    fun onVotePoll(optionId: String) {
        viewModelScope.launch {
            if (roomRepository.votePoll(optionId)) showToast("Vote recorded!")
            else showToast("Your vote could not be recorded. You may already have voted.")
        }
    }

    fun onToggleFriendCountPrivacy() {
        viewModelScope.launch {
            val profile = _uiState.value.userProfile
            val nextValue = !_uiState.value.isFriendCountPublic
            val result = com.nebula.app.data.backend.NebulaApiClient.updateProfile(
                displayName = profile.displayName,
                username = profile.username?.removePrefix("@"),
                bio = profile.bio,
                status = profile.status,
                bannerIndex = profile.bannerIndex,
                interests = profile.interests,
                avatarUrl = profile.avatarUri,
                isFriendCountPublic = nextValue,
                dateOfBirth = profile.dateOfBirth,
                gender = profile.gender,
                bannerUrl = profile.bannerUrl
            )
            if (result is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Success) {
                val updated = profile.copy(isFriendCountPublic = nextValue)
                profileRepository.setLoggedInUser(updated)
                sessionManager?.saveUserProfile(updated)
                _uiState.value = _uiState.value.copy(
                    userProfile = updated,
                    isFriendCountPublic = nextValue
                )
                showToast(if (nextValue) "Friend list is now public" else "Friend list is now private")
            } else {
                showToast("Privacy setting could not be saved. Try again.")
            }
        }
    }

    /**
     * Persists one profile block's privacy switch. Room activity and top content are
     * independent, so each toggle sends only its own flag and the server keeps the
     * other one exactly as it was.
     */
    fun onToggleRoomActivityPrivacy() {
        viewModelScope.launch {
            val profile = _uiState.value.userProfile
            val nextValue = !_uiState.value.isRoomActivityPublic
            val result = com.nebula.app.data.backend.NebulaApiClient.updateProfile(
                displayName = profile.displayName,
                username = profile.username?.removePrefix("@"),
                bio = profile.bio,
                status = profile.status,
                bannerIndex = profile.bannerIndex,
                interests = profile.interests,
                avatarUrl = profile.avatarUri,
                isRoomActivityPublic = nextValue,
                dateOfBirth = profile.dateOfBirth,
                gender = profile.gender,
                bannerUrl = profile.bannerUrl
            )
            if (result is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Success) {
                _uiState.value = _uiState.value.copy(isRoomActivityPublic = nextValue)
                showToast(if (nextValue) "Room activity is now public" else "Room activity is now private")
            } else {
                showToast("Privacy setting could not be saved. Try again.")
            }
        }
    }

    fun onToggleTopContentPrivacy() {
        viewModelScope.launch {
            val profile = _uiState.value.userProfile
            val nextValue = !_uiState.value.isTopContentPublic
            val result = com.nebula.app.data.backend.NebulaApiClient.updateProfile(
                displayName = profile.displayName,
                username = profile.username?.removePrefix("@"),
                bio = profile.bio,
                status = profile.status,
                bannerIndex = profile.bannerIndex,
                interests = profile.interests,
                avatarUrl = profile.avatarUri,
                isTopContentPublic = nextValue,
                dateOfBirth = profile.dateOfBirth,
                gender = profile.gender,
                bannerUrl = profile.bannerUrl
            )
            if (result is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Success) {
                _uiState.value = _uiState.value.copy(isTopContentPublic = nextValue)
                showToast(if (nextValue) "Top content is now public" else "Top content is now private")
            } else {
                showToast("Privacy setting could not be saved. Try again.")
            }
        }
    }

    fun onSelectSubscriptionTier(tier: com.nebula.app.domain.model.NebulaSubscriptionTier) {
        if (tier == _uiState.value.userProfile.subscriptionTier) return

        // Choosing a plan is not a verified purchase. Keep the authoritative profile and
        // entitlement unchanged until Google Play Billing returns a verified receipt.
        showToast("Purchases are not available yet. Your plan has not been changed.")
    }

    fun onExportSavedVault() {
        showToast("Media export is not available yet. No files were exported.")
    }

    fun onPurgeSavedVault() {
        showToast("Vault storage is not available yet. No saved media was deleted.")
    }

    fun onDismissNotification(id: String) {
        viewModelScope.launch {
            when (com.nebula.app.data.backend.NebulaApiClient.deleteNotification(id)) {
                is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Success -> {
                    _uiState.value = _uiState.value.copy(
                        notifications = _uiState.value.notifications.filterNot { it.id == id }
                    )
                }
                is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Error ->
                    showToast("Notification could not be dismissed. Try again.")
            }
        }
    }

    fun onClearAllNotifications() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(notifications = emptyList())
            when (com.nebula.app.data.backend.NebulaApiClient.clearAllNotifications()) {
                is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Success -> {
                    showToast("Notifications cleared")
                }
                is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Error -> {
                    refreshAll()
                    showToast("Failed to clear notifications")
                }
            }
        }
    }

    fun onAcceptNotification(notif: com.nebula.app.domain.model.NebulaNotification) {
        when (notif.type) {
            com.nebula.app.domain.model.NotificationType.FRIEND_REQUEST -> {
                notif.relatedId?.let { requestId ->
                    onAcceptFriendRequest(requestId) {
                        viewModelScope.launch {
                            com.nebula.app.data.backend.NebulaApiClient.markNotificationRead(notif.id)
                            onDismissNotification(notif.id)
                        }
                    }
                }
            }
            com.nebula.app.domain.model.NotificationType.DIRECT_MESSAGE -> {
                viewModelScope.launch {
                    when (com.nebula.app.data.backend.NebulaApiClient.markNotificationRead(notif.id)) {
                        is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Success -> {
                            _uiState.value = _uiState.value.copy(
                                notifications = _uiState.value.notifications.map {
                                    if (it.id == notif.id) it.copy(isRead = true) else it
                                }
                            )
                        }
                        is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Error ->
                            showToast("Notification could not be marked read. Try again.")
                    }
                    notif.relatedId?.let { onSelectSocialConversation(it) }
                }
            }
            com.nebula.app.domain.model.NotificationType.ROOM_INVITE,
            com.nebula.app.domain.model.NotificationType.ROOM_STARTED -> {
                viewModelScope.launch {
                    when (com.nebula.app.data.backend.NebulaApiClient.markNotificationRead(notif.id)) {
                        is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Success -> {
                            _uiState.value = _uiState.value.copy(
                                notifications = _uiState.value.notifications.map {
                                    if (it.id == notif.id) it.copy(isRead = true) else it
                                }
                            )
                        }
                        is com.nebula.app.data.backend.NebulaApiClient.ApiResponse.Error ->
                            showToast("Notification could not be marked read. Try again.")
                    }
                }
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
                    val finalProfile = result.profile
                    _showWelcomeBack.value = finalProfile.isOnboardingCompleted
                    profileRepository.setLoggedInUser(finalProfile)
                    sessionManager?.saveUserProfile(finalProfile)
                    _uiState.value = _uiState.value.copy(
                        userProfile = finalProfile,
                        activeSubscriptionTier = finalProfile.subscriptionTier,
                        isAuthLoading = false,
                        authErrorMessage = null,
                        quickToastMessage = if (result.replacedOldestDevice) {
                            // The user needs to know a device was signed out, or the next
                            // unexplained sign-out on that device looks like a bug.
                            "Connected as ${finalProfile.displayName} · signed out your oldest device (2 allowed)"
                        } else {
                            "Connected as ${finalProfile.displayName}"
                        }
                    )
                    refreshAll()
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

    fun signOutGoogle() {
        val manager = authManager
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAuthLoading = true)
            runCatching { roomRepository.leaveRoom() }
            (roomRepository as? RoomRepositoryImpl)?.disconnectRealtime()
            // Drop the live DM channel before the token is revoked, or it reconnects
            // with a session that no longer exists.
            socialRepository.stopRealtime()
            runCatching { com.nebula.app.data.backend.NebulaApiClient.logout() }
            manager?.signOut()
            sessionManager?.clearSession()
            tearDownSignedInState("Signed out of Nebula")
        }
    }

    /**
     * The session is already gone server-side. Drop every local artefact and return to
     * the sign-in screen without calling logout, which would fail by definition.
     */
    private suspend fun handleForcedSignOut(reason: String) {
        if (!_uiState.value.userProfile.isLoggedIn) return
        runCatching { roomRepository.leaveRoom() }
        (roomRepository as? RoomRepositoryImpl)?.disconnectRealtime()
        socialRepository.stopRealtime()
        runCatching { authManager?.signOut() }
        sessionManager?.clearSession()
        tearDownSignedInState(reason)
    }

    private suspend fun tearDownSignedInState(message: String) {
        (socialRepository as? SocialRepositoryImpl)?.clearLocalState()
        chatRepository.clearAllLocalData()
        val loggedOutProfile = com.nebula.app.domain.model.UserProfile(isLoggedIn = false)
        profileRepository.setLoggedInUser(loggedOutProfile)
        com.nebula.app.data.backend.NebulaApiClient.authToken = null
        _uiState.value = _uiState.value.copy(
            isAuthLoading = false,
            userProfile = loggedOutProfile,
            activeModalSheet = null,
            fullScreenDestination = null,
            activeRoom = null,
            activeSocialConversation = null,
            socialConversations = emptyList(),
            friends = emptyList(),
            friendRequests = emptyList(),
            suggestedPeople = emptyList(),
            notifications = emptyList(),
            activeSubscriptionTier = NebulaSubscriptionTier.FREE,
            authErrorMessage = message,
            quickToastMessage = message
        )
    }
}
