package com.nebula.app.domain.repository

import com.nebula.app.domain.model.NebulaConversation
import com.nebula.app.domain.model.NebulaFriendRequest
import com.nebula.app.domain.model.NebulaUser
import kotlinx.coroutines.flow.StateFlow

interface ISocialRepository {
    val friends: StateFlow<List<NebulaUser>>
    val friendRequests: StateFlow<List<NebulaFriendRequest>>
    val suggestedPeople: StateFlow<List<NebulaUser>>
    val searchResults: StateFlow<List<NebulaUser>>
    val conversations: StateFlow<List<NebulaConversation>>
    val activeConversation: StateFlow<NebulaConversation?>

    /** Opens the live direct-message channel. Safe to call repeatedly. */
    fun startRealtime() {}
    /** Closes it on sign-out so a stale token is not kept connected. */
    fun stopRealtime() {}

    suspend fun selectConversation(id: String)
    /**
     * Re-pulls the currently open thread. Used to pick up read receipts and
     * messages the other side sent while this screen stayed open.
     */
    suspend fun refreshActiveConversation()
    suspend fun closeConversation()
    suspend fun sendMessage(conversationId: String, content: String, mediaUrl: String? = null, messageType: String = "text", isSpoiler: Boolean = false, mediaMimeType: String? = null, mediaUrls: List<String> = emptyList()): Boolean
    /** Re-sends a message that failed to persist, reusing its idempotency key. */
    suspend fun retryMessage(clientKey: String): Boolean
    /**
     * Attempts every unsent message. Called when connectivity returns, so a message
     * typed in a tunnel goes out on its own instead of waiting for a tap.
     */
    suspend fun flushPendingNow()
    suspend fun editMessage(messageId: String, content: String): Boolean
    suspend fun unsendMessage(messageId: String): Boolean
    suspend fun acceptFriendRequest(requestId: String): Boolean
    suspend fun declineFriendRequest(requestId: String): Boolean
    suspend fun addFriend(user: NebulaUser): Boolean
    suspend fun withdrawFriendRequest(userId: String): Boolean
    suspend fun refresh()
    suspend fun searchPeople(query: String)
    suspend fun refreshSuggestions(): Boolean
    suspend fun blockUser(userId: String): Boolean
    suspend fun loadPublicProfile(username: String): NebulaUser?
    suspend fun loadPublicProfileById(userId: String): NebulaUser?
    suspend fun removeFriend(userId: String): Boolean
}
