package com.nebula.app.data.repository

import com.nebula.app.domain.model.ChatMessage
import com.nebula.app.domain.model.MessageRole
import com.nebula.app.domain.model.NebulaConversation
import com.nebula.app.domain.model.NebulaFriendRequest
import com.nebula.app.domain.model.NebulaUser
import com.nebula.app.domain.repository.ISocialRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class SocialRepositoryImpl : ISocialRepository {

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
            _activeConversation.value = target.copy(unreadCount = 0)
            _conversations.value = _conversations.value.map {
                if (it.id == id) it.copy(unreadCount = 0) else it
            }
        }
    }

    override suspend fun closeConversation() {
        _activeConversation.value = null
    }

    override suspend fun sendMessage(conversationId: String, content: String) {
        if (content.isBlank()) return
        val currentConv = _conversations.value.firstOrNull { it.id == conversationId } ?: return
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
    }

    override suspend fun acceptFriendRequest(requestId: String) {
        val req = _friendRequests.value.firstOrNull { it.id == requestId }
        if (req != null) {
            _friends.value = _friends.value + req.sender
            _friendRequests.value = _friendRequests.value.filter { it.id != requestId }
        }
    }

    override suspend fun declineFriendRequest(requestId: String) {
        _friendRequests.value = _friendRequests.value.filter { it.id != requestId }
    }

    override suspend fun addFriend(user: NebulaUser) {
        if (!_friends.value.any { it.id == user.id }) {
            _friends.value = _friends.value + user
            _suggestedPeople.value = _suggestedPeople.value.filter { it.id != user.id }
        }
    }
}
