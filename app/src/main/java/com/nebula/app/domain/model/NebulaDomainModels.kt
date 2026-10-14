package com.nebula.app.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PeopleOutline
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Tv
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Primary 5-Tab Navigation for NEBULA:
 * 1. HOME: Social hub, Active Now, Rooms for you, Recent Chats
 * 2. ROOMS: Social discovery, Watch parties, Create Room, Active rooms
 * 3. CHATS: 1:1 and group conversations with inline AI assistance
 * 4. FRIENDS: Social graph, friend requests, suggested friends, profiles
 * 5. PROFILE: Opens the profile sidebar / drawer directly
 */
enum class NebulaTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    ROOMS("Rooms", Icons.Default.Tv),
    CHATS("Chats", Icons.Default.ChatBubbleOutline),
    FRIENDS("Friends", Icons.Default.PeopleOutline),
    PROFILE("Profile", Icons.Default.PersonOutline)
}

/**
 * Consistent presence states throughout NEBULA:
 * ONLINE (Green), IN_A_ROOM (Violet), PRIVATE_CHAT (Blue), OFFLINE (Gray)
 */
enum class NebulaPresenceStatus(
    val label: String,
    val hexColor: Long
) {
    ONLINE("Online", 0xFF10B981),
    IN_A_ROOM("In a room", 0xFF8B5CF6),
    PRIVATE_CHAT("Private chat", 0xFF3B82F6),
    OFFLINE("Offline", 0xFF6B7280)
}

/**
 * Core User model representing friends and network participants in NEBULA.
 */
data class NebulaUser(
    val id: String,
    val username: String,
    val displayName: String,
    val email: String = "",
    val avatarLetter: String,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val bio: String,
    val status: String,
    val presenceStatus: NebulaPresenceStatus = NebulaPresenceStatus.ONLINE,
    val isOnline: Boolean = presenceStatus != NebulaPresenceStatus.OFFLINE,
    val bannerIndex: Int = 0,
    val interests: List<String> = emptyList(),
    val mutualFriendsCount: Int = 0,
    val currentRoomId: String? = null,
    val roomsHostedCount: Int = 0,
    val roomsJoinedCount: Int = 0,
    val timeInSyncHours: Double = 0.0,
    val friendsCount: Int? = 0,
    val tier: String = "free",
    val isFriend: Boolean = false,
    val friendRequestPending: Boolean = false,
    val friendRequestReceived: Boolean = false,
    val friendRequestId: String? = null
) {
    companion object {
        val CurrentUser = NebulaUser(
            id = "user_current",
            username = "@nebula_user",
            displayName = "Nebula User",
            email = "user@nebula.local",
            avatarLetter = "N",
            bio = "",
            status = "Dynamic",
            presenceStatus = NebulaPresenceStatus.ONLINE,
            bannerIndex = 0,
            roomsHostedCount = 0,
            roomsJoinedCount = 0,
            timeInSyncHours = 0.0,
            isFriend = false
        )

        val Rahul = NebulaUser(
            id = "user_rahul",
            username = "@rahul_m",
            displayName = "Rahul Malhotra",
            avatarLetter = "R",
            bio = "Film buff & systems builder.",
            status = "Ambient Watching",
            presenceStatus = NebulaPresenceStatus.IN_A_ROOM,
            bannerIndex = 1,
            mutualFriendsCount = 5,
            currentRoomId = "room_cinema"
        )

        val Aditi = NebulaUser(
            id = "user_aditi",
            username = "@aditi_k",
            displayName = "Aditi Kapoor",
            avatarLetter = "A",
            bio = "Design, lo-fi beats, and anime marathons.",
            status = "Watching Cyberpunk",
            presenceStatus = NebulaPresenceStatus.IN_A_ROOM,
            bannerIndex = 2,
            mutualFriendsCount = 8,
            currentRoomId = "room_cinema"
        )

        val Arjun = NebulaUser(
            id = "user_arjun",
            username = "@arjun_p",
            displayName = "Arjun Patel",
            avatarLetter = "A",
            bio = "Music curation & chill vibes.",
            status = "Online",
            presenceStatus = NebulaPresenceStatus.ONLINE,
            bannerIndex = 0,
            mutualFriendsCount = 3
        )

        val Priya = NebulaUser(
            id = "user_priya",
            username = "@priya_s",
            displayName = "Priya Sharma",
            avatarLetter = "P",
            bio = "Mobile architect & AI researcher.",
            status = "Private chat",
            presenceStatus = NebulaPresenceStatus.PRIVATE_CHAT,
            bannerIndex = 3,
            mutualFriendsCount = 6
        )

        val Vikram = NebulaUser(
            id = "user_vikram",
            username = "@vikram_dev",
            displayName = "Vikram Sen",
            avatarLetter = "V",
            bio = "Android enthusiast & game streamer.",
            status = "Offline",
            presenceStatus = NebulaPresenceStatus.OFFLINE,
            bannerIndex = 4,
            mutualFriendsCount = 2,
            isFriend = false,
            friendRequestPending = true
        )

        val AllSampleUsers = listOf(Rahul, Aditi, Arjun, Priya, Vikram)
    }
}

