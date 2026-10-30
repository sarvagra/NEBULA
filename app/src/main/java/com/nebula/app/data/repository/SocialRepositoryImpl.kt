package com.nebula.app.data.repository

import com.nebula.app.domain.model.ChatMessage
import com.nebula.app.domain.model.MessageRole
import com.nebula.app.domain.model.MessageSendState
import com.nebula.app.domain.model.NebulaConversation
import com.nebula.app.domain.model.NebulaFriendRequest
import com.nebula.app.domain.model.NebulaUser
import com.nebula.app.domain.repository.ISocialRepository
import com.nebula.app.data.backend.NebulaApiClient
import com.nebula.app.data.backend.NebulaApiClient.ApiResponse
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

import com.nebula.app.data.local.PendingOutgoingMessage
import com.nebula.app.data.local.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SocialRepositoryImpl(
    private val apiClient: NebulaApiClient = NebulaApiClient,
    private val sessionManager: SessionManager? = null
) : ISocialRepository {

    private val backgroundScope = CoroutineScope(Dispatchers.IO)

    /**
     * Live direct-message channel, connected while the user is signed in.
     *
     * Without it a DM only appeared on the next poll of an open thread, so an incoming
     * message could sit unseen on the Chats tab for as long as the user stayed there.
     */
    private val directMessageSocket = com.nebula.app.data.realtime.DirectMessageSocketClient(
        tokenProvider = { apiClient.authToken },
        baseUrlProvider = { apiClient.socketBaseUrl() },
        onDirectMessage = { payload -> applyIncomingDirectMessage(payload) },
        onReadReceipt = { payload -> applyReadReceipt(payload) },
        onFriendshipChanged = { backgroundScope.launch { refresh() } },
        onReconnected = { backgroundScope.launch { refresh() } }
    )

    override fun startRealtime() {
        directMessageSocket.connect()
    }

    override fun stopRealtime() {
        directMessageSocket.disconnect()
    }

    fun clearLocalState() {
        _friends.value = emptyList()
        _friendRequests.value = emptyList()
        _suggestedPeople.value = emptyList()
        _conversations.value = emptyList()
        _activeConversation.value = null
    }

    private val _friends = MutableStateFlow<List<NebulaUser>>(sessionManager?.getCachedFriends() ?: emptyList())
    override val friends: StateFlow<List<NebulaUser>> = _friends.asStateFlow()

    private val _friendRequests = MutableStateFlow<List<NebulaFriendRequest>>(emptyList())
    override val friendRequests: StateFlow<List<NebulaFriendRequest>> = _friendRequests.asStateFlow()

    private val _suggestedPeople = MutableStateFlow<List<NebulaUser>>(emptyList())
    override val suggestedPeople: StateFlow<List<NebulaUser>> = _suggestedPeople.asStateFlow()

    private val _searchResults = MutableStateFlow<List<NebulaUser>>(emptyList())
    override val searchResults: StateFlow<List<NebulaUser>> = _searchResults.asStateFlow()

    private val _conversations = MutableStateFlow<List<NebulaConversation>>(sessionManager?.getCachedConversations() ?: emptyList())
    override val conversations: StateFlow<List<NebulaConversation>> = _conversations.asStateFlow()

    private val _activeConversation = MutableStateFlow<NebulaConversation?>(null)
    override val activeConversation: StateFlow<NebulaConversation?> = _activeConversation.asStateFlow()

    override suspend fun selectConversation(id: String) {
        val target = _conversations.value.firstOrNull { it.id == id }
        if (target != null) {
            // STEP 1: INSTANT OPTIMISTIC TRANSITION (0ms latency, no blocking network calls)
            val readTarget = target.copy(unreadCount = 0)
            _activeConversation.value = readTarget
            _conversations.value = _conversations.value.map { if (it.id == id) readTarget else it }

            // STEP 2: Asynchronous background synchronization without freezing the UI
            backgroundScope.launch {
                val freshPartner = apiClient.getPublicProfileById(id).let { response ->
                    if (response is ApiResponse.Success) {
                        response.data.optJSONObject("data")?.let { parseUsers(JSONArray().put(it)).firstOrNull() }
                    } else null
                } ?: target.partnerUser

                val history = apiClient.getConversationMessages(id)
                val messages = if (history is ApiResponse.Success) {
                    buildList {
                        for (index in 0 until history.data.length()) {
                            val item = history.data.optJSONObject(index) ?: continue
                            val parsed = messageFromJson(item, id)
                            val senderIsPartner = parsed.role == MessageRole.ASSISTANT
                            add(if (senderIsPartner && freshPartner != null) parsed.copy(
                                senderName = freshPartner.displayName,
                                senderAvatarUrl = freshPartner.avatarUrl,
                                senderAvatarLetter = freshPartner.avatarLetter
                            ) else parsed)
                        }
                    }
                } else target.messages

                // Carry over anything still waiting to be persisted, so re-opening a
                // thread never loses an unsent message.
                val unsent = readTarget.messages.filter { it.sendState != MessageSendState.SENT }
                val mergedMessages = messages + unsent.filter { local -> messages.none { it.clientKey != null && it.clientKey == local.clientKey } }

                val updatedTarget = readTarget.copy(
                    title = freshPartner?.displayName ?: target.title,
                    partnerUser = freshPartner ?: target.partnerUser,
                    messages = mergedMessages
                )

                if (_activeConversation.value?.id == id) {
                    _activeConversation.value = updatedTarget
                }
                _conversations.value = _conversations.value.map {
                    if (it.id == id) updatedTarget else it
                }
                sessionManager?.saveCachedConversations(_conversations.value)

                // Restore anything typed before the last process exit, then try to send it.
                hydratePendingQueue()
                flushUnsent(id)

                apiClient.markConversationRead(id)
            }
        }
    }

    override suspend fun refreshActiveConversation() {
        var current = _activeConversation.value ?: return
        val history = apiClient.getConversationMessages(current.id)
        if (history !is ApiResponse.Success) return
        // The server just answered, so connectivity is back: retry anything still
        // waiting instead of leaving it for a manual tap. It runs on the repository's
        // own scope because this call arrives from a screen-scoped job that is cancelled
        // whenever the thread screen is re-entered, which would abort the send mid-flight.
        val conversationId = current.id
        backgroundScope.launch { flushUnsent(conversationId) }
        current = _activeConversation.value ?: return
        val fresh = buildList {
            for (index in 0 until history.data.length()) {
                val item = history.data.optJSONObject(index) ?: continue
                add(messageFromJson(item, current.id))
            }
        }
        // Messages that never reached the server are not in the history yet. Keep them
        // at the end so a background sync cannot silently discard an unsent message
        // (and the user's text with it).
        val unsent = current.messages.filter { it.sendState != MessageSendState.SENT }
        val merged = fresh + unsent.filter { local -> fresh.none { it.clientKey != null && it.clientKey == local.clientKey } }
        // Only swap the list in when something actually changed, so the ticking
        // refresh does not fight the user's scroll or re-trigger recomposition.
        val unchanged = merged.size == current.messages.size &&
            merged.zip(current.messages).all { (a, b) ->
                a.id == b.id && a.readAt == b.readAt && a.editedAt == b.editedAt &&
                    a.deletedAt == b.deletedAt && a.content == b.content && a.mediaUrl == b.mediaUrl &&
                    a.sendState == b.sendState
            }
        if (unchanged) return
        val updated = current.copy(messages = merged)
        _activeConversation.value = updated
        _conversations.value = _conversations.value.map { if (it.id == current.id) updated else it }
    }

    override suspend fun closeConversation() {
        _activeConversation.value = null
    }

    /** Applies a change to a conversation in both the inbox list and the open thread. */
    private fun updateConversation(conversationId: String, transform: (NebulaConversation) -> NebulaConversation) {
        // Sending moves the thread to the top of the inbox, which is the one ordering
        // rule every messaging app shares.
        _conversations.value = _conversations.value
            .map { if (it.id == conversationId) transform(it) else it }
            .sortedByDescending { it.messages.lastOrNull()?.timestamp ?: 0L }
        if (_activeConversation.value?.id == conversationId) {
            _activeConversation.value = _activeConversation.value?.let(transform)
        }
    }

    /**
     * Server history plus anything still waiting to be delivered.
     *
     * A message with a client key the server already knows about is dropped from the
     * local side: the server's copy is authoritative from that point on.
     */
    /**
     * Applies a message that arrived over the socket.
     *
     * The payload reaches both participants, so the peer's thread is derived from the
     * side that is not us. A message this device just sent is already on screen, and is
     * matched by id rather than added twice.
     */
    private fun applyIncomingDirectMessage(payload: JSONObject) {
        val senderId = payload.optString("senderId")
        val recipientId = payload.optString("conversationId")
        val myId = sessionManager?.getUserProfile()?.id?.takeIf { it.isNotBlank() } ?: return
        val peerId = if (senderId == myId) recipientId else senderId
        if (peerId.isBlank() || peerId == myId) return
        val incoming = messageFromJson(payload, peerId)
        if (incoming.id.isBlank()) return

        var knownConversation = false
        val conversations = _conversations.value.map { conversation ->
            if (conversation.id != peerId) conversation else {
                knownConversation = true
                if (conversation.messages.any { it.id == incoming.id }) {
                    conversation
                } else {
                    val merged = conversation.messages + incoming
                    conversation.copy(
                        messages = merged,
                        lastMessageText = previewText(incoming),
                        lastMessageTime = compactTimeLabel(incoming.timestamp),
                        // Only the peer's messages count as unread, and only while the
                        // thread is not the one on screen.
                        unreadCount = if (incoming.role == MessageRole.ASSISTANT &&
                            _activeConversation.value?.id != peerId
                        ) conversation.unreadCount + 1 else conversation.unreadCount
                    )
                }
            }
        }
        // A thread we have never seen (new friend, or the list not loaded yet) is worth
        // one refresh rather than a half-built row.
        if (!knownConversation) {
            backgroundScope.launch { refresh() }
            return
        }
        _conversations.value = conversations.sortedByDescending { it.messages.lastOrNull()?.timestamp ?: 0L }

        if (_activeConversation.value?.id == peerId) {
            val active = _activeConversation.value ?: return
            if (active.messages.none { it.id == incoming.id }) {
                _activeConversation.value = active.copy(
                    messages = active.messages + incoming,
                    unreadCount = 0
                )
            }
            if (incoming.role == MessageRole.ASSISTANT) {
                backgroundScope.launch { apiClient.markConversationRead(peerId) }
            }
        }
    }

    /** Moves this device's ticks to read when the peer opens the thread. */
    private fun applyReadReceipt(payload: JSONObject) {
        val readerId = payload.optString("readerId")
        if (readerId.isBlank()) return
        val readAt = System.currentTimeMillis()
        fun List<ChatMessage>.stamped(): List<ChatMessage> = map { message ->
            if (message.role == MessageRole.USER && message.readAt == null && message.sendState == MessageSendState.SENT) {
                message.copy(readAt = readAt)
            } else message
        }
        _conversations.value = _conversations.value.map { conversation ->
            if (conversation.id == readerId) conversation.copy(messages = conversation.messages.stamped()) else conversation
        }
        if (_activeConversation.value?.id == readerId) {
            _activeConversation.value = _activeConversation.value?.copy(
                messages = _activeConversation.value?.messages.orEmpty().stamped()
            )
        }
    }

    private fun mergeUnsent(
        serverMessages: List<ChatMessage>,
        previousMessages: List<ChatMessage>
    ): List<ChatMessage> {
        val unsent = previousMessages.filter { it.sendState != MessageSendState.SENT && it.clientKey != null }
        if (unsent.isEmpty()) return serverMessages
        val deliveredKeys = serverMessages.mapNotNull { it.clientKey }.toSet()
        return serverMessages + unsent.filter { it.clientKey !in deliveredKeys }
    }

    /** Inbox preview line, honest about state: a failed send never reads as sent. */
    private fun previewText(message: ChatMessage): String {
        val body = when {
            message.deletedAt != null -> "Message deleted"
            message.messageType == "room_card" -> "Watch party invite"
            message.messageType == "video" -> message.content.ifBlank { "Video" }
            message.mediaUrl != null || message.messageType == "media" -> message.content.ifBlank { "Photo" }
            else -> message.content
        }
        return when {
            message.role != MessageRole.USER -> body
            message.sendState == MessageSendState.FAILED -> "Not sent · $body"
            message.sendState == MessageSendState.SENDING -> "Sending…"
            else -> "you: $body"
        }
    }

    /**
     * Sets the delivery state of a locally tracked message. A non-null [replacement]
     * swaps in the server's own record; otherwise the placeholder is updated in place,
     * so a failed send never loses what the user typed.
     */
    private fun markLocalMessage(
        conversationId: String,
        clientKey: String,
        state: MessageSendState,
        replacement: ChatMessage? = null
    ) {
        updateConversation(conversationId) { conv ->
            val messages = conv.messages.map { message ->
                when {
                    message.clientKey != clientKey -> message
                    replacement != null -> replacement.copy(clientKey = clientKey, sendState = state)
                    else -> message.copy(sendState = state)
                }
            }
            val last = messages.lastOrNull()
            conv.copy(
                messages = messages,
                lastMessageText = last?.let { previewText(it) } ?: conv.lastMessageText,
                lastMessageTime = last?.let { compactTimeLabel(it.timestamp) } ?: conv.lastMessageTime
            )
        }
    }

    private data class SendOutcome(val ok: Boolean, val permanentFailure: Boolean)

    /** Keys the server rejected outright; auto-retry must not hammer them forever. */
    private val permanentlyFailedKeys = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** Convariant shortcut: only the message id and state matter to the caller. */
    private fun SendOutcome.asBoolean() = ok

    /** Mirrors the unsent messages to disk so they survive process death. */
    private fun persistPendingQueue() {
        val manager = sessionManager ?: return
        val pending = _conversations.value.flatMap { conv ->
            conv.messages.filter { it.sendState != MessageSendState.SENT && it.clientKey != null }.map { message ->
                PendingOutgoingMessage(
                    conversationId = conv.id,
                    clientKey = message.clientKey!!,
                    content = message.content,
                    mediaUrl = message.mediaUrl,
                    messageType = message.messageType,
                    isSpoiler = message.isSpoiler,
                    mediaMimeType = message.mediaMimeType,
                    timestamp = message.timestamp
                )
            }
        }
        manager.savePendingMessages(pending)
    }

    /** Restores unsent messages saved before the last process exit. */
    private fun hydratePendingQueue() {
        val manager = sessionManager ?: return
        val pending = manager.getPendingMessages()
        if (pending.isEmpty()) return
        for (item in pending) {
            updateConversation(item.conversationId) { conv ->
                if (conv.messages.any { it.clientKey == item.clientKey }) return@updateConversation conv
                conv.copy(
                    messages = conv.messages + ChatMessage(
                        id = "pending_${item.clientKey}",
                        role = MessageRole.USER,
                        content = item.content,
                        timestamp = item.timestamp,
                        clientKey = item.clientKey,
                        sendState = MessageSendState.FAILED,
                        mediaUrl = item.mediaUrl,
                        messageType = item.messageType,
                        isSpoiler = item.isSpoiler,
                        mediaMimeType = item.mediaMimeType
                    )
                )
            }
        }
    }

    private val retryMutex = kotlinx.coroutines.sync.Mutex()

    private companion object {
        /** An in-flight attempt older than this is presumed dead and retried. */
        const val STALE_SENDING_MS = 20_000L
    }

    /**
     * Retries unsent messages once the server has proven reachable again. A 4xx answer
     * is a permanent rejection, so it is not retried in a loop.
     */
    private suspend fun flushUnsent(onlyConversationId: String? = null) {
        if (!retryMutex.tryLock()) return
        try {
            val targets = _conversations.value.filter { onlyConversationId == null || it.id == onlyConversationId }
            for (conv in targets) {
                // A message left in SENDING by a cancelled or interrupted attempt is just
                // as unsent as a FAILED one; without this it would stay that way forever.
                val staleBefore = System.currentTimeMillis() - STALE_SENDING_MS
                val unsent = conv.messages.filter {
                    it.clientKey != null && it.clientKey !in permanentlyFailedKeys && (
                        it.sendState == MessageSendState.FAILED ||
                            (it.sendState == MessageSendState.SENDING && it.timestamp < staleBefore)
                        )
                }
                for (message in unsent) {
                    val key = message.clientKey ?: continue
                    markLocalMessage(conv.id, key, MessageSendState.SENDING)
                    val outcome = try {
                        dispatchMessage(
                            conv.id, message.content, message.mediaUrl, message.messageType,
                            message.isSpoiler, message.mediaMimeType, key, insertPlaceholder = false
                        )
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        // Never convert cancellation into a failed send; the stale-SENDING
                        // guard picks it up again on the next pass.
                        throw e
                    } catch (e: Exception) {
                        markLocalMessage(conv.id, key, MessageSendState.FAILED)
                        break
                    }
                    if (outcome.permanentFailure) permanentlyFailedKeys.add(key)
                    if (!outcome.ok) break
                }
            }
        } finally {
            retryMutex.unlock()
        }
    }

    private suspend fun dispatchMessage(
        conversationId: String,
        content: String,
        mediaUrl: String?,
        messageType: String,
        isSpoiler: Boolean,
        mediaMimeType: String?,
        clientKey: String,
        insertPlaceholder: Boolean,
        mediaUrls: List<String> = emptyList()
    ): SendOutcome {
        if (insertPlaceholder) {
            val placeholder = ChatMessage(
                id = "pending_$clientKey",
                role = MessageRole.USER,
                content = content.trim(),
                clientKey = clientKey,
                sendState = MessageSendState.SENDING,
                mediaUrl = mediaUrl,
                mediaUrls = mediaUrls,
                messageType = messageType,
                isSpoiler = isSpoiler,
                mediaMimeType = mediaMimeType
            )
            updateConversation(conversationId) { conv ->
                conv.copy(messages = conv.messages.filterNot { it.clientKey == clientKey } + placeholder)
            }
        }
        // No network means no attempt. Waiting for a connect timeout would leave the
        // bubble spinning for up to 15 s, which a user reads as "the app is broken"
        // rather than "this did not send".
        if (!com.nebula.app.data.local.ConnectivityMonitor.isOnlineNow()) {
            markLocalMessage(conversationId, clientKey, MessageSendState.FAILED)
            persistPendingQueue()
            return SendOutcome(ok = false, permanentFailure = false)
        }
        val response = apiClient.sendDirectMessage(
            conversationId, content.trim(), mediaUrl, messageType, isSpoiler, mediaMimeType, clientKey, mediaUrls
        )
        if (response !is ApiResponse.Success) {
            // Persistence failed: the bubble stays visible but marked unsent and retryable.
            markLocalMessage(conversationId, clientKey, MessageSendState.FAILED)
            persistPendingQueue()
            val status = (response as? ApiResponse.Error)?.statusCode ?: 0
            return SendOutcome(ok = false, permanentFailure = status in 400..499 && status != 408 && status != 429)
        }
        val saved = response.data.optJSONObject("data")?.let { messageFromJson(it, conversationId) }
        markLocalMessage(conversationId, clientKey, MessageSendState.SENT, saved)
        persistPendingQueue()
        return SendOutcome(ok = true, permanentFailure = false)
    }

    override suspend fun sendMessage(conversationId: String, content: String, mediaUrl: String?, messageType: String, isSpoiler: Boolean, mediaMimeType: String?, mediaUrls: List<String>): Boolean {
        if (content.isBlank() && mediaUrl.isNullOrBlank() && mediaUrls.isEmpty()) return false
        return dispatchMessage(
            conversationId, content, mediaUrl, messageType, isSpoiler, mediaMimeType,
            clientKey = UUID.randomUUID().toString(),
            insertPlaceholder = true,
            mediaUrls = mediaUrls
        ).asBoolean()
    }

    override suspend fun retryMessage(clientKey: String): Boolean {
        // The open thread is checked first: it is the only place the user can tap, and
        // it can legitimately hold a message the conversation list has not absorbed yet
        // (the very first message of a brand-new thread, for example).
        val candidates = listOfNotNull(_activeConversation.value) + _conversations.value
        val conversation = candidates.firstOrNull { conv -> conv.messages.any { it.clientKey == clientKey } }
            ?: return false
        val message = conversation.messages.firstOrNull { it.clientKey == clientKey } ?: return false
        if (message.sendState == MessageSendState.SENT) return true
        // An explicit tap always gets a fresh attempt, even after a permanent rejection.
        permanentlyFailedKeys.remove(clientKey)
        markLocalMessage(conversation.id, clientKey, MessageSendState.SENDING)
        // Same key as the original attempt: the server dedupes if the first request
        // actually landed but its response was lost.
        return dispatchMessage(
            conversation.id, message.content, message.mediaUrl, message.messageType,
            message.isSpoiler, message.mediaMimeType, clientKey, insertPlaceholder = false
        ).asBoolean()
    }

    override suspend fun flushPendingNow() {
        flushUnsent(null)
    }

    override suspend fun editMessage(messageId: String, content: String): Boolean = updateMessageFromResponse(apiClient.editDirectMessage(messageId, content))

    override suspend fun unsendMessage(messageId: String): Boolean = updateMessageFromResponse(apiClient.unsendDirectMessage(messageId))

    private fun updateMessageFromResponse(response: ApiResponse<JSONObject>): Boolean {
        if (response !is ApiResponse.Success) return false
        val raw = response.data.optJSONObject("data") ?: return false
        val active = _activeConversation.value ?: return false
        val updated = messageFromJson(raw, active.id)
        val revised = active.copy(messages = active.messages.map { if (it.id == updated.id) updated else it })
        _activeConversation.value = revised
        _conversations.value = _conversations.value.map { if (it.id == active.id) revised else it }
        return true
    }

    /**
     * Applies a relationship change to every list that can render this user, so a row
     * flips between Add / Requested / Accept the moment the server confirms it instead
     * of waiting for a list refetch that search results never get.
     */
    private fun applyRelationship(userId: String, pending: Boolean, isFriend: Boolean = false) {
        fun NebulaUser.patched(): NebulaUser = if (id != userId) this else copy(
            // Never demote a friend: only a confirmed acceptance upgrades the flag.
            isFriend = this.isFriend || isFriend,
            friendRequestPending = pending,
            friendRequestReceived = false,
            friendRequestId = if (isFriend || !pending) null else friendRequestId
        )
        _searchResults.value = _searchResults.value.map { it.patched() }
        _suggestedPeople.value = _suggestedPeople.value.map { it.patched() }
        _friends.value = _friends.value.map { it.patched() }
    }

    override suspend fun acceptFriendRequest(requestId: String): Boolean {
        if (apiClient.acceptFriendRequest(requestId) !is ApiResponse.Success) return false
        val req = _friendRequests.value.firstOrNull { it.id == requestId }
        if (req != null) {
            _friends.value = _friends.value.filterNot { it.id == req.sender.id } + req.sender.copy(
                isFriend = true,
                friendRequestReceived = false,
                friendRequestPending = false,
                friendRequestId = null
            )
            _friendRequests.value = _friendRequests.value.filter { it.id != requestId }
            _suggestedPeople.value = _suggestedPeople.value.filter { it.id != req.sender.id }
            applyRelationship(req.sender.id, pending = false, isFriend = true)
        }
        return true
    }

    override suspend fun declineFriendRequest(requestId: String): Boolean {
        if (apiClient.declineFriendRequest(requestId) !is ApiResponse.Success) return false
        val declined = _friendRequests.value.firstOrNull { it.id == requestId }
        _friendRequests.value = _friendRequests.value.filter { it.id != requestId }
        if (declined != null) {
            _searchResults.value = _searchResults.value.map { candidate ->
                if (candidate.id == declined.sender.id) {
                    candidate.copy(friendRequestReceived = false, friendRequestId = null)
                } else candidate
            }
        }
        return true
    }

    override suspend fun withdrawFriendRequest(userId: String): Boolean {
        if (apiClient.withdrawFriendRequest(userId) !is ApiResponse.Success) return false
        applyRelationship(userId, pending = false)
        return true
    }

    override suspend fun addFriend(user: NebulaUser): Boolean {
        if (apiClient.sendFriendRequest(user.id) is ApiResponse.Success) {
            // Optimistic but server-confirmed: the request is already persisted as pending.
            applyRelationship(user.id, pending = true)
            return true
        }
        // A double tap or a request sent from another device answers 409. That is not a
        // failure — reconcile with the server so the row shows the real relationship.
        val actual = loadPublicProfileById(user.id) ?: return false
        if (!actual.friendRequestPending && !actual.isFriend && !actual.friendRequestReceived) return false
        applyRelationship(user.id, pending = actual.friendRequestPending, isFriend = actual.isFriend)
        return true
    }

    override suspend fun refresh() {
        val friends = apiClient.getFriends()
        if (friends is ApiResponse.Success) {
            val friendUsers = parseUsers(friends.data)
            _friends.value = attachRoomPresence(friendUsers)
            // One round-trip per friend, but issued concurrently: the previous
            // sequential loop made the Chats list wait on every friend in turn.
            val histories: Map<String, ApiResponse<JSONArray>> = coroutineScope {
                _friends.value.map { friend ->
                    async(kotlinx.coroutines.Dispatchers.IO) {
                        friend.id to apiClient.getConversationMessages(friend.id)
                    }
                }.awaitAll().toMap()
            }
            val previousById = _conversations.value.associateBy { it.id }
            _conversations.value = _friends.value.map { friend ->
                val history = histories[friend.id]
                val serverMessages = if (history is ApiResponse.Success) buildList {
                    for (index in 0 until history.data.length()) {
                        history.data.optJSONObject(index)?.let { add(messageFromJson(it, friend.id)) }
                    }
                } else previousById[friend.id]?.messages.orEmpty()
                // Rebuilding the list from the server used to silently delete every
                // message that had not been delivered yet. A refresh now keeps them, so
                // a failed send survives returning to the Chats tab instead of vanishing.
                val messages = mergeUnsent(serverMessages, previousById[friend.id]?.messages.orEmpty())
                val unread = messages.count { it.role == MessageRole.ASSISTANT && it.readAt == null && it.deletedAt == null }
                val latest = messages.lastOrNull()
                NebulaConversation(
                    id = friend.id,
                    title = friend.displayName,
                    isGroup = false,
                    partnerUser = friend,
                    participants = listOf(NebulaUser.CurrentUser, friend),
                    lastMessageText = latest?.let { previewText(it) } ?: "",
                    lastMessageTime = latest?.let { compactTimeLabel(it.timestamp) } ?: "",
                    unreadCount = unread,
                    messages = messages,
                )
            }.sortedByDescending { it.messages.lastOrNull()?.timestamp ?: 0L }
        }
        val requests = apiClient.getFriendRequests()
        if (requests is ApiResponse.Success) {
            _friendRequests.value = parseRequests(requests.data)
        }
        val suggestions = apiClient.getSuggestedPeople()
        if (suggestions is ApiResponse.Success) {
            val currentUserId = sessionManager?.getUserProfile()?.id
            val friendIds = _friends.value.map { it.id }.toSet()
            val requestSenderIds = _friendRequests.value.map { it.sender.id }.toSet()
            _suggestedPeople.value = parseUsers(suggestions.data).filter { candidate ->
                candidate.id.isNotBlank() &&
                candidate.id != currentUserId &&
                !friendIds.contains(candidate.id) &&
                !requestSenderIds.contains(candidate.id)
            }
        }
        hydratePendingQueue()
        sessionManager?.saveCachedFriends(_friends.value)
        sessionManager?.saveCachedConversations(_conversations.value)
    }

    /** Chat-list timestamp: time for today, "Yesterday", else a short date. */
    private fun compactTimeLabel(timestampMs: Long): String {
        val message = java.util.Calendar.getInstance().apply { timeInMillis = timestampMs }
        val today = java.util.Calendar.getInstance()
        val sameDay = message.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) &&
            message.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)
        if (sameDay) {
            return java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(timestampMs))
        }
        val yesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
        val isYesterday = message.get(java.util.Calendar.YEAR) == yesterday.get(java.util.Calendar.YEAR) &&
            message.get(java.util.Calendar.DAY_OF_YEAR) == yesterday.get(java.util.Calendar.DAY_OF_YEAR)
        if (isYesterday) return "Yesterday"
        return java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault())
            .format(java.util.Date(timestampMs))
    }

    private suspend fun attachRoomPresence(users: List<NebulaUser>): List<NebulaUser> {
        if (users.isEmpty()) return users
        val activeRooms = apiClient.getRooms()
        if (activeRooms !is ApiResponse.Success) return users
        val roomByUser = mutableMapOf<String, String>()
        for (index in 0 until activeRooms.data.length()) {
            val room = activeRooms.data.optJSONObject(index) ?: continue
            val roomId = room.optString("id")
            val participants = room.optJSONArray("participants") ?: continue
            for (participantIndex in 0 until participants.length()) {
                val userId = participants.optJSONObject(participantIndex)?.optString("userId") ?: continue
                if (userId.isNotBlank()) roomByUser[userId] = roomId
            }
        }
        return users.map { user -> roomByUser[user.id]?.let { user.copy(currentRoomId = it, isOnline = true, presenceStatus = com.nebula.app.domain.model.NebulaPresenceStatus.IN_A_ROOM) } ?: user }
    }

    override suspend fun searchPeople(query: String) {
        if (query.trim().length < 2) {
            _searchResults.value = emptyList()
            return
        }
        val result = apiClient.searchUsers(query)
        if (result is ApiResponse.Success) _searchResults.value = attachRoomPresence(parseUsers(result.data))
    }

    override suspend fun refreshSuggestions(): Boolean {
        val result = apiClient.refreshSuggestedPeople(_suggestedPeople.value.map { it.id })
        if (result is ApiResponse.Success) {
            val currentUserId = sessionManager?.getUserProfile()?.id
            _suggestedPeople.value = attachRoomPresence(parseUsers(result.data)).filter { it.id.isNotBlank() && it.id != currentUserId }
            return true
        }
        return false
    }

    override suspend fun blockUser(userId: String): Boolean {
        if (apiClient.blockUser(userId) !is ApiResponse.Success) return false
        _friends.value = _friends.value.filterNot { it.id == userId }
        _suggestedPeople.value = _suggestedPeople.value.filterNot { it.id == userId }
        _searchResults.value = _searchResults.value.filterNot { it.id == userId }
        _friendRequests.value = _friendRequests.value.filterNot { it.sender.id == userId }
        _conversations.value = _conversations.value.filterNot { it.id == userId || it.partnerUser?.id == userId }
        if (_activeConversation.value?.id == userId || _activeConversation.value?.partnerUser?.id == userId) {
            _activeConversation.value = null
        }
        return true
    }

    override suspend fun loadPublicProfile(username: String): NebulaUser? {
        val response = apiClient.getPublicProfile(username)
        if (response !is ApiResponse.Success) return null
        val user = response.data.optJSONObject("data") ?: return null
        return parseUsers(JSONArray().put(user)).firstOrNull()
    }

    override suspend fun loadPublicProfileById(userId: String): NebulaUser? {
        val response = apiClient.getPublicProfileById(userId)
        if (response !is ApiResponse.Success) return null
        val user = response.data.optJSONObject("data") ?: return null
        return parseUsers(JSONArray().put(user)).firstOrNull()
    }

    override suspend fun removeFriend(userId: String): Boolean {
        if (apiClient.removeFriend(userId) !is ApiResponse.Success) return false
        refresh()
        return true
    }

    private fun parseUsers(array: JSONArray): List<NebulaUser> = buildList {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val displayedStatus = item.optString("status", "Offline")
            val presence = when (item.optString("presenceStatus").ifBlank { displayedStatus }.lowercase()) {
                "offline" -> com.nebula.app.domain.model.NebulaPresenceStatus.OFFLINE
                "in a room", "in_a_room" -> com.nebula.app.domain.model.NebulaPresenceStatus.IN_A_ROOM
                "private chat", "private_chat" -> com.nebula.app.domain.model.NebulaPresenceStatus.PRIVATE_CHAT
                else -> com.nebula.app.domain.model.NebulaPresenceStatus.ONLINE
            }
            add(
                NebulaUser(
                    id = item.optString("id"),
                    username = item.optString("username").let { if (it.startsWith("@")) it else "@$it" },
                    displayName = item.optString("displayName", "User"),
                    avatarLetter = item.optString("avatarLetter", "U"),
                    avatarUrl = item.optString("avatarUrl").ifBlank { null },
                    bio = item.optString("bio"),
                    status = displayedStatus,
                    profileStatus = item.optString("profileStatus", "dynamic"),
                    customStatus = item.optString("customStatus"),
                    presenceStatus = presence,
                    isOnline = presence != com.nebula.app.domain.model.NebulaPresenceStatus.OFFLINE,
                    bannerUrl = item.optString("bannerUrl").ifBlank { null },
                    bannerIndex = item.optInt("bannerIndex", 0),
                    tier = item.optString("tier", "free"),
                    friendsCount = item.optJSONObject("stats")?.let { if (it.isNull("friendsCount")) null else it.optInt("friendsCount") },
                    roomsHostedCount = item.optJSONObject("stats")?.optInt("roomsHostedCount", 0) ?: 0,
                    roomsJoinedCount = item.optJSONObject("stats")?.optInt("roomsJoinedCount", 0) ?: 0,
                    timeInSyncHours = item.optJSONObject("stats")?.optString("timeInSyncHours")?.toDoubleOrNull() ?: 0.0,
                    interests = item.optJSONArray("interests")?.let { interests ->
                        List(interests.length()) { index -> interests.optString(index) }
                    } ?: emptyList(),
                    friendRequestPending = item.optString("relationship") == "request_sent",
                    friendRequestReceived = item.optString("relationship") == "request_received",
                    friendRequestId = item.optString("friendRequestId").ifBlank { null },
                    isFriend = item.optString("relationship") == "friends"
                )
            )
        }
    }

    private fun messageFromJson(item: JSONObject, partnerId: String): ChatMessage {
        val history = item.optJSONArray("editHistory")?.let { entries ->
            List(entries.length()) { index -> entries.optJSONObject(index)?.optString("text").orEmpty() }
        } ?: emptyList()
        return ChatMessage(
            id = item.optString("id"),
            role = if (item.optString("senderId") == partnerId) MessageRole.ASSISTANT else MessageRole.USER,
            content = item.optString("text"), timeLabel = "",
            mediaUrls = item.optJSONArray("mediaUrls")?.let { arr ->
                (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }
            } ?: emptyList(),
            timestamp = item.optString("createdAt").takeIf { it.isNotBlank() }?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrDefault(System.currentTimeMillis()) } ?: System.currentTimeMillis(),
            senderId = item.optString("senderId"), senderName = item.optString("senderName"),
            senderAvatarUrl = item.optString("senderAvatarUrl").ifBlank { null },
            senderAvatarLetter = item.optString("senderAvatarLetter", "U"),
            mediaUrl = item.optString("mediaUrl").ifBlank { null }, messageType = item.optString("messageType", "text"),
            editedAt = item.optString("editedAt").takeIf { it.isNotBlank() }?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() },
            editHistory = history,
            deletedAt = item.optString("deletedAt").takeIf { it.isNotBlank() }?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() },
            readAt = item.optString("readAt").takeIf { it.isNotBlank() }?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() },
            deliveredAt = item.optString("deliveredAt").takeIf { it.isNotBlank() }?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() },
            isSpoiler = item.optBoolean("isSpoiler", false),
            mediaMimeType = item.optString("mediaMimeType").ifBlank { null },
        )
    }

    private fun parseRequests(array: JSONArray): List<NebulaFriendRequest> = buildList {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val requester = item.optJSONObject("requester") ?: continue
            val user = parseUsers(JSONArray().put(requester)).firstOrNull() ?: continue
            add(NebulaFriendRequest(item.optString("id"), user))
        }
    }
}
