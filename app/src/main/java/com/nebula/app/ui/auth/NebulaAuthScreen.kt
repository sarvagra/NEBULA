package com.nebula.app.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * NebulaAuthScreen:
 * Mandatory entry gate for NEBULA.
 * NEBULA MUST NOT be usable without authentication.
 * Supports:
 * 1. Native Google Sign-In with Credential Manager (physical devices & GMS emulators)
 * 2. Gmail / Multi-Device Sign-In fallback (for emulators without Google Play Services or parallel testing)
 * 3. Dynamic Backend Server address configuration (for testing across physical phone and PC LAN)
 */
@Composable
fun NebulaAuthScreen(
    onSignInWithGoogle: () -> Unit,
    onSignInWithEmail: (email: String, displayName: String) -> Unit = { _, _ -> },
    onRequestOtp: (email: String, onResult: (Boolean) -> Unit) -> Unit = { _, _ -> },
    onVerifyOtp: (email: String, otp: String) -> Unit = { _, _ -> },
    isAuthLoading: Boolean,
    errorMessage: String? = null,
    currentBackendUrl: String = com.nebula.app.data.backend.NebulaApiClient.baseUrl,
    onUpdateBackendUrl: (String) -> Unit = {},
    onExitApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    var showEmailDialog by remember { mutableStateOf(false) }
    var emailInput by remember { mutableStateOf("") }
    var otpStep by remember { mutableIntStateOf(1) } // 1: Email input, 2: OTP input
    var otpInput by remember { mutableStateOf("") }
    var isSendingOtp by remember { mutableStateOf(false) }
    val isGmailAddress = emailInput.trim().lowercase().endsWith("@gmail.com")

    var showServerDialog by remember { mutableStateOf(false) }
    var serverInput by remember(currentBackendUrl) { mutableStateOf(currentBackendUrl) }

    // Back button terminates the application rather than bypassing authentication
    BackHandler {
        onExitApp()
    }

    // Whole-screen welcome background, built ONLY from the active theme tokens so that light,
    // dark, lavender and red-hot-pink each get their own look (no hardcoded gray anywhere).
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
        // Ambient background glow (theme accent, fades out to fully transparent so the themed
        // background below stays the visible surface in every theme)
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
            // App Emblem
            NebulaLogo(Modifier.size(80.dp))

            Spacer(modifier = Modifier.height(20.dp))

            // Brand Wordmark
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

            // 1. Primary Google Sign-In Button (Standard Flow)
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
                    Spacer(modifier = Modifier.padding(horizontal = 6.dp))
                    Text(
                        text = "Authenticating...",
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

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Secondary Button: Sign In with Email & OTP
            OutlinedButton(
                onClick = {
                    otpInput = ""
                    otpStep = 1
                    showEmailDialog = true
                },
                enabled = !isAuthLoading,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, theme.colors.surfaceBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = theme.colors.textSecondary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = theme.colors.primaryAccent
                    )
                    Text(
                        text = "Sign In with Email / OTP",
                        style = theme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                            color = theme.colors.textSecondary
                        )
                    )
                }
            }

            // Error Message Display / GMS Fallback Hint
            AnimatedVisibility(
                visible = !errorMessage.isNullOrBlank(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val isGmsError = errorMessage?.contains("Credential", ignoreCase = true) == true ||
                        errorMessage?.contains("Google Play", ignoreCase = true) == true

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
                        text = if (isGmsError) {
                            "Google Play Services not found on this emulator.\nTap below to sign in directly with your Gmail account:"
                        } else {
                            errorMessage ?: ""
                        },
                        style = theme.typography.caption.copy(
                            color = theme.colors.errorColor,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isGmsError) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                showEmailDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.colors.primaryAccent,
                                contentColor = theme.colors.onPrimaryAccent
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(
                                text = "Enter Gmail Account",
                                style = theme.typography.caption.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Spacer(modifier = Modifier.height(16.dp))

            // Privacy & Auth guarantee footer
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
                    text = "Encrypted in transit",
                    style = theme.typography.caption.copy(
                        color = theme.colors.textTertiary,
                        fontSize = 11.5.sp
                    )
                )
            }
        }
    }

    // Email / OTP Sign-in Dialog (Clean 2-Step Verification)
    if (showEmailDialog) {
        AlertDialog(
            onDismissRequest = { showEmailDialog = false },
            containerColor = theme.colors.surfaceElevated,
            titleContentColor = theme.colors.textPrimary,
            textContentColor = theme.colors.textSecondary,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = theme.colors.primaryAccent,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = if (otpStep == 1) "Sign In with Email" else "Verify 6-Digit Code",
                        style = theme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (otpStep == 1) {
                        Text(
                            text = "Enter your email address. We will verify your account via OTP. If you are new, you will proceed to choose your username.",
                            style = theme.typography.bodySmall.copy(
                                color = theme.colors.textSecondary,
                                lineHeight = 16.sp
                            )
                        )

                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            label = { Text("Email Address") },
                            placeholder = { Text("you@example.com") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = theme.colors.primaryAccent,
                                unfocusedBorderColor = theme.colors.surfaceBorder,
                                focusedTextColor = theme.colors.textPrimary,
                                unfocusedTextColor = theme.colors.textPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (emailInput.isNotBlank() && !isGmailAddress) {
                            Text(
                                text = "Only @gmail.com addresses are supported.",
                                color = theme.colors.errorColor,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        val displayEmail = emailInput.trim().ifEmpty { "your email" }
                        Text(
                            text = "Enter the 6-digit verification code sent to:\n$displayEmail",
                            style = theme.typography.bodySmall.copy(
                                color = theme.colors.textSecondary,
                                lineHeight = 16.sp
                            )
                        )

                        OutlinedTextField(
                            value = otpInput,
                            onValueChange = { otpInput = it.filter { ch -> ch.isDigit() }.take(6) },
                            label = { Text("6-Digit OTP") },
                            placeholder = { Text("• • • • • •") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = theme.colors.primaryAccent,
                                unfocusedBorderColor = theme.colors.surfaceBorder,
                                focusedTextColor = theme.colors.textPrimary,
                                unfocusedTextColor = theme.colors.textPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        TextButton(
                            onClick = { 
                                otpStep = 1 
                                otpInput = ""
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(text = "← Change Email", color = theme.colors.primaryAccent, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanEmail = emailInput.trim().lowercase()
                        if (otpStep == 1) {
                            isSendingOtp = true
                            otpInput = ""
                            onRequestOtp(cleanEmail) { sent ->
                                isSendingOtp = false
                                if (sent) {
                                    otpInput = ""
                                    otpStep = 2
                                }
                            }
                        } else {
                            val cleanOtp = otpInput.trim()
                            if (cleanOtp.length == 6) {
                                showEmailDialog = false
                                onVerifyOtp(cleanEmail, cleanOtp)
                            }
                        }
                    },
                    enabled = if (otpStep == 1) {
                        !isSendingOtp && emailInput.isNotBlank() && isGmailAddress
                    } else {
                        otpInput.trim().length == 6
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.colors.primaryAccent,
                        disabledContainerColor = theme.colors.surface,
                        disabledContentColor = theme.colors.textTertiary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .border(
                            width = 1.dp,
                            color = theme.colors.surfaceBorder,
                            shape = RoundedCornerShape(10.dp)
                        )
                ) {
                    Text(
                        text = if (otpStep == 1) {
                            if (isSendingOtp) "Sending..." else "Send Code"
                        } else "Verify & Enter",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmailDialog = false }) {
                    Text(
                        text = "Cancel",
                        color = theme.colors.textSecondary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        )
    }
}
