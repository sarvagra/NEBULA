package com.nebula.app.ui.home

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.nebula.app.ui.components.PABottomSheet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.domain.model.NebulaTab
import com.nebula.app.domain.model.NebulaUser
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.NebulaSidebarMenu
import com.nebula.app.ui.components.NABottomBar
import com.nebula.app.ui.components.NebulaLogo
import com.nebula.app.ui.components.NebulaPullRefresh
import com.nebula.app.ui.dialogs.AboutSheet
import com.nebula.app.ui.dialogs.CreateRoomSheet
import com.nebula.app.ui.dialogs.FriendProfileSheet
import com.nebula.app.ui.dialogs.HistorySheet
import com.nebula.app.ui.dialogs.NebulaAISettingsSheet
import com.nebula.app.ui.dialogs.NebulaSettingsSheet
import com.nebula.app.ui.dialogs.NotificationsSheet
import com.nebula.app.ui.dialogs.PermissionsSheet
import com.nebula.app.ui.dialogs.PremiumPlanSheet
import com.nebula.app.ui.dialogs.PrivacyPolicySheet
import com.nebula.app.ui.dialogs.PrivacyVaultSheet
import com.nebula.app.ui.dialogs.ProfileEditorSheet
import com.nebula.app.ui.dialogs.ThemeSelectorSheet
import com.nebula.app.ui.membership.NebulaMembershipScreen
import com.nebula.app.ui.profile.NebulaEditProfileScreen
import com.nebula.app.ui.profile.NebulaPublicProfileScreen
import com.nebula.app.ui.settings.NebulaAboutScreen
import com.nebula.app.ui.settings.NebulaPrivacyPolicyScreen
import com.nebula.app.ui.settings.NebulaSettingsScreen

/**
 * HomeScreen:
 * The primary container for NEBULA.
 * Manages top app bar, 5-tab bottom navigation, full-screen Room / Chat views,
 * the profile navigation sidebar drawer, and modal sheets.
 */
