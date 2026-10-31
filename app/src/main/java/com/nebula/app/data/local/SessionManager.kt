package com.nebula.app.data.local

import android.content.Context
import android.content.SharedPreferences
import com.nebula.app.BuildConfig
import com.nebula.app.domain.model.ChatMessage
import com.nebula.app.domain.model.MessageRole
import com.nebula.app.domain.model.MessageSendState
import com.nebula.app.domain.model.NebulaConversation
import com.nebula.app.domain.model.NebulaPresenceStatus
import com.nebula.app.domain.model.NebulaRoom
import com.nebula.app.domain.model.NebulaSubscriptionTier
import com.nebula.app.domain.model.NebulaUser
import com.nebula.app.domain.model.PlayMode
import com.nebula.app.domain.model.RoomActivityType
import com.nebula.app.domain.model.RoomParticipant
import com.nebula.app.domain.model.RoomPrivacy
import com.nebula.app.domain.model.SharedMediaItem
import com.nebula.app.domain.model.UserProfile
import org.json.JSONArray
import org.json.JSONObject

/** A message that has not yet been confirmed as persisted by the server. */
data class PendingOutgoingMessage(
    val conversationId: String,
    val clientKey: String,
    val content: String,
    val mediaUrl: String?,
    val messageType: String,
    val isSpoiler: Boolean,
    val mediaMimeType: String?,
    val timestamp: Long
)

/**
 * SessionManager:
 * Persistent session and credential storage for Nebula.
 * Uses Android SharedPreferences to persist authentication tokens and user profile
 * across application restarts, process death, and force stops.
 */
class SessionManager(context: Context) {

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        /** Stable keys for the per-feature switches shown in Settings. */
        const val FEATURE_CUSTOM_STATUS = "feature_custom_status"
        const val FEATURE_CUSTOM_BANNER = "feature_custom_banner"
        const val FEATURE_VIDEO_BANNER = "feature_video_banner"
        const val FEATURE_ALL_TAGS = "feature_all_tags"

        const val PREFS_NAME = "nebula_session_prefs"
        private const val DEFAULT_BACKEND_URL = "https://nebula-73kb.onrender.com/api/v1"
        /** Hosts that identify a developer machine rather than a public Nebula origin. */
        private val LOCAL_BACKEND_HINTS = listOf(
            "localhost",
            "127.0.0.1",
            "10.0.2.2",
            "192.168.",
            "172.",
            ":5001",
            "trycloudflare.com"
        )
        private const val KEY_AUTH_TOKEN = "key_auth_token"
        private const val KEY_REFRESH_TOKEN = "key_refresh_token"
        private const val KEY_USER_PROFILE_JSON = "key_user_profile_json"
        private const val KEY_IS_LOGGED_IN = "key_is_logged_in"
        private const val KEY_BACKEND_URL = "key_backend_url"
        private const val KEY_CACHED_ROOMS_JSON = "key_cached_rooms_json"
        private const val KEY_CACHED_CONVERSATIONS_JSON = "key_cached_conversations_json"
        private const val KEY_CACHED_FRIENDS_JSON = "key_cached_friends_json"
        private const val KEY_PENDING_MESSAGES_JSON = "key_pending_messages_json"
        private const val KEY_CACHED_ANALYTICS_JSON = "key_cached_analytics_json"
        private const val KEY_CACHED_SETTINGS_JSON = "key_cached_settings_json"
        private const val KEY_DEVICE_ID = "key_device_id"
        private const val KEY_SELECTED_THEME_ID = "key_selected_theme_id"

        @Volatile
        private var INSTANCE: SessionManager? = null

