package com.nebula.app.domain.repository

import com.nebula.app.domain.model.NebulaRoom
import com.nebula.app.domain.model.RoomActivityType
import com.nebula.app.domain.model.RoomQueueItem
import com.nebula.app.domain.model.RoomPrivacy
import com.nebula.app.domain.model.SharedMediaItem
import kotlinx.coroutines.flow.StateFlow

interface IRoomRepository {
    val rooms: StateFlow<List<NebulaRoom>>
    val activeRoom: StateFlow<NebulaRoom?>
    val lastReaction: StateFlow<String?>

    /** Items the room has agreed to watch later, oldest first. */
    val queue: StateFlow<List<RoomQueueItem>>

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
    fun reportVideoEnded(positionMs: Long)
    fun restartVideo()
    suspend fun sendRoomMessage(content: String)
    suspend fun sendReaction(emoji: String)
    suspend fun votePoll(optionId: String): Boolean
    suspend fun switchPlayMode(playMode: com.nebula.app.domain.model.PlayMode)
    suspend fun updateMedia(title: String, mediaUrl: String, durationSeconds: Long = 0): NebulaRoom?

    // Room queue. Every mutation returns whether the server accepted it so the UI can
    // report the real outcome instead of assuming a local edit stuck.
    suspend fun refreshQueue()
    suspend fun addToQueue(
        title: String,
        mediaUrl: String,
        subtitle: String = "",
        durationSeconds: Long = 0L
    ): Boolean
    suspend fun voteSkipQueueItem(itemId: String): Boolean
    suspend fun advanceQueue(): Boolean
    suspend fun removeQueueItem(itemId: String): Boolean
}
