package com.nebula.app.domain.model

enum class MessageRole {
    USER,
    ASSISTANT
}

enum class AssistantStatus {
    IDLE,
    THINKING,
    STREAMING,
    COMPLETED,
    ERROR
}

data class ChatAttachment(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeLabel: String = "240 KB",
    val type: AttachmentType = AttachmentType.DOCUMENT
)

enum class AttachmentType {
    DOCUMENT,
    IMAGE,
    CAMERA,
    FILE
}

data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val timeLabel: String = "Just now",
    val status: AssistantStatus = AssistantStatus.COMPLETED,
    val attachments: List<ChatAttachment> = emptyList(),
    val isSavedToMemory: Boolean = false,
    val senderId: String? = null,
    val senderName: String? = null,
    val senderAvatarUrl: String? = null,
    val senderAvatarLetter: String = "U",
    val mediaUrl: String? = null,
    val messageType: String = "text",
    val editedAt: Long? = null,
    val editHistory: List<String> = emptyList(),
    val deletedAt: Long? = null,
    val readAt: Long? = null,
    val isSpoiler: Boolean = false,
    val mediaMimeType: String? = null,
)