@Composable
@OptIn(ExperimentalMaterialApi::class)
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val theme = Theme.current
    val context = LocalContext.current

    // "New chat" used to be a button with an empty callback. It now opens a
    // picker over the people you are connected to, and starting a conversation
    // goes through the same path as messaging from a profile or a room.
    var showNewChatSheet by remember { mutableStateOf(false) }

    // Quick Toast feedback
    LaunchedEffect(uiState.quickToastMessage) {
        uiState.quickToastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    // One account, one room, one device. The server refuses the second device, so say
    // so plainly instead of letting the tap look like a network failure.
    uiState.deviceConflictMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeviceConflict() },
            title = { Text("Room is open on another device") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissDeviceConflict() }) { Text("Got it") }
            }
        )
    }

    // Modern Android Back Button Handling
    if (showNewChatSheet) {
        BackHandler { showNewChatSheet = false }
    } else if (uiState.fullScreenDestination != null) {
        BackHandler { viewModel.onNavigateBack() }
    } else if (uiState.activeModalSheet != null) {
        BackHandler { viewModel.onDismissModalSheet() }
    } else if (uiState.isSidebarOpen) {
        BackHandler { viewModel.onDismissSidebar() }
    } else if (uiState.activeRoom != null) {
        BackHandler { viewModel.onLeaveRoom() }
    } else if (uiState.activeSocialConversation != null) {
        BackHandler { viewModel.onCloseSocialConversation() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(theme.colors.background)
    ) {
        // 1. MAIN CONTENT (Full screen destination, room, chat detail, or tab screen)
        if (uiState.fullScreenDestination != null) {
            when (val dest = uiState.fullScreenDestination!!) {
                is FullScreenDestination.PublicProfile -> {
                    val isCurrent = dest.user.id == uiState.userProfile.id || dest.user.id == NebulaUser.CurrentUser.id || dest.user.username == uiState.userProfile.username || dest.user.username == NebulaUser.CurrentUser.username
                    NebulaPublicProfileScreen(
                        user = if (isCurrent) {
                            dest.user.copy(
                                id = uiState.userProfile.id,
                                username = uiState.userProfile.username?.let { if (it.startsWith("@")) it else "@$it" } ?: "@nebula_user",
                                displayName = uiState.userProfile.displayName,
                                email = uiState.userProfile.email,
                                avatarLetter = uiState.userProfile.avatarFallbackLetter,
                                bio = uiState.userProfile.bio,
                                status = uiState.userProfile.status,
                                bannerIndex = uiState.userProfile.bannerIndex,
                                avatarUrl = uiState.userProfile.avatarUri,
                                bannerUrl = uiState.userProfile.bannerUrl,
                                interests = uiState.userProfile.interests
                            )
                        } else dest.user,
                        isCurrentUser = isCurrent,
                        onBack = { viewModel.onNavigateBack() },
                        onMessage = { friend ->
                            viewModel.onNavigateBack()
                            val conv = uiState.socialConversations.firstOrNull { it.partnerUser?.id == friend.id }
                            if (conv != null) {
                                viewModel.onSelectSocialConversation(conv.id)
                            }
                        },
                        onAddFriend = { friend -> viewModel.onAddFriend(friend) },
                        onCancelRequest = { friend -> viewModel.onCancelFriendRequest(friend) },
                        onRemoveFriend = { friend -> viewModel.onRemoveFriend(friend) },
                        onAcceptRequest = { friend -> viewModel.onAcceptProfileRequest(friend) },
                        onBlockUser = { friend -> viewModel.onBlockUser(friend) },
                        onInviteToRoom = { friend ->
                            viewModel.onInviteFriendToActiveRoom(friend)
                        },
                        onOpenEditProfile = {
                            viewModel.onOpenEditProfile()
                        },
                        viewerFriendsCount = uiState.friends.size
                    )
                }

                is FullScreenDestination.EditProfile -> {
                    NebulaEditProfileScreen(
                        userProfile = uiState.userProfile,
                        onSave = { dName, uName, bio, status, bannerIdx, interests, isFriendPublic, avatarUrl, bannerUrl, customStatus, onResult ->
                            viewModel.onSaveProfile(dName, uName, bio, status, bannerIdx, interests, isFriendPublic, avatarUrl, bannerUrl, customStatus, onResult)
                        },
                        onBack = { viewModel.onNavigateBack() },
                        onOpenMembership = { viewModel.onOpenMembership() },
                        friendsCount = uiState.friends.size
                    )
                }

                is FullScreenDestination.Settings -> {
                    NebulaSettingsScreen(
                        onBack = { viewModel.onNavigateBack() },
                        onOpenAccount = { viewModel.onOpenAccountSettings() },
                        onOpenGoogleAccount = { viewModel.onOpenGoogleAccountSettings() },
                        onOpenBlockedUsers = { viewModel.onOpenBlockedUsers() },
                        onOpenAppearance = { viewModel.onOpenThemes() },
                        onOpenPermissions = { viewModel.onOpenPermissions() },
                        onOpenPrivacyPolicy = { viewModel.onOpenPrivacyPolicy() },
                        onOpenAbout = { viewModel.onOpenAbout() },
                        onOpenNebulaPremium = { viewModel.onOpenMembership() }
                    )
                }

                is FullScreenDestination.Account -> {
                    com.nebula.app.ui.settings.NebulaAccountDetailScreen(
                        userProfile = uiState.userProfile,
                        onBack = { viewModel.onNavigateBack() },
                        onEditProfile = { viewModel.onOpenEditProfile() },
                        onSignOut = { viewModel.signOutGoogle() }
                    )
                }

                is FullScreenDestination.GoogleAccount -> {
                    LaunchedEffect(Unit) { viewModel.refreshEmailChangeStatus() }
                    com.nebula.app.ui.settings.NebulaGoogleAccountScreen(
                        userProfile = uiState.userProfile,
                        onBack = { viewModel.onNavigateBack() },
                        onSwitchGoogleAccount = { viewModel.onSwitchGoogleAccount(context) },
                        onSignOut = { viewModel.signOutGoogle() },
                        emailChangeFreeNow = uiState.emailChangeFreeNow,
                        emailChangeAvailableFrom = uiState.emailChangeAvailableFrom,
                        emailChangePriceInr = uiState.emailChangePriceInr,
                        isRebinding = uiState.isRebindingEmail
                    )
                }

                is FullScreenDestination.BlockedUsers -> {
                    com.nebula.app.ui.settings.NebulaBlockedUsersScreen(
                        onBack = { viewModel.onNavigateBack() }
                    )
                }

                is FullScreenDestination.About -> {
                    NebulaAboutScreen(
                        onBack = { viewModel.onNavigateBack() },
                        onOpenPrivacyPolicy = { viewModel.onOpenPrivacyPolicy() }
                    )
                }

                is FullScreenDestination.PrivacyPolicy -> {
                    NebulaPrivacyPolicyScreen(
                        onBack = { viewModel.onNavigateBack() }
                    )
                }

                is FullScreenDestination.Membership -> {
                    NebulaMembershipScreen(
                        currentTier = uiState.activeSubscriptionTier,
                        onSelectTier = { viewModel.onSelectSubscriptionTier(it) },
                        onBack = { viewModel.onNavigateBack() }
                    )
                }
            }
        } else if (uiState.activeRoom != null) {
            NebulaRoomScreen(
                room = uiState.activeRoom!!,
                currentUser = uiState.userProfile,
                friends = uiState.friends,
                friendRequests = uiState.friendRequests,
                suggestedPeople = uiState.suggestedPeople,
                queue = uiState.roomQueue,
                actions = NebulaRoomActions(
                    onAcceptFriendRequest = { viewModel.onAcceptFriendRequest(it) },
                    onDeclineFriendRequest = { viewModel.onDeclineFriendRequest(it) },
                    onCancelFriendRequest = { viewModel.onCancelFriendRequest(it) },
                    onKickParticipant = { viewModel.onKickRoomParticipant(it) },
                    onAddFriend = { viewModel.onAddFriend(it) },
                    onMessageFriend = { friend -> viewModel.onMessageFriend(friend) },
                    onLeaveRoom = { newHostId -> viewModel.onLeaveRoom(newHostId) },
                    onRefreshRoom = { viewModel.refreshActiveRoom() },
                    onHostPlaybackSample = { positionMs, playing -> viewModel.onHostPlaybackSample(positionMs, playing) },
                    onHostVideoEnded = { viewModel.onHostVideoEnded(it) },
                    onRestartVideo = { viewModel.onRestartRoomVideo() },
                    onViewParticipantProfile = { viewModel.onOpenFriendProfile(it) },
                    onUpdateRoomSettings = { name, max, tags ->
                        viewModel.onUpdateRoomSettings(name, max, tags)
                    },
                    onTransferHost = { newHostId, random ->
                        viewModel.onTransferHost(newHostId, random)
                    },
                    onTogglePlayPause = { viewModel.onToggleRoomPlayPause() },
                    onSendRoomMessage = { viewModel.onSendRoomMessage(it) },
                    onSendReaction = { viewModel.onSendRoomReaction(it) },
                    onVotePoll = { viewModel.onVotePoll(it) },
                    onSeekTo = { viewModel.onSeekTo(it) },
                    onUpdateMedia = { title, url -> viewModel.onUpdateRoomMedia(title, url) },
                    onAddToQueue = { title, url -> viewModel.onAddToRoomQueue(title, url) },
                    onVoteSkipQueueItem = { viewModel.onVoteSkipQueueItem(it) },
                    onAdvanceQueue = { viewModel.onAdvanceRoomQueue() },
                    onRemoveQueueItem = { viewModel.onRemoveQueueItem(it) },
                    onInviteFriend = { viewModel.onInviteFriendToActiveRoom(it) },
                    onShareRoomInChat = { viewModel.onShareRoomIntoDm(it) }
                )
            )
        } else if (uiState.activeSocialConversation != null) {
            NebulaChatDetailScreen(
                conversation = uiState.activeSocialConversation!!,
                showInlineSuggestions = uiState.showInlineSuggestions,
                onBack = { viewModel.onCloseSocialConversation() },
                onSendMessage = { viewModel.onSendDirectMessage(it) },
                onSendAttachment = { text, url, type, spoiler, mime -> viewModel.onSendDirectAttachment(text, url, type, spoiler, mime) },
                onSendAttachments = { text, urls, type, spoiler, mime -> viewModel.onSendDirectAttachments(text, urls, type, spoiler, mime) },
                onEditMessage = { id, text -> viewModel.onEditDirectMessage(id, text) },
                onUnsendMessage = { id -> viewModel.onUnsendDirectMessage(id) },
                onRetryMessage = { clientKey -> viewModel.onRetryDirectMessage(clientKey) },
                onOpenProfile = { uiState.activeSocialConversation?.partnerUser?.let { viewModel.onOpenFriendProfile(it) } },
                onInviteToRoom = {
                    val partner = uiState.activeSocialConversation?.partnerUser
                    if (partner != null) {
                        viewModel.onInviteFromChat(partner)
                    } else {
                        viewModel.onCreateRoomClick()
                    }
                },
                onJoinRoomByCode = { viewModel.onJoinRoomByCode(it) },
                isOnline = uiState.isOnline
            )
        } else {
            // STANDARD TAB SHELL
            // Profile owns the full screen: its header banner runs under the status
            // bar and insets only its own floating controls, so the shell must not
            // add a second top inset for that tab.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (uiState.currentTab == NebulaTab.PROFILE) Modifier
                        else Modifier.statusBarsPadding()
                    )
            ) {
                // TOP BAR: Avatar (opens overlay), Wordmark "NEBULA", Notifications banner, Quick Room
                // Hide on Profile tab because Profile has its own dedicated header with Settings gear only (no redundant bell)
                if (uiState.currentTab != NebulaTab.PROFILE) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                    // Left: Avatar button with status badge (taps open profile sidebar drawer)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(theme.colors.surfaceElevated)
                                    .border(1.5.dp, theme.colors.surfaceBorder, CircleShape)
                                    .clickable { viewModel.onOpenSidebar() },
                                contentAlignment = Alignment.Center
                            ) {
                                com.nebula.app.ui.components.NebulaAvatarImage(
                                    avatarUrl = uiState.userProfile.avatarUri,
                                    fallbackLetter = uiState.userProfile.avatarFallbackLetter,
                                    contentDescription = uiState.userProfile.displayName,
                                    modifier = Modifier.fillMaxSize(),
                                    fallbackTextSize = 14.sp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(theme.colors.successColor)
                                    .border(1.5.dp, theme.colors.background, CircleShape)
                            )
                        }

                        // Top heading for Home, Rooms, Chats, Friends: @actual_username
                        val currentHandle = uiState.userProfile.username?.let {
                            if (it.startsWith("@")) it else "@$it"
                        } ?: "@nebula_user"

                        Text(
                            text = currentHandle,
                            style = theme.typography.titleMedium.copy(
                                color = theme.colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                fontSize = 16.sp
                            )
                        )
                    }

                    // Right Actions: Quick Room TV icon first, then Notification bell icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 1. Quick Room icon (TV)
                        IconButton(
                            onClick = { viewModel.onCreateRoomClick() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = "Quick Room",
                                tint = theme.colors.primaryAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 2. Notifications Banner Icon Button with dynamic unread indicator
                        Box(contentAlignment = Alignment.TopEnd) {
                            IconButton(
                                onClick = { viewModel.onOpenNotifications() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsNone,
                                    contentDescription = "Notifications",
                                    tint = theme.colors.textPrimary,
                                    modifier = Modifier.size(21.dp)
                                )
                            }

                            val hasPendingNotices = uiState.notifications.any { !it.isRead } || uiState.friendRequests.isNotEmpty()
                            if (hasPendingNotices) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 4.dp, end = 4.dp)
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(theme.colors.primaryAccent)
                                        .align(Alignment.TopEnd)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                }

                // TAB CONTENT
                //
                // Each tab owns its own pull-to-refresh container so the gesture
                // works from the top of every tab's list, not only outside a list.
                //
                // A horizontal swipe moves to the neighbouring tab in bottom-bar order,
                // which is the gesture people already expect from a feed app. The
                // detector only claims the pointer once a horizontal drag passes touch
                // slop, so vertical list scrolling inside each tab is untouched.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(uiState.currentTab) {
                            var accumulated = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { accumulated = 0f },
                                onDragCancel = { accumulated = 0f },
                                onDragEnd = {
                                    val threshold = 56.dp.toPx()
                                    when {
                                        accumulated <= -threshold -> viewModel.onSwipeToAdjacentTab(forward = true)
                                        accumulated >= threshold -> viewModel.onSwipeToAdjacentTab(forward = false)
                                    }
                                    accumulated = 0f
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    accumulated += dragAmount
                                }
                            )
                        }
                ) {
                    when (uiState.currentTab) {
                        NebulaTab.HOME -> {
                            NebulaPullRefresh(
                                isRefreshing = uiState.isRefreshing,
                                onRefresh = { viewModel.refreshAll(showIndicator = true) }
                            ) {
                                NebulaHomeScreen(
                                    friends = uiState.friends,
                                    rooms = uiState.rooms,
                                    conversations = uiState.socialConversations,
                                    onJoinRoom = { viewModel.onJoinRoom(it) },
                                    onCreateRoomClick = { viewModel.onCreateRoomClick() },
                                    onOpenConversation = { viewModel.onSelectSocialConversation(it) },
                                    onFriendClick = { viewModel.onOpenFriendProfile(it) }
                                )
                            }
                        }

                        NebulaTab.ROOMS -> {
                            NebulaPullRefresh(
                                isRefreshing = uiState.isRefreshing,
                                onRefresh = { viewModel.refreshAll(showIndicator = true) }
                            ) {
                                NebulaRoomsScreen(
                                    rooms = uiState.rooms,
                                    onJoinRoom = { viewModel.onJoinRoom(it) },
                                    onCreateRoomClick = { viewModel.onCreateRoomClick() }
                                )
                            }
                        }

                        NebulaTab.CHATS -> {
                            NebulaPullRefresh(
                                isRefreshing = uiState.isRefreshing,
                                onRefresh = { viewModel.refreshAll(showIndicator = true) }
                            ) {
                                NebulaChatsScreen(
                                    conversations = uiState.socialConversations,
                                    onSelectConversation = { viewModel.onSelectSocialConversation(it) },
                                    onStartNewChat = { showNewChatSheet = true }
                                )
                            }
                        }

                        NebulaTab.FRIENDS -> {
                            NebulaPullRefresh(
                                isRefreshing = uiState.isRefreshing,
                                onRefresh = { viewModel.refreshAll(showIndicator = true) }
                            ) {
                                NebulaFriendsScreen(
                                    friends = uiState.friends,
                                    friendRequests = uiState.friendRequests,
                                    suggestedPeople = uiState.suggestedPeople,
                                    searchResults = uiState.searchResults,
                                    onSearchQuery = { viewModel.onSearchPeople(it) },
                                    onRefreshSuggestions = { viewModel.onRefreshSuggestions() },
                                    onMessageFriend = { friend -> viewModel.onMessageFriend(friend) },
                                    onInviteToRoom = { friend ->
                                        viewModel.onInviteFriendToActiveRoom(friend)
                                    },
                                    onFriendClick = { friend ->
                                        viewModel.onOpenFriendProfile(friend)
                                    },
                                    onAcceptRequest = { reqId ->
                                        viewModel.onAcceptFriendRequest(reqId)
                                    },
                                    onDeclineRequest = { reqId ->
                                        viewModel.onDeclineFriendRequest(reqId)
                                    },
                                    onAddSuggested = { user ->
                                        viewModel.onAddSuggestedFriend(user)
                                    },
                                    onWithdrawRequest = { user ->
                                        viewModel.onCancelFriendRequest(user)
                                    }
                                )
                            }
                        }

                        NebulaTab.PROFILE -> {
                            NebulaPullRefresh(
                                isRefreshing = uiState.isRefreshing,
                                onRefresh = { viewModel.refreshAll(showIndicator = true) }
                            ) {
                            NebulaProfileScreen(
                                user = NebulaUser.CurrentUser.copy(
                                    id = uiState.userProfile.id,
                                    username = uiState.userProfile.username ?: "@nebula_user",
                                    displayName = uiState.userProfile.displayName,
                                    email = uiState.userProfile.email,
                                    avatarLetter = uiState.userProfile.avatarFallbackLetter,
                                    avatarUrl = uiState.userProfile.avatarUri,
                                    bio = uiState.userProfile.bio,
                                    status = uiState.userProfile.status,
                                    bannerIndex = uiState.userProfile.bannerIndex,
                                    interests = uiState.userProfile.interests,
                                    bannerUrl = uiState.userProfile.bannerUrl,
                                    friendsCount = uiState.friends.size,
                                    tier = uiState.userProfile.subscriptionTier.name.lowercase(),
                                    roomsHostedCount = uiState.userProfile.roomsHostedCount,
                                    roomsJoinedCount = uiState.userProfile.roomsJoinedCount,
                                    timeInSyncHours = uiState.userProfile.timeInSyncHours
                                ),
                                friendsCount = uiState.friends.size,
                                isFriendCountPublic = uiState.isFriendCountPublic,
                                isRoomActivityPublic = uiState.isRoomActivityPublic,
                                isTopContentPublic = uiState.isTopContentPublic,
                                topTags = uiState.myTopTags,
                                onToggleRoomActivityPrivacy = { viewModel.onToggleRoomActivityPrivacy() },
                                onToggleTopContentPrivacy = { viewModel.onToggleTopContentPrivacy() },
                                onToggleFriendPrivacy = { viewModel.onToggleFriendCountPrivacy() },
                                onNavigateFriends = { viewModel.onTabSelected(NebulaTab.FRIENDS) },
                                onOpenEditProfile = { viewModel.onOpenEditProfile() },
                                onOpenSettings = { viewModel.onOpenSettings() },
                                onOpenPremiumPlans = { viewModel.onOpenMembership() },
                                onViewPublicProfile = {
                                    viewModel.onOpenPublicProfile(
                                        NebulaUser.CurrentUser.copy(
                                            id = uiState.userProfile.id,
                                            username = uiState.userProfile.username?.let { if (it.startsWith("@")) it else "@$it" } ?: "@nebula_user",
                                            displayName = uiState.userProfile.displayName,
                                            email = uiState.userProfile.email,
                                            avatarLetter = uiState.userProfile.avatarFallbackLetter,
                                            avatarUrl = uiState.userProfile.avatarUri,
                                            bannerUrl = uiState.userProfile.bannerUrl,
                                            bannerIndex = uiState.userProfile.bannerIndex,
                                            bio = uiState.userProfile.bio,
                                            status = uiState.userProfile.status,
                                            interests = uiState.userProfile.interests,
                                            friendsCount = uiState.friends.size,
                                            roomsHostedCount = uiState.userProfile.roomsHostedCount,
                                            roomsJoinedCount = uiState.userProfile.roomsJoinedCount
                                        )
                                    )
                                },
                                onSignInWithGoogle = { viewModel.signInWithGoogle(context) },
                                onSignOutGoogle = { viewModel.signOutGoogle() },
                                isAuthLoading = uiState.isAuthLoading,
                                activeTier = uiState.activeSubscriptionTier
                            )
                            }
                        }
                    }
                }
            }

            // 2. BOTTOM NAVIGATION BAR
            NABottomBar(
                currentTab = uiState.currentTab,
                onTabSelected = { tab -> viewModel.onTabSelected(tab) },
                hasUnreadChats = uiState.socialConversations.any { it.unreadCount > 0 },
                hasActiveRooms = uiState.rooms.isNotEmpty(),
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // 3. APP MENU (Account / Settings / About / Log out)
        NebulaSidebarMenu(
            isOpen = uiState.isSidebarOpen,
            onOpenAccount = {
                viewModel.onDismissSidebar()
                viewModel.onOpenAccountDetail()
            },
            onOpenSettings = {
                viewModel.onDismissSidebar()
                viewModel.onOpenSettings()
            },
            onOpenAbout = {
                viewModel.onDismissSidebar()
                viewModel.onOpenAbout()
            },
            onLogoutClick = { viewModel.onLogout() },
            onDismissRequest = { viewModel.onDismissSidebar() }
        )

        // 3b. START A NEW CONVERSATION
        PABottomSheet(
            isVisible = showNewChatSheet,
            onDismissRequest = { showNewChatSheet = false },
            title = "New message"
        ) {
            var query by remember { mutableStateOf("") }
            val matches = remember(query, uiState.friends) {
                if (query.isBlank()) uiState.friends
                else uiState.friends.filter {
                    it.displayName.contains(query, ignoreCase = true) ||
                        it.username.contains(query, ignoreCase = true)
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search connections") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = theme.colors.primaryAccent,
                    unfocusedBorderColor = theme.colors.surfaceBorder,
                    focusedTextColor = theme.colors.textPrimary,
                    unfocusedTextColor = theme.colors.textPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            if (matches.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (uiState.friends.isEmpty()) {
                            "No connections yet. Add someone from the Friends tab to start a conversation."
                        } else {
                            "Nobody matches \"$query\"."
                        },
                        style = theme.typography.bodySmall.copy(color = theme.colors.textSecondary),
                        textAlign = TextAlign.Center
                    )
                    if (uiState.friends.isEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Find people",
                            style = theme.typography.bodyMedium.copy(
                                color = theme.colors.primaryAccent,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    showNewChatSheet = false
                                    viewModel.onTabSelected(NebulaTab.FRIENDS)
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    matches.forEach { friend ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    showNewChatSheet = false
                                    viewModel.onMessageFriend(friend)
                                }
                                .padding(vertical = 10.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(theme.colors.primaryAccent.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = friend.displayName.take(1).uppercase(),
                                    style = theme.typography.bodyMedium.copy(
                                        color = theme.colors.primaryAccent,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.size(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = friend.displayName,
                                    style = theme.typography.bodyMedium.copy(
                                        color = theme.colors.textPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = friend.username,
                                    style = theme.typography.caption.copy(
                                        color = theme.colors.textTertiary,
                                        fontSize = 12.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. MODAL SHEETS & DIALOGS
        when (val sheet = uiState.activeModalSheet) {
            is ActiveModalSheet.CreateRoom -> {
                CreateRoomSheet(
                    isVisible = true,
                    onCreateRoom = { name, privacy, tags, maxParticipants, media ->
                        viewModel.onCreateRoom(name, privacy, tags, media, maxParticipants)
                    },
                    // Let the sheet surface a rejected link inline before create.
                    verifySourceLink = { url -> com.nebula.app.data.backend.NebulaApiClient.verifyMediaLink(url) },
                    onDismiss = { viewModel.onDismissModalSheet() }
                )
            }

            is ActiveModalSheet.FriendProfile -> {
                FriendProfileSheet(
                    isVisible = true,
                    user = sheet.user,
                    onMessage = { friend ->
                        val conv = uiState.socialConversations.firstOrNull { it.partnerUser?.id == friend.id }
                        if (conv != null) {
                            viewModel.onSelectSocialConversation(conv.id)
                        }
                    },
                    onInviteToRoom = { friend ->
                        viewModel.onInviteFriendToActiveRoom(friend)
                    },
                    onJoinTheirRoom = { roomId -> viewModel.onJoinRoom(roomId) },
                    onViewFullProfile = { friend -> viewModel.onOpenUserProfileById(friend.id) },
                    onDismiss = { viewModel.onDismissModalSheet() }
                )
            }

            is ActiveModalSheet.Notifications -> {
                NotificationsSheet(
                    isVisible = true,
                    notifications = uiState.notifications,
                    onAcceptNotification = { viewModel.onAcceptNotification(it) },
                    onDismissNotification = { viewModel.onDismissNotification(it) },
                    onClearAll = { viewModel.onClearAllNotifications() },
                    onOpenPreferences = {
                        viewModel.onDismissModalSheet()
                        viewModel.onOpenSettings()
                    },
                    onOpenUserProfile = { userId ->
                        viewModel.onDismissModalSheet()
                        viewModel.onOpenUserProfileById(userId)
                    },
                    onDismiss = { viewModel.onDismissModalSheet() }
                )
            }

            is ActiveModalSheet.Settings -> {
                NebulaSettingsSheet(
                    isVisible = true,
                    onOpenAccount = { viewModel.onOpenProfileEditor() },
                    onOpenGoogleAccount = { viewModel.onOpenGoogleAccountSettings() },
                    onOpenAppearance = { viewModel.onOpenThemes() },
                    onOpenPermissions = { viewModel.onOpenPermissions() },
                    onOpenPrivacyPolicy = { viewModel.onOpenPrivacyPolicy() },
                    onOpenAbout = { viewModel.onOpenAbout() },
                    onOpenNebulaPremium = { viewModel.onOpenPremiumPlans() },
                    onDismiss = { viewModel.onDismissModalSheet() }
                )
            }

            is ActiveModalSheet.AISettings -> {
                NebulaAISettingsSheet(
                    isVisible = true,
                    showInlineSuggestions = uiState.showInlineSuggestions,
                    aiPersonalizationEnabled = uiState.aiPersonalizationEnabled,
                    localAiMode = uiState.localAiMode,
                    onToggleInlineSuggestions = { viewModel.onToggleInlineSuggestions(it) },
                    onTogglePersonalization = { viewModel.onToggleAiPersonalization(it) },
                    onSelectLocalAiMode = { viewModel.onSelectLocalAiMode(it) },
                    onDismiss = { viewModel.onDismissModalSheet() }
                )
            }

            is ActiveModalSheet.About -> {
                AboutSheet(
                    isVisible = true,
                    onOpenPrivacyPolicy = { viewModel.onOpenPrivacyPolicy() },
                    onDismiss = { viewModel.onDismissModalSheet() }
                )
            }

            is ActiveModalSheet.PrivacyPolicy -> {
                PrivacyPolicySheet(
                    isVisible = true,
                    onDismiss = { viewModel.onDismissModalSheet() }
                )
            }

            is ActiveModalSheet.History -> {
                HistorySheet(
                    isVisible = true,
                    conversations = uiState.conversations,
                    activeConversationId = uiState.activeConversation?.id,
                    onSelectConversation = { viewModel.onSelectConversation(it) },
                    onDeleteConversation = { viewModel.onDeleteConversation(it) },
                    onNewConversation = { viewModel.onNewConversation() },
                    onDismiss = { viewModel.onDismissModalSheet() }
                )
            }

            is ActiveModalSheet.Themes -> {
                ThemeSelectorSheet(
                    isVisible = true,
                    availableThemes = uiState.availableThemes,
                    activeTheme = uiState.currentTheme,
                    userProfile = uiState.userProfile,
                    onSelectTheme = { viewModel.onSelectTheme(it) },
                    onUpgradeRequested = {
                        viewModel.onDismissModalSheet()
                        viewModel.onOpenMembership()
                    },
                    onDismiss = { viewModel.onDismissModalSheet() }
                )
            }

            is ActiveModalSheet.Profile -> {
                ProfileEditorSheet(
                    isVisible = true,
                    profile = uiState.userProfile,
                    onUpdateName = { viewModel.onUpdateDisplayName(it) },
                    onTogglePremium = { /* Handled in settings */ },
                    onOpenPremiumPlans = { viewModel.onOpenMembership() },
                    onPickGallery = { viewModel.onDismissModalSheet() },
                    onTakePhoto = { viewModel.onDismissModalSheet() },
                    onRemovePhoto = { viewModel.onDismissModalSheet() },
                    onDismiss = { viewModel.onDismissModalSheet() }
                )
            }

            is ActiveModalSheet.PremiumPlans -> {
                PremiumPlanSheet(
                    isVisible = true,
                    currentTier = uiState.activeSubscriptionTier,
                    isTrial = uiState.userProfile.isTrial,
                    onSelectTier = { viewModel.onSelectSubscriptionTier(it) },
                    onDismiss = { viewModel.onDismissModalSheet() }
                )
            }

            is ActiveModalSheet.Permissions -> {
                PermissionsSheet(
                    isVisible = true,
                    onDismiss = { viewModel.onDismissModalSheet() }
                )
            }

            is ActiveModalSheet.Privacy -> {
                PrivacyVaultSheet(
                    isVisible = true,
                    vaultItems = uiState.savedVaultItems,
                    onExportVault = { viewModel.onExportSavedVault() },
                    onPurgeVault = { viewModel.onPurgeSavedVault() },
                    onDismiss = { viewModel.onDismissModalSheet() }
                )
            }

            else -> { /* No active modal */ }
        }

        // 5. ROOM CREATION BUFFERING OVERLAY
        if (uiState.isCreatingRoom) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(46.dp),
                        color = theme.colors.primaryAccent,
                        strokeWidth = 3.5.dp
                    )
                    Text(
                        text = "Creating room...",
                        style = theme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    )
                    Text(
                        text = "\"${uiState.roomCreationQuote}\"",
                        style = theme.typography.bodyMedium.copy(
                            color = Color.White.copy(alpha = 0.8f),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            fontSize = 14.sp
                        ),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                if (uiState.isRefreshing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(82.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(theme.colors.surface.copy(alpha = 0.96f))
                                .border(
                                    width = 1.dp,
                                    color = theme.colors.primaryAccent.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(24.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            NebulaLogo(modifier = Modifier.size(38.dp))
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(58.dp),
                                color = theme.colors.primaryAccent,
                                trackColor = theme.colors.primaryAccent.copy(alpha = 0.18f),
                                strokeWidth = 3.dp
                            )
                        }
                    }
                }
            }
        }
    }
}
