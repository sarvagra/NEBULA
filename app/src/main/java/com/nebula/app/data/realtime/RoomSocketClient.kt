package com.nebula.app.data.realtime

import android.util.Base64
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class RoomSocketClient(
    private val tokenProvider: () -> String?,
    private val baseUrlProvider: () -> String,
    private val onMediaState: (positionMs: Long, isPlaying: Boolean) -> Unit,
    private val onMediaSwitch: (JSONObject) -> Unit,
    private val onChatMessage: (JSONObject) -> Unit,
    private val onParticipantsChanged: () -> Unit,
    private val onSocialChanged: () -> Unit,
    private val onRoomRemoved: (roomId: String?, reason: String?) -> Unit,
    /**
     * Fired when the socket comes back after a drop. Anything broadcast while it was
     * down was never delivered, so the room has to backfill from its own cursor.
     */
    private val onReconnected: () -> Unit = {}
) {
    private var socket: Socket? = null
    private var roomId: String? = null
    private var hasConnectedOnce = false
    @Volatile private var hostId: String? = null
    private var heartbeat: ScheduledExecutorService? = null
    private var basePositionMs = 0L
    private var baseTimestampMs = 0L
    private var playing = false
    @Volatile private var ended = false
    /**
     * Playback generation as granted by the server. Every command carries it back, so
     * a command built before a newer one cannot rewind the room; every inbound event
     * that is older than what we already applied is discarded.
     */
    @Volatile private var playbackVersion = 0
    private var onEndedState: (Long, Boolean) -> Unit = { _, _ -> }

    fun setOnEndedStateListener(listener: (Long, Boolean) -> Unit) { onEndedState = listener }

    fun connect(roomId: String, hostId: String) {
        disconnect()
        this.roomId = roomId
        this.hostId = hostId
        this.hasConnectedOnce = false
        this.playbackVersion = 0
        val token = tokenProvider()?.takeIf { it.isNotBlank() } ?: return
        val options = IO.Options().apply {
            auth = mapOf("token" to token)
            transports = arrayOf("websocket", "polling")
            reconnection = true
        }
        socket = IO.socket(baseUrlProvider(), options).apply {
            on(Socket.EVENT_CONNECT) {
                emit("room:join", JSONObject().put("roomId", roomId))
                emit("room:foreground", JSONObject().put("roomId", roomId))
                if (hasConnectedOnce) onReconnected() else hasConnectedOnce = true
            }
            on("participant:joined") { onParticipantsChanged() }
            on("participant:left") { onParticipantsChanged() }
            on("participant:updated") { onParticipantsChanged() }
            on("room:host_changed") { args ->
                val payload = args.firstOrNull() as? JSONObject
                this@RoomSocketClient.hostId = payload?.optString("newHostId")?.takeIf { it.isNotBlank() } ?: this@RoomSocketClient.hostId
                onParticipantsChanged()
            }
            on("friendship:changed") { onSocialChanged() }
            on("room:removed") { args ->
                val payload = args.firstOrNull() as? JSONObject
                onRoomRemoved(payload?.optString("roomId"), payload?.optString("reason"))
                disconnect()
            }
            on("media:ack") { args ->
                (args.firstOrNull() as? JSONObject)?.let { adoptVersion(it) }
            }
            on("media:state") { args ->
                val state = args.firstOrNull() as? JSONObject ?: return@on
                if (!adoptVersion(state)) return@on
                ended = state.optBoolean("ended", ended)
                applyState(
                    state.optLong("positionMs", 0L),
                    state.optBoolean("isPlaying", playing),
                    state.optLong("timestamp", System.currentTimeMillis())
                )
                onEndedState(state.optLong("positionMs", 0L), ended)
            }
            on("media:sync") { args ->
                val state = args.firstOrNull() as? JSONObject ?: return@on
                if (!adoptVersion(state)) return@on
                state.optJSONObject("mediaState")?.let { mediaState ->
                    mediaState.put(
                        "playbackPositionMs",
                        mediaState.optLong("playbackPositionMs", state.optLong("positionMs", 0L))
                    )
                    onMediaSwitch(mediaState)
                }
                applyState(
                    state.optLong("positionMs", state.optLong("position", 0L) * 1000L),
                    state.optBoolean("isPlaying", false),
                    state.optLong("timestamp", System.currentTimeMillis())
                )
                ended = state.optBoolean("ended", false)
                onEndedState(state.optLong("positionMs", 0L), ended)
            }
            on("media:ended") { args ->
                val state = args.firstOrNull() as? JSONObject ?: return@on
                if (!adoptVersion(state)) return@on
                ended = true
                val position = state.optLong("positionMs", 0L)
                applyState(position, false, state.optLong("timestamp", System.currentTimeMillis()))
                onEndedState(position, true)
            }
            on("media:restart") { args ->
                val state = args.firstOrNull() as? JSONObject ?: return@on
                if (!adoptVersion(state)) return@on
                ended = false
                applyState(0L, true, state.optLong("timestamp", System.currentTimeMillis()))
                onEndedState(0L, false)
            }
            on("media:play") { args -> if (!ended) handlePlayback(args, true) }
            on("media:pause") { args -> if (!ended) handlePlayback(args, false) }
            on("media:seek") { args ->
                val payload = args.firstOrNull() as? JSONObject ?: return@on
                if (!adoptVersion(payload)) return@on
                applyState(
                    payload.optLong("positionMs", payload.optLong("position", 0L) * 1000L),
                    playing,
                    payload.optLong("timestamp", System.currentTimeMillis())
                )
            }
            on("media:switch") { args ->
                val payload = args.firstOrNull() as? JSONObject ?: return@on
                if (!adoptVersion(payload)) return@on
                val mediaState = payload.optJSONObject("mediaState") ?: return@on
                mediaState.put(
                    "playbackPositionMs",
                    mediaState.optLong("playbackPositionMs", mediaState.optLong("playbackPosition", 0L) * 1000L)
                )
                basePositionMs = mediaState.optLong("playbackPositionMs")
                baseTimestampMs = payload.optLong("timestamp", System.currentTimeMillis())
                playing = mediaState.optBoolean("isPlaying", false)
                ended = false
                onEndedState(0L, false)
                onMediaSwitch(mediaState)
            }
            on("chat:message") { args ->
                (args.firstOrNull() as? JSONObject)?.let(onChatMessage)
            }
            connect()
        }
        startHeartbeat()
    }

    fun leave() {
        roomId?.let { socket?.emit("room:leave", JSONObject().put("roomId", it)) }
        disconnect()
    }

    fun setAppForegrounded(foregrounded: Boolean) {
        val currentRoom = roomId ?: return
        socket?.emit(
            if (foregrounded) "room:foreground" else "room:background",
            JSONObject().put("roomId", currentRoom)
        )
    }

    fun play(positionMs: Long) = emitPlayback("media:play", positionMs, true)

    fun pause(positionMs: Long) = emitPlayback("media:pause", positionMs, false)

    fun seek(positionMs: Long) {
        basePositionMs = positionMs.coerceAtLeast(0L)
        baseTimestampMs = System.currentTimeMillis()
        socket?.emit(
            "media:seek",
            JSONObject().put("roomId", roomId)
                .put("positionMs", basePositionMs)
                .put("version", playbackVersion)
        )
    }

    fun reportHostPlayerSample(positionMs: Long, isPlaying: Boolean) {
        if (!isHost()) return
        val stateChanged = playing != isPlaying
        basePositionMs = positionMs.coerceAtLeast(0L)
        baseTimestampMs = System.currentTimeMillis()
        playing = isPlaying
        if (stateChanged) {
            socket?.emit(
                if (isPlaying) "media:play" else "media:pause",
                JSONObject().put("roomId", roomId)
                    .put("positionMs", basePositionMs)
                    .put("version", playbackVersion)
            )
        }
    }

    fun reportVideoEnded(positionMs: Long) {
        if (!isHost() || ended) return
        ended = true
        playing = false
        basePositionMs = positionMs.coerceAtLeast(0L)
        baseTimestampMs = System.currentTimeMillis()
        socket?.emit(
            "media:end",
            JSONObject().put("roomId", roomId)
                .put("positionMs", basePositionMs)
                .put("version", playbackVersion)
        )
    }

    fun restartVideo() {
        if (!isHost()) return
        ended = false
        playing = true
        basePositionMs = 0L
        baseTimestampMs = System.currentTimeMillis()
        socket?.emit("media:restart", JSONObject().put("roomId", roomId).put("version", playbackVersion))
    }

    fun currentPlaybackPositionMs(): Long = (basePositionMs + if (playing) {
        (System.currentTimeMillis() - baseTimestampMs).coerceAtLeast(0L)
    } else 0L).coerceAtLeast(0L)

    fun switchMedia(title: String, mediaUrl: String, durationSeconds: Long = 0L) {
        socket?.emit(
            "media:switch",
            JSONObject().put("roomId", roomId)
                .put("title", title)
                .put("mediaUrl", mediaUrl)
                .put("durationSeconds", durationSeconds)
                .put("version", playbackVersion)
        )
    }

    fun sendChat(text: String, mediaUrl: String? = null) {
        val currentRoom = roomId ?: return
        if (text.isBlank() && mediaUrl.isNullOrBlank()) return
        val payload = JSONObject().put("roomId", currentRoom).put("text", text)
        if (!mediaUrl.isNullOrBlank()) payload.put("mediaUrl", mediaUrl)
        socket?.emit("chat:message", payload)
    }

    fun sendReaction(emoji: String) {
        val currentRoom = roomId ?: return
        if (emoji.isBlank()) return
        socket?.emit("chat:reaction", JSONObject().put("roomId", currentRoom).put("emoji", emoji))
    }

    fun isConnected(): Boolean = socket?.connected() == true

    fun disconnect() {
        heartbeat?.shutdownNow()
        heartbeat = null
        socket?.off()
        socket?.disconnect()
        socket = null
        roomId = null
        hostId = null
    }

    private fun emitPlayback(event: String, positionMs: Long, isPlaying: Boolean) {
        basePositionMs = positionMs.coerceAtLeast(0L)
        baseTimestampMs = System.currentTimeMillis()
        playing = isPlaying
        socket?.emit(
            event,
            JSONObject().put("roomId", roomId)
                .put("positionMs", basePositionMs)
                .put("version", playbackVersion)
        )
    }

    /**
     * Returns false when the event belongs to an older playback generation than the
     * one already applied, which is how a reordered packet is kept from rewinding the
     * room. Events without a version (older servers) are accepted unchanged.
     */
    private fun adoptVersion(payload: JSONObject): Boolean {
        val incoming = payload.optInt("version", -1)
        if (incoming < 0) return true
        if (incoming < playbackVersion) return false
        playbackVersion = incoming
        return true
    }

    private fun handlePlayback(args: Array<Any>, isPlaying: Boolean) {
        val payload = args.firstOrNull() as? JSONObject ?: return
        if (!adoptVersion(payload)) return
        applyState(
            payload.optLong("positionMs", payload.optLong("position", 0L) * 1000L),
            isPlaying,
            payload.optLong("timestamp", System.currentTimeMillis())
        )
    }

    private fun applyState(positionMs: Long, isPlaying: Boolean, timestampMs: Long) {
        basePositionMs = positionMs.coerceAtLeast(0L)
        baseTimestampMs = timestampMs
        playing = isPlaying
        if (positionMs == 0L && isPlaying) ended = false
        val projected = if (isPlaying) {
            basePositionMs + (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
        } else {
            basePositionMs
        }
        onMediaState(projected, isPlaying)
    }

    private fun startHeartbeat() {
        heartbeat = Executors.newSingleThreadScheduledExecutor()
        heartbeat?.scheduleAtFixedRate({
            val currentSocket = socket ?: return@scheduleAtFixedRate
            val currentRoom = roomId ?: return@scheduleAtFixedRate
            if (!isHost()) return@scheduleAtFixedRate
            val now = System.currentTimeMillis()
            val positionMs = basePositionMs + if (playing) {
                (now - baseTimestampMs).coerceAtLeast(0L)
            } else 0L
            currentSocket.emit(
                "media:sync",
                JSONObject().put("roomId", currentRoom)
                    .put("positionMs", positionMs)
                    .put("isPlaying", playing)
                    .put("timestamp", now)
                    .put("version", playbackVersion)
            )
        }, 0L, 250L, TimeUnit.MILLISECONDS)
    }

    private fun isHost(): Boolean {
        val payload = tokenProvider()?.split(".")?.getOrNull(1) ?: return false
        return try {
            val json = JSONObject(
                String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP), StandardCharsets.UTF_8)
            )
            json.optString("id") == hostId || json.optString("sub") == hostId
        } catch (_: Exception) {
            false
        }
    }
}
