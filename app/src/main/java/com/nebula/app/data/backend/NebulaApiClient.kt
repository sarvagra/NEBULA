package com.nebula.app.data.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Robust HTTP client connecting the Android application to the Nebula Node.js/Express backend server.
 * Uses the deployed HTTPS API for both emulators and physical devices.
 */
object NebulaApiClient {
    var sessionManager: com.nebula.app.data.local.SessionManager? = null

    private var defaultBaseUrl: String = "https://nebula-73kb.onrender.com/api/v1"

    var baseUrl: String
        get() = sessionManager?.getBackendUrl() ?: defaultBaseUrl
        set(value) {
            defaultBaseUrl = value
            sessionManager?.saveBackendUrl(value)
        }

    fun socketBaseUrl(): String = baseUrl.substringBefore("/api/v1").trimEnd('/')

    var authToken: String? = null
        get() = field ?: sessionManager?.getAuthToken()
        set(value) {
            field = value
            if (value != null) {
                sessionManager?.saveAuthToken(value)
            }
        }

    sealed class ApiResponse<out T> {
        data class Success<out T>(val data: T) : ApiResponse<T>()
        data class Error(val message: String, val statusCode: Int = 0) : ApiResponse<Nothing>()
    }

    private suspend fun request(
        endpoint: String,
        method: String = "GET",
        jsonBody: JSONObject? = null,
        allowRefresh: Boolean = true,
        uploadProgress: ((Int) -> Unit)? = null
    ): ApiResponse<JSONObject> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("$baseUrl$endpoint")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                // Render's free instance can cold-start; do not fail auth while it wakes.
                connectTimeout = 15000
                readTimeout = 60000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                authToken?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Authorization", "Bearer $it") }

