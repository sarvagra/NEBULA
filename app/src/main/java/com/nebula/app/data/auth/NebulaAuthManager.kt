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
    data class Success(
        val profile: UserProfile,
        val isNewSession: Boolean = true,
        /** True when signing in retired the least recently used of the two device sessions. */
        val replacedOldestDevice: Boolean = false
    ) : GoogleAuthResult()
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
            var backendResponse = apiClient.googleSignIn(firebaseIdToken)
            var replacedOldestDevice = false

            // An account can already hold its two device sessions from installs the user no
            // longer has. That used to lock them out of their own account permanently, since
            // the only fix was to revoke a session from a signed-in device they had lost.
            // The refusal is retried once with an explicit opt-in to retire the least recently
            // used session; the caller is told so the outcome can be shown, not hidden.
            if (backendResponse is ApiResponse.Error &&
                backendResponse.statusCode == 403 &&
                backendResponse.message.contains("Device limit", ignoreCase = true)
            ) {
                val retry = apiClient.googleSignIn(firebaseIdToken, replaceOldestDevice = true)
                if (retry is ApiResponse.Success) {
                    replacedOldestDevice = true
                    backendResponse = retry
                }
            }

            val profile = when (backendResponse) {
                is ApiResponse.Success -> {
                    parseUserProfileFromBackend(backendResponse.data, firebaseUser)
                }
                is ApiResponse.Error -> {
                    return GoogleAuthResult.Error("Nebula backend verification failed: ${backendResponse.message}")
                }
            }

            GoogleAuthResult.Success(profile, replacedOldestDevice = replacedOldestDevice)
        } catch (e: GetCredentialCancellationException) {
            GoogleAuthResult.Error("Sign-in cancelled", isCancelled = true)
        } catch (e: GetCredentialException) {
            GoogleAuthResult.Error("Credential Manager error: ${e.message}")
        } catch (e: Exception) {
            GoogleAuthResult.Error(e.localizedMessage ?: "Google sign-in failed")
        }
    }

    sealed class IdentityTokenResult {
        data class Success(val idToken: String, val email: String?) : IdentityTokenResult()
        data class Error(val message: String, val isCancelled: Boolean = false) : IdentityTokenResult()
    }

    /**
     * Picks a Google account and returns a verified Firebase ID token without touching
     * the current Nebula session.
     *
     * Re-binding the linked identity is a change to an account that already exists, so
     * it must not run the sign-in path: that path would mint a session for whatever
     * account was picked, which is a different (and wrong) operation.
     */
    suspend fun acquireIdentityToken(activityContext: Context): IdentityTokenResult {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()
            val credentialResult = credentialManager.getCredential(
                request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build(),
                context = activityContext
            )
            val credential = credentialResult.credential
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return IdentityTokenResult.Error("Unsupported credential type: ${credential.type}")
            }
            val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val authResult = firebaseAuth.signInWithCredential(
                GoogleAuthProvider.getCredential(googleIdToken, null)
            ).await()
            val firebaseUser = authResult.user
                ?: return IdentityTokenResult.Error("Could not verify that Google account")
            val token = firebaseUser.getIdToken(true).await().token
                ?: return IdentityTokenResult.Error("Could not get a fresh token")
            IdentityTokenResult.Success(token, firebaseUser.email)
        } catch (e: GetCredentialCancellationException) {
            IdentityTokenResult.Error("Cancelled", isCancelled = true)
        } catch (e: GetCredentialException) {
            IdentityTokenResult.Error("Credential Manager error: ${e.message}")
        } catch (e: Exception) {
            IdentityTokenResult.Error(e.localizedMessage ?: "Google account picker failed")
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
        val backendOnboarding = userObj?.optBoolean("isOnboardingCompleted", false) ?: false
        val isOnboardingCompleted = backendOnboarding && displayName.isNotBlank()

        return UserProfile(
            id = userObj?.optString("id")?.ifBlank { null } ?: java.util.UUID.randomUUID().toString(),
            displayName = displayName,
            email = email,
            username = username,
            bio = userObj?.optString("bio") ?: "",
            status = userObj?.optString("status")?.lowercase()?.let { if (it in setOf("dynamic", "offline", "custom")) it else "dynamic" } ?: "dynamic",
            customStatus = userObj?.optString("customStatus") ?: "",
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

}
