package com.nebula.app.data.repository

import com.nebula.app.domain.model.AssistantStatus
import com.nebula.app.domain.model.ChatAttachment
import com.nebula.app.domain.model.ChatMessage
import com.nebula.app.domain.model.Conversation
import com.nebula.app.domain.model.MessageRole
import com.nebula.app.domain.repository.IChatRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ChatRepositoryImpl : IChatRepository {

    private val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    override val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _activeConversation = MutableStateFlow<Conversation?>(null)
    override val activeConversation: StateFlow<Conversation?> = _activeConversation.asStateFlow()

    private val _activeMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    override val activeMessages: StateFlow<List<ChatMessage>> = _activeMessages.asStateFlow()

    override suspend fun startNewConversation(): Conversation {
        val newConv = Conversation(
            id = "conv_${UUID.randomUUID().toString().take(8)}",
            title = "New Conversation",
            lastMessagePreview = "Ready to assist",
            timestamp = System.currentTimeMillis(),
            messages = emptyList()
        )
        _activeConversation.value = newConv
        _activeMessages.value = emptyList()
        return newConv
    }

    override suspend fun selectConversation(id: String) {
        val conv = _conversations.value.firstOrNull { it.id == id }
        if (conv != null) {
            _activeConversation.value = conv
            _activeMessages.value = conv.messages
        }
    }

    override suspend fun sendMessage(
        content: String,
        attachments: List<ChatAttachment>
    ) {
        val timeLabel = timeFormatter.format(Date())
        val userMsg = ChatMessage(
            id = "msg_${UUID.randomUUID().toString().take(8)}",
            role = MessageRole.USER,
            content = content,
            timestamp = System.currentTimeMillis(),
            timeLabel = timeLabel,
            status = AssistantStatus.COMPLETED,
            attachments = attachments
        )

        val updatedMessages = _activeMessages.value + userMsg
        _activeMessages.value = updatedMessages

        // Generate intelligent assistant reply with Thinking -> Completed animation states
        val assistantMsgId = "asst_${UUID.randomUUID().toString().take(8)}"
        val thinkingMsg = ChatMessage(
            id = assistantMsgId,
            role = MessageRole.ASSISTANT,
            content = "",
            timestamp = System.currentTimeMillis(),
            timeLabel = timeLabel,
            status = AssistantStatus.THINKING
        )
        _activeMessages.value = updatedMessages + thinkingMsg

        // Simulate fast assistant cognition
        delay(600)

        val responseText = generateAssistantResponse(content, attachments)
        val completedMsg = thinkingMsg.copy(
            content = responseText,
            status = AssistantStatus.COMPLETED
        )
        val finalMessages = updatedMessages + completedMsg
        _activeMessages.value = finalMessages

        // Update active conversation in history
        val convTitle = if (_activeConversation.value?.title == "New Conversation" || _activeConversation.value == null) {
            summarizeTitle(content)
        } else {
            _activeConversation.value?.title ?: summarizeTitle(content)
        }

        val currentConv = _activeConversation.value ?: Conversation(
            id = "conv_${UUID.randomUUID().toString().take(8)}",
            title = convTitle,
            lastMessagePreview = responseText,
            timestamp = System.currentTimeMillis(),
            messages = finalMessages
        )

        val updatedConv = currentConv.copy(
            title = convTitle,
            lastMessagePreview = responseText,
            timestamp = System.currentTimeMillis(),
            messages = finalMessages
        )
        _activeConversation.value = updatedConv

        // Persist to conversation list
        val currentList = _conversations.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == updatedConv.id }
        if (index >= 0) {
            currentList[index] = updatedConv
        } else {
            currentList.add(0, updatedConv)
        }
        _conversations.value = currentList
    }

    override suspend fun retryLastMessage() {
        val lastUserMsg = _activeMessages.value.lastOrNull { it.role == MessageRole.USER }
        if (lastUserMsg != null) {
            // Remove error assistant message if any
            val filtered = _activeMessages.value.filter { it.status != AssistantStatus.ERROR }
            _activeMessages.value = filtered
            sendMessage(lastUserMsg.content, lastUserMsg.attachments)
        }
    }

    override suspend fun toggleSaveToMemory(messageId: String) {
        val updated = _activeMessages.value.map { msg ->
            if (msg.id == messageId) msg.copy(isSavedToMemory = !msg.isSavedToMemory) else msg
        }
        _activeMessages.value = updated
    }

    override suspend fun deleteConversation(id: String) {
        _conversations.value = _conversations.value.filter { it.id != id }
        if (_activeConversation.value?.id == id) {
            _activeConversation.value = null
            _activeMessages.value = emptyList()
        }
    }

    override suspend fun clearCurrentChat() {
        _activeConversation.value = null
        _activeMessages.value = emptyList()
    }

    override suspend fun clearAllLocalData() {
        _conversations.value = emptyList()
        _activeConversation.value = null
        _activeMessages.value = emptyList()
    }

    private fun summarizeTitle(prompt: String): String {
        val clean = prompt.trim().replace(Regex("[^a-zA-Z0-9 ]"), "")
        val words = clean.split(" ").filter { it.isNotBlank() }
        return if (words.size <= 4) words.joinToString(" ") else words.take(4).joinToString(" ") + "…"
    }

    private fun generateAssistantResponse(prompt: String, attachments: List<ChatAttachment>): String {
        val lower = prompt.lowercase()
        return when {
            attachments.isNotEmpty() ->
                "I've processed your attachment '${attachments.first().name}'. I've indexed key entities, action items, and private notes into your local vault."
            lower.contains("rahul") ->
                "Rahul is noted as a close friend. Your last conversation covered college projects and internship deadlines 4 days ago. Would you like me to draft an update regarding the submission?"
            lower.contains("priority") || lower.contains("task") || lower.contains("today") ->
                "Here are your top priorities for today:\n1. Finalize the Nebula architecture review\n2. Sync with Rahul on design tokens\n3. Review encrypted local database schemas."
            lower.contains("memory") || lower.contains("note") ->
                "Found 3 relevant memories in your local knowledge vault:\n• Preferred theme: Dark & Minimal\n• Current project: Android Native Nebula OS\n• Key relationship: Rahul (Collaborator)."
            else ->
                "Understood. I have logged this into your local graph. How else can I assist with your workflow today?"
        }
    }

    private fun createSampleConversations(): List<Conversation> {
        val now = System.currentTimeMillis()
        val oneHour = 3600 * 1000L
        val oneDay = 24 * 3600 * 1000L

        return listOf(
            Conversation(
                id = "conv_today_1",
                title = "Compose Insets Architecture",
                lastMessagePreview = "WindowInsets.ime padding synchronized perfectly.",
                timestamp = now - 2 * oneHour,
                messages = listOf(
                    ChatMessage(
                        id = "m1",
                        role = MessageRole.USER,
                        content = "How should we handle keyboard insets in Jetpack Compose?",
                        timeLabel = "2:15 PM"
                    ),
                    ChatMessage(
                        id = "m2",
                        role = MessageRole.ASSISTANT,
                        content = "Use WindowInsets.ime combined with WindowInsets.navigationBars in an IME-aware layout so the chat composer stays docked directly above the keyboard.",
                        timeLabel = "2:16 PM"
                    )
                )
            ),
            Conversation(
                id = "conv_yest_1",
                title = "Rahul Project Discussion",
                lastMessagePreview = "Rahul: Let's sync about the new theme tokens.",
                timestamp = now - 1 * oneDay - 3 * oneHour,
                messages = listOf(
                    ChatMessage(
                        id = "m3",
                        role = MessageRole.USER,
                        content = "What did Rahul and I talk about yesterday?",
                        timeLabel = "Yesterday"
                    ),
                    ChatMessage(
                        id = "m4",
                        role = MessageRole.ASSISTANT,
                        content = "You discussed finalizing the four core themes: Light, Dark, Lavender, and Red-Hot-Pink.",
                        timeLabel = "Yesterday"
                    )
                )
            ),
            Conversation(
                id = "conv_prev_1",
                title = "Local Vault Cryptography",
                lastMessagePreview = "Zero cloud telemetry with on-device AES-256.",
                timestamp = now - 4 * oneDay,
                messages = listOf(
                    ChatMessage(
                        id = "m5",
                        role = MessageRole.USER,
                        content = "Verify privacy and local storage policies.",
                        timeLabel = "4 days ago"
                    ),
                    ChatMessage(
                        id = "m6",
                        role = MessageRole.ASSISTANT,
                        content = "All conversation graphs, biometric credentials, and memory vectors remain 100% on-device.",
                        timeLabel = "4 days ago"
                    )
                )
            ),
            Conversation(
                id = "conv_older_1",
                title = "Nebula OS Vision",
                lastMessagePreview = "Calm, minimal, intelligent, modern social platform.",
                timestamp = now - 12 * oneDay,
                messages = listOf(
                    ChatMessage(
                        id = "m7",
                        role = MessageRole.USER,
                        content = "Define our core product principles.",
                        timeLabel = "2 weeks ago"
                    ),
                    ChatMessage(
                        id = "m8",
                        role = MessageRole.ASSISTANT,
                        content = "Private, premium, calm, intelligent, modern, minimal, fluid, responsive, sophisticated.",
                        timeLabel = "2 weeks ago"
                    )
                )
            )
        )
    }
}