/**
 * Friend Request model for social discovery.
 */
data class NebulaFriendRequest(
    val id: String,
    val sender: NebulaUser,
    val timestamp: Long = System.currentTimeMillis(),
    val mutualFriendsText: String = "${sender.mutualFriendsCount} mutual friends"
)

/**
 * Room privacy settings.
 */
enum class RoomPrivacy(val label: String) {
    FRIENDS("Friends"),
    INVITE_ONLY("Invite Only"),
    PUBLIC("Public")
}

/**
 * Activity type in a Nebula room.
 */
enum class RoomActivityType(val label: String, val description: String) {
    WATCH("Watch", "Synchronized media & cinema"),
    CHAT("Chat", "Group text discussion"),
    VOICE("Voice", "Audio room")
}

/**
 * Media item for shared synchronized playback.
 */
data class SharedMediaItem(
    val id: String,
    val title: String,
    val source: String,
    val durationLabel: String,
    val durationMs: Long,
    val previewHexColor: Long = 0xFF1E202B,
    val mediaUrl: String = "",
    val youtubeVideoId: String? = null
) {
    companion object {
        fun extractYouTubeId(url: String): String? {
            if (url.isBlank()) return null
            val pattern = "(?:youtu\\.be/|youtube\\.com/(?:embed/|v/|watch\\?v=|watch\\?.+&v=))([\\w-]{11})".toRegex()
            val match = pattern.find(url.trim())
            return match?.groupValues?.get(1)
        }

        val DefaultMedia = SharedMediaItem(
            id = "media_default",
            title = "No Video Playing",
            source = "Idle",
            durationLabel = "Idle",
            durationMs = 0L,
            previewHexColor = 0xFF141824,
            youtubeVideoId = null
        )

        val LofiStudy = SharedMediaItem(
            id = "media_lofi",
            title = "Lofi Girl • beats to relax / study to",
            source = "YouTube 24/7",
            durationLabel = "Live Stream",
            durationMs = 0L,
            previewHexColor = 0xFF1F1B2B,
            youtubeVideoId = "jfKfPfyJRdk"
        )

        val CyberpunkAnime = SharedMediaItem(
            id = "media_cyberpunk",
            title = "Cyberpunk: Edgerunners Ep. 1",
            source = "Anime Watchparty",
            durationLabel = "24:15",
            durationMs = 1455000L,
            previewHexColor = 0xFF24141E,
            youtubeVideoId = "JtqIas3bYhg"
        )
    }
}

/**
 * Participant inside an active room.
 */
data class RoomParticipant(
    val user: NebulaUser,
    val isHost: Boolean = false,
    val isSpeaking: Boolean = false,
    val isVideoActive: Boolean = true,
    val isAudioActive: Boolean = true
)

/**
 * Message sent inside a room's realtime chat feed.
 */