        fun getInstance(context: Context): SessionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SessionManager(context).also { INSTANCE = it }
            }
        }
    }

    fun getBackendUrl(): String {
        val saved = prefs.getString(KEY_BACKEND_URL, null)?.takeIf { it.isNotBlank() }
        if (saved != null && isAcceptableBackendUrl(saved)) return saved

        return DEFAULT_BACKEND_URL
    }

    /**
     * A stored server address is only honoured when it is a real origin for this build.
     *
     * Release builds must never be redirected off the verified HTTPS production host by a
     * persisted value, so only https origins outside the local/development ranges are used.
     * Debug builds are how the app is exercised against a developer machine, so they also
     * accept plain-http loopback and LAN addresses (10.0.2.2 is the emulator's host alias).
     *
     * Previously every local address was rejected here while the settings dialog still
     * appeared to save it, which made the control silently revert to production.
     */
    private fun isAcceptableBackendUrl(url: String): Boolean {
        val isLocalHost = LOCAL_BACKEND_HINTS.any { url.contains(it) }
        if (BuildConfig.DEBUG) {
            return if (url.startsWith("https://")) true else url.startsWith("http://") && isLocalHost
        }
        return url.startsWith("https://") && !isLocalHost
    }

    fun saveBackendUrl(url: String) {
        val normalized = url.trim().removeSuffix("/")
        val finalUrl = if (normalized.endsWith("/api/v1")) normalized else "$normalized/api/v1"
        prefs.edit()
            .putString(KEY_BACKEND_URL, finalUrl)
            .apply()
    }

    fun saveAuthToken(token: String) {
        prefs.edit()
            .putString(KEY_AUTH_TOKEN, token)
            .apply()
    }

    fun getAuthToken(): String? {
        return prefs.getString(KEY_AUTH_TOKEN, null)
    }

    fun saveRefreshToken(token: String) {
        prefs.edit().putString(KEY_REFRESH_TOKEN, token).apply()
    }

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun saveUserProfile(profile: UserProfile) {
        val json = JSONObject().apply {
            put("id", profile.id)
            put("displayName", profile.displayName)
            put("email", profile.email)
            put("phoneNumber", profile.phoneNumber)
            put("username", profile.username ?: "")
            put("bio", profile.bio)
            put("status", profile.status)
            put("customStatus", profile.customStatus)
            put("avatarUri", profile.avatarUri ?: "")
            put("avatarFallbackLetter", profile.avatarFallbackLetter)
            put("bannerIndex", profile.bannerIndex)
            put("isCustomAvatar", profile.isCustomAvatar)
            put("isPremium", profile.isPremium)
            put("isTrial", profile.isTrial)
            put("subscriptionTier", profile.subscriptionTier.name)
            put("interests", JSONArray(profile.interests))
            put("customInstructions", profile.customInstructions)
            put("referralCount", profile.referralCount)
            put("referralCode", profile.referralCode)
            put("isLoggedIn", profile.isLoggedIn)
            put("roomsHostedCount", profile.roomsHostedCount)
            put("roomsJoinedCount", profile.roomsJoinedCount)
            put("timeInSyncHours", profile.timeInSyncHours)
            put("isFriendCountPublic", profile.isFriendCountPublic)
            put("isOnboardingCompleted", profile.isOnboardingCompleted)
            put("dateOfBirth", profile.dateOfBirth ?: "")
            put("gender", profile.gender ?: "")
            put("bannerUrl", profile.bannerUrl ?: "")
        }

        prefs.edit()
            .putString(KEY_USER_PROFILE_JSON, json.toString())
            .putBoolean(KEY_IS_LOGGED_IN, profile.isLoggedIn)
            .apply()
    }

    fun getUserProfile(): UserProfile? {
        val jsonStr = prefs.getString(KEY_USER_PROFILE_JSON, null) ?: return null
        return try {
            val json = JSONObject(jsonStr)
            val rawTier = json.optString("subscriptionTier", NebulaSubscriptionTier.FREE.name)
            val tier = try {
                NebulaSubscriptionTier.valueOf(rawTier)
            } catch (_: Exception) {
                NebulaSubscriptionTier.FREE
            }

            val interestsArray = json.optJSONArray("interests")
            val interestsList = mutableListOf<String>()
            if (interestsArray != null) {
                for (i in 0 until interestsArray.length()) {
                    interestsList.add(interestsArray.getString(i))
                }
            }

            UserProfile(
                id = json.optString("id", ""),
                displayName = json.optString("displayName", "Nebula Explorer"),
                email = json.optString("email", ""),
                phoneNumber = json.optString("phoneNumber", ""),
                username = json.optString("username", "").ifEmpty { null },
                bio = json.optString("bio", ""),
                status = json.optString("status", "dynamic").lowercase().let { if (it in setOf("dynamic", "offline", "custom")) it else "dynamic" },
                customStatus = json.optString("customStatus", ""),
                avatarUri = json.optString("avatarUri", "").ifEmpty { null },
                avatarFallbackLetter = json.optString("avatarFallbackLetter", "N"),
                bannerIndex = json.optInt("bannerIndex", 0),
                isCustomAvatar = json.optBoolean("isCustomAvatar", false),
                isPremium = json.optBoolean("isPremium", false),
                isTrial = json.optBoolean("isTrial", false),
                subscriptionTier = tier,
                interests = interestsList,
                customInstructions = json.optString("customInstructions", ""),
                referralCount = json.optInt("referralCount", 0),
                referralCode = json.optString("referralCode", ""),
                isLoggedIn = json.optBoolean("isLoggedIn", true),
                roomsHostedCount = json.optInt("roomsHostedCount", 0),
                roomsJoinedCount = json.optInt("roomsJoinedCount", 0),
                timeInSyncHours = json.optDouble("timeInSyncHours", 0.0),
                isFriendCountPublic = json.optBoolean("isFriendCountPublic", true),
                isOnboardingCompleted = json.optBoolean("isOnboardingCompleted", false),
                dateOfBirth = json.optString("dateOfBirth", "").ifEmpty { null },
                gender = json.optString("gender", "").ifEmpty { null },
                bannerUrl = json.optString("bannerUrl", "").ifEmpty { null }
            )
        } catch (_: Exception) {
            null
        }
    }

    // --- PERSISTENT SECURE LOCAL-FIRST CACHING ---

    fun saveCachedRooms(rooms: List<NebulaRoom>) {
        try {
            val array = JSONArray()
            for (room in rooms) {
                val obj = JSONObject().apply {
                    put("id", room.id)
                    put("name", room.name)
                    put("hostId", room.hostId)
                    put("hostName", room.hostName)
                    put("privacy", room.privacy.name)
                    put("activityType", room.activityType.name)
                    put("participantCount", room.participantCount)
                    put("isPlaying", room.isPlaying)
                    put("playbackPositionMs", room.playbackPositionMs)
                    put("playbackPositionLabel", room.playbackPositionLabel)
                    put("durationLabel", room.durationLabel)
                    put("tags", JSONArray(room.tags))
                    put("maxParticipants", room.maxParticipants)
                    put("description", room.description)
                    if (room.mediaItem != null) {
                        put("mediaItem", JSONObject().apply {
                            put("id", room.mediaItem.id)
                            put("title", room.mediaItem.title)
                            put("source", room.mediaItem.source)
                            put("durationLabel", room.mediaItem.durationLabel)
                            put("durationMs", room.mediaItem.durationMs)
                            put("mediaUrl", room.mediaItem.mediaUrl)
                            put("youtubeVideoId", room.mediaItem.youtubeVideoId ?: "")
                        })
                    }
                    val parts = JSONArray()
                    for (p in room.participants) {
                        parts.put(JSONObject().apply {
                            put("userId", p.user.id)
                            put("displayName", p.user.displayName)
                            put("username", p.user.username)
                            put("avatarLetter", p.user.avatarLetter)
                            put("avatarUrl", p.user.avatarUrl ?: "")
                            put("isHost", p.isHost)
                        })
                    }
                    put("participants", parts)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_CACHED_ROOMS_JSON, array.toString()).apply()
        } catch (_: Exception) {}
    }

    fun getCachedRooms(): List<NebulaRoom> {
        val jsonStr = prefs.getString(KEY_CACHED_ROOMS_JSON, null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<NebulaRoom>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val privacy = try { RoomPrivacy.valueOf(obj.optString("privacy", "PUBLIC")) } catch (_: Exception) { RoomPrivacy.PUBLIC }
                val activity = try { RoomActivityType.valueOf(obj.optString("activityType", "WATCH")) } catch (_: Exception) { RoomActivityType.WATCH }
                val mediaObj = obj.optJSONObject("mediaItem")
                val media = if (mediaObj != null) {
                    SharedMediaItem(
                        id = mediaObj.optString("id", ""),
                        title = mediaObj.optString("title", ""),
                        source = mediaObj.optString("source", ""),
                        durationLabel = mediaObj.optString("durationLabel", ""),
                        durationMs = mediaObj.optLong("durationMs", 0L),
                        mediaUrl = mediaObj.optString("mediaUrl", ""),
                        youtubeVideoId = mediaObj.optString("youtubeVideoId", "").ifEmpty { null }
                    )
                } else null

                val tagsArray = obj.optJSONArray("tags")
                val tags = mutableListOf<String>()
                if (tagsArray != null) {
                    for (t in 0 until tagsArray.length()) tags.add(tagsArray.getString(t))
                }

                val partsArray = obj.optJSONArray("participants")
                val participants = mutableListOf<RoomParticipant>()
                if (partsArray != null) {
                    for (p in 0 until partsArray.length()) {
                        val pObj = partsArray.optJSONObject(p) ?: continue
                        participants.add(
                            RoomParticipant(
                                user = NebulaUser(
                                    id = pObj.optString("userId"),
                                    displayName = pObj.optString("displayName", "User"),
                                    username = pObj.optString("username", "@user"),
                                    avatarLetter = pObj.optString("avatarLetter", "U"),
                                    avatarUrl = pObj.optString("avatarUrl", "").ifEmpty { null },
                                    bio = "",
                                    status = "In room",
                                    isOnline = true
                                ),
                                isHost = pObj.optBoolean("isHost", false)
                            )
                        )
                    }
                }

                list.add(
                    NebulaRoom(
                        id = obj.optString("id"),
                        name = obj.optString("name", "Ambient Room"),
                        hostId = obj.optString("hostId", ""),
                        hostName = obj.optString("hostName", "Host"),
                        privacy = privacy,
                        activityType = activity,
                        mediaItem = media,
                        participantCount = obj.optInt("participantCount", participants.size),
                        participants = participants,
                        isPlaying = obj.optBoolean("isPlaying", true),
                        playbackPositionMs = obj.optLong("playbackPositionMs", 0L),
                        playbackPositionLabel = obj.optString("playbackPositionLabel", "00:00"),
                        durationLabel = obj.optString("durationLabel", "00:00"),
                        tags = tags,
                        maxParticipants = obj.optInt("maxParticipants", 24),
                        description = obj.optString("description", "")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCachedConversations(conversations: List<NebulaConversation>) {
        try {
            val array = JSONArray()
            for (c in conversations) {
                val obj = JSONObject().apply {
                    put("id", c.id)
                    put("title", c.title)
                    put("isGroup", c.isGroup)
                    put("lastMessageText", c.lastMessageText)
                    put("lastMessageTime", c.lastMessageTime)
                    put("unreadCount", c.unreadCount)
                    if (c.partnerUser != null) {
                        put("partnerUser", JSONObject().apply {
                            put("id", c.partnerUser.id)
                            put("displayName", c.partnerUser.displayName)
                            put("username", c.partnerUser.username)
                            put("avatarLetter", c.partnerUser.avatarLetter)
                            put("avatarUrl", c.partnerUser.avatarUrl ?: "")
                            put("bio", c.partnerUser.bio)
                            put("status", c.partnerUser.status)
                            put("isOnline", c.partnerUser.isOnline)
                        })
                    }
                    val msgs = JSONArray()
                    for (m in c.messages.takeLast(50)) {
                        msgs.put(JSONObject().apply {
                            put("id", m.id)
                            put("role", m.role.name)
                            put("content", m.content)
                            put("timeLabel", m.timeLabel)
                            put("timestamp", m.timestamp)
                            put("senderId", m.senderId)
                            put("senderName", m.senderName)
                            put("senderAvatarUrl", m.senderAvatarUrl ?: "")
                            put("senderAvatarLetter", m.senderAvatarLetter)
                            put("mediaUrl", m.mediaUrl ?: "")
                            put("messageType", m.messageType)
                            put("isSpoiler", m.isSpoiler)
                            put("mediaMimeType", m.mediaMimeType ?: "")
                            // Delivery state and the retry key must survive a restart.
                            // Without them a failed message came back looking delivered
                            // and could no longer be retried, because the idempotency
                            // key that a retry reuses had been dropped.
                            put("sendState", m.sendState.name)
                            if (m.clientKey != null) put("clientKey", m.clientKey)
                            m.editedAt?.let { put("editedAt", it) }
                            m.deletedAt?.let { put("deletedAt", it) }
                        })
                    }
                    put("messages", msgs)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_CACHED_CONVERSATIONS_JSON, array.toString()).apply()
        } catch (_: Exception) {}
    }

    fun getCachedConversations(): List<NebulaConversation> {
        val jsonStr = prefs.getString(KEY_CACHED_CONVERSATIONS_JSON, null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<NebulaConversation>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val partnerObj = obj.optJSONObject("partnerUser")
                val partner = if (partnerObj != null) {
                    NebulaUser(
                        id = partnerObj.optString("id"),
                        displayName = partnerObj.optString("displayName", "User"),
                        username = partnerObj.optString("username", "@user"),
                        avatarLetter = partnerObj.optString("avatarLetter", "U"),
                        avatarUrl = partnerObj.optString("avatarUrl", "").ifEmpty { null },
                        bio = partnerObj.optString("bio", ""),
                        status = partnerObj.optString("status", "Offline"),
                        isOnline = partnerObj.optBoolean("isOnline", false)
                    )
                } else null

                val msgsArray = obj.optJSONArray("messages")
                val messages = mutableListOf<ChatMessage>()
                if (msgsArray != null) {
                    for (m in 0 until msgsArray.length()) {
                        val mObj = msgsArray.optJSONObject(m) ?: continue
                        val role = try { MessageRole.valueOf(mObj.optString("role", "USER")) } catch (_: Exception) { MessageRole.USER }
                        messages.add(
                            ChatMessage(
                                id = mObj.optString("id"),
                                role = role,
                                content = mObj.optString("content", ""),
                                timeLabel = mObj.optString("timeLabel", ""),
                                timestamp = mObj.optLong("timestamp", System.currentTimeMillis()),
                                senderId = mObj.optString("senderId"),
                                senderName = mObj.optString("senderName"),
                                senderAvatarUrl = mObj.optString("senderAvatarUrl", "").ifEmpty { null },
                                senderAvatarLetter = mObj.optString("senderAvatarLetter", "U"),
                                mediaUrl = mObj.optString("mediaUrl", "").ifEmpty { null },
                                messageType = mObj.optString("messageType", "text"),
                                isSpoiler = mObj.optBoolean("isSpoiler", false),
                                mediaMimeType = mObj.optString("mediaMimeType", "").ifEmpty { null },
                                sendState = try {
                                    MessageSendState.valueOf(mObj.optString("sendState", "SENT"))
                                } catch (_: Exception) {
                                    MessageSendState.SENT
                                },
                                clientKey = mObj.optString("clientKey", "").ifEmpty { null },
                                editedAt = mObj.optLong("editedAt", 0L).takeIf { it > 0L },
                                deletedAt = mObj.optLong("deletedAt", 0L).takeIf { it > 0L }
                            )
                        )
                    }
                }

                list.add(
                    NebulaConversation(
                        id = obj.optString("id"),
                        title = obj.optString("title", partner?.displayName ?: "Chat"),
                        isGroup = obj.optBoolean("isGroup", false),
                        partnerUser = partner,
                        participants = listOfNotNull(NebulaUser.CurrentUser, partner),
                        lastMessageText = obj.optString("lastMessageText", ""),
                        lastMessageTime = obj.optString("lastMessageTime", ""),
                        unreadCount = obj.optInt("unreadCount", 0),
                        messages = messages
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCachedFriends(friends: List<NebulaUser>) {
        try {
            val array = JSONArray()
            for (f in friends) {
                array.put(JSONObject().apply {
                    put("id", f.id)
                    put("displayName", f.displayName)
                    put("username", f.username)
                    put("avatarLetter", f.avatarLetter)
                    put("avatarUrl", f.avatarUrl ?: "")
                    put("bannerUrl", f.bannerUrl ?: "")
                    put("bio", f.bio)
                    put("status", f.status)
                    put("isOnline", f.isOnline)
                    put("tier", f.tier)
                    put("friendsCount", f.friendsCount ?: 0)
                    put("roomsHostedCount", f.roomsHostedCount)
                    put("roomsJoinedCount", f.roomsJoinedCount)
                    put("timeInSyncHours", f.timeInSyncHours)
                    put("interests", JSONArray(f.interests))
                })
            }
            prefs.edit().putString(KEY_CACHED_FRIENDS_JSON, array.toString()).apply()
        } catch (_: Exception) {}
    }

    fun getCachedFriends(): List<NebulaUser> {
        val jsonStr = prefs.getString(KEY_CACHED_FRIENDS_JSON, null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<NebulaUser>()
            for (i in 0 until array.length()) {
                val f = array.optJSONObject(i) ?: continue
                val interestsArr = f.optJSONArray("interests")
                val interests = mutableListOf<String>()
                if (interestsArr != null) {
                    for (j in 0 until interestsArr.length()) interests.add(interestsArr.getString(j))
                }
                list.add(
                    NebulaUser(
                        id = f.optString("id"),
                        displayName = f.optString("displayName", "User"),
                        username = f.optString("username", "@user"),
                        avatarLetter = f.optString("avatarLetter", "U"),
                        avatarUrl = f.optString("avatarUrl", "").ifEmpty { null },
                        bannerUrl = f.optString("bannerUrl", "").ifEmpty { null },
                        bio = f.optString("bio", ""),
                        status = f.optString("status", "Offline"),
                        isOnline = f.optBoolean("isOnline", false),
                        tier = f.optString("tier", "free"),
                        friendsCount = f.optInt("friendsCount", 0),
                        roomsHostedCount = f.optInt("roomsHostedCount", 0),
                        roomsJoinedCount = f.optInt("roomsJoinedCount", 0),
                        timeInSyncHours = f.optDouble("timeInSyncHours", 0.0),
                        interests = interests,
                        isFriend = true
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCachedAnalytics(analyticsJson: String) {
        prefs.edit().putString(KEY_CACHED_ANALYTICS_JSON, analyticsJson).apply()
    }

    fun getCachedAnalytics(): String? = prefs.getString(KEY_CACHED_ANALYTICS_JSON, null)

    fun saveCachedSettings(settingsJson: String) {
        prefs.edit().putString(KEY_CACHED_SETTINGS_JSON, settingsJson).apply()
    }

    fun getCachedSettings(): String? = prefs.getString(KEY_CACHED_SETTINGS_JSON, null)

    /**
     * Feature switches that used to be paywalled.
     *
     * Payment is gone from the product, so every account owns every feature and each
     * one is instead a plain switch the owner can turn off for themselves. Defaults
     * are on, which is what "everyone gets everything" has to mean.
     */
    fun isFeatureEnabled(featureKey: String): Boolean = prefs.getBoolean(featureKey, true)

    fun setFeatureEnabled(featureKey: String, enabled: Boolean) {
        prefs.edit().putBoolean(featureKey, enabled).apply()
    }

    fun hasSession(): Boolean {
        return prefs.contains(KEY_USER_PROFILE_JSON) || !getAuthToken().isNullOrBlank()
    }

    fun getDeviceId(): String {
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id.isNullOrBlank()) {
            id = "android_" + java.util.UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    fun getDeviceInfo(): String {
        val manufacturer = android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = android.os.Build.MODEL
        val release = android.os.Build.VERSION.RELEASE
        val isClone = try {
            val path = appContext.filesDir?.absolutePath ?: ""
            path.contains("/user/999/") || path.contains("/user/10/") || path.contains("/user/11/") ||
                (path.contains("/user/") && !path.contains("/user/0/"))
        } catch (_: Exception) {
            false
        }
        val suffix = if (isClone) " (Clone)" else ""
        return "$manufacturer $model$suffix (Android $release)"
    }

    fun saveThemeId(themeId: String) {
        prefs.edit().putString(KEY_SELECTED_THEME_ID, themeId).apply()
    }

    fun getThemeId(): String? {
        return prefs.getString(KEY_SELECTED_THEME_ID, null)
    }

    /**
     * Messages that were typed but never confirmed by the server. They are kept per
     * account so a restart can offer them back instead of losing what was written.
     */
    fun savePendingMessages(messages: List<PendingOutgoingMessage>) {
        try {
            if (messages.isEmpty()) {
                prefs.edit().remove(KEY_PENDING_MESSAGES_JSON).apply()
                return
            }
            val array = JSONArray()
            for (m in messages) {
                array.put(JSONObject().apply {
                    put("conversationId", m.conversationId)
                    put("clientKey", m.clientKey)
                    put("content", m.content)
                    put("mediaUrl", m.mediaUrl ?: "")
                    put("messageType", m.messageType)
                    put("isSpoiler", m.isSpoiler)
                    put("mediaMimeType", m.mediaMimeType ?: "")
                    put("timestamp", m.timestamp)
                })
            }
            prefs.edit().putString(KEY_PENDING_MESSAGES_JSON, array.toString()).apply()
        } catch (_: Exception) {}
    }

    fun getPendingMessages(): List<PendingOutgoingMessage> {
        val raw = prefs.getString(KEY_PENDING_MESSAGES_JSON, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val conversationId = obj.optString("conversationId")
                    val clientKey = obj.optString("clientKey")
                    if (conversationId.isBlank() || clientKey.isBlank()) continue
                    add(
                        PendingOutgoingMessage(
                            conversationId = conversationId,
                            clientKey = clientKey,
                            content = obj.optString("content"),
                            mediaUrl = obj.optString("mediaUrl").ifBlank { null },
                            messageType = obj.optString("messageType", "text"),
                            isSpoiler = obj.optBoolean("isSpoiler", false),
                            mediaMimeType = obj.optString("mediaMimeType").ifBlank { null },
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_USER_PROFILE_JSON)
            .remove(KEY_PENDING_MESSAGES_JSON)
            .remove(KEY_CACHED_CONVERSATIONS_JSON)
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .apply()
    }
}
