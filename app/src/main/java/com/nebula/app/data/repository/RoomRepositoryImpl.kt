package com.nebula.app.data.repository

import com.nebula.app.data.backend.NebulaApiClient
import com.nebula.app.data.backend.NebulaApiClient.ApiResponse
import com.nebula.app.domain.model.NebulaRoom
import com.nebula.app.domain.model.NebulaUser
import com.nebula.app.domain.model.PlayMode
import com.nebula.app.domain.model.RoomActivityType
import com.nebula.app.domain.model.RoomChatMessage
import com.nebula.app.domain.model.RoomParticipant
import com.nebula.app.domain.model.RoomPrivacy
import com.nebula.app.domain.model.SharedMediaItem
import com.nebula.app.domain.repository.IRoomRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class RoomRepositoryImpl(
    private val apiClient: NebulaApiClient = NebulaApiClient
) : IRoomRepository {

    // Clean initial state: no hardcoded fake rooms
    private val _rooms = MutableStateFlow<List<NebulaRoom>>(emptyList())
    override val rooms: StateFlow<List<NebulaRoom>> = _rooms.asStateFlow()

    private val _activeRoom = MutableStateFlow<NebulaRoom?>(null)
    override val activeRoom: StateFlow<NebulaRoom?> = _activeRoom.asStateFlow()

    private val _lastReaction = MutableStateFlow<String?>(null)
    override val lastReaction: StateFlow<String?> = _lastReaction.asStateFlow()

    /**
     * Refreshes the active room list from the authoritative Nebula backend server.
     */
    suspend fun refreshRooms(): List<NebulaRoom> {
        val res = apiClient.getRooms()
        if (res is ApiResponse.Success) {
            val list = mutableListOf<NebulaRoom>()
            for (i in 0 until res.data.length()) {
                val item = res.data.optJSONObject(i)
                if (item != null) {
                    list.add(parseRoomFromJson(item))
                }
            }
            _rooms.value = list
            return list
        }
        return _rooms.value
    }

    override suspend fun createRoom(
        name: String,
        privacy: RoomPrivacy,
        activityType: RoomActivityType,
        mediaItem: SharedMediaItem?,
        tags: List<String>?,
        maxParticipants: Int?
    ): NebulaRoom {
        val privacyStr = when (privacy) {
            RoomPrivacy.INVITE_ONLY -> "invite_only"
            RoomPrivacy.FRIENDS -> "friends"
            else -> "public"
        }
        val activityStr = when (activityType) {
            RoomActivityType.VOICE -> "voice"
            RoomActivityType.CHAT -> "chat"
            else -> "watch"
        }

        val res = apiClient.createRoom(
            name = name.ifBlank { "Chill Lounge" },
            privacy = privacyStr,
            activity = activityStr,
            tags = tags,
            maxParticipants = maxParticipants,
            mediaTitle = mediaItem?.title ?: "Ambient Stream",
            mediaSubtitle = mediaItem?.source ?: "Synchronized Session",
            mediaUrl = mediaItem?.youtubeVideoId?.let { "https://www.youtube.com/watch?v=$it" } ?: (mediaItem?.mediaUrl ?: "")
        )

        val newRoom = if (res is ApiResponse.Success) {
            parseRoomFromJson(res.data.optJSONObject("data") ?: res.data)
        } else {
            throw IllegalStateException(
                (res as? ApiResponse.Error)?.message ?: "Room creation failed because the remote service is unavailable"
            )
        }

        val updated = listOf(newRoom) + _rooms.value.filter { it.id != newRoom.id }
        _rooms.value = updated
        _activeRoom.value = newRoom
        return newRoom
    }

    override suspend fun joinRoom(roomId: String): NebulaRoom? {
        val apiRes = apiClient.joinRoom(roomId)
        if (apiRes is ApiResponse.Success) {
            val room = parseRoomFromJson(apiRes.data.optJSONObject("data") ?: apiRes.data)
            _activeRoom.value = room
            _rooms.value = _rooms.value.map { if (it.id == room.id) room else it }
            return room
        }

        val target = _rooms.value.firstOrNull { it.id == roomId }
        if (target != null) {
            val hasUser = target.participants.any {
                it.user.id == NebulaUser.CurrentUser.id ||
                it.user.displayName.equals(NebulaUser.CurrentUser.displayName, ignoreCase = true)
            }
            val updated = if (!hasUser) {
                target.copy(
                    participantCount = target.participantCount + 1,
                    participants = target.participants + RoomParticipant(NebulaUser.CurrentUser, isHost = false)
                )
            } else target
            _activeRoom.value = updated
            return updated
        }
        return null
    }

    override suspend fun leaveRoom(newHostId: String?) {
        val current = _activeRoom.value
        if (current != null) {
            val res = apiClient.leaveRoom(current.id, newHostId)
            val isDissolved = if (res is ApiResponse.Success) {
                res.data.optBoolean("dissolved", current.participants.size <= 1)
            } else {
                current.participants.size <= 1
            }
            if (isDissolved) {
                _rooms.value = _rooms.value.filter { it.id != current.id }
            } else {
                refreshRooms()
            }
        }
        _activeRoom.value = null
    }

    override suspend fun updateRoomSettings(
        name: String?,
        maxParticipants: Int?,
        isMicAllowed: Boolean?,
        isVideoAllowed: Boolean?,
        tags: List<String>?
    ): NebulaRoom? {
        val current = _activeRoom.value ?: return null
        val res = apiClient.updateRoomSettings(
            roomId = current.id,
            name = name,
            maxParticipants = maxParticipants,
            isMicAllowed = isMicAllowed,
            isVideoAllowed = isVideoAllowed,
            tags = tags
        )
        val updated = if (res is ApiResponse.Success) {
            parseRoomFromJson(res.data.optJSONObject("data") ?: res.data)
        } else {
            current.copy(
                name = name ?: current.name,
                maxParticipants = maxParticipants ?: current.maxParticipants,
                isMicAllowed = isMicAllowed ?: current.isMicAllowed,
                isVideoAllowed = isVideoAllowed ?: current.isVideoAllowed,
                tags = tags ?: current.tags
            )
        }
        _activeRoom.value = updated
        _rooms.value = _rooms.value.map { if (it.id == updated.id) updated else it }
        return updated
    }

    override suspend fun transferHost(newHostId: String?, random: Boolean): NebulaRoom? {
        val current = _activeRoom.value ?: return null
        val res = apiClient.transferHost(current.id, newHostId, random)
        val updated = if (res is ApiResponse.Success) {
            parseRoomFromJson(res.data.optJSONObject("data") ?: res.data)
        } else {
            val candidate = current.participants.firstOrNull { it.user.id == newHostId }
                ?: if (random) current.participants.filter { it.user.id != current.hostId }.randomOrNull() else null
            if (candidate != null) {
                current.copy(
                    hostId = candidate.user.id,
                    hostName = candidate.user.displayName,
                    participants = current.participants.map {
                        it.copy(isHost = it.user.id == candidate.user.id)
                    }
                )
            } else current
        }
        _activeRoom.value = updated
        _rooms.value = _rooms.value.map { if (it.id == updated.id) updated else it }
        return updated
    }

    override suspend fun syncActiveRoom(): NebulaRoom? {
        val current = _activeRoom.value ?: return null
        val res = apiClient.getRoom(current.id)
        if (res is ApiResponse.Success) {
            val fresh = parseRoomFromJson(res.data.optJSONObject("data") ?: res.data)
            _activeRoom.value = fresh
            return fresh
        }
        return current
    }

    override suspend fun togglePlayPause() {
        val current = _activeRoom.value ?: return
        val newPlay = !current.isPlaying
        val updated = current.copy(isPlaying = newPlay)
        _activeRoom.value = updated
        _rooms.value = _rooms.value.map { if (it.id == updated.id) updated else it }
        apiClient.updateMedia(
            roomId = current.id,
            title = current.mediaItem?.title,
            isPlaying = newPlay,
            playbackPosition = current.playbackPositionMs / 1000L
        )
    }

    override suspend fun seekTo(positionMs: Long, positionLabel: String) {
        val current = _activeRoom.value ?: return
        val updated = current.copy(
            playbackPositionMs = positionMs,
            playbackPositionLabel = positionLabel
        )
        _activeRoom.value = updated
        _rooms.value = _rooms.value.map { if (it.id == updated.id) updated else it }
        apiClient.updateMedia(
            roomId = current.id,
            title = current.mediaItem?.title,
            isPlaying = current.isPlaying,
            playbackPosition = positionMs / 1000L
        )
    }

    override suspend fun updateMedia(title: String, mediaUrl: String, durationSeconds: Long): NebulaRoom? {
        val current = _activeRoom.value ?: return null
        val ytId = SharedMediaItem.extractYouTubeId(mediaUrl)
        val newMedia = SharedMediaItem(
            id = "media_${System.currentTimeMillis()}",
            title = title,
            source = if (ytId != null) "YouTube" else "Stream",
            durationLabel = if (durationSeconds > 0) "${durationSeconds / 60}:${String.format("%02d", durationSeconds % 60)}" else "Live",
            durationMs = durationSeconds * 1000L,
            mediaUrl = mediaUrl,
            youtubeVideoId = ytId
        )
        val updated = current.copy(
            mediaItem = newMedia,
            isPlaying = true,
            playbackPositionMs = 0L,
            playbackPositionLabel = "00:00"
        )
        _activeRoom.value = updated
        _rooms.value = _rooms.value.map { if (it.id == updated.id) updated else it }
        apiClient.updateMedia(
            roomId = current.id,
            title = title,
            isPlaying = true,
            playbackPosition = 0L,
            mediaUrl = mediaUrl
        )
        return updated
    }

    override suspend fun sendRoomMessage(content: String) {
        if (content.isBlank()) return
        val current = _activeRoom.value ?: return
        val newMsg = RoomChatMessage(
            id = "msg_${System.currentTimeMillis()}",
            roomId = current.id,
            senderId = NebulaUser.CurrentUser.id,
            senderName = NebulaUser.CurrentUser.displayName.split(" ").first(),
            senderAvatar = NebulaUser.CurrentUser.avatarLetter,
            content = content.trim(),
            timeLabel = "Just now"
        )
        val updated = current.copy(recentMessages = current.recentMessages + newMsg)
        _activeRoom.value = updated
        _rooms.value = _rooms.value.map { if (it.id == updated.id) updated else it }
    }

    override suspend fun sendReaction(emoji: String) {
        _lastReaction.value = emoji
    }

    override suspend fun votePoll(optionId: String) {
        val current = _activeRoom.value ?: return
        val poll = current.activePoll ?: return
        val updatedOptions = poll.options.map { opt ->
            if (opt.id == optionId) opt.copy(votes = opt.votes + 1) else opt
        }
        val updatedPoll = poll.copy(
            options = updatedOptions,
            userVotedOptionId = optionId
        )
        val updatedRoom = current.copy(activePoll = updatedPoll)
        _activeRoom.value = updatedRoom
        _rooms.value = _rooms.value.map { if (it.id == updatedRoom.id) updatedRoom else it }
    }

    override suspend fun switchPlayMode(playMode: PlayMode) {
        val current = _activeRoom.value ?: return
        val updated = current.copy(playMode = playMode)
        _activeRoom.value = updated
        _rooms.value = _rooms.value.map { if (it.id == updated.id) updated else it }
    }

    private fun parseRoomFromJson(json: JSONObject): NebulaRoom {
        val id = json.optString("id").ifBlank { json.optString("_id", "room_${UUID.randomUUID().toString().take(6)}") }
        val name = json.optString("name", "Ambient Room")
        val hostId = json.optString("hostId", "")
        val privacyStr = json.optString("privacy", "public")
        val privacy = when (privacyStr) {
            "friends" -> RoomPrivacy.FRIENDS
            "invite_only", "private" -> RoomPrivacy.INVITE_ONLY
            else -> RoomPrivacy.PUBLIC
        }
        val activityStr = json.optString("activity", "watch")
        val activity = when (activityStr) {
            "voice" -> RoomActivityType.VOICE
            "chat" -> RoomActivityType.CHAT
            else -> RoomActivityType.WATCH
        }
        val mediaObj = json.optJSONObject("mediaState")
        val mediaTitle = mediaObj?.optString("title", "Ambient Stream") ?: "Ambient Stream"
        val mediaSubtitle = mediaObj?.optString("subtitle", "Synchronized Session") ?: "Synchronized Session"
        val isPlaying = mediaObj?.optBoolean("isPlaying", true) ?: true
        val positionSeconds = mediaObj?.optLong("playbackPosition", 0L) ?: 0L
        val durationSeconds = mediaObj?.optLong("durationSeconds", 3600L) ?: 3600L

        val participantsJson = json.optJSONArray("participants") ?: JSONArray()
        val participantList = mutableListOf<RoomParticipant>()
        var hostName = "Host"
        for (i in 0 until participantsJson.length()) {
            val p = participantsJson.optJSONObject(i) ?: continue
            val pId = p.optString("userId")
            val pName = p.optString("displayName", "Explorer")
            val isHost = p.optBoolean("isHost", false)
            if (isHost) hostName = pName
            val u = NebulaUser(
                id = pId,
                username = "@${pName.lowercase().replace(" ", "")}",
                displayName = pName,
                avatarLetter = p.optString("avatarLetter", pName.take(1).uppercase()),
                bio = "",
                status = "In room",
                currentRoomId = id,
                roomsHostedCount = 0,
                roomsJoinedCount = 0,
                timeInSyncHours = 0.0
            )
            participantList.add(RoomParticipant(user = u, isHost = isHost))
        }

        val mediaUrl = mediaObj?.optString("mediaUrl", "") ?: ""
        var ytId = SharedMediaItem.extractYouTubeId(mediaUrl)
        if (ytId.isNullOrBlank() && mediaUrl.length == 11 && !mediaUrl.contains(".")) {
            ytId = mediaUrl
        }

        val hasValidMedia = !ytId.isNullOrBlank() || (mediaUrl.isNotBlank() && mediaUrl.startsWith("http"))

        val sharedMedia: SharedMediaItem? = if (hasValidMedia) {
            SharedMediaItem(
                id = "media_$id",
                title = mediaTitle,
                source = mediaSubtitle,
                durationLabel = String.format("%02d:%02d", durationSeconds / 60, durationSeconds % 60),
                durationMs = durationSeconds * 1000L,
                mediaUrl = mediaUrl,
                youtubeVideoId = ytId
            )
        } else {
            null
        }

        val tagsJson = json.optJSONArray("tags")
        val parsedTags = mutableListOf<String>()
        if (tagsJson != null) {
            for (i in 0 until tagsJson.length()) {
                val t = tagsJson.optString(i)
                if (t.isNotBlank()) parsedTags.add(t)
            }
        }

        return NebulaRoom(
            id = id,
            name = name,
            hostId = hostId,
            hostName = hostName,
            privacy = privacy,
            activityType = activity,
            mediaItem = sharedMedia,
            participantCount = json.optInt("participantCount", participantList.size.coerceAtLeast(1)),
            maxParticipants = json.optInt("maxParticipants", 20),
            isMicAllowed = json.optBoolean("isMicAllowed", true),
            isVideoAllowed = json.optBoolean("isVideoAllowed", true),
            participants = participantList.ifEmpty {
                listOf(RoomParticipant(user = NebulaUser.CurrentUser, isHost = true))
            },
            isPlaying = isPlaying,
            playbackPositionMs = positionSeconds * 1000L,
            playbackPositionLabel = String.format("%02d:%02d", positionSeconds / 60, positionSeconds % 60),
            durationLabel = String.format("%02d:%02d", durationSeconds / 60, durationSeconds % 60),
            tags = if (parsedTags.isNotEmpty()) parsedTags else listOf("Social", "Cinema"),
            playMode = PlayMode.POLL_PLAY
        )
    }
}
