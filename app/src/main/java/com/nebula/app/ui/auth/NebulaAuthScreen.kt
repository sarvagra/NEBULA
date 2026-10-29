package com.nebula.app.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.NebulaLogo

/**
 * NebulaAuthScreen — the mandatory entry gate.
 *
 * Nebula is not usable without an authenticated identity, and the only identity
 * provider is Firebase Authentication through Google Sign-In. There is
 * deliberately no password, email-link, OTP or anonymous path here: adding one
 * would create a second source of identity that the backend does not verify.
 */
@Composable
fun NebulaAuthScreen(
    onSignInWithGoogle: () -> Unit,
    isAuthLoading: Boolean,
    errorMessage: String? = null,
    onExitApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current

    // Back terminates the application rather than bypassing authentication.
    BackHandler {
        onExitApp()
    }

    val welcomeBackground = Brush.verticalGradient(
        colors = listOf(
            theme.colors.background,
            theme.colors.background,
            theme.colors.surface
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(welcomeBackground)
            .statusBarsPadding()
    ) {
        // Ambient accent glow, fully transparent at the edge so the themed
        // surface below stays visible in every theme.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp)
                .size(320.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            theme.colors.primaryAccent.copy(alpha = 0.22f),
                            theme.colors.primaryAccent.copy(alpha = 0f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            NebulaLogo(Modifier.size(80.dp))

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "NEBULA",
                style = theme.typography.titleLarge.copy(
                    color = theme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                    fontSize = 28.sp
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Ambient watch parties, synchronized media, and private shared spaces.",
                style = theme.typography.bodyMedium.copy(
                    color = theme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                ),
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(36.dp))

            Button(
                onClick = onSignInWithGoogle,
                enabled = !isAuthLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.colors.surfaceElevated,
                    contentColor = theme.colors.textPrimary
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
            ) {
                if (isAuthLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = theme.colors.primaryAccent,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(
                        text = "Authenticating…",
                        style = theme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = theme.colors.textSecondary
                        )
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(theme.colors.primaryAccent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "G",
                                style = theme.typography.caption.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = theme.colors.primaryAccent,
                                    fontSize = 13.sp
                                )
                            )
                        }

                        Text(
                            text = "Continue with Google",
                            style = theme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = theme.colors.textPrimary
                            )
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = !errorMessage.isNullOrBlank(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.colors.errorColor.copy(alpha = 0.12f))
                        .border(1.dp, theme.colors.errorColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = errorMessage ?: "",
                        style = theme.typography.caption.copy(
                            color = theme.colors.errorColor,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Explain the failure without inventing a fallback login:
                    // both causes are environmental, not alternatives to Google.
                    if (isCredentialEnvironmentError(errorMessage)) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Nebula signs in only with Google. Check that this " +
                                "device has Google Play services and that this build's " +
                                "signing key is registered for the Firebase project.",
                            style = theme.typography.caption.copy(
                                color = theme.colors.textSecondary,
                                fontSize = 11.5.sp,
                                textAlign = TextAlign.Center
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = theme.colors.textTertiary,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "Google sign-in · encrypted in transit",
                    style = theme.typography.caption.copy(
                        color = theme.colors.textTertiary,
                        fontSize = 11.5.sp
                    )
                )
            }
        }
    }
}

/** Credential/Play-services problems deserve an explanation, not a fake login path. */
internal fun isCredentialEnvironmentError(message: String?): Boolean {
    val raw = message ?: return false
    return raw.contains("Credential", ignoreCase = true) ||
        raw.contains("Google Play", ignoreCase = true) ||
        raw.contains("OAuth2.0", ignoreCase = true) ||
        raw.contains("not registered", ignoreCase = true)
}
