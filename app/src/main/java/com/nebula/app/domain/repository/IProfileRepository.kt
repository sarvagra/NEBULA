package com.nebula.app.domain.repository

import com.nebula.app.domain.model.NebulaSubscriptionTier
import com.nebula.app.domain.model.UserProfile
import kotlinx.coroutines.flow.StateFlow

interface IProfileRepository {
    val userProfile: StateFlow<UserProfile>
    suspend fun updateAvatar(uri: String?)
    suspend fun updateDisplayName(name: String)
    suspend fun updateUsername(username: String?)
    suspend fun updateBio(bio: String)
    suspend fun updateStatus(status: String)
    suspend fun updateBannerIndex(bannerIndex: Int)
    suspend fun updateInterests(interests: List<String>)
    suspend fun setPremium(isPremium: Boolean)
    suspend fun updateSubscriptionTier(tier: NebulaSubscriptionTier)
    suspend fun updateCustomInstructions(instructions: String)
    suspend fun setLoggedIn(isLoggedIn: Boolean)
    suspend fun setLoggedInUser(profile: UserProfile)
    suspend fun updateFriendCountPublic(isPublic: Boolean)
}
