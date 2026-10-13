package com.nebula.app.domain.service

import com.nebula.app.domain.model.AnalyticsEventType
import com.nebula.app.domain.model.DeviceSession
import com.nebula.app.domain.model.NebulaAnalyticsEvent
import com.nebula.app.domain.model.NebulaNotification
import com.nebula.app.domain.model.NebulaRoom
import com.nebula.app.domain.model.NebulaUser
import com.nebula.app.domain.model.PollOption
import com.nebula.app.domain.model.RoomPoll
import com.nebula.app.domain.model.SharedMediaItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ==========================================
// 1. AUTH SERVICE
// ==========================================
interface AuthService {
    val currentSession: StateFlow<DeviceSession?>
    suspend fun loginWithGoogle(idToken: String): Result<NebulaUser>
    suspend fun loginWithPhone(phoneNumber: String, otp: String): Result<NebulaUser>
    suspend fun logout()
    fun canOccupyRoom(userId: String, roomId: String): Boolean
}

class MockAuthService : AuthService {
    private val _currentSession = MutableStateFlow<DeviceSession?>(
        DeviceSession(
            sessionId = "sess_current",
            userId = NebulaUser.CurrentUser.id,
            deviceId = "device_pixel_local",
            activeRoomId = null
        )
    )
    override val currentSession: StateFlow<DeviceSession?> = _currentSession.asStateFlow()

    override suspend fun loginWithGoogle(idToken: String): Result<NebulaUser> {
        return Result.success(NebulaUser.CurrentUser)
    }

    override suspend fun loginWithPhone(phoneNumber: String, otp: String): Result<NebulaUser> {
        return Result.success(NebulaUser.CurrentUser)
    }

    override suspend fun logout() {
        _currentSession.value = null
    }

    override fun canOccupyRoom(userId: String, roomId: String): Boolean {
        // Enforce ONE active room per user device session
        val current = _currentSession.value
        return current == null || current.activeRoomId == null || current.activeRoomId == roomId
    }
}

// ==========================================
// 2. CAPTCHA / ANTI-ABUSE SERVICE
// ==========================================
interface CaptchaService {
    suspend fun verifyRiskAction(actionType: RiskActionType): Boolean
}

enum class RiskActionType {
    SIGNUP,
    BULK_FRIEND_REQUEST,
    ROOM_SPAM_PROTECTION
}

class MockCaptchaService : CaptchaService {
    override suspend fun verifyRiskAction(actionType: RiskActionType): Boolean = true
}

// ==========================================
// 3. REALTIME SERVICE
// ==========================================
enum class RealtimeEventType {
    ROOM_JOINED,
    ROOM_LEFT,
    ROOM_MESSAGE,
    PLAYBACK_CHANGED,
    MEDIA_CHANGED,
    POLL_CREATED,
    POLL_UPDATED,
    POLL_CLOSED,
    PARTICIPANT_KICKED,
    PARTICIPANT_MUTED,
    PRESENCE_CHANGED
}

