package com.nebula.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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

import com.nebula.app.data.auth.NebulaAuthManager
import com.nebula.app.data.repository.RoomRepositoryImpl
import com.nebula.app.data.repository.SocialRepositoryImpl

class MainActivity : ComponentActivity() {

    // Session Manager
    private val sessionManager by lazy { com.nebula.app.data.local.SessionManager.getInstance(this) }

    // Repositories & Auth Manager
    private val themeRepository = ThemeRepositoryImpl()
    private val profileRepository by lazy { ProfileRepositoryImpl(sessionManager) }
    private val chatRepository = ChatRepositoryImpl()
    private val roomRepository = RoomRepositoryImpl()
    private val socialRepository = SocialRepositoryImpl()
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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install Android 12+ system splashscreen
        installSplashScreen()
        super.onCreate(savedInstanceState)
        com.nebula.app.data.backend.NebulaApiClient.sessionManager = sessionManager
        enableEdgeToEdge()

        setContent {
            val activeTheme by themeRepository.activeTheme.collectAsState()
            val uiState by homeViewModel.uiState.collectAsState()
            var isSplashVisible by remember { mutableStateOf(true) }
            var showWelcomeBack by remember { mutableStateOf(false) }
            var wasLoggedIn by remember { mutableStateOf(false) }

            LaunchedEffect(uiState.userProfile.isLoggedIn) {
                if (uiState.userProfile.isLoggedIn && !wasLoggedIn && uiState.userProfile.isOnboardingCompleted) {
                    showWelcomeBack = true
                }
                wasLoggedIn = uiState.userProfile.isLoggedIn
            }

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
                                onSignInWithEmail = { email, name -> homeViewModel.signInWithEmail(email, name) },
                                onRequestOtp = { email, onSent -> homeViewModel.requestOtp(email, onSent) },
                                onVerifyOtp = { email, otp -> homeViewModel.verifyOtp(email, otp) },
                                isAuthLoading = uiState.isAuthLoading,
                                errorMessage = uiState.authErrorMessage,
                                currentBackendUrl = uiState.backendUrl,
                                onUpdateBackendUrl = { homeViewModel.updateBackendUrl(it) },
                                onExitApp = { finish() },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (showWelcomeBack) {
                            WelcomeBackScreen(
                                displayName = uiState.userProfile.displayName,
                                onContinue = { showWelcomeBack = false },
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
}

@androidx.compose.runtime.Composable
private fun WelcomeBackScreen(
    displayName: String,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    Column(
        modifier = modifier,
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
