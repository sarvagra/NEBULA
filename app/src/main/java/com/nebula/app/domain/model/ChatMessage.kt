package com.nebula.app.domain.model

enum class MessageRole {
    USER,
    ASSISTANT
}

/**
 * Delivery state of an outgoing message. Only [SENT] means the server has persisted
 * it; [SENDING] and [FAILED] are local placeholders that must never be presented as
 * delivered.
 */
enum class MessageSendState {
    SENDING,
    SENT,
    FAILED
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
    /** When one bubble carries a pile of photos, this holds all of them. */
    val mediaUrls: List<String> = emptyList(),
    val messageType: String = "text",
    val editedAt: Long? = null,
    val editHistory: List<String> = emptyList(),
    val deletedAt: Long? = null,
    val readAt: Long? = null,
    /** When the recipient's device acknowledged the message (double tick). */
    val deliveredAt: Long? = null,
    val isSpoiler: Boolean = false,
    val mediaMimeType: String? = null,
    val sendState: MessageSendState = MessageSendState.SENT,
    /** Stable idempotency key so a retry cannot create a second copy on the server. */
    val clientKey: String? = null,
)
