package com.nebula.app.data.repository

import com.nebula.app.domain.model.ChatMessage
import com.nebula.app.domain.model.MessageRole
import com.nebula.app.domain.model.NebulaConversation
import com.nebula.app.domain.model.NebulaFriendRequest
import com.nebula.app.domain.model.NebulaUser
import com.nebula.app.domain.repository.ISocialRepository
import com.nebula.app.data.backend.NebulaApiClient
import com.nebula.app.data.backend.NebulaApiClient.ApiResponse
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class SocialRepositoryImpl(
    private val apiClient: NebulaApiClient = NebulaApiClient
) : ISocialRepository {

    fun clearLocalState() {
        _friends.value = emptyList()
        _friendRequests.value = emptyList()
        _suggestedPeople.value = emptyList()
        _conversations.value = emptyList()
        _activeConversation.value = null
    }

    private val _friends = MutableStateFlow<List<NebulaUser>>(emptyList())
    override val friends: StateFlow<List<NebulaUser>> = _friends.asStateFlow()

    private val _friendRequests = MutableStateFlow<List<NebulaFriendRequest>>(emptyList())
    override val friendRequests: StateFlow<List<NebulaFriendRequest>> = _friendRequests.asStateFlow()

    private val _suggestedPeople = MutableStateFlow<List<NebulaUser>>(emptyList())
    override val suggestedPeople: StateFlow<List<NebulaUser>> = _suggestedPeople.asStateFlow()

    private val _searchResults = MutableStateFlow<List<NebulaUser>>(emptyList())
    override val searchResults: StateFlow<List<NebulaUser>> = _searchResults.asStateFlow()

    private val _conversations = MutableStateFlow<List<NebulaConversation>>(emptyList())
    override val conversations: StateFlow<List<NebulaConversation>> = _conversations.asStateFlow()

    private val _activeConversation = MutableStateFlow<NebulaConversation?>(null)
    override val activeConversation: StateFlow<NebulaConversation?> = _activeConversation.asStateFlow()

    override suspend fun selectConversation(id: String) {
        val target = _conversations.value.firstOrNull { it.id == id }
        if (target != null) {
            val history = apiClient.getConversationMessages(id)
            val messages = if (history is ApiResponse.Success) {
                buildList {
                    for (index in 0 until history.data.length()) {
                        val item = history.data.optJSONObject(index) ?: continue
                        add(messageFromJson(item, id))
                    }
                }
            } else target.messages
            _activeConversation.value = target.copy(unreadCount = 0, messages = messages)
            _conversations.value = _conversations.value.map {
                if (it.id == id) it.copy(unreadCount = 0, messages = messages) else it
            }
            // Opening a conversation is the only point at which messages become seen.
            apiClient.markConversationRead(id)
        }
    }

    override suspend fun closeConversation() {
        _activeConversation.value = null
    }

    override suspend fun sendMessage(conversationId: String, content: String, mediaUrl: String?, messageType: String): Boolean {
        if (content.isBlank() && mediaUrl.isNullOrBlank()) return false
        val response = apiClient.sendDirectMessage(conversationId, content.trim(), mediaUrl, messageType)
        if (response !is ApiResponse.Success) return false
        val currentConv = _conversations.value.firstOrNull { it.id == conversationId } ?: return false
        val newMsg = response.data.optJSONObject("data")?.let { messageFromJson(it, conversationId) } ?: ChatMessage(id = "msg_${UUID.randomUUID().toString().take(6)}", role = MessageRole.USER, content = content.trim(), timeLabel = "Just now", mediaUrl = mediaUrl, messageType = messageType)
        val updatedConv = currentConv.copy(
            lastMessageText = "you: ${content.trim()}",
            lastMessageTime = "Just now",
            messages = currentConv.messages + newMsg
        )
        _conversations.value = _conversations.value.map {
            if (it.id == conversationId) updatedConv else it
        }
        if (_activeConversation.value?.id == conversationId) {
            _activeConversation.value = updatedConv
        }
        return true
    }

    override suspend fun editMessage(messageId: String, content: String): Boolean = updateMessageFromResponse(apiClient.editDirectMessage(messageId, content))

    override suspend fun unsendMessage(messageId: String): Boolean = updateMessageFromResponse(apiClient.unsendDirectMessage(messageId))

    private fun updateMessageFromResponse(response: ApiResponse<JSONObject>): Boolean {
        if (response !is ApiResponse.Success) return false
        val raw = response.data.optJSONObject("data") ?: return false
        val active = _activeConversation.value ?: return false
        val updated = messageFromJson(raw, active.id)
        val revised = active.copy(messages = active.messages.map { if (it.id == updated.id) updated else it })
        _activeConversation.value = revised
        _conversations.value = _conversations.value.map { if (it.id == active.id) revised else it }
        return true
    }

    override suspend fun acceptFriendRequest(requestId: String): Boolean {
        if (apiClient.acceptFriendRequest(requestId) !is ApiResponse.Success) return false
        val req = _friendRequests.value.firstOrNull { it.id == requestId }
        if (req != null) {
            _friends.value = _friends.value + req.sender
            _friendRequests.value = _friendRequests.value.filter { it.id != requestId }
        }
        return true
    }

    override suspend fun declineFriendRequest(requestId: String): Boolean {
        if (apiClient.declineFriendRequest(requestId) !is ApiResponse.Success) return false
        _friendRequests.value = _friendRequests.value.filter { it.id != requestId }
        return true
    }

    override suspend fun withdrawFriendRequest(userId: String): Boolean {
        if (apiClient.withdrawFriendRequest(userId) !is ApiResponse.Success) return false
        _suggestedPeople.value = _suggestedPeople.value.map {
            if (it.id == userId) it.copy(friendRequestPending = false) else it
        }
        return true
    }

    override suspend fun addFriend(user: NebulaUser): Boolean {
        if (apiClient.sendFriendRequest(user.id) !is ApiResponse.Success) return false
        _suggestedPeople.value = _suggestedPeople.value.filter { it.id != user.id }
        return true
    }

    override suspend fun refresh() {
        val friends = apiClient.getFriends()
        if (friends is ApiResponse.Success) {
            val friendUsers = parseUsers(friends.data)
            _friends.value = attachRoomPresence(friendUsers)
            _conversations.value = _friends.value.map { friend ->
                val history = apiClient.getConversationMessages(friend.id)
                val messages = if (history is ApiResponse.Success) buildList {
                    for (index in 0 until history.data.length()) {
                        history.data.optJSONObject(index)?.let { add(messageFromJson(it, friend.id)) }
                    }
                } else emptyList()
                val unread = messages.count { it.role == MessageRole.ASSISTANT && it.readAt == null && it.deletedAt == null }
                val latest = messages.lastOrNull()
                NebulaConversation(
                    id = friend.id,
                    title = friend.displayName,
                    isGroup = false,
                    partnerUser = friend,
                    participants = listOf(NebulaUser.CurrentUser, friend),
                    lastMessageText = latest?.content ?: "",
                    lastMessageTime = if (latest != null) "Recent" else "",
                    unreadCount = unread,
                    messages = messages,
                )
            }
        }
        val suggestions = apiClient.getSuggestedPeople()
        if (suggestions is ApiResponse.Success) _suggestedPeople.value = parseUsers(suggestions.data)
        val requests = apiClient.getFriendRequests()
        if (requests is ApiResponse.Success) {
            _friendRequests.value = parseRequests(requests.data)
        }
    }

    private suspend fun attachRoomPresence(users: List<NebulaUser>): List<NebulaUser> {
        if (users.isEmpty()) return users
        val activeRooms = apiClient.getRooms()
        if (activeRooms !is ApiResponse.Success) return users
        val roomByUser = mutableMapOf<String, String>()
        for (index in 0 until activeRooms.data.length()) {
            val room = activeRooms.data.optJSONObject(index) ?: continue
            val roomId = room.optString("id")
            val participants = room.optJSONArray("participants") ?: continue
            for (participantIndex in 0 until participants.length()) {
                val userId = participants.optJSONObject(participantIndex)?.optString("userId") ?: continue
                if (userId.isNotBlank()) roomByUser[userId] = roomId
            }
        }
        return users.map { user -> roomByUser[user.id]?.let { user.copy(currentRoomId = it, isOnline = true, presenceStatus = com.nebula.app.domain.model.NebulaPresenceStatus.IN_A_ROOM) } ?: user }
    }

    override suspend fun searchPeople(query: String) {
        if (query.trim().length < 2) {
            _searchResults.value = emptyList()
            return
        }
        val result = apiClient.searchUsers(query)
        if (result is ApiResponse.Success) _searchResults.value = attachRoomPresence(parseUsers(result.data))
    }

    override suspend fun refreshSuggestions(): Boolean {
        val result = apiClient.refreshSuggestedPeople(_suggestedPeople.value.map { it.id })
        if (result is ApiResponse.Success) {
            _suggestedPeople.value = attachRoomPresence(parseUsers(result.data))
            return true
        }
        return false
    }

    override suspend fun blockUser(userId: String): Boolean {
        if (apiClient.blockUser(userId) !is ApiResponse.Success) return false
        _friends.value = _friends.value.filterNot { it.id == userId }
        _suggestedPeople.value = _suggestedPeople.value.filterNot { it.id == userId }
        _searchResults.value = _searchResults.value.filterNot { it.id == userId }
        _friendRequests.value = _friendRequests.value.filterNot { it.sender.id == userId }
        return true
    }

    override suspend fun loadPublicProfile(username: String): NebulaUser? {
        val response = apiClient.getPublicProfile(username)
        if (response !is ApiResponse.Success) return null
        val user = response.data.optJSONObject("data") ?: return null
        return parseUsers(JSONArray().put(user)).firstOrNull()
    }

    override suspend fun loadPublicProfileById(userId: String): NebulaUser? {
        val response = apiClient.getPublicProfileById(userId)
        if (response !is ApiResponse.Success) return null
        val user = response.data.optJSONObject("data") ?: return null
        return parseUsers(JSONArray().put(user)).firstOrNull()
    }

    override suspend fun removeFriend(userId: String): Boolean {
        if (apiClient.removeFriend(userId) !is ApiResponse.Success) return false
        refresh()
        return true
    }

    private fun parseUsers(array: JSONArray): List<NebulaUser> = buildList {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val displayedStatus = item.optString("status", "Offline")
            val presence = when (displayedStatus.lowercase()) {
                "offline" -> com.nebula.app.domain.model.NebulaPresenceStatus.OFFLINE
                "in a room" -> com.nebula.app.domain.model.NebulaPresenceStatus.IN_A_ROOM
                "private chat" -> com.nebula.app.domain.model.NebulaPresenceStatus.PRIVATE_CHAT
                else -> com.nebula.app.domain.model.NebulaPresenceStatus.ONLINE
            }
            add(
                NebulaUser(
                    id = item.optString("id"),
                    username = item.optString("username").let { if (it.startsWith("@")) it else "@$it" },
                    displayName = item.optString("displayName", "User"),
                    avatarLetter = item.optString("avatarLetter", "U"),
                    avatarUrl = item.optString("avatarUrl").ifBlank { null },
                    bio = item.optString("bio"),
                    status = displayedStatus,
                    presenceStatus = presence,
                    isOnline = presence != com.nebula.app.domain.model.NebulaPresenceStatus.OFFLINE,
                    bannerUrl = item.optString("bannerUrl").ifBlank { null },
                    bannerIndex = item.optInt("bannerIndex", 0),
                    tier = item.optString("tier", "free"),
                    friendsCount = item.optJSONObject("stats")?.let { if (it.isNull("friendsCount")) null else it.optInt("friendsCount") },
                    roomsHostedCount = item.optJSONObject("stats")?.optInt("roomsHostedCount", 0) ?: 0,
                    roomsJoinedCount = item.optJSONObject("stats")?.optInt("roomsJoinedCount", 0) ?: 0,
                    timeInSyncHours = item.optJSONObject("stats")?.optString("timeInSyncHours")?.toDoubleOrNull() ?: 0.0,
                    interests = item.optJSONArray("interests")?.let { interests ->
                        List(interests.length()) { index -> interests.optString(index) }
                    } ?: emptyList(),
                    friendRequestPending = item.optString("relationship") == "request_sent",
                    friendRequestReceived = item.optString("relationship") == "request_received",
                    friendRequestId = item.optString("friendRequestId").ifBlank { null },
                    isFriend = item.optString("relationship") == "friends"
                )
            )
        }
    }

    private fun messageFromJson(item: JSONObject, partnerId: String): ChatMessage {
        val history = item.optJSONArray("editHistory")?.let { entries ->
            List(entries.length()) { index -> entries.optJSONObject(index)?.optString("text").orEmpty() }
        } ?: emptyList()
        return ChatMessage(
            id = item.optString("id"),
            role = if (item.optString("senderId") == partnerId) MessageRole.ASSISTANT else MessageRole.USER,
            content = item.optString("text"), timeLabel = "Recent",
            timestamp = item.optString("createdAt").takeIf { it.isNotBlank() }?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrDefault(System.currentTimeMillis()) } ?: System.currentTimeMillis(),
            senderId = item.optString("senderId"), senderName = item.optString("senderName"),
            senderAvatarUrl = item.optString("senderAvatarUrl").ifBlank { null },
            senderAvatarLetter = item.optString("senderAvatarLetter", "U"),
            mediaUrl = item.optString("mediaUrl").ifBlank { null }, messageType = item.optString("messageType", "text"),
            editedAt = item.optString("editedAt").takeIf { it.isNotBlank() }?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() },
            editHistory = history,
            deletedAt = item.optString("deletedAt").takeIf { it.isNotBlank() }?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() },
            readAt = item.optString("readAt").takeIf { it.isNotBlank() }?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() },
        )
    }

    private fun parseRequests(array: JSONArray): List<NebulaFriendRequest> = buildList {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val requester = item.optJSONObject("requester") ?: continue
            val user = parseUsers(JSONArray().put(requester)).firstOrNull() ?: continue
            add(NebulaFriendRequest(item.optString("id"), user))
        }
    }
}
