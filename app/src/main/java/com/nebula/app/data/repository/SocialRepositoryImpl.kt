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
                        add(
                            ChatMessage(
                                id = item.optString("id"),
                                role = if (item.optString("senderId") == id) MessageRole.ASSISTANT else MessageRole.USER,
                                content = item.optString("text"),
                                timeLabel = "Recent"
                            )
                        )
                    }
                }
            } else target.messages
            _activeConversation.value = target.copy(unreadCount = 0, messages = messages)
            _conversations.value = _conversations.value.map {
                if (it.id == id) it.copy(unreadCount = 0, messages = messages) else it
            }
        }
    }

    override suspend fun closeConversation() {
        _activeConversation.value = null
    }

    override suspend fun sendMessage(conversationId: String, content: String): Boolean {
        if (content.isBlank()) return false
        val response = apiClient.sendDirectMessage(conversationId, content.trim())
        if (response !is ApiResponse.Success) return false
        val currentConv = _conversations.value.firstOrNull { it.id == conversationId } ?: return false
        val newMsg = ChatMessage(
            id = "msg_${UUID.randomUUID().toString().take(6)}",
            role = MessageRole.USER,
            content = content.trim(),
            timeLabel = "Just now"
        )
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
            _friends.value = parseUsers(friends.data)
            _conversations.value = _friends.value.map { friend ->
                NebulaConversation(
                    id = friend.id,
                    title = friend.displayName,
                    isGroup = false,
                    partnerUser = friend,
                    participants = listOf(NebulaUser.CurrentUser, friend),
                    lastMessageText = "",
                    lastMessageTime = "",
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

    private fun parseUsers(array: JSONArray): List<NebulaUser> = buildList {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            add(
                NebulaUser(
                    id = item.optString("id"),
                    username = item.optString("username").let { if (it.startsWith("@")) it else "@$it" },
                    displayName = item.optString("displayName", "User"),
                    avatarLetter = item.optString("avatarLetter", "U"),
                    avatarUrl = item.optString("avatarUrl").ifBlank { null },
                    bio = item.optString("bio"),
                    status = item.optString("status", "Online"),
                    interests = item.optJSONArray("interests")?.let { interests ->
                        List(interests.length()) { index -> interests.optString(index) }
                    } ?: emptyList(),
                    friendRequestPending = item.optString("relationship") == "request_sent",
                    isFriend = item.optString("relationship") == "friends"
                )
            )
        }
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