data class RoomChatMessage(
    val id: String,
    val roomId: String,
    val senderId: String,
    val senderName: String,
    val senderAvatar: String,
    val senderAvatarUrl: String? = null,
    val content: String,
    val timeLabel: String = "Just now",
    val isSystemMessage: Boolean = false
)

/**
 * Quick reaction in a room.
 */
data class RoomReactionItem(
    val id: String,
    val emoji: String,
    val senderName: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class PlayMode(val label: String) {
    ADMIN_PLAY("Admin Play"),
    POLL_PLAY("Poll Play")
}

data class PollOption(
    val id: String,
    val text: String,
    val votes: Int = 0
)

data class RoomPoll(
    val id: String,
    val roomId: String,
    val question: String,
    val options: List<PollOption>,
    val userVotedOptionId: String? = null,
    val isClosed: Boolean = false,
    val winningOptionId: String? = null
)

/**
 * Room: The central social shared space in NEBULA.
 */
data class NebulaRoom(
    val id: String,
    val name: String,
    val hostId: String,
    val hostName: String,
    val privacy: RoomPrivacy,
    val activityType: RoomActivityType = RoomActivityType.WATCH,
    val mediaItem: SharedMediaItem? = null,
    val participantCount: Int = 0,
    val participants: List<RoomParticipant> = emptyList(),
    val isPlaying: Boolean = true,
    val mediaEnded: Boolean = false,
    val playbackPositionMs: Long = 762000L, // 12:42
    val playbackPositionLabel: String = "12:42",
    val durationLabel: String = "2:49:00",
    val playMode: PlayMode = PlayMode.POLL_PLAY,
    val activePoll: RoomPoll? = null,
    val serverTimestamp: Long = System.currentTimeMillis(),
    val recentMessages: List<RoomChatMessage> = emptyList(),
    val tags: List<String> = emptyList(),
    val maxParticipants: Int = 20,
    val isMicAllowed: Boolean = true,
    val isVideoAllowed: Boolean = true,
    val description: String = ""
) {
    companion object {
        val SampleRooms = listOf(
            NebulaRoom(
                id = "room_cinema",
                name = "Late Night Movies",
                hostId = NebulaUser.Rahul.id,
                hostName = NebulaUser.Rahul.displayName,
                privacy = RoomPrivacy.FRIENDS,
                activityType = RoomActivityType.WATCH,
                mediaItem = null,
                participantCount = 4,
                participants = listOf(
                    RoomParticipant(user = NebulaUser.Rahul, isHost = true),
                    RoomParticipant(user = NebulaUser.Aditi, isHost = false),
                    RoomParticipant(user = NebulaUser.Arjun, isHost = false),
                    RoomParticipant(user = NebulaUser.CurrentUser, isHost = false)
                ),
                isPlaying = true,
                playbackPositionMs = 762000L,
                playbackPositionLabel = "12:42",
                durationLabel = "2:49:00",
                playMode = PlayMode.POLL_PLAY,
                activePoll = RoomPoll(
                    id = "poll_cinema_1",
                    roomId = "room_cinema",
                    question = "What should we watch next?",
                    options = listOf(
                        PollOption(id = "opt_1", text = "Dune: Part Two", votes = 3),
                        PollOption(id = "opt_2", text = "Blade Runner 2049", votes = 1),
                        PollOption(id = "opt_3", text = "Oppenheimer", votes = 2)
                    ),
                    userVotedOptionId = "opt_1"
                ),
                tags = listOf("Space", "Sci-Fi", "Watch Party"),
                recentMessages = listOf(
                    RoomChatMessage(
                        id = "rm_1",
                        roomId = "room_cinema",
                        senderId = NebulaUser.Rahul.id,
                        senderName = "Rahul",
                        senderAvatar = "R",
                        content = "bro this movie is insane",
                        timeLabel = "12:40"
                    ),
                    RoomChatMessage(
                        id = "rm_2",
                        roomId = "room_cinema",
                        senderId = NebulaUser.Aditi.id,
                        senderName = "Aditi",
                        senderAvatar = "A",
                        content = "wait WHAT just happened with the docking scene",
                        timeLabel = "12:41"
                    ),
                    RoomChatMessage(
                        id = "rm_3",
                        roomId = "room_cinema",
                        senderId = NebulaUser.Arjun.id,
                        senderName = "Arjun",
                        senderAvatar = "A",
                        content = "that Hans Zimmer organ score hits every time 🔥",
                        timeLabel = "12:42"
                    )
                )
            ),
            NebulaRoom(
                id = "room_lofi",
                name = "Study & Lo-Fi Session",
                hostId = NebulaUser.Priya.id,
                hostName = NebulaUser.Priya.displayName,
                privacy = RoomPrivacy.PUBLIC,
                activityType = RoomActivityType.WATCH,
                mediaItem = SharedMediaItem.LofiStudy,
                participantCount = 6,
                participants = listOf(
                    RoomParticipant(user = NebulaUser.Priya, isHost = true),
                    RoomParticipant(user = NebulaUser.Arjun, isHost = false)
                ),
                isPlaying = true,
                playbackPositionMs = 0L,
                playbackPositionLabel = "Live",
                durationLabel = "24/7",
                tags = listOf("Deep Work", "Lo-Fi", "Focus"),
                recentMessages = listOf(
                    RoomChatMessage(
                        id = "rm_4",
                        roomId = "room_lofi",
                        senderId = NebulaUser.Priya.id,
                        senderName = "Priya",
                        senderAvatar = "P",
                        content = "pomodoro 45m sprint starting now!",
                        timeLabel = "10m ago"
                    )
                )
            ),
            NebulaRoom(
                id = "room_anime",
                name = "Anime Watchparty",
                hostId = NebulaUser.Aditi.id,
                hostName = NebulaUser.Aditi.displayName,
                privacy = RoomPrivacy.FRIENDS,
                activityType = RoomActivityType.WATCH,
                mediaItem = SharedMediaItem.CyberpunkAnime,
                participantCount = 3,
                participants = listOf(
                    RoomParticipant(user = NebulaUser.Aditi, isHost = true),
                    RoomParticipant(user = NebulaUser.Rahul, isHost = false)
                ),
                isPlaying = false,
                playbackPositionMs = 320000L,
                playbackPositionLabel = "05:20",
                durationLabel = "24:15",
                tags = listOf("Anime", "Night Chill"),
                recentMessages = emptyList()
            )
        )
    }
}

/**
 * Social conversation item (1:1 or Group chat).
 */
data class NebulaConversation(
    val id: String,
    val title: String,
    val isGroup: Boolean,
    val partnerUser: NebulaUser?,
    val participants: List<NebulaUser>,
    val lastMessageText: String,
    val lastMessageTime: String,
    val unreadCount: Int = 0,
    val messages: List<ChatMessage> = emptyList()
) {
    companion object {
        val SampleConversations = listOf(
            NebulaConversation(
                id = "conv_rahul",
                title = "Rahul Malhotra",
                isGroup = false,
                partnerUser = NebulaUser.Rahul,
                participants = listOf(NebulaUser.CurrentUser, NebulaUser.Rahul),
                lastMessageText = "you: bro join the room",
                lastMessageTime = "2m",
                unreadCount = 0,
                messages = listOf(
                    ChatMessage(
                        id = "m1",
                        role = MessageRole.USER,
                        content = "hey are we still watching the stream tonight?",
                        timeLabel = "8:30 PM"
                    ),
                    ChatMessage(
                        id = "m2",
                        role = MessageRole.ASSISTANT,
                        content = "Yeah! Room is live already, jump in whenever",
                        timeLabel = "8:32 PM"
                    ),
                    ChatMessage(
                        id = "m3",
                        role = MessageRole.USER,
                        content = "bro join the room",
                        timeLabel = "2m ago"
                    )
                )
            ),
            NebulaConversation(
                id = "conv_aditi",
                title = "Aditi Kapoor",
                isGroup = false,
                partnerUser = NebulaUser.Aditi,
                participants = listOf(NebulaUser.CurrentUser, NebulaUser.Aditi),
                lastMessageText = "see you tonight in the room!",
                lastMessageTime = "1h",
                unreadCount = 1,
                messages = listOf(
                    ChatMessage(
                        id = "m4_1",
                        role = MessageRole.ASSISTANT,
                        content = "Hey! Did you see the new Cyberpunk: Edgerunners episode?",
                        timeLabel = "4:15 PM"
                    ),
                    ChatMessage(
                        id = "m4_2",
                        role = MessageRole.USER,
                        content = "Not yet, was waiting for you to finish work so we can sync it!",
                        timeLabel = "4:18 PM"
                    ),
                    ChatMessage(
                        id = "m4_3",
                        role = MessageRole.ASSISTANT,
                        content = "Awesome, let's start the room around 9 PM.",
                        timeLabel = "4:20 PM"
                    ),
                    ChatMessage(
                        id = "m4_4",
                        role = MessageRole.USER,
                        content = "Sounds good! I'll prepare the queue.",
                        timeLabel = "4:22 PM"
                    ),
                    ChatMessage(
                        id = "m4_5",
                        role = MessageRole.ASSISTANT,
                        content = "see you tonight in the room!",
                        timeLabel = "1h ago"
                    )
                )
            ),
            NebulaConversation(
                id = "conv_cinema_squad",
                title = "Late Night Cinema Club",
                isGroup = true,
                partnerUser = null,
                participants = listOf(
                    NebulaUser.CurrentUser,
                    NebulaUser.Rahul,
                    NebulaUser.Aditi,
                    NebulaUser.Arjun
                ),
                lastMessageText = "Arjun: queued Dune Part Two next",
                lastMessageTime = "3h",
                unreadCount = 2,
                messages = listOf(
                    ChatMessage(
                        id = "m5_1",
                        role = MessageRole.ASSISTANT,
                        content = "Rahul: Space stream just finished, what a soundtrack!",
                        timeLabel = "5:10 PM"
                    ),
                    ChatMessage(
                        id = "m5_2",
                        role = MessageRole.ASSISTANT,
                        content = "Aditi: Still gives me chills every single rewatch 🚀",
                        timeLabel = "5:12 PM"
                    ),
                    ChatMessage(
                        id = "m5_3",
                        role = MessageRole.USER,
                        content = "Should we do Blade Runner 2049 or Dune Part Two next?",
                        timeLabel = "5:15 PM"
                    ),
                    ChatMessage(
                        id = "m5_4",
                        role = MessageRole.ASSISTANT,
                        content = "Arjun: queued Dune Part Two next",
                        timeLabel = "3h ago"
                    )
                )
            )
        )
    }
}

/**
 * Notifications feed replacing the old History banner.
 */
enum class NotificationType {
    FRIEND_REQUEST,
    ROOM_INVITE,
    ROOM_STARTED,
    NOTICE
}

data class NebulaNotification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val subtitle: String,
    val timeLabel: String,
    val actionLabel: String? = null,
    val relatedId: String? = null,
    val isRead: Boolean = false
) {
    companion object {
        val SampleNotifications = listOf(
            NebulaNotification(
                id = "notif_1",
                type = NotificationType.FRIEND_REQUEST,
                title = "Vikram Sen",
                subtitle = "Sent you a friend request • 2 mutual friends",
                timeLabel = "10m ago",
                actionLabel = "Accept",
                relatedId = "user_vikram"
            ),
            NebulaNotification(
                id = "notif_2",
                type = NotificationType.ROOM_INVITE,
                title = "Late Night Movies",
                subtitle = "Rahul invited you to watch the stream",
                timeLabel = "35m ago",
                actionLabel = "Join Room",
                relatedId = "room_cinema"
            ),
            NebulaNotification(
                id = "notif_3",
                type = NotificationType.ROOM_STARTED,
                title = "Anime Watchparty",
                subtitle = "Aditi started streaming Cyberpunk: Edgerunners",
                timeLabel = "1h ago",
                actionLabel = "Watch",
                relatedId = "room_anime"
            ),
            NebulaNotification(
                id = "notif_4",
                type = NotificationType.NOTICE,
                title = "Welcome to Nebula v2",
                subtitle = "Discover live rooms, synchronized watchparties, and friends.",
                timeLabel = "Yesterday",
                actionLabel = null,
                isRead = true
            )
        )
    }
}

