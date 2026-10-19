package com.nebula.app.data.local

import android.content.Context
import android.content.SharedPreferences
import com.nebula.app.domain.model.NebulaSubscriptionTier
import com.nebula.app.domain.model.UserProfile
import org.json.JSONArray
import org.json.JSONObject

/**
 * SessionManager:
 * Persistent session and credential storage for Nebula.
 * Uses Android SharedPreferences to persist authentication tokens and user profile
 * across application restarts, process death, and force stops.
 */
class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "nebula_session_prefs"
        private const val KEY_AUTH_TOKEN = "key_auth_token"
        private const val KEY_REFRESH_TOKEN = "key_refresh_token"
        private const val KEY_USER_PROFILE_JSON = "key_user_profile_json"
        private const val KEY_IS_LOGGED_IN = "key_is_logged_in"
        private const val KEY_BACKEND_URL = "key_backend_url"

        @Volatile
        private var INSTANCE: SessionManager? = null

        fun getInstance(context: Context): SessionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SessionManager(context).also { INSTANCE = it }
            }
        }
    }

    fun getBackendUrl(): String {
        val saved = prefs.getString(KEY_BACKEND_URL, null)
        if (!saved.isNullOrBlank() &&
            saved.startsWith("https://") &&
            !saved.contains("trycloudflare.com") &&
            !saved.contains("localhost") &&
            !saved.contains("10.0.2.2") &&
            !saved.contains("192.168.") &&
            !saved.contains("172.") &&
            !saved.contains(":5001")
        ) return saved

        return "https://nebula-73kb.onrender.com/api/v1"
    }

    fun saveBackendUrl(url: String) {
        val normalized = url.trim().removeSuffix("/")
        val finalUrl = if (normalized.endsWith("/api/v1")) normalized else "$normalized/api/v1"
        prefs.edit()
            .putString(KEY_BACKEND_URL, finalUrl)
            .apply()
    }

    fun saveAuthToken(token: String) {
        prefs.edit()
            .putString(KEY_AUTH_TOKEN, token)
            .apply()
    }

    fun getAuthToken(): String? {
        return prefs.getString(KEY_AUTH_TOKEN, null)
    }

    fun saveRefreshToken(token: String) {
        prefs.edit().putString(KEY_REFRESH_TOKEN, token).apply()
    }

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun saveUserProfile(profile: UserProfile) {
        val json = JSONObject().apply {
            put("id", profile.id)
            put("displayName", profile.displayName)
            put("email", profile.email)
            put("phoneNumber", profile.phoneNumber)
            put("username", profile.username ?: "")
            put("bio", profile.bio)
            put("status", profile.status)
            put("customStatus", profile.customStatus)
            put("avatarUri", profile.avatarUri ?: "")
            put("avatarFallbackLetter", profile.avatarFallbackLetter)
            put("bannerIndex", profile.bannerIndex)
            put("isCustomAvatar", profile.isCustomAvatar)
            put("isPremium", profile.isPremium)
            put("isTrial", profile.isTrial)
            put("subscriptionTier", profile.subscriptionTier.name)
            put("interests", JSONArray(profile.interests))
            put("customInstructions", profile.customInstructions)
            put("referralCount", profile.referralCount)
            put("referralCode", profile.referralCode)
            put("isLoggedIn", profile.isLoggedIn)
            put("roomsHostedCount", profile.roomsHostedCount)
            put("roomsJoinedCount", profile.roomsJoinedCount)
            put("timeInSyncHours", profile.timeInSyncHours)
            put("isFriendCountPublic", profile.isFriendCountPublic)
            put("isOnboardingCompleted", profile.isOnboardingCompleted)
            put("dateOfBirth", profile.dateOfBirth ?: "")
            put("gender", profile.gender ?: "")
            put("bannerUrl", profile.bannerUrl ?: "")
        }

        prefs.edit()
            .putString(KEY_USER_PROFILE_JSON, json.toString())
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .apply()
    }

    fun getUserProfile(): UserProfile? {
        val jsonStr = prefs.getString(KEY_USER_PROFILE_JSON, null) ?: return null
        return try {
            val json = JSONObject(jsonStr)
            val rawTier = json.optString("subscriptionTier", NebulaSubscriptionTier.FREE.name)
            val tier = try {
                NebulaSubscriptionTier.valueOf(rawTier)
            } catch (_: Exception) {
                NebulaSubscriptionTier.FREE
            }

            val interestsArray = json.optJSONArray("interests")
            val interestsList = mutableListOf<String>()
            if (interestsArray != null) {
                for (i in 0 until interestsArray.length()) {
                    interestsList.add(interestsArray.getString(i))
                }
            }

            UserProfile(
                id = json.optString("id", ""),
                displayName = json.optString("displayName", "Nebula Explorer"),
                email = json.optString("email", ""),
                phoneNumber = json.optString("phoneNumber", ""),
                username = json.optString("username", "").ifEmpty { null },
                bio = json.optString("bio", ""),
                status = json.optString("status", "dynamic").lowercase().let { if (it in setOf("dynamic", "offline", "custom")) it else "dynamic" },
                customStatus = json.optString("customStatus", ""),
                avatarUri = json.optString("avatarUri", "").ifEmpty { null },
                avatarFallbackLetter = json.optString("avatarFallbackLetter", "N"),
                bannerIndex = json.optInt("bannerIndex", 0),
                isCustomAvatar = json.optBoolean("isCustomAvatar", false),
                isPremium = json.optBoolean("isPremium", false),
                isTrial = json.optBoolean("isTrial", false),
                subscriptionTier = tier,
                interests = interestsList,
                customInstructions = json.optString("customInstructions", ""),
                referralCount = json.optInt("referralCount", 0),
                referralCode = json.optString("referralCode", ""),
                isLoggedIn = json.optBoolean("isLoggedIn", true),
                roomsHostedCount = json.optInt("roomsHostedCount", 0),
                roomsJoinedCount = json.optInt("roomsJoinedCount", 0),
                timeInSyncHours = json.optDouble("timeInSyncHours", 0.0),
                isFriendCountPublic = json.optBoolean("isFriendCountPublic", true),
                isOnboardingCompleted = json.optBoolean("isOnboardingCompleted", false),
                dateOfBirth = json.optString("dateOfBirth", "").ifEmpty { null },
                gender = json.optString("gender", "").ifEmpty { null },
                bannerUrl = json.optString("bannerUrl", "").ifEmpty { null }
            )
        } catch (_: Exception) {
            null
        }
    }

    fun hasSession(): Boolean {
        return prefs.contains(KEY_USER_PROFILE_JSON) || !getAuthToken().isNullOrBlank()
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_USER_PROFILE_JSON)
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .apply()
    }
}
