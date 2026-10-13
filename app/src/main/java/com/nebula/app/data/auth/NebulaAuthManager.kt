package com.nebula.app.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.nebula.app.data.backend.NebulaApiClient
import com.nebula.app.data.backend.NebulaApiClient.ApiResponse
import com.nebula.app.domain.model.NebulaSubscriptionTier
import com.nebula.app.domain.model.UserProfile
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

sealed class GoogleAuthResult {
    data class Success(val profile: UserProfile, val isNewSession: Boolean = true) : GoogleAuthResult()
    data class Error(val message: String, val isCancelled: Boolean = false) : GoogleAuthResult()
    data object Loading : GoogleAuthResult()
}

/**
 * NebulaAuthManager:
 * Implements the authoritative modern Google Sign-In pipeline:
 * Nebula -> Google Account Picker (Credential Manager) -> Google Credential ->
 * Firebase Auth -> Firebase ID Token -> Nebula Backend (/api/auth/google).
 */
class NebulaAuthManager(
    private val context: Context,
    private val apiClient: NebulaApiClient = NebulaApiClient
) {
    private val credentialManager: CredentialManager by lazy {
        CredentialManager.create(context)
    }

    private val firebaseAuth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    // Web Client ID extracted from app/google-services.json (oauth_client type 3)
    val webClientId = "605429732376-b2vlducpph4d42bmglh66m31cnr7p5g6.apps.googleusercontent.com"

    val isFirebaseSignedIn: Boolean
        get() = firebaseAuth.currentUser != null

    val currentFirebaseUser
        get() = firebaseAuth.currentUser

    /**
     * Checks if a user is already signed into Firebase locally and restores their token & session.
     */
    suspend fun restoreSessionIfPresent(): UserProfile? {
        val user = firebaseAuth.currentUser ?: return null
        return try {
            val tokenResult = user.getIdToken(false).await()
            val idToken = tokenResult.token
            if (!idToken.isNullOrBlank()) {
                val apiRes = apiClient.googleSignIn(idToken)
                if (apiRes is ApiResponse.Success) {
                    parseUserProfileFromBackend(apiRes.data, user)
                } else {
                    null
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Executes the complete Google Sign-In flow:
     * 1. Credential Manager displays Google Account Picker
     * 2. Retrieves Google ID Token
     * 3. Authenticates with Firebase Auth
     * 4. Retrieves Firebase ID Token
     * 5. Exchanges with Nebula Backend for session JWT & authoritative user profile
     */
    suspend fun signInWithGoogle(activityContext: Context): GoogleAuthResult {
        return try {
            // 1. Configure modern Credential Manager Google ID Option
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            // 2. Launch Google Account Picker bottom-sheet
            val credentialResult = credentialManager.getCredential(
                request = request,
                context = activityContext
            )

            // 3. Extract Google ID Token
            val credential = credentialResult.credential
            val googleIdToken: String = if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                googleIdTokenCredential.idToken
            } else {
                return GoogleAuthResult.Error("Unsupported credential type: ${credential.type}")
            }

            // 4. Authenticate into Firebase with Google Credential
            val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
            val authResult = firebaseAuth.signInWithCredential(authCredential).await()
            val firebaseUser = authResult.user
                ?: return GoogleAuthResult.Error("Firebase user authentication failed")

            // 5. Retrieve fresh Firebase ID Token
            val idTokenResult = firebaseUser.getIdToken(true).await()
            val firebaseIdToken = idTokenResult.token ?: googleIdToken

            // 6. Authoritative authentication against Nebula Backend
            val backendResponse = apiClient.googleSignIn(firebaseIdToken)

            val profile = when (backendResponse) {
                is ApiResponse.Success -> {
                    parseUserProfileFromBackend(backendResponse.data, firebaseUser)
                }
                is ApiResponse.Error -> {
                    return GoogleAuthResult.Error("Nebula backend verification failed: ${backendResponse.message}")
                }
            }

            GoogleAuthResult.Success(profile)
        } catch (e: GetCredentialCancellationException) {
            GoogleAuthResult.Error("Sign-in cancelled", isCancelled = true)
        } catch (e: GetCredentialException) {
            GoogleAuthResult.Error("Credential Manager error: ${e.message}")
        } catch (e: Exception) {
            GoogleAuthResult.Error(e.localizedMessage ?: "Google sign-in failed")
        }
    }

    /**
     * Signs out from Firebase and clears backend authorization headers.
     */
    suspend fun signOut(): Boolean {
        return try {
            // Revoke the server refresh token before clearing its local copy.
            try { apiClient.logout() } catch (_: Exception) {}
            firebaseAuth.signOut()
            apiClient.authToken = null
            try {
                credentialManager.clearCredentialState(
                    androidx.credentials.ClearCredentialStateRequest()
                )
            } catch (_: Exception) {}
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun requestOtp(target: String): ApiResponse<JSONObject> {
        return apiClient.requestOtp(target)
    }

    suspend fun verifyOtp(target: String, otp: String): GoogleAuthResult {
        return try {
            val res = apiClient.verifyOtp(target, otp)
            if (res is ApiResponse.Success) {
                val data = res.data.optJSONObject("data")
                val userObj = data?.optJSONObject("user")
                val isNewUser = data?.optBoolean("isNewUser", false) ?: false
                val backendOnboarding = userObj?.optBoolean("isOnboardingCompleted", false) ?: false
                val profile = parseUserProfileFromBackendJson(userObj, target, "")
                val finalProfile = if (backendOnboarding) {
                    profile.copy(isOnboardingCompleted = true)
                } else if (isNewUser) {
                    profile.copy(isOnboardingCompleted = false)
                } else {
                    profile.copy(isOnboardingCompleted = true)
                }
                GoogleAuthResult.Success(finalProfile)
            } else if (res is ApiResponse.Error) {
                GoogleAuthResult.Error(res.message)
            } else {
                GoogleAuthResult.Error("Verification failed")
            }
        } catch (e: Exception) {
            GoogleAuthResult.Error(e.localizedMessage ?: "Verification failed")
        }
    }

    suspend fun signInWithEmailAccount(email: String, displayName: String = ""): GoogleAuthResult {
        return try {
            val res = apiClient.emailSignIn(email, displayName)
            if (res is ApiResponse.Success) {
                val data = res.data.optJSONObject("data")
                val userObj = data?.optJSONObject("user")
                val profile = parseUserProfileFromBackendJson(userObj, email, displayName)
                GoogleAuthResult.Success(profile)
            } else if (res is ApiResponse.Error) {
                GoogleAuthResult.Error(res.message)
            } else {
                GoogleAuthResult.Error("Unknown authentication error")
            }
        } catch (e: Exception) {
            GoogleAuthResult.Error(e.localizedMessage ?: "Sign-in failed")
        }
    }

    private fun parseUserProfileFromBackendJson(
        userObj: JSONObject?,
        fallbackEmail: String,
        fallbackDisplayName: String
    ): UserProfile {
        // Entitlements and onboarding state come from the authenticated backend profile.
        // An email address on the client is not an authorization source.
        val rawTier = userObj?.optString("tier")?.lowercase() ?: "free"
        val tier = when (rawTier) {
            "premium_plus", "plus" -> NebulaSubscriptionTier.PREMIUM_PLUS
            "premium", "pro" -> NebulaSubscriptionTier.PREMIUM
            else -> NebulaSubscriptionTier.FREE
        }

        val email = userObj?.optString("email")?.ifBlank { null } ?: fallbackEmail
        val rawDisplayName = userObj?.optString("displayName")?.ifBlank { null }
        val displayName = rawDisplayName ?: fallbackDisplayName

        val rawUsername = userObj?.optString("username")?.ifBlank { null }
        val username = if (rawUsername != null) {
            if (rawUsername.startsWith("@")) rawUsername else "@$rawUsername"
        } else {
            "@${email.substringBefore("@").ifBlank { "nebula_user" }}"
        }

        val statsObj = userObj?.optJSONObject("stats")
        val roomsJoined = statsObj?.optInt("roomsJoinedCount") ?: 0
        val roomsHosted = statsObj?.optInt("roomsHostedCount") ?: 0
        val timeInSync = statsObj?.optDouble("timeInSyncHours") ?: 0.0

        val avatarUrl = userObj?.optString("avatarUrl")?.ifBlank { null }
        val backendOnboarding = userObj?.optBoolean("isOnboardingCompleted", true) ?: true
        val isOnboardingCompleted = backendOnboarding && displayName.isNotBlank()

        return UserProfile(
            id = userObj?.optString("id")?.ifBlank { null } ?: java.util.UUID.randomUUID().toString(),
            displayName = displayName,
            email = email,
            username = username,
            bio = userObj?.optString("bio")?.ifBlank { null }
                ?: "Building ambient systems & shared spaces on Android.",
            status = userObj?.optString("status")?.ifBlank { null } ?: "Online",
            avatarUri = avatarUrl,
            avatarFallbackLetter = (displayName.firstOrNull() ?: 'N').uppercaseChar().toString(),
            isCustomAvatar = !avatarUrl.isNullOrBlank(),
            isLoggedIn = true,
            isPremium = tier != NebulaSubscriptionTier.FREE,
            subscriptionTier = tier,
            roomsJoinedCount = roomsJoined,
            roomsHostedCount = roomsHosted,
            timeInSyncHours = timeInSync,
            isOnboardingCompleted = isOnboardingCompleted,
            dateOfBirth = userObj?.optString("dateOfBirth")?.ifBlank { null },
            gender = userObj?.optString("gender")?.ifBlank { null },
            bannerUrl = userObj?.optString("bannerUrl")?.ifBlank { null }
        )
    }

    private fun parseUserProfileFromBackend(json: JSONObject, firebaseUser: com.google.firebase.auth.FirebaseUser): UserProfile {
        val data = json.optJSONObject("data")
        val userObj = data?.optJSONObject("user")
        return parseUserProfileFromBackendJson(
            userObj,
            firebaseUser.email ?: "",
            firebaseUser.displayName ?: "Nebula Explorer"
        ).copy(
            id = userObj?.optString("id")?.ifBlank { null } ?: firebaseUser.uid,
            avatarUri = userObj?.optString("avatarUrl")?.ifBlank { null } ?: firebaseUser.photoUrl?.toString()
        )
    }

    private fun buildLocalProfileFromFirebase(firebaseUser: com.google.firebase.auth.FirebaseUser): UserProfile {
        val email = firebaseUser.email ?: ""
        val username = "@${email.substringBefore("@").ifBlank { "nebula_user" }}"
        val initialLetter = (firebaseUser.displayName?.firstOrNull() ?: 'N').uppercaseChar().toString()

        return UserProfile(
            id = firebaseUser.uid,
            displayName = firebaseUser.displayName ?: "Nebula Explorer",
            email = email,
            username = username,
            avatarUri = firebaseUser.photoUrl?.toString(),
            avatarFallbackLetter = initialLetter,
            isLoggedIn = true,
            isPremium = false,
            subscriptionTier = NebulaSubscriptionTier.FREE,
            roomsJoinedCount = 0,
            roomsHostedCount = 0,
            timeInSyncHours = 0.0
        )
    }
}