/**
 * Freemium subscription tiers: Free, Premium (₹99 / 2 mo), Premium Plus (₹399 / 4 mo).
 */
enum class NebulaSubscriptionTier(
    val title: String,
    val price: String,
    val billingCycle: String,
    val badgeLabel: String,
    val tagLine: String,
    val features: List<String>
) {
    FREE(
        title = "Free",
        price = "₹0",
        billingCycle = "Forever",
        badgeLabel = "Member",
        tagLine = "I can genuinely use Nebula",
        features = listOf(
            "Public room discovery & joining",
            "Create & host standard rooms",
            "Realtime room chat & standard reactions",
            "Interactive room polls & voting",
            "Direct messaging & friends list",
            "Profile statistics & top content",
            "Non-intrusive discovery ads"
        )
    ),
    PREMIUM(
        title = "Premium",
        price = "₹99",
        billingCycle = "2 months",
        badgeLabel = "Premium",
        tagLine = "Now Nebula feels like MY Nebula",
        features = listOf(
            "100% Completely ad-free experience",
            "Custom profile banner & accent colors",
            "Exclusive Premium profile badge",
            "Advanced room appearance customizations",
            "Extra reactions & custom emojis",
            "Enhanced room usage statistics"
        )
    ),
    PREMIUM_PLUS(
        title = "Premium Plus",
        price = "₹399",
        billingCycle = "4 months",
        badgeLabel = "Plus",
        tagLine = "I want my presence to be customized",
        features = listOf(
            "Everything included in Premium",
            "Multiple custom profile identities & layouts",
            "Exclusive cosmetics & room branding",
            "Premium host & queue management tools",
            "Advanced poll controls & voting modes",
            "Real-time room analytics & retention metrics",
            "Early access to experimental features"
        )
    )
}

