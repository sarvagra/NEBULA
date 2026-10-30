package com.nebula.app.data.repository

import com.nebula.app.data.backend.NebulaApiClient
import com.nebula.app.data.backend.NebulaApiClient.ApiResponse
import com.nebula.app.data.realtime.RoomSocketClient
import com.nebula.app.domain.model.NebulaRoom
import com.nebula.app.domain.model.PollOption
import com.nebula.app.domain.model.RoomPoll
import com.nebula.app.domain.model.NebulaUser
import com.nebula.app.domain.model.PlayMode
import com.nebula.app.domain.model.RoomActivityType
import com.nebula.app.domain.model.RoomChatMessage
import com.nebula.app.domain.model.RoomParticipant
import com.nebula.app.domain.model.RoomPrivacy
import com.nebula.app.domain.model.RoomQueueItem
import com.nebula.app.domain.model.SharedMediaItem
import com.nebula.app.domain.repository.IRoomRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import com.nebula.app.data.local.SessionManager
import java.util.UUID

class RoomRepositoryImpl(
    private val apiClient: NebulaApiClient = NebulaApiClient,
    private val sessionManager: SessionManager? = null
) : IRoomRepository {

    var onSocialChanged: (() -> Unit)? = null

    fun disconnectRealtime() {
        socketClient.disconnect()
        _activeRoom.value = null
        _queue.value = emptyList()
    }

    // Instant local-first initial state from cache
    private val _rooms = MutableStateFlow<List<NebulaRoom>>(sessionManager?.getCachedRooms() ?: emptyList())
    override val rooms: StateFlow<List<NebulaRoom>> = _rooms.asStateFlow()

    private val _activeRoom = MutableStateFlow<NebulaRoom?>(null)
    override val activeRoom: StateFlow<NebulaRoom?> = _activeRoom.asStateFlow()

    private val _lastReaction = MutableStateFlow<String?>(null)
    override val lastReaction: StateFlow<String?> = _lastReaction.asStateFlow()

    private val _queue = MutableStateFlow<List<RoomQueueItem>>(emptyList())
    override val queue: StateFlow<List<RoomQueueItem>> = _queue.asStateFlow()

    private val realtimeScope = CoroutineScope(Dispatchers.IO)
    private val socketClient = RoomSocketClient(
        tokenProvider = { apiClient.authToken },
        baseUrlProvider = { apiClient.socketBaseUrl() },
        onMediaState = { positionMs, isPlaying ->
            applyRealtimeMediaState(positionMs, isPlaying)
        },
        onMediaSwitch = {
            realtimeScope.launch { syncActiveRoom() }
        },
        onChatMessage = { message ->
            applyRealtimeChatMessage(message)
        },
        onParticipantsChanged = { realtimeScope.launch { syncActiveRoom() } },
        onSocialChanged = { onSocialChanged?.invoke() },
        onRoomRemoved = { removedRoomId, reason ->
            _activeRoom.value = null
            if (reason == "kicked" && !removedRoomId.isNullOrBlank()) {
                _rooms.value = _rooms.value.filterNot { it.id == removedRoomId }
            }
        },
        onReconnected = {
            // Whatever the room broadcast while the socket was down was never queued
            // for us, so recover it from the history cursor rather than showing a
            // thread with a silent hole in it.
            realtimeScope.launch {
                syncActiveRoom()
                backfillMissedChat()
                refreshQueue()
            }
        },
        onQueueChanged = { realtimeScope.launch { refreshQueue() } }
    )

    /** Newest room-chat timestamp already shown, used as the reconnect backfill cursor. */
    @Volatile private var lastChatCursor: String? = null
    @Volatile private var lastChatRoomId: String? = null

    private suspend fun backfillMissedChat() {
        val current = _activeRoom.value ?: return
        // A cursor only belongs to the room it came from; a different room needs a full page.
        val cursor = if (lastChatRoomId == current.id) lastChatCursor else null
        if (cursor == null) {
            loadRoomMessages(current.id)
        } else {
            loadRoomMessages(current.id, since = cursor)
        }
    }

    init { socketClient.setOnEndedStateListener { positionMs, ended -> applyRealtimeEndedState(positionMs, ended) } }

    fun setAppForegrounded(foregrounded: Boolean) = socketClient.setAppForegrounded(foregrounded)

    /**
     * True while an authenticated realtime socket is open. The socket already keeps
     * presence fresh server-side, so the HTTP presence pulse can skip its write
     * whenever this is true — which at scale is almost always.
     */
    fun isRealtimeConnected(): Boolean = socketClient.isConnected()

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
            sessionManager?.saveCachedRooms(list)
            return list
        }
        return _rooms.value
    }

    suspend fun reconnectActiveRoom() {
        val current = _activeRoom.value ?: return
        socketClient.connect(current.id, current.hostId)
        syncActiveRoom()
        loadRoomMessages(current.id)
    }

    /**
     * Loads room chat. With [since] the server returns only what was created at or after
     * that cursor and the result is merged into the open thread; without it the page
     * replaces the thread.
     */
    private suspend fun loadRoomMessages(roomId: String, since: String? = null) {
        val current = _activeRoom.value ?: return
        val response = apiClient.getRoomMessages(roomId, since)
        if (response !is ApiResponse.Success) return
        val messages = mutableListOf<RoomChatMessage>()
        var cursor = if (since != null) lastChatCursor else null
        for (i in 0 until response.data.length()) {
            val json = response.data.optJSONObject(i) ?: continue
            val id = json.optString("id").ifBlank { json.optString("_id") }
            if (id.isBlank()) continue
            val createdAt = json.optString("createdAt")
            if (createdAt.isNotBlank() && (cursor == null || createdAt > cursor!!)) cursor = createdAt
            messages += RoomChatMessage(
                id = id,
                roomId = json.optString("roomId", roomId),
                senderId = json.optString("senderId"),
                senderName = json.optString("senderName", "Nebula User"),
                senderAvatar = json.optString("senderAvatarLetter", "U"),
                senderAvatarUrl = json.optString("senderAvatarUrl").ifBlank { null },
                content = json.optString("text", ""),
                timeLabel = formatRoomMessageTime(createdAt),
                isSystemMessage = json.optString("messageType") == "system"
            )
        }
        val rosterCurrent = _activeRoom.value ?: current
        val withAvatars = messages.map { message ->
            if (!message.senderAvatarUrl.isNullOrBlank()) message
            else rosterCurrent.participants.firstOrNull { it.user.id == message.senderId }?.user?.avatarUrl?.let { message.copy(senderAvatarUrl = it) } ?: message
        }
        val base = if (since != null) rosterCurrent.recentMessages else emptyList()
        val merged = base + withAvatars.filter { recovered -> base.none { it.id == recovered.id } }
        lastChatCursor = cursor
        lastChatRoomId = roomId
        _activeRoom.value = rosterCurrent.copy(recentMessages = merged)
        _rooms.value = _rooms.value.map { if (it.id == roomId) rosterCurrent.copy(recentMessages = merged) else it }
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
        socketClient.connect(newRoom.id, newRoom.hostId)
        return newRoom
    }

    /**
     * The last server reason a join was refused, so the caller can explain a device
     * conflict instead of a generic "could not join". Null means no refusal.
     */
    var lastJoinBlockReason: String? = null
        private set

    override suspend fun joinRoom(roomId: String): NebulaRoom? {
        lastJoinBlockReason = null
        val apiRes = apiClient.joinRoom(roomId)
        if (apiRes is ApiResponse.Success) {
            val room = parseRoomFromJson(apiRes.data.optJSONObject("data") ?: apiRes.data)
            val roomWithPoll = loadActivePoll(room)
            _activeRoom.value = roomWithPoll
            _rooms.value = _rooms.value.map { if (it.id == room.id) roomWithPoll else it }
            socketClient.connect(room.id, room.hostId)
            loadRoomMessages(room.id)
            refreshQueue()
            return roomWithPoll
        }

        lastJoinBlockReason = (apiRes as? ApiResponse.Error)?.message
        return null
    }

    override suspend fun leaveRoom(newHostId: String?) {
        val current = _activeRoom.value
        if (current == null) return
        val res = apiClient.leaveRoom(current.id, newHostId)
        if (res !is ApiResponse.Success) return
        socketClient.leave()
        if (res.data.optBoolean("dissolved", false)) _rooms.value = _rooms.value.filter { it.id != current.id }
        else refreshRooms()
        _activeRoom.value = null
        _queue.value = emptyList()
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
        if (res !is ApiResponse.Success) return null
        val updated = parseRoomFromJson(res.data.optJSONObject("data") ?: res.data)
        _activeRoom.value = updated
        _rooms.value = _rooms.value.map { if (it.id == updated.id) updated else it }
        return updated
    }

    override suspend fun transferHost(newHostId: String?, random: Boolean): NebulaRoom? {
        val current = _activeRoom.value ?: return null
        val res = apiClient.transferHost(current.id, newHostId, random)
        if (res !is ApiResponse.Success) return null
        val updated = parseRoomFromJson(res.data.optJSONObject("data") ?: res.data)
        _activeRoom.value = updated
        _rooms.value = _rooms.value.map { if (it.id == updated.id) updated else it }
        return updated
    }

    override suspend fun syncActiveRoom(): NebulaRoom? {
        val current = _activeRoom.value ?: return null
        val res = apiClient.getRoom(current.id)
        if (res is ApiResponse.Success) {
            val freshFromServer = loadActivePoll(parseRoomFromJson(res.data.optJSONObject("data") ?: res.data))
            // Roster/settings refreshes may read a checkpointed DB playhead. Keep
            // the live socket timeline when the selected media has not changed.
            val fresh = if (freshFromServer.mediaItem?.mediaUrl == current.mediaItem?.mediaUrl) {
                freshFromServer.copy(
                    isPlaying = current.isPlaying,
                    playbackPositionMs = current.playbackPositionMs,
                    playbackPositionLabel = current.playbackPositionLabel
                )
            } else freshFromServer
            // An active room without its member roster is invalid. Do not leave a
            // phantom room on screen with a zero-member badge.
            if (fresh.participants.isEmpty()) {
                socketClient.leave()
                _activeRoom.value = null
                _rooms.value = _rooms.value.filterNot { it.id == current.id }
                refreshRooms()
                return null
            }
            _activeRoom.value = fresh
            _rooms.value = _rooms.value.map { if (it.id == fresh.id) fresh else it }
            return fresh
        }
        if (res is ApiResponse.Error && (res.statusCode == 404 || res.statusCode == 403)) {
            socketClient.leave()
            _activeRoom.value = null
            _rooms.value = _rooms.value.filterNot { it.id == current.id }
            refreshRooms()
            return null
        }
        return current
    }

    override suspend fun togglePlayPause() {
        val current = _activeRoom.value ?: return
        val newPlay = !current.isPlaying
        val positionMs = socketClient.currentPlaybackPositionMs().takeIf { it > 0L } ?: current.playbackPositionMs
        val updated = current.copy(isPlaying = newPlay)
        _activeRoom.value = updated
        _rooms.value = _rooms.value.map { if (it.id == updated.id) updated else it }
        if (newPlay) socketClient.play(positionMs) else socketClient.pause(positionMs)
    }

    override suspend fun seekTo(positionMs: Long, positionLabel: String) {
        val current = _activeRoom.value ?: return
        val updated = current.copy(
            playbackPositionMs = positionMs,
            playbackPositionLabel = positionLabel
        )
        _activeRoom.value = updated
        _rooms.value = _rooms.value.map { if (it.id == updated.id) updated else it }
        socketClient.seek(positionMs)
    }

    override fun reportHostPlayerSample(positionMs: Long, isPlaying: Boolean) {
        socketClient.reportHostPlayerSample(positionMs, isPlaying)
        applyRealtimeMediaState(positionMs, isPlaying)
    }

    override suspend fun updateMedia(title: String, mediaUrl: String, durationSeconds: Long): NebulaRoom? {
        val current = _activeRoom.value ?: return null
        val persisted = apiClient.updateMedia(
            current.id,
            title = title,
            mediaUrl = mediaUrl,
            playbackPosition = 0L,
            isPlaying = true
        )
        if (persisted !is ApiResponse.Success) return null
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
            mediaEnded = false,
            playbackPositionMs = 0L,
            playbackPositionLabel = "00:00"
        )
        _activeRoom.value = updated
        _rooms.value = _rooms.value.map { if (it.id == updated.id) updated else it }
        return updated
    }

    // ==========================================
    // ROOM QUEUE
    // ==========================================

    override suspend fun refreshQueue() {
        val room = _activeRoom.value ?: run {
            _queue.value = emptyList()
            return
        }
        val res = apiClient.getRoomQueue(room.id)
        if (res !is ApiResponse.Success) return
        val items = mutableListOf<RoomQueueItem>()
        for (i in 0 until res.data.length()) {
            res.data.optJSONObject(i)?.let { items.add(parseQueueItem(it)) }
        }
        // A queue is only meaningful for the room it was read from. Losing that
        // association would let a stale list appear inside a different room.
        if (_activeRoom.value?.id == room.id) _queue.value = items
    }

    override suspend fun addToQueue(
        title: String,
        mediaUrl: String,
        subtitle: String,
        durationSeconds: Long
    ): Boolean {
        val room = _activeRoom.value ?: return false
        val res = apiClient.addRoomQueueItem(
            roomId = room.id,
            title = title,
            mediaUrl = mediaUrl,
            subtitle = subtitle,
            durationSeconds = durationSeconds
        )
        // The socket echo refreshes too, but patching immediately keeps the sheet
        // from sitting empty until the round trip finishes.
        if (res is ApiResponse.Success) {
            res.data.optJSONObject("data")?.let { item ->
                _queue.value = _queue.value + parseQueueItem(item)
            }
        }
        return res is ApiResponse.Success
    }

    override suspend fun voteSkipQueueItem(itemId: String): Boolean {
        val room = _activeRoom.value ?: return false
        return apiClient.voteSkipQueueItem(room.id, itemId) is ApiResponse.Success
    }

    override suspend fun advanceQueue(): Boolean {
        val room = _activeRoom.value ?: return false
        return apiClient.advanceRoomQueue(room.id) is ApiResponse.Success
    }

    override suspend fun removeQueueItem(itemId: String): Boolean {
        val room = _activeRoom.value ?: return false
        val res = apiClient.removeRoomQueueItem(room.id, itemId)
        if (res is ApiResponse.Success) {
            _queue.value = _queue.value.filterNot { it.id == itemId }
        }
        return res is ApiResponse.Success
    }

    private fun parseQueueItem(json: JSONObject): RoomQueueItem {
        val voters = mutableListOf<String>()
        json.optJSONArray("skipVoterIds")?.let { array ->
            for (i in 0 until array.length()) {
                array.optString(i).takeIf { it.isNotBlank() }?.let { voters.add(it) }
            }
        }
        return RoomQueueItem(
            id = json.optString("id"),
            roomId = json.optString("roomId"),
            title = json.optString("title", "Queued video"),
            subtitle = json.optString("subtitle", ""),
            mediaUrl = json.optString("mediaUrl", ""),
            durationSeconds = json.optLong("durationSeconds", 0L),
            addedByUserId = json.optString("addedByUserId", ""),
            addedByName = json.optString("addedByName", "Nebula User"),
            skipVoteCount = json.optInt("skipVoteCount", 0),
            skipVotesRequired = json.optInt("skipVotesRequired", 1).coerceAtLeast(1),
            skipVoterIds = voters
        )
    }

    private fun applyRealtimeMediaState(positionMs: Long, isPlaying: Boolean) {
        val current = _activeRoom.value ?: return
        val safePositionMs = positionMs.coerceAtLeast(0L)
        val updated = current.copy(
            isPlaying = isPlaying,
            mediaEnded = if (isPlaying) false else current.mediaEnded,
            playbackPositionMs = safePositionMs,
            playbackPositionLabel = String.format(
                "%02d:%02d",
                safePositionMs / 60000L,
                (safePositionMs / 1000L) % 60L
            )
        )
        _activeRoom.value = updated
        _rooms.value = _rooms.value.map { if (it.id == updated.id) updated else it }
    }

    private fun applyRealtimeEndedState(positionMs: Long, ended: Boolean) {
        val current = _activeRoom.value ?: return
        val safePositionMs = positionMs.coerceAtLeast(0L)
        val updated = current.copy(
            isPlaying = if (ended) false else current.isPlaying,
            mediaEnded = ended,
            playbackPositionMs = safePositionMs,
            playbackPositionLabel = String.format("%02d:%02d", safePositionMs / 60000L, (safePositionMs / 1000L) % 60L)
        )
        _activeRoom.value = updated
        _rooms.value = _rooms.value.map { if (it.id == updated.id) updated else it }
    }

    override fun reportVideoEnded(positionMs: Long) {
        socketClient.reportVideoEnded(positionMs)
        applyRealtimeEndedState(positionMs, true)
    }

    override fun restartVideo() {
        socketClient.restartVideo()
        applyRealtimeEndedState(0L, false)
        applyRealtimeMediaState(0L, true)
    }

    override suspend fun sendRoomMessage(content: String) {
        if (content.isBlank()) return
        socketClient.sendChat(content.trim())
    }

    private fun applyRealtimeChatMessage(json: JSONObject) {
        val current = _activeRoom.value ?: return
        // Every authenticated socket also receives the user's direct messages, so a
        // payload carrying a conversationId is a DM and must never be written into the
        // room's chat feed.
        if (json.optString("conversationId").isNotBlank()) return
        val payloadRoomId = json.optString("roomId")
        if (payloadRoomId.isNotBlank() && payloadRoomId != current.id) return
        val messageId = json.optString("id").ifBlank { json.optString("_id") }
        if (messageId.isBlank() || current.recentMessages.any { it.id == messageId }) return
        val createdAt = json.optString("createdAt")
        val message = RoomChatMessage(
            id = messageId,
            roomId = json.optString("roomId", current.id),
            senderId = json.optString("senderId"),
            senderName = json.optString("senderName", "Nebula User"),
            senderAvatar = json.optString("senderAvatarLetter", "U"),
            senderAvatarUrl = json.optString("senderAvatarUrl").ifBlank { null },
            content = json.optString("text", ""),
            timeLabel = formatRoomMessageTime(createdAt),
            isSystemMessage = json.optString("messageType") == "system"
        )
        // Live messages advance the backfill cursor, so a later reconnect asks only for
        // what genuinely arrived while the socket was down.
        if (createdAt.isNotBlank() && (lastChatCursor == null || createdAt > lastChatCursor!!)) {
            lastChatCursor = createdAt
        }
        lastChatRoomId = current.id
        val updated = current.copy(recentMessages = current.recentMessages + message)
        _activeRoom.value = updated
        _rooms.value = _rooms.value.map { if (it.id == updated.id) updated else it }
    }

    override suspend fun sendReaction(emoji: String) {
        if (!socketClient.isConnected()) return
        socketClient.sendReaction(emoji)
        _lastReaction.value = emoji
    }

    override suspend fun votePoll(optionId: String): Boolean {
        val current = _activeRoom.value ?: return false
        val poll = current.activePoll ?: return false
        val response = apiClient.votePoll(poll.id, optionId)
        if (response !is ApiResponse.Success) return false
        val updatedPollJson = response.data.optJSONObject("data") ?: return false
        val updatedPoll = parsePoll(updatedPollJson) ?: return false
        val updatedRoom = current.copy(activePoll = updatedPoll)
        _activeRoom.value = updatedRoom
        _rooms.value = _rooms.value.map { if (it.id == updatedRoom.id) updatedRoom else it }
        return true
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
        val inviteCode = json.optString("inviteCode").takeIf { it.isNotBlank() && it != "null" }
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
        val positionMs = mediaObj?.optDouble("playbackPosition", 0.0)?.times(1000.0)?.toLong() ?: 0L
        val durationSeconds = mediaObj?.optLong("durationSeconds", 3600L) ?: 3600L

        val participantsJson = json.optJSONArray("participants") ?: JSONArray()
        val participantList = mutableListOf<RoomParticipant>()
        var hostName = json.optString("hostName").takeIf { it.isNotBlank() && it != "null" }.orEmpty()
        for (i in 0 until participantsJson.length()) {
            val p = participantsJson.optJSONObject(i) ?: continue
            val pId = p.optString("userId")
            if (pId.isBlank() || pId == "null") continue
            val pName = p.optString("displayName").takeIf { it.isNotBlank() && it != "null" } ?: "Explorer"
            val isHost = p.optBoolean("isHost", false)
            if (pId == hostId || isHost) hostName = pName
            val u = NebulaUser(
                id = pId,
                username = p.optString("username").takeIf { it.isNotBlank() }?.let { if (it.startsWith("@")) it else "@$it" } ?: "@${pName.lowercase().replace(" ", "")}",
                displayName = pName,
                avatarLetter = p.optString("avatarLetter", pName.take(1).uppercase()),
                avatarUrl = p.optString("avatarUrl").ifBlank { null },
                bannerUrl = p.optString("bannerUrl").ifBlank { null },
                bio = p.optString("bio", ""),
                status = p.optString("status", "In room"),
                interests = p.optJSONArray("interests")?.let { arr -> List(arr.length()) { index -> arr.optString(index) }.filter { it.isNotBlank() } } ?: emptyList(),
                isOnline = true,
                presenceStatus = com.nebula.app.domain.model.NebulaPresenceStatus.IN_A_ROOM,
                currentRoomId = id,
                roomsHostedCount = 0,
                roomsJoinedCount = 0,
                timeInSyncHours = 0.0
            )
            if (participantList.none { it.user.id == pId }) participantList.add(
                RoomParticipant(
                    user = u,
                    isHost = isHost,
                    isAudioActive = !p.optBoolean("isMuted", false),
                    isVideoActive = !p.optBoolean("isCameraOff", false)
                )
            )
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
            inviteCode = inviteCode,
            name = name,
            hostId = hostId,
            hostName = hostName.ifBlank { participantList.firstOrNull { it.user.id == hostId }?.user?.displayName ?: "Host unavailable" },
            privacy = privacy,
            activityType = activity,
            mediaItem = sharedMedia,
            participantCount = participantList.size,
            maxParticipants = json.optInt("maxParticipants", 20),
            isMicAllowed = json.optBoolean("isMicAllowed", true),
            isVideoAllowed = json.optBoolean("isVideoAllowed", true),
            participants = participantList,
            isPlaying = isPlaying,
            mediaEnded = false,
            playbackPositionMs = positionMs,
            playbackPositionLabel = String.format("%02d:%02d", positionMs / 60000L, (positionMs / 1000L) % 60L),
            durationLabel = String.format("%02d:%02d", durationSeconds / 60, durationSeconds % 60),
            tags = parsedTags,
            playMode = PlayMode.POLL_PLAY
        )
    }

    private suspend fun loadActivePoll(room: NebulaRoom): NebulaRoom {
        val response = apiClient.getRoomPoll(room.id)
        if (response !is ApiResponse.Success) return room
        val payload = response.data.optJSONObject("data")
        return room.copy(activePoll = payload?.let(::parsePoll))
    }

    private fun parsePoll(json: JSONObject): RoomPoll? {
        val id = json.optString("id").ifBlank { return null }
        val optionsJson = json.optJSONArray("options") ?: JSONArray()
        val options = List(optionsJson.length()) { index ->
            val option = optionsJson.optJSONObject(index) ?: JSONObject()
            PollOption(
                id = option.optString("id"),
                text = option.optString("text"),
                votes = option.optInt("votes")
            )
        }.filter { it.id.isNotBlank() }
        return RoomPoll(
            id = id,
            roomId = json.optString("roomId"),
            question = json.optString("question"),
            options = options,
            userVotedOptionId = json.optString("userVotedOptionId").ifBlank { null },
            isClosed = !json.optBoolean("isActive", true)
        )
    }
}

private fun formatRoomMessageTime(isoString: String?): String {
    if (isoString.isNullOrBlank()) {
        return java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date())
    }
    return try {
        val instant = java.time.Instant.parse(isoString)
        val zone = java.time.ZoneId.systemDefault()
        val localTime = instant.atZone(zone)
        val formatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.getDefault())
        localTime.format(formatter)
    } catch (_: Exception) {
        try {
            val date = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.parse(isoString)
            java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(date ?: java.util.Date())
        } catch (_: Exception) {
            java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date())
        }
    }
}
