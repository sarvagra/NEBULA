package com.nebula.app.data.realtime

import android.util.Base64
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RoomSocketClient(
    private val tokenProvider: () -> String?,
    private val onMediaState: (positionSeconds: Long, isPlaying: Boolean) -> Unit,
    private val onMediaSwitch: (JSONObject) -> Unit
) {
    private var socket: Socket? = null
    private var roomId: String? = null
    private var hostId: String? = null
    private var heartbeat: ScheduledExecutorService? = null
    private var basePositionSeconds = 0L
    private var baseTimestampMs = 0L
    private var playing = false

    fun connect(roomId: String, hostId: String) {
        disconnect()
        this.roomId = roomId
        this.hostId = hostId
        val token = tokenProvider()?.takeIf { it.isNotBlank() } ?: return
        val root = "https://nebula-73kb.onrender.com"
        val options = IO.Options().apply {
            auth = mapOf("token" to token)
            transports = arrayOf("websocket", "polling")
            reconnection = true
        }
        socket = IO.socket(root, options).apply {
            on(Socket.EVENT_CONNECT) {
                emit("room:join", JSONObject().put("roomId", roomId))
            }
            on("media:sync") { args ->
                val state = args.firstOrNull() as? JSONObject ?: return@on
                val position = state.optLong("position", 0L)
                val isPlaying = state.optBoolean("isPlaying", false)
                basePositionSeconds = position
                baseTimestampMs = state.optLong("timestamp", System.currentTimeMillis())
                playing = isPlaying
                onMediaState(position, isPlaying)
            }
            on("media:play") { args -> handlePlayback(args, true) }
            on("media:pause") { args -> handlePlayback(args, false) }
            on("media:seek") { args -> handlePlayback(args, playing) }
            on("media:switch") { args ->
                val payload = args.firstOrNull() as? JSONObject ?: return@on
                val mediaState = payload.optJSONObject("mediaState") ?: return@on
                basePositionSeconds = mediaState.optLong("playbackPosition", 0L)
                baseTimestampMs = System.currentTimeMillis()
                playing = mediaState.optBoolean("isPlaying", false)
                onMediaSwitch(mediaState)
            }
            connect()
        }
        startHeartbeat()
    }

    fun leave() {
        roomId?.let { socket?.emit("room:leave", JSONObject().put("roomId", it)) }
        disconnect()
    }

    fun play(positionSeconds: Long) = emitPlayback("media:play", positionSeconds, true)

    fun pause(positionSeconds: Long) = emitPlayback("media:pause", positionSeconds, false)

    fun seek(positionSeconds: Long) {
        basePositionSeconds = positionSeconds.coerceAtLeast(0L)
        baseTimestampMs = System.currentTimeMillis()
        socket?.emit(
            "media:seek",
            JSONObject().put("roomId", roomId).put("position", basePositionSeconds)
        )
    }

    fun switchMedia(title: String, mediaUrl: String, durationSeconds: Long = 0L) {
        socket?.emit(
            "media:switch",
            JSONObject()
                .put("roomId", roomId)
                .put("title", title)
                .put("mediaUrl", mediaUrl)
                .put("durationSeconds", durationSeconds)
        )
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

    private fun emitPlayback(event: String, positionSeconds: Long, isPlaying: Boolean) {
        basePositionSeconds = positionSeconds.coerceAtLeast(0L)
        baseTimestampMs = System.currentTimeMillis()
        playing = isPlaying
        socket?.emit(
            event,
            JSONObject().put("roomId", roomId).put("position", basePositionSeconds)
        )
    }

    private fun handlePlayback(args: Array<Any>, isPlaying: Boolean) {
        val payload = args.firstOrNull() as? JSONObject ?: return
        val position = payload.optLong("position", 0L)
        basePositionSeconds = position
        baseTimestampMs = payload.optLong("timestamp", System.currentTimeMillis())
        playing = isPlaying
        onMediaState(position, isPlaying)
    }

    private fun startHeartbeat() {
        heartbeat = Executors.newSingleThreadScheduledExecutor()
        heartbeat?.scheduleAtFixedRate({
            val currentSocket = socket ?: return@scheduleAtFixedRate
            val currentRoom = roomId ?: return@scheduleAtFixedRate
            if (!isHost()) return@scheduleAtFixedRate
            val now = System.currentTimeMillis()
            val position = basePositionSeconds + if (playing) ((now - baseTimestampMs) / 1000L) else 0L
            currentSocket.emit(
                "media:sync",
                JSONObject()
                    .put("roomId", currentRoom)
                    .put("position", position)
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
