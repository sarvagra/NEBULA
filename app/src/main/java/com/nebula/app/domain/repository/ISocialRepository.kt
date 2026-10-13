package com.nebula.app.domain.repository

import com.nebula.app.domain.model.NebulaConversation
import com.nebula.app.domain.model.NebulaFriendRequest
import com.nebula.app.domain.model.NebulaUser
import kotlinx.coroutines.flow.StateFlow

interface ISocialRepository {
    val friends: StateFlow<List<NebulaUser>>
    val friendRequests: StateFlow<List<NebulaFriendRequest>>
    val suggestedPeople: StateFlow<List<NebulaUser>>
    val conversations: StateFlow<List<NebulaConversation>>
    val activeConversation: StateFlow<NebulaConversation?>

    suspend fun selectConversation(id: String)
    suspend fun closeConversation()
    suspend fun sendMessage(conversationId: String, content: String)
    suspend fun acceptFriendRequest(requestId: String)
    suspend fun declineFriendRequest(requestId: String)
    suspend fun addFriend(user: NebulaUser)
    suspend fun withdrawFriendRequest(userId: String)
    suspend fun refresh()
}
