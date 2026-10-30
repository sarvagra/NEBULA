package com.nebula.app.data.realtime

import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

/**
 * The app-wide socket for direct messages.
 *
 * The room socket only exists while a room is open, so a DM arriving outside a room
 * was invisible until the client's polling loop came around, or until the user switched
 * tabs. Every authenticated socket joins the user's private channel on connect, so a
 * plain connection with no room is enough to receive messages, read receipts and
 * friendship changes as they happen.
 *
 * This socket deliberately owns no room state: joining, playback and presence stay with
 * [RoomSocketClient], and the two can be connected at the same time without competing.
 */
class DirectMessageSocketClient(
    private val tokenProvider: () -> String?,
    private val baseUrlProvider: () -> String,
    private val onDirectMessage: (JSONObject) -> Unit,
    private val onReadReceipt: (JSONObject) -> Unit = {},
    private val onFriendshipChanged: () -> Unit = {},
    private val onReconnected: () -> Unit = {}
) {
    private var socket: Socket? = null
    private var hasConnectedOnce = false

    fun connect() {
        if (socket?.connected() == true) return
        val token = tokenProvider()?.takeIf { it.isNotBlank() } ?: return
        disconnect()
        val options = IO.Options().apply {
            auth = mapOf("token" to token)
            transports = arrayOf("websocket", "polling")
            reconnection = true
        }
        socket = IO.socket(baseUrlProvider(), options).apply {
            on(Socket.EVENT_CONNECT) {
                if (hasConnectedOnce) onReconnected() else hasConnectedOnce = true
            }
            on("chat:message") { args ->
                (args.firstOrNull() as? JSONObject)?.let(onDirectMessage)
            }
            on("chat:read") { args ->
                (args.firstOrNull() as? JSONObject)?.let(onReadReceipt)
            }
            on("friendship:changed") { onFriendshipChanged() }
            connect()
        }
    }

    fun disconnect() {
        socket?.apply {
            off()
            disconnect()
        }
        socket = null
        hasConnectedOnce = false
    }
}