/**
 * Saved media in private sandbox vault.
 */
data class SavedVaultItem(
    val id: String,
    val fileName: String,
    val sizeLabel: String,
    val sourceRoom: String,
    val timeLabel: String,
    val previewHexColor: Long = 0xFF1C2230
) {
    companion object {
        val SampleItems = listOf(
            SavedVaultItem(
                id = "v1",
                fileName = "cosmic_nebula_scene.png",
                sizeLabel = "2.4 MB",
                sourceRoom = "Late Night Movies",
                timeLabel = "Yesterday",
                previewHexColor = 0xFF141824
            ),
            SavedVaultItem(
                id = "v2",
                fileName = "lofi_study_notes_diagram.png",
                sizeLabel = "1.1 MB",
                sourceRoom = "Study & Lo-Fi Session",
                timeLabel = "3 days ago",
                previewHexColor = 0xFF1F1B2B
            ),
            SavedVaultItem(
                id = "v3",
                fileName = "dune_part_two_poster.png",
                sizeLabel = "3.2 MB",
                sourceRoom = "Late Night Cinema Club",
                timeLabel = "5 days ago",
                previewHexColor = 0xFF2A1C16
            )
        )
    }
}

/**
 * Single active room per user device session restriction.
 */
data class DeviceSession(
    val sessionId: String,
    val userId: String,
    val deviceId: String,
    val activeRoomId: String?,
    val lastHeartbeat: Long = System.currentTimeMillis()
)

/**
 * Clean analytics & behavioral event system.
 */
enum class AnalyticsEventType {
    ROOM_JOINED,
    ROOM_LEFT,
    ROOM_HOSTED,
    MEDIA_STARTED,
    MEDIA_COMPLETED,
    MEDIA_SKIPPED,
    MEDIA_LIKED,
    POLL_VOTED,
    MESSAGE_SENT,
    FRIEND_ADDED,
    PROFILE_VIEWED,
    ROOM_CREATED,
    ROOM_INVITED
}

data class NebulaAnalyticsEvent(
    val id: String,
    val type: AnalyticsEventType,
    val userId: String,
    val metadata: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

