package com.nebula.app.domain.repository

import com.nebula.app.domain.model.NebulaRoom
import com.nebula.app.domain.model.RoomActivityType
import com.nebula.app.domain.model.RoomPrivacy
import com.nebula.app.domain.model.SharedMediaItem
import kotlinx.coroutines.flow.StateFlow

interface IRoomRepository {
    val rooms: StateFlow<List<NebulaRoom>>
    val activeRoom: StateFlow<NebulaRoom?>
    val lastReaction: StateFlow<String?>

    suspend fun createRoom(
        name: String,
        privacy: RoomPrivacy = RoomPrivacy.FRIENDS,
        activityType: RoomActivityType = RoomActivityType.WATCH,
        mediaItem: SharedMediaItem? = null,
        tags: List<String>? = null,
        maxParticipants: Int? = null
    ): NebulaRoom

    suspend fun joinRoom(roomId: String): NebulaRoom?
    suspend fun leaveRoom(newHostId: String? = null)
    suspend fun updateRoomSettings(
        name: String? = null,
        maxParticipants: Int? = null,
        isMicAllowed: Boolean? = null,
        isVideoAllowed: Boolean? = null,
        tags: List<String>? = null
    ): NebulaRoom?
    suspend fun transferHost(newHostId: String? = null, random: Boolean = false): NebulaRoom?
    suspend fun syncActiveRoom(): NebulaRoom?
    suspend fun togglePlayPause()
    suspend fun seekTo(positionMs: Long, positionLabel: String)
    fun reportHostPlayerSample(positionMs: Long, isPlaying: Boolean)
    suspend fun sendRoomMessage(content: String)
    suspend fun sendReaction(emoji: String)
    suspend fun votePoll(optionId: String): Boolean
    suspend fun switchPlayMode(playMode: com.nebula.app.domain.model.PlayMode)
    suspend fun updateMedia(title: String, mediaUrl: String, durationSeconds: Long = 0): NebulaRoom?
}
