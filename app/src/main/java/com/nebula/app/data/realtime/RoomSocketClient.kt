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
    private val onMediaState: (positionMs: Long, isPlaying: Boolean) -> Unit,
    private val onMediaSwitch: (JSONObject) -> Unit,
    private val onChatMessage: (JSONObject) -> Unit
) {
    private var socket: Socket? = null
    private var roomId: String? = null
    private var hostId: String? = null
    private var heartbeat: ScheduledExecutorService? = null
    private var basePositionMs = 0L
    private var baseTimestampMs = 0L
    private var playing = false

    fun connect(roomId: String, hostId: String) {
        disconnect()
        this.roomId = roomId
        this.hostId = hostId
        val token = tokenProvider()?.takeIf { it.isNotBlank() } ?: return
        val options = IO.Options().apply {
            auth = mapOf("token" to token)
            transports = arrayOf("websocket", "polling")
            reconnection = true
        }
        socket = IO.socket("https://nebula-73kb.onrender.com", options).apply {
            on(Socket.EVENT_CONNECT) {
                emit("room:join", JSONObject().put("roomId", roomId))
            }
            on("media:sync") { args ->
                val state = args.firstOrNull() as? JSONObject ?: return@on
                applyState(
                    state.optLong("positionMs", state.optLong("position", 0L) * 1000L),
                    state.optBoolean("isPlaying", false),
                    state.optLong("timestamp", System.currentTimeMillis())
                )
            }
            on("media:play") { args -> handlePlayback(args, true) }
            on("media:pause") { args -> handlePlayback(args, false) }
            on("media:seek") { args ->
                val payload = args.firstOrNull() as? JSONObject ?: return@on
                applyState(
                    payload.optLong("positionMs", payload.optLong("position", 0L) * 1000L),
                    playing,
                    payload.optLong("timestamp", System.currentTimeMillis())
                )
            }
            on("media:switch") { args ->
                val payload = args.firstOrNull() as? JSONObject ?: return@on
                val mediaState = payload.optJSONObject("mediaState") ?: return@on
                mediaState.put(
                    "playbackPositionMs",
                    mediaState.optLong("playbackPositionMs", mediaState.optLong("playbackPosition", 0L) * 1000L)
                )
                basePositionMs = mediaState.optLong("playbackPositionMs")
                baseTimestampMs = payload.optLong("timestamp", System.currentTimeMillis())
                playing = mediaState.optBoolean("isPlaying", false)
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

    fun play(positionMs: Long) = emitPlayback("media:play", positionMs, true)

    fun pause(positionMs: Long) = emitPlayback("media:pause", positionMs, false)

    fun seek(positionMs: Long) {
        basePositionMs = positionMs.coerceAtLeast(0L)
        baseTimestampMs = System.currentTimeMillis()
        socket?.emit(
            "media:seek",
            JSONObject().put("roomId", roomId).put("positionMs", basePositionMs)
        )
    }

    fun switchMedia(title: String, mediaUrl: String, durationSeconds: Long = 0L) {
        socket?.emit(
            "media:switch",
            JSONObject().put("roomId", roomId)
                .put("title", title)
                .put("mediaUrl", mediaUrl)
                .put("durationSeconds", durationSeconds)
        )
    }

    fun sendChat(text: String, mediaUrl: String? = null) {
        val currentRoom = roomId ?: return
        if (text.isBlank() && mediaUrl.isNullOrBlank()) return
        val payload = JSONObject().put("roomId", currentRoom).put("text", text)
        if (!mediaUrl.isNullOrBlank()) payload.put("mediaUrl", mediaUrl)
        socket?.emit("chat:message", payload)
    }

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
            JSONObject().put("roomId", roomId).put("positionMs", basePositionMs)
        )
    }

    private fun handlePlayback(args: Array<Any>, isPlaying: Boolean) {
        val payload = args.firstOrNull() as? JSONObject ?: return
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
            )
        }, 0L, 1L, TimeUnit.SECONDS)
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