data class RealtimeEnvelope(
    val eventType: RealtimeEventType,
    val roomId: String?,
    val payload: Map<String, Any?> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

interface RealtimeService {
    fun subscribeToRoom(roomId: String): Flow<RealtimeEnvelope>
    suspend fun broadcast(envelope: RealtimeEnvelope)
}

// ==========================================
// 4. MEDIA PROVIDER ABSTRACTION
// ==========================================
interface MediaProvider {
    val providerName: String
    suspend fun search(query: String): List<SharedMediaItem>
    suspend fun getStreamMetadata(mediaId: String): SharedMediaItem?
}

class YouTubeMediaProvider : MediaProvider {
    override val providerName: String = "YouTube"
    override suspend fun search(query: String): List<SharedMediaItem> = listOf(
        SharedMediaItem.LofiStudy,
        SharedMediaItem.CyberpunkAnime
    )
    override suspend fun getStreamMetadata(mediaId: String): SharedMediaItem? = null
}

// ==========================================
// 5. POLL REPOSITORY
// ==========================================
interface PollRepository {
    suspend fun createPoll(roomId: String, question: String, options: List<String>): RoomPoll
    suspend fun votePoll(pollId: String, optionId: String): RoomPoll
    suspend fun resolvePoll(pollId: String): RoomPoll
}

// ==========================================
// 6. NOTIFICATION REPOSITORY
// ==========================================
interface NotificationRepository {
    val notifications: StateFlow<List<NebulaNotification>>
    suspend fun markAsRead(notificationId: String)
    suspend fun dismiss(notificationId: String)
    suspend fun addNotification(notification: NebulaNotification)
}

class MockNotificationRepository : NotificationRepository {
    private val _notifications = MutableStateFlow(NebulaNotification.SampleNotifications)
    override val notifications: StateFlow<List<NebulaNotification>> = _notifications.asStateFlow()

    override suspend fun markAsRead(notificationId: String) {
        _notifications.value = _notifications.value.map {
            if (it.id == notificationId) it.copy(isRead = true) else it
        }
    }

    override suspend fun dismiss(notificationId: String) {
        _notifications.value = _notifications.value.filterNot { it.id == notificationId }
    }

    override suspend fun addNotification(notification: NebulaNotification) {
        _notifications.value = listOf(notification) + _notifications.value
    }
}

// ==========================================
// 7. ANALYTICS & EVENT LOGGING REPOSITORY
// ==========================================
interface AnalyticsRepository {
    val loggedEvents: StateFlow<List<NebulaAnalyticsEvent>>
    suspend fun logEvent(type: AnalyticsEventType, metadata: Map<String, String> = emptyMap())
}

class MockAnalyticsRepository : AnalyticsRepository {
    private val _loggedEvents = MutableStateFlow<List<NebulaAnalyticsEvent>>(emptyList())
    override val loggedEvents: StateFlow<List<NebulaAnalyticsEvent>> = _loggedEvents.asStateFlow()

    override suspend fun logEvent(type: AnalyticsEventType, metadata: Map<String, String>) {
        val event = NebulaAnalyticsEvent(
            id = "evt_${System.currentTimeMillis()}",
            type = type,
            userId = NebulaUser.CurrentUser.id,
            metadata = metadata
        )
        _loggedEvents.value = _loggedEvents.value + event
    }
}

// ==========================================
// 8. AI SERVICE ABSTRACTION (NO EXTERNAL API)
// ==========================================
interface AIService {
    fun isEnabled(): Boolean
    suspend fun buildBehavioralContext(userId: String): Map<String, Any>
}

class LocalContextAIService(
    private val analyticsRepository: AnalyticsRepository
) : AIService {
    override fun isEnabled(): Boolean = false // Zero external AI API calls per spec

    override suspend fun buildBehavioralContext(userId: String): Map<String, Any> {
        val events = analyticsRepository.loggedEvents.value.filter { it.userId == userId }
        return mapOf(
            "totalSessions" to events.count { it.type == AnalyticsEventType.ROOM_JOINED },
            "preferredCategories" to listOf("Watch Parties", "Sci-Fi", "Lo-Fi")
        )
    }
}

// ==========================================
// 9. BACKEND INTEGRATION READINESS CONTRACTS
// ==========================================
typealias UserRepository = com.nebula.app.domain.repository.IProfileRepository
typealias RoomRepository = com.nebula.app.domain.repository.IRoomRepository
typealias RoomRealtimeService = RealtimeService
typealias MessageRepository = com.nebula.app.domain.repository.IChatRepository
typealias FriendRepository = com.nebula.app.domain.repository.ISocialRepository
typealias PrivateChatRepository = com.nebula.app.domain.repository.IChatRepository
typealias MediaRepository = MediaProvider
typealias UserStatsRepository = AnalyticsRepository

