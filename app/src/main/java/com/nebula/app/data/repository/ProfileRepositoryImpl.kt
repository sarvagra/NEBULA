package com.nebula.app.data.repository

import com.nebula.app.data.local.SessionManager
import com.nebula.app.domain.model.NebulaSubscriptionTier
import com.nebula.app.domain.model.UserProfile
import com.nebula.app.domain.repository.IProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProfileRepositoryImpl(
    private val sessionManager: SessionManager? = null,
    initialProfile: UserProfile = sessionManager?.getUserProfile() ?: UserProfile()
) : IProfileRepository {

    private val _userProfile = MutableStateFlow(initialProfile)
    override val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private fun persist() {
        sessionManager?.saveUserProfile(_userProfile.value)
    }

    override suspend fun updateAvatar(uri: String?) {
        _userProfile.value = _userProfile.value.copy(
            avatarUri = uri,
            isCustomAvatar = uri != null
        )
        persist()
    }

    override suspend fun updateDisplayName(name: String) {
        val initials = name.trim().split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .map { it.first().uppercase() }
            .joinToString("")
        val fallback = if (initials.isNotEmpty()) initials else "SS"
        _userProfile.value = _userProfile.value.copy(
            displayName = name,
            avatarFallbackLetter = fallback
        )
        persist()
    }

    override suspend fun updateUsername(username: String?) {
        _userProfile.value = _userProfile.value.copy(username = username)
        persist()
    }

    override suspend fun updateBio(bio: String) {
        _userProfile.value = _userProfile.value.copy(bio = bio)
        persist()
    }

    override suspend fun updateStatus(status: String) {
        _userProfile.value = _userProfile.value.copy(status = status)
        persist()
    }

    override suspend fun updateBannerIndex(bannerIndex: Int) {
        _userProfile.value = _userProfile.value.copy(bannerIndex = bannerIndex)
        persist()
    }

    override suspend fun updateInterests(interests: List<String>) {
        _userProfile.value = _userProfile.value.copy(interests = interests)
        persist()
    }

    override suspend fun setPremium(isPremium: Boolean) {
        val tier = if (isPremium) NebulaSubscriptionTier.PREMIUM else NebulaSubscriptionTier.FREE
        _userProfile.value = _userProfile.value.copy(
            isPremium = isPremium,
            subscriptionTier = tier
        )
        persist()
    }

    override suspend fun updateSubscriptionTier(tier: NebulaSubscriptionTier) {
        _userProfile.value = _userProfile.value.copy(
            subscriptionTier = tier,
            isPremium = tier != NebulaSubscriptionTier.FREE
        )
        persist()
    }

    override suspend fun updateCustomInstructions(instructions: String) {
        _userProfile.value = _userProfile.value.copy(customInstructions = instructions)
        persist()
    }

    override suspend fun setLoggedIn(isLoggedIn: Boolean) {
        _userProfile.value = _userProfile.value.copy(isLoggedIn = isLoggedIn)
        persist()
    }

    override suspend fun setLoggedInUser(profile: UserProfile) {
        _userProfile.value = profile
        persist()
    }

    override suspend fun updateFriendCountPublic(isPublic: Boolean) {
        _userProfile.value = _userProfile.value.copy(isFriendCountPublic = isPublic)
        persist()
    }
}