                if (jsonBody != null && (method == "POST" || method == "PUT" || method == "PATCH")) {
                    doOutput = true
                    val bytes = jsonBody.toString().toByteArray(Charsets.UTF_8)
                    if (uploadProgress == null) {
                        outputStream.use { it.write(bytes) }
                    } else {
                        outputStream.use { stream ->
                            var sent = 0
                            while (sent < bytes.size) {
                                val end = minOf(sent + 16 * 1024, bytes.size)
                                stream.write(bytes, sent, end - sent)
                                sent = end
                                val percent = (sent * 100L / bytes.size).toInt()
                                android.os.Handler(android.os.Looper.getMainLooper()).post { uploadProgress(percent) }
                            }
                            stream.flush()
                        }
                    }
                }
            }

            val statusCode = connection.responseCode
            val isSuccess = statusCode in 200..299
            val inputStream = if (isSuccess) connection.inputStream else connection.errorStream

            if (inputStream == null) {
                return@withContext ApiResponse.Error("Empty response from server", statusCode)
            }

            val responseText = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
            val json = JSONObject(responseText)

            if (statusCode == 401 && allowRefresh && endpoint != "/auth/refresh" && refreshAccessToken()) {
                connection.disconnect()
                connection = null
                return@withContext request(endpoint, method, jsonBody, allowRefresh = false)
            }

            if (isSuccess && json.optBoolean("success", true)) {
                ApiResponse.Success(json)
            } else {
                val errorMsg = json.optString("error", "Request failed with code $statusCode")
                ApiResponse.Error(errorMsg, statusCode)
            }
        } catch (e: Exception) {
            ApiResponse.Error(e.message ?: "Network error connecting to $baseUrl")
        } finally {
            connection?.disconnect()
        }
    }

    private suspend fun refreshAccessToken(): Boolean {
        val refreshToken = sessionManager?.getRefreshToken()?.takeIf { it.isNotBlank() } ?: return false
        val response = request(
            "/auth/refresh",
            "POST",
            JSONObject().put("refreshToken", refreshToken),
            allowRefresh = false
        )
        if (response !is ApiResponse.Success) return false
        val token = response.data.optJSONObject("data")?.optString("token")?.takeIf { it.isNotBlank() } ?: return false
        authToken = token
        return true
    }

    // ==========================================
    // AUTHENTICATION
    // ==========================================
    suspend fun googleSignIn(idToken: String): ApiResponse<JSONObject> {
        val body = JSONObject().apply { put("idToken", idToken) }
        val res = request("/auth/google", "POST", body)
        if (res is ApiResponse.Success) {
            val tokens = res.data.optJSONObject("data")?.optJSONObject("tokens")
            tokens?.optString("token")?.let { authToken = it }
            tokens?.optString("refreshToken")?.takeIf { it.isNotBlank() }?.let { sessionManager?.saveRefreshToken(it) }
        }
        return res
    }

    suspend fun requestOtp(target: String): ApiResponse<JSONObject> {
        val isEmail = target.contains("@")
        val body = JSONObject().apply {
            if (isEmail) put("email", target.trim().lowercase())
            else put("phone", target.trim())
        }
        return request("/auth/otp/request", "POST", body)
    }

    suspend fun verifyOtp(target: String, otp: String): ApiResponse<JSONObject> {
        val isEmail = target.contains("@")
        val body = JSONObject().apply {
            if (isEmail) put("email", target.trim().lowercase())
            else put("phone", target.trim())
            put("otp", otp.trim())
        }
        val res = request("/auth/otp/verify", "POST", body)
        if (res is ApiResponse.Success) {
            val tokens = res.data.optJSONObject("data")?.optJSONObject("tokens")
            tokens?.optString("token")?.let { authToken = it }
            tokens?.optString("refreshToken")?.takeIf { it.isNotBlank() }?.let { sessionManager?.saveRefreshToken(it) }
        }
        return res
    }

    suspend fun developerSignIn(username: String = "nebula_explorer"): ApiResponse<JSONObject> {
        val body = JSONObject().apply {
            put("username", username.trim().replace("^@".toRegex(), ""))
        }
        val res = request("/auth/dev-login", "POST", body)
        if (res is ApiResponse.Success) {
            val tokens = res.data.optJSONObject("data")?.optJSONObject("tokens")
            val token = tokens?.optString("token")
            if (!token.isNullOrBlank()) {
                authToken = token
                tokens?.optString("refreshToken")?.takeIf { it.isNotBlank() }?.let { sessionManager?.saveRefreshToken(it) }
            }
        }
        return res
    }

    suspend fun emailSignIn(email: String, displayName: String = ""): ApiResponse<JSONObject> {
        val cleanEmail = email.trim().lowercase()
        val baseHandle = cleanEmail.substringBefore("@").replace("[^a-zA-Z0-9_]".toRegex(), "_")
        val body = JSONObject().apply {
            put("email", cleanEmail)
            put("displayName", displayName.trim().ifEmpty { cleanEmail.substringBefore("@") })
            put("username", baseHandle)
        }
        val res = request("/auth/dev-login", "POST", body)
        if (res is ApiResponse.Success) {
            val tokens = res.data.optJSONObject("data")?.optJSONObject("tokens")
            val token = tokens?.optString("token")
            if (!token.isNullOrBlank()) {
                authToken = token
                tokens?.optString("refreshToken")?.takeIf { it.isNotBlank() }?.let { sessionManager?.saveRefreshToken(it) }
            }
        }
        return res
    }

    suspend fun checkHealth(): ApiResponse<JSONObject> = withContext(Dispatchers.IO) {
        val rootUrl = baseUrl.substringBefore("/api/v1")
        var connection: java.net.HttpURLConnection? = null
        try {
            val url = java.net.URL("$rootUrl/health")
            connection = (url.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
            }
            val statusCode = connection.responseCode
            if (statusCode in 200..299) {
                val responseText = java.io.BufferedReader(java.io.InputStreamReader(connection.inputStream)).use { it.readText() }
                ApiResponse.Success(JSONObject(responseText))
            } else {
                ApiResponse.Error("HTTP $statusCode", statusCode)
            }
        } catch (e: Exception) {
            ApiResponse.Error(e.message ?: "Connection failed")
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun getMe(): ApiResponse<JSONObject> {
        return request("/auth/me", "GET")
    }

    suspend fun updateAppPresence(active: Boolean): ApiResponse<JSONObject> =
        request("/users/presence", "PUT", JSONObject().put("active", active))

    suspend fun logout(): ApiResponse<JSONObject> {
        val body = JSONObject().apply {
            sessionManager?.getRefreshToken()?.takeIf { it.isNotBlank() }?.let { put("refreshToken", it) }
        }
        return request("/auth/logout", "POST", body)
    }

    suspend fun getNotifications(): ApiResponse<JSONArray> {
        val res = request("/notifications?limit=50", "GET")
        return when (res) {
            is ApiResponse.Success -> ApiResponse.Success(res.data.optJSONArray("data") ?: JSONArray())
            is ApiResponse.Error -> ApiResponse.Error(res.message, res.statusCode)
        }
    }

    suspend fun registerNotificationDevice(token: String): ApiResponse<JSONObject> =
        request("/notifications/device-token", "POST", JSONObject().put("token", token))

    suspend fun markNotificationRead(id: String): ApiResponse<JSONObject> =
        request("/notifications/$id/read", "PUT")

    suspend fun deleteNotification(id: String): ApiResponse<JSONObject> =
        request("/notifications/$id", "DELETE")

    // ==========================================
    // ROOMS
    // ==========================================
    suspend fun getRooms(category: String? = null): ApiResponse<JSONArray> {
        val query = if (category != null) "?category=$category" else ""
        val res = request("/rooms$query", "GET")
        return when (res) {
            is ApiResponse.Success -> ApiResponse.Success(res.data.optJSONArray("data") ?: JSONArray())
            is ApiResponse.Error -> ApiResponse.Error(res.message, res.statusCode)
        }
    }

    suspend fun createRoom(
        name: String,
        privacy: String = "public",
        activity: String = "watch",
        tags: List<String>? = null,
        maxParticipants: Int? = null,
        mediaTitle: String? = null,
        mediaSubtitle: String? = null,
        mediaUrl: String? = null
    ): ApiResponse<JSONObject> {
        val body = JSONObject().apply {
            put("name", name)
            put("privacy", privacy)
            put("activity", activity)
            tags?.let { put("tags", JSONArray(it.take(3))) }
            maxParticipants?.let { put("maxParticipants", it) }
            mediaTitle?.let { put("mediaTitle", it) }
            mediaSubtitle?.let { put("mediaSubtitle", it) }
            mediaUrl?.let { put("mediaUrl", it) }
        }
        return request("/rooms", "POST", body)
    }

    suspend fun searchYoutube(query: String): ApiResponse<JSONArray> {
        val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
        val res = request("/media/youtube/search?q=$encoded", "GET")
        return when (res) {
            is ApiResponse.Success -> ApiResponse.Success(res.data.optJSONArray("results") ?: JSONArray())
            is ApiResponse.Error -> ApiResponse.Error(res.message, res.statusCode)
        }
    }

    suspend fun getRoom(roomId: String): ApiResponse<JSONObject> {
        return request("/rooms/$roomId", "GET")
    }

    suspend fun getRoomMessages(roomId: String): ApiResponse<JSONArray> {
        val res = request("/chat/room/$roomId?limit=50", "GET")
        return when (res) {
            is ApiResponse.Success -> ApiResponse.Success(res.data.optJSONArray("data") ?: JSONArray())
            is ApiResponse.Error -> ApiResponse.Error(res.message, res.statusCode)
        }
    }

    suspend fun getRoomPoll(roomId: String): ApiResponse<JSONObject> =
        request("/polls/room/$roomId", "GET")

    suspend fun votePoll(pollId: String, optionId: String): ApiResponse<JSONObject> =
        request("/polls/$pollId/vote", "POST", JSONObject().put("optionId", optionId))

    suspend fun joinRoom(roomId: String): ApiResponse<JSONObject> {
        return request("/rooms/$roomId/join", "POST")
    }

    suspend fun leaveRoom(roomId: String, newHostId: String? = null): ApiResponse<JSONObject> {
        val body = JSONObject().apply {
            newHostId?.let { put("newHostId", it) }
        }
        return request("/rooms/$roomId/leave", "POST", body)
    }

    suspend fun updateRoomSettings(
        roomId: String,
        name: String? = null,
        maxParticipants: Int? = null,
        isMicAllowed: Boolean? = null,
        isVideoAllowed: Boolean? = null,
        tags: List<String>? = null
    ): ApiResponse<JSONObject> {
        val body = JSONObject().apply {
            name?.let { put("name", it) }
            maxParticipants?.let { put("maxParticipants", it) }
            isMicAllowed?.let { put("isMicAllowed", it) }
            isVideoAllowed?.let { put("isVideoAllowed", it) }
            tags?.let { put("tags", JSONArray(it)) }
        }
        return request("/rooms/$roomId/settings", "PUT", body)
    }

    suspend fun transferHost(roomId: String, newHostId: String? = null, random: Boolean = false): ApiResponse<JSONObject> {
        val body = JSONObject().apply {
            newHostId?.let { put("newHostId", it) }
            put("random", random)
        }
        return request("/rooms/$roomId/transfer-host", "POST", body)
    }

    suspend fun inviteToRoom(roomId: String, userId: String): ApiResponse<JSONObject> {
        val body = JSONObject().put("userId", userId)
        return request("/rooms/$roomId/invite", "POST", body)
    }

    suspend fun kickRoomParticipant(roomId: String, userId: String): ApiResponse<JSONObject> =
        request("/rooms/$roomId/participants/$userId", "DELETE")

    suspend fun moderateRoomParticipant(
        roomId: String,
        userId: String,
        isMuted: Boolean? = null,
        isCameraOff: Boolean? = null
    ): ApiResponse<JSONObject> {
        val body = JSONObject().apply {
            isMuted?.let { put("isMuted", it) }
            isCameraOff?.let { put("isCameraOff", it) }
        }
        return request("/rooms/$roomId/participants/$userId/moderation", "PUT", body)
    }

    suspend fun updateMedia(
        roomId: String,
        title: String? = null,
        isPlaying: Boolean? = null,
        playbackPosition: Long? = null,
        mediaUrl: String? = null
    ): ApiResponse<JSONObject> {
        val body = JSONObject().apply {
            title?.let { put("title", it) }
            isPlaying?.let { put("isPlaying", it) }
            playbackPosition?.let { put("playbackPosition", it) }
            mediaUrl?.let { put("mediaUrl", it) }
        }
        return request("/rooms/$roomId/media", "PUT", body)
    }

    // ==========================================
    // PROFILE & USERS
    // ==========================================
    suspend fun updateProfile(
        displayName: String,
        username: String? = null,
        bio: String = "",
        status: String = "dynamic",
        customStatus: String? = null,
        bannerIndex: Int = 0,
        interests: List<String> = emptyList(),
        avatarUrl: String? = null,
        isFriendCountPublic: Boolean? = null,
        isOnboardingCompleted: Boolean? = true,
        dateOfBirth: String? = null,
        gender: String? = null,
        bannerUrl: String? = null
    ): ApiResponse<JSONObject> {
        val body = JSONObject().apply {
            put("displayName", displayName)
            if (!username.isNullOrBlank()) {
                val clean = username.trim().replace("^@".toRegex(), "")
                put("username", clean)
            }
            put("bio", bio)
            put("status", when (status.trim().lowercase()) {
                "online", "dynamic" -> "dynamic"
                "offline" -> "offline"
                "custom" -> "custom"
                else -> "dynamic"
            })
            customStatus?.let { put("customStatus", it) }
            put("bannerIndex", bannerIndex)
            put("interests", JSONArray(interests))
            if (avatarUrl != null) {
                put("avatarUrl", avatarUrl)
            }
            if (isFriendCountPublic != null) {
                put("isFriendCountPublic", isFriendCountPublic)
            }
            put("isOnboardingCompleted", isOnboardingCompleted ?: true)
            if (dateOfBirth != null) {
                put("dateOfBirth", dateOfBirth)
            }
            if (gender != null) {
                put("gender", gender)
            }
            if (bannerUrl != null) {
                put("bannerUrl", bannerUrl)
            }
        }
        return request("/users/profile", "PUT", body)
    }

    suspend fun checkUsername(username: String): ApiResponse<JSONObject> {
        val clean = username.trim().replace("^@".toRegex(), "")
        return request("/users/check-username/$clean", "GET")
    }

    suspend fun getPublicProfile(username: String): ApiResponse<JSONObject> {
        val clean = username.trim().replace("^@".toRegex(), "")
        return request("/users/public/$clean", "GET")
    }

    suspend fun getPublicProfileById(userId: String): ApiResponse<JSONObject> = request("/users/public/id/${userId.trim()}", "GET")

    // ==========================================
    // PROFILE ASSETS & MEDIA
    // ==========================================
    suspend fun getProfileAssets(): ApiResponse<JSONArray> {
        val res = request("/users/me/assets", "GET")
        return when (res) {
            is ApiResponse.Success -> ApiResponse.Success(res.data.optJSONArray("data") ?: JSONArray())
            is ApiResponse.Error -> ApiResponse.Error(res.message, res.statusCode)
        }
    }

    suspend fun uploadProfileAsset(assetType: String, url: String, name: String): ApiResponse<JSONObject> {
        val body = JSONObject().apply {
            put("assetType", assetType)
            put("url", url)
            put("name", name)
        }
        return request("/users/me/assets", "POST", body)
    }

    suspend fun applyProfileAsset(assetId: String): ApiResponse<JSONObject> {
        return request("/users/me/assets/$assetId/apply", "POST")
    }

    suspend fun deleteProfileAsset(assetId: String): ApiResponse<JSONObject> {
        return request("/users/me/assets/$assetId", "DELETE")
    }

    suspend fun uploadChatMedia(url: String, mimeType: String, mediaType: String, sizeBytes: Long = 0L, onProgress: (Int) -> Unit = {}): ApiResponse<JSONObject> {
        val body = JSONObject().apply {
            put("url", url)
            put("mimeType", mimeType)
            put("mediaType", mediaType)
            put("sizeBytes", sizeBytes)
        }
        return request("/chat/upload", "POST", body, uploadProgress = onProgress)
    }

    suspend fun getFriends(): ApiResponse<JSONArray> {
        val res = request("/friends", "GET")
        return when (res) {
            is ApiResponse.Success -> ApiResponse.Success(res.data.optJSONArray("data") ?: JSONArray())
            is ApiResponse.Error -> ApiResponse.Error(res.message, res.statusCode)
        }
    }

    suspend fun getFriendRequests(): ApiResponse<JSONArray> {
        val res = request("/friends/requests", "GET")
        return when (res) {
            is ApiResponse.Success -> ApiResponse.Success(res.data.optJSONArray("data") ?: JSONArray())
            is ApiResponse.Error -> ApiResponse.Error(res.message, res.statusCode)
        }
    }

    suspend fun getSuggestedPeople(): ApiResponse<JSONArray> {
        val res = request("/friends/suggested", "GET")
        return when (res) {
            is ApiResponse.Success -> ApiResponse.Success(res.data.optJSONArray("data") ?: JSONArray())
            is ApiResponse.Error -> ApiResponse.Error(res.message, res.statusCode)
        }
    }

    suspend fun refreshSuggestedPeople(excludeIds: List<String> = emptyList()): ApiResponse<JSONArray> {
        val body = JSONObject().put("excludeIds", JSONArray(excludeIds))
        val res = request("/friends/suggested/refresh", "POST", body)
        return when (res) {
            is ApiResponse.Success -> ApiResponse.Success(res.data.optJSONArray("data") ?: JSONArray())
            is ApiResponse.Error -> ApiResponse.Error(res.message, res.statusCode)
        }
    }

    suspend fun searchUsers(query: String): ApiResponse<JSONArray> {
        val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
        val res = request("/friends/search?q=$encoded", "GET")
        return when (res) {
            is ApiResponse.Success -> ApiResponse.Success(res.data.optJSONArray("data") ?: JSONArray())
            is ApiResponse.Error -> ApiResponse.Error(res.message, res.statusCode)
        }
    }

    suspend fun blockUser(userId: String): ApiResponse<JSONObject> = request("/friends/$userId/block", "POST")
    suspend fun unblockUser(userId: String): ApiResponse<JSONObject> = request("/friends/$userId/block", "DELETE")
    suspend fun getBlockedUsers(): ApiResponse<JSONArray> {
        val res = request("/friends/blocked", "GET")
        return when (res) {
            is ApiResponse.Success -> ApiResponse.Success(res.data.optJSONArray("data") ?: JSONArray())
            is ApiResponse.Error -> ApiResponse.Error(res.message, res.statusCode)
        }
    }

    suspend fun sendFriendRequest(userId: String): ApiResponse<JSONObject> =
        request("/friends/request", "POST", JSONObject().put("recipientId", userId))

    suspend fun acceptFriendRequest(requestId: String): ApiResponse<JSONObject> =
        request("/friends/request/$requestId/accept", "POST")

    suspend fun declineFriendRequest(requestId: String): ApiResponse<JSONObject> =
        request("/friends/request/$requestId/decline", "POST")

    suspend fun withdrawFriendRequest(userId: String): ApiResponse<JSONObject> =
        request("/friends/request/withdraw/$userId", "DELETE")
    suspend fun removeFriend(userId: String): ApiResponse<JSONObject> = request("/friends/$userId", "DELETE")

    suspend fun sendDirectMessage(conversationId: String, text: String, mediaUrl: String? = null, messageType: String = "text", isSpoiler: Boolean = false, mediaMimeType: String? = null): ApiResponse<JSONObject> =
        request(
            "/chat",
            "POST",
            JSONObject().put("conversationId", conversationId).put("text", text).put("isSpoiler", isSpoiler).apply {
                mediaUrl?.let { put("mediaUrl", it) }
                put("messageType", messageType)
                mediaMimeType?.let { put("mediaMimeType", it) }
            }
        )

    suspend fun getConversationMessages(conversationId: String): ApiResponse<JSONArray> {
        val res = request("/chat/conversation/$conversationId", "GET")
        return when (res) {
            is ApiResponse.Success -> ApiResponse.Success(res.data.optJSONArray("data") ?: JSONArray())
            is ApiResponse.Error -> ApiResponse.Error(res.message, res.statusCode)
        }
    }

    suspend fun markConversationRead(conversationId: String): ApiResponse<JSONObject> =
        request("/chat/conversation/$conversationId/read", "POST")

    suspend fun editDirectMessage(messageId: String, text: String): ApiResponse<JSONObject> =
        request("/chat/$messageId", "PUT", JSONObject().put("text", text))

    suspend fun unsendDirectMessage(messageId: String): ApiResponse<JSONObject> =
        request("/chat/$messageId", "DELETE")


    // ==========================================
    // MEMBERSHIP & BILLING
    // ==========================================
    suspend fun getMembershipPlans(): ApiResponse<JSONArray> {
        val res = request("/billing/plans", "GET")
        return when (res) {
            is ApiResponse.Success -> ApiResponse.Success(res.data.optJSONArray("data") ?: JSONArray())
            is ApiResponse.Error -> ApiResponse.Error(res.message, res.statusCode)
        }
    }

    suspend fun verifyPurchase(purchaseToken: String, planTier: String): ApiResponse<JSONObject> {
        val body = JSONObject().apply {
            put("purchaseToken", purchaseToken)
            put("planTier", planTier)
        }
        return request("/billing/verify", "POST", body)
    }

    // ==========================================
    // ANALYTICS
    // ==========================================
    suspend fun trackEvent(eventName: String, metadata: Map<String, Any> = emptyMap()): ApiResponse<JSONObject> {
        val body = JSONObject().apply {
            put("eventName", eventName)
            put("metadata", JSONObject(metadata))
        }
        return request("/analytics/track", "POST", body)
    }
}
