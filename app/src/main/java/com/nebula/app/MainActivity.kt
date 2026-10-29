package com.nebula.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.nebula.app.data.repository.ChatRepositoryImpl
import com.nebula.app.data.repository.ProfileRepositoryImpl
import com.nebula.app.data.repository.ThemeRepositoryImpl
import com.nebula.app.theme.ProvideAppTheme
import com.nebula.app.ui.home.HomeScreen
import com.nebula.app.ui.home.HomeViewModel
import com.nebula.app.ui.splash.SplashScreen
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.NebulaLogo
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow

import com.nebula.app.data.auth.NebulaAuthManager
import com.nebula.app.data.repository.RoomRepositoryImpl
import com.nebula.app.data.repository.SocialRepositoryImpl

class MainActivity : ComponentActivity() {
    private val notificationDestination = MutableStateFlow<Pair<String, String>?>(null)

    // Session Manager
    private val sessionManager by lazy { com.nebula.app.data.local.SessionManager.getInstance(this) }

    // Repositories & Auth Manager
    private val themeRepository by lazy { ThemeRepositoryImpl(sessionManager) }
    private val profileRepository by lazy { ProfileRepositoryImpl(sessionManager) }
    private val chatRepository = ChatRepositoryImpl()
    private val roomRepository by lazy { RoomRepositoryImpl(sessionManager = sessionManager) }
    private val socialRepository by lazy { SocialRepositoryImpl(sessionManager = sessionManager) }
    private val authManager by lazy { NebulaAuthManager(this) }

    private val homeViewModel by lazy {
        com.nebula.app.data.backend.NebulaApiClient.sessionManager = sessionManager
        HomeViewModel(
            themeRepository = themeRepository,
            profileRepository = profileRepository,
            chatRepository = chatRepository,
            roomRepository = roomRepository,
            socialRepository = socialRepository,
            authManager = authManager,
            sessionManager = sessionManager
        )
    }

    override fun onStart() {
        super.onStart()
        homeViewModel.onAppForegrounded()
        roomRepository.setAppForegrounded(true)
    }

    override fun onStop() {
        homeViewModel.onAppBackgrounded()
        roomRepository.setAppForegrounded(false)
        super.onStop()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationDestination.value = notificationDestinationFrom(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install Android 12+ system splashscreen
        installSplashScreen()
        super.onCreate(savedInstanceState)
        com.nebula.app.data.backend.NebulaApiClient.sessionManager = sessionManager
        notificationDestination.value = notificationDestinationFrom(intent)
        enableEdgeToEdge()

        setContent {
            val activeTheme by themeRepository.activeTheme.collectAsState()
            val uiState by homeViewModel.uiState.collectAsState()
            val pendingNotification by notificationDestination.collectAsState()
            val showWelcomeBack by homeViewModel.showWelcomeBack.collectAsState()
            val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                getSharedPreferences("nebula_push", MODE_PRIVATE).edit().putBoolean("notification_permission_prompt_completed", true).apply()
            }
            LaunchedEffect(uiState.userProfile.isLoggedIn, uiState.userProfile.id) {
                if (uiState.userProfile.isLoggedIn && uiState.userProfile.id.isNotBlank()) {
                    if (android.os.Build.VERSION.SDK_INT >= 33 &&
                        androidx.core.content.ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
                        !getSharedPreferences("nebula_push", MODE_PRIVATE).getBoolean("notification_permission_prompt_completed", false) &&
                        !shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)
                    ) {
                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                    runCatching {
                        val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                        if (token.isNotBlank()) com.nebula.app.data.backend.NebulaApiClient.registerNotificationDevice(token)
                    }
                }
            }
            LaunchedEffect(uiState.userProfile.isLoggedIn, pendingNotification) {
                val destination = pendingNotification
                if (uiState.userProfile.isLoggedIn && destination != null) {
                    homeViewModel.onOpenNotificationDestination(destination.first, destination.second)
                    notificationDestination.value = null
                }
            }
            var isSplashVisible by remember { mutableStateOf(true) }

            ProvideAppTheme(theme = activeTheme) {
                Crossfade(
                    targetState = isSplashVisible,
                    animationSpec = tween(durationMillis = 350),
                    label = "splashTransition"
                ) { showSplash ->
                    if (showSplash) {
                        SplashScreen(
                            onSplashFinished = { isSplashVisible = false },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // MANDATORY AUTHENTICATION GATE: User cannot enter any authenticated screens without login
                        if (!uiState.userProfile.isLoggedIn) {
                            com.nebula.app.ui.auth.NebulaAuthScreen(
                                onSignInWithGoogle = { homeViewModel.signInWithGoogle(this@MainActivity) },
                                isAuthLoading = uiState.isAuthLoading,
                                errorMessage = uiState.authErrorMessage,
                                onExitApp = { finish() },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (!uiState.userProfile.isOnboardingCompleted) {
                            com.nebula.app.ui.auth.NebulaOnboardingScreen(
                                initialProfile = uiState.userProfile,
                                onCompleteOnboarding = { displayName, username, dob, gender, avatarUri ->
                                    homeViewModel.onCompleteOnboarding(displayName, username, dob, gender, avatarUri)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (showWelcomeBack) {
                            WelcomeBackScreen(
                                displayName = uiState.userProfile.displayName,
                                onContinue = { homeViewModel.dismissWelcomeBack() },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            HomeScreen(
                                viewModel = homeViewModel,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                }
            }
        }
    }

    private fun notificationDestinationFrom(intent: android.content.Intent?): Pair<String, String>? {
        if (intent == null) return null
        fun payloadValue(key: String) = intent.getStringExtra("nebula_$key") ?: intent.getStringExtra(key)
        // A shared room link is a navigation too: nebula://room/<code> or
        // https://nebula.app/room/<code>. The code is resolved server-side so a link
        // never has to carry (or reveal) the room's database id.
        val linkCode = intent.data?.let { uri ->
            val looksLikeRoom = uri.host == "room" || uri.pathSegments.firstOrNull() == "room"
            if (looksLikeRoom) (if (uri.host == "room") uri.lastPathSegment else uri.pathSegments.getOrNull(1))
                ?.trim()?.uppercase()?.takeIf { it.length == 8 } else null
        }
        if (linkCode != null) return "room_invite_code" to linkCode
        val type = payloadValue("type") ?: return null
        val id = when (type) {
            "dm" -> payloadValue("conversationId")
            "room_invite", "room_activity" -> payloadValue("roomId")
            "friend_request" -> payloadValue("userId")
            else -> null
        }?.takeIf { it.isNotBlank() } ?: return null
        return type to id
    }
}

@androidx.compose.runtime.Composable
private fun WelcomeBackScreen(
    displayName: String,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    // The welcome-back surface follows the selected theme instead of falling
    // through to the platform window background, which is gray in dark themes.
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        theme.colors.background,
                        theme.colors.background,
                        theme.colors.surface
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            NebulaLogo(Modifier.size(92.dp))
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Welcome back${displayName.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""}",
                color = theme.colors.textPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text("Your Nebula spaces are ready.", color = theme.colors.textSecondary)
            Spacer(Modifier.height(28.dp))
            Button(onClick = onContinue) {
                Text("Continue")
            }
        }
    }
}
