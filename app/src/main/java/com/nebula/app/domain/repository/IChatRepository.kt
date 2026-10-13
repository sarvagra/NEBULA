package com.nebula.app.domain.repository

import com.nebula.app.domain.model.ChatAttachment
import com.nebula.app.domain.model.ChatMessage
import com.nebula.app.domain.model.Conversation
import kotlinx.coroutines.flow.StateFlow

interface IChatRepository {
    val conversations: StateFlow<List<Conversation>>
    val activeConversation: StateFlow<Conversation?>
    val activeMessages: StateFlow<List<ChatMessage>>

    suspend fun startNewConversation(): Conversation
    suspend fun selectConversation(id: String)
    suspend fun sendMessage(
        content: String,
        attachments: List<ChatAttachment> = emptyList()
    )
    suspend fun retryLastMessage()
    suspend fun toggleSaveToMemory(messageId: String)
    suspend fun deleteConversation(id: String)
    suspend fun clearCurrentChat()
    suspend fun clearAllLocalData()
}
