package com.nebula.app.ui.settings

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.nebula.app.data.backend.NebulaApiClient
import com.nebula.app.domain.model.UserProfile
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.NebulaAvatarImage
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val BANNER_PALETTES = com.nebula.app.ui.components.NebulaBannerThemes.ALL.map { it.colors }

/**
 * NebulaAccountDetailScreen:
 * Shows real authenticated account details, session status, and allows logout.
 */
@Composable
fun NebulaAccountDetailScreen(
    userProfile: UserProfile,
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    val scope = rememberCoroutineScope()

    // --- Active Sessions / Devices (Max 2 concurrent) ---
    var activeSessions by remember { mutableStateOf<List<NebulaApiClient.ActiveDeviceSession>>(emptyList()) }
    var isLoadingSessions by remember { mutableStateOf(true) }
    var sessionsError by remember { mutableStateOf<String?>(null) }
    var revokingSessionId by remember { mutableStateOf<String?>(null) }
    var sessionToRevoke by remember { mutableStateOf<NebulaApiClient.ActiveDeviceSession?>(null) }
    var reloadSessionsToken by remember { mutableIntStateOf(0) }
    var isRevokingAllOthers by remember { mutableStateOf(false) }
    var showRevokeOthersConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(reloadSessionsToken) {
        isLoadingSessions = true
        sessionsError = null
        when (val response = NebulaApiClient.getActiveSessions()) {
            is NebulaApiClient.ApiResponse.Success -> {
                activeSessions = response.data
                sessionsError = null
            }
            is NebulaApiClient.ApiResponse.Error -> {
                sessionsError = response.message
            }
        }
        isLoadingSessions = false
    }

    // --- Account deletion (OTP-verified, 30-day grace period) ---
    var isLoadingDeletionStatus by remember { mutableStateOf(true) }
    var deletionStatusError by remember { mutableStateOf<String?>(null) }
    var deletionNotice by remember { mutableStateOf<String?>(null) }
    var isDeletionPending by remember { mutableStateOf(false) }
    var deletionScheduledFor by remember { mutableStateOf<String?>(null) }
    var deletionDaysRemaining by remember { mutableStateOf<Int?>(null) }
    var deletionGraceDays by remember { mutableIntStateOf(DEFAULT_GRACE_DAYS) }
    var reloadDeletionStatusToken by remember { mutableIntStateOf(0) }

    // Flow: IDLE -> CONFIRM_EXPLANATION -> OTP
    var deletionFlowStage by remember { mutableStateOf(DeletionFlowStage.IDLE) }
    var otpInput by remember { mutableStateOf("") }
    var isDeletionSubmitting by remember { mutableStateOf(false) }
    var deletionFlowError by remember { mutableStateOf<String?>(null) }
    var isCancellingDeletion by remember { mutableStateOf(false) }

    // Applies the server's deletion payload ({ success, data: {...} }) to local state.
    val applyDeletionStatus: (JSONObject) -> Unit = { envelope ->
        val status = parseDeletionStatus(envelope)
        isDeletionPending = status.pending
        deletionScheduledFor = status.scheduledFor
        deletionDaysRemaining = status.daysRemaining
        deletionGraceDays = status.graceDays
    }

    LaunchedEffect(reloadDeletionStatusToken) {
        isLoadingDeletionStatus = true
        deletionStatusError = null
        when (val response = NebulaApiClient.getAccountDeletionStatus()) {
            is NebulaApiClient.ApiResponse.Success -> {
                applyDeletionStatus(response.data)
                deletionStatusError = null
            }
            is NebulaApiClient.ApiResponse.Error -> {
                deletionStatusError = response.message
            }
        }
        isLoadingDeletionStatus = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.colors.background)
            .statusBarsPadding()
    ) {
        // TOP APP BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = theme.colors.textPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Text(
                text = "Account",
                style = theme.typography.titleMedium.copy(
                    color = theme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Identity Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(theme.colors.surface)
                        .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(16.dp))
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Profile Banner Preview in Account
                    com.nebula.app.ui.components.NebulaBannerView(
                        bannerUrl = userProfile.bannerUrl,
                        bannerIndex = userProfile.bannerIndex,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp),
                        shape = RoundedCornerShape(12.dp),
                        borderColor = theme.colors.surfaceBorder
                    )

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(theme.colors.surfaceElevated)
                            .border(2.dp, theme.colors.primaryAccent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        NebulaAvatarImage(
                            avatarUrl = userProfile.avatarUri,
                            fallbackLetter = userProfile.avatarFallbackLetter,
                            modifier = Modifier.fillMaxSize(),
                            fallbackTextSize = 28.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = userProfile.displayName,
                            style = theme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = theme.colors.textPrimary,
                                fontSize = 17.sp
                            )
                        )
                        Text(
                            text = userProfile.username ?: "@nebula_user",
                            style = theme.typography.caption.copy(
                                color = theme.colors.primaryAccent,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        )
                    }

                    Button(
                        onClick = onEditProfile,
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.colors.surfaceElevated,
                            contentColor = theme.colors.textPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Edit Profile",
                                style = theme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }

            // Account Details
            item {
                val displayEmail = run {
                    val raw = userProfile.email
                    when {
                        raw.isNullOrBlank() -> "Connected via Google"
                        raw.contains("@nebula.local") -> "Connected via Google"
                        raw.contains("@") -> raw
                        else -> "Connected via Google"
                    }
                }
                val displayAccountId = userProfile.username?.takeIf { it.isNotBlank() }
                    ?: userProfile.id.takeIf { it.isNotBlank() && !it.startsWith("usr_dev") }
                    ?: "nebula_user"

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(theme.colors.surface)
                        .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(16.dp))
                ) {
                    AccountDetailRow("Email", displayEmail)
                    AccountDivider()
                    AccountDetailRow("Account", displayAccountId)
                    AccountDivider()
                    AccountDetailRow("Tier", userProfile.subscriptionTier.title)
                    AccountDivider()
                    AccountDetailRow("Banner", if (!userProfile.bannerUrl.isNullOrBlank()) "Custom Media" else "Palette ${userProfile.bannerIndex + 1}")
                    AccountDivider()
                    AccountDetailRow("Status", userProfile.status)
                }
            }

            // Active Devices & Sessions (Max 2 concurrent)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            // The server returns one row per device, so this is the real
                            // count. It used to be inflated by duplicate rows for the same
                            // install, which read as 2/2 on a single phone.
                            text = "Active Devices (${activeSessions.size.coerceAtMost(MAX_ACTIVE_DEVICES)}/$MAX_ACTIVE_DEVICES)",
                            style = theme.typography.bodyMedium.copy(
                                color = theme.colors.textSecondary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        )
                        IconButton(
                            onClick = { reloadSessionsToken++ },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh devices",
                                tint = theme.colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(theme.colors.surface)
                            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(16.dp))
                    ) {
                        when {
                            isLoadingSessions -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = theme.colors.primaryAccent,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Checking active devices…",
                                        style = theme.typography.bodyMedium.copy(
                                            color = theme.colors.textSecondary,
                                            fontSize = 14.sp
                                        )
                                    )
                                }
                            }
                            sessionsError != null -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = sessionsError.orEmpty(),
                                        style = theme.typography.bodySmall.copy(
                                            color = theme.colors.errorColor,
                                            fontSize = 12.5.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    TextButton(
                                        onClick = { reloadSessionsToken++ },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(
                                            text = "Retry",
                                            style = theme.typography.button.copy(
                                                color = theme.colors.primaryAccent
                                            )
                                        )
                                    }
                                }
                            }
                            else -> {
                                val current = activeSessions.firstOrNull { it.isCurrent }
                                val others = activeSessions.filterNot { it.isCurrent }

                                // 1. Primary "This Device" Card
                                CurrentDeviceCard(
                                    session = current,
                                    userDeviceName = android.os.Build.MODEL
                                )

                                // 2. Other Signed-in Devices Section
                                if (others.isNotEmpty()) {
                                    AccountDivider()
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "OTHER SIGNED-IN DEVICES (${others.size})",
                                            style = theme.typography.caption.copy(
                                                color = theme.colors.textTertiary,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.8.sp,
                                                fontSize = 10.5.sp
                                            )
                                        )

                                        others.forEach { otherSession ->
                                            OtherSessionRow(
                                                session = otherSession,
                                                isRevoking = revokingSessionId == otherSession.id,
                                                onRevoke = { sessionToRevoke = otherSession }
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))

                                        Button(
                                            onClick = { showRevokeOthersConfirmDialog = true },
                                            enabled = !isRevokingAllOthers,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(38.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = theme.colors.errorColor.copy(alpha = 0.12f),
                                                contentColor = theme.colors.errorColor
                                            ),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            if (isRevokingAllOthers) {
                                                CircularProgressIndicator(
                                                    color = theme.colors.errorColor,
                                                    strokeWidth = 2.dp,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            } else {
                                                Text(
                                                    text = "Log Out of All Other Devices",
                                                    style = theme.typography.bodySmall.copy(
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 12.5.sp
                                                    )
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    AccountDivider()
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color(0xFF00C853),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "No other devices. Your account is only signed in on this phone.",
                                            style = theme.typography.caption.copy(
                                                color = theme.colors.textSecondary,
                                                fontSize = 12.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        AccountDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = theme.colors.primaryAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Strict Limit: Max 2 concurrent devices per account. Only 1 device may join a watch room at a time.",
                                style = theme.typography.caption.copy(
                                    color = theme.colors.textSecondary.copy(alpha = 0.85f),
                                    fontSize = 11.5.sp,
                                    lineHeight = 15.sp
                                )
                            )
                        }
                    }
                }
            }

            // Account Deletion
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Account deletion",
                        style = theme.typography.bodyMedium.copy(
                            color = theme.colors.textSecondary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(theme.colors.surface)
                            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(16.dp))
                    ) {
                        when {
                            isLoadingDeletionStatus -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = theme.colors.primaryAccent,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Checking deletion status…",
                                        style = theme.typography.bodyMedium.copy(
                                            color = theme.colors.textSecondary,
                                            fontSize = 14.sp
                                        )
                                    )
                                }
                            }

                            deletionStatusError != null -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = deletionStatusError.orEmpty(),
                                        style = theme.typography.bodySmall.copy(
                                            color = theme.colors.errorColor,
                                            fontSize = 12.5.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    TextButton(
                                        onClick = { reloadDeletionStatusToken++ },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(
                                            text = "Retry",
                                            style = theme.typography.button.copy(
                                                color = theme.colors.primaryAccent
                                            )
                                        )
                                    }
                                }
                            }

                            isDeletionPending -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = deletionPendingSummary(
                                            deletionScheduledFor,
                                            deletionDaysRemaining
                                        ),
                                        style = theme.typography.bodyMedium.copy(
                                            color = theme.colors.textPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.5.sp
                                        )
                                    )
                                    Text(
                                        text = "Signing in before that date also cancels the deletion. You can cancel it right now instead.",
                                        style = theme.typography.bodySmall.copy(
                                            color = theme.colors.textSecondary,
                                            lineHeight = 16.sp
                                        )
                                    )
                                    if (deletionNotice != null) {
                                        Text(
                                            text = deletionNotice.orEmpty(),
                                            style = theme.typography.bodySmall.copy(
                                                color = theme.colors.successColor,
                                                fontSize = 12.5.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            if (isCancellingDeletion) return@Button
                                            isCancellingDeletion = true
                                            deletionStatusError = null
                                            deletionNotice = null
                                            scope.launch {
                                                when (val response = NebulaApiClient.cancelAccountDeletion()) {
                                                    is NebulaApiClient.ApiResponse.Success -> {
                                                        applyDeletionStatus(response.data)
                                                        if (!isDeletionPending) {
                                                            deletionNotice = "Account deletion cancelled."
                                                        }
                                                    }
                                                    is NebulaApiClient.ApiResponse.Error -> {
                                                        deletionStatusError = response.message
                                                    }
                                                }
                                                isCancellingDeletion = false
                                            }
                                        },
                                        enabled = !isCancellingDeletion,
                                        modifier = Modifier.fillMaxWidth().height(44.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = theme.colors.primaryAccent,
                                            contentColor = theme.colors.onPrimaryAccent
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = if (isCancellingDeletion) "Cancelling…" else "Cancel deletion",
                                            style = theme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }

                            else -> {
                                DeletionActionRow(
                                    icon = Icons.Default.DeleteOutline,
                                    title = "Delete account",
                                    subtitle = "Deletes your account after a ${deletionGraceDays}-day grace period",
                                    onClick = {
                                        deletionFlowError = null
                                        otpInput = ""
                                        deletionFlowStage = DeletionFlowStage.CONFIRM_EXPLANATION
                                    }
                                )
                                if (deletionNotice != null) {
                                    AccountDivider()
                                    Text(
                                        text = deletionNotice.orEmpty(),
                                        style = theme.typography.bodySmall.copy(
                                            color = theme.colors.successColor,
                                            fontSize = 12.5.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Sign Out
            item {
                Button(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.colors.errorColor.copy(alpha = 0.15f),
                        contentColor = theme.colors.errorColor
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Log Out",
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Log Out of Nebula",
                            style = theme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }

    // --- Deletion flow: step 1, explanation + server request ---
    if (deletionFlowStage == DeletionFlowStage.CONFIRM_EXPLANATION) {
        AlertDialog(
            onDismissRequest = {
                if (!isDeletionSubmitting) {
                    deletionFlowStage = DeletionFlowStage.IDLE
                    deletionFlowError = null
                }
            },
            containerColor = theme.colors.surfaceElevated,
            titleContentColor = theme.colors.textPrimary,
            textContentColor = theme.colors.textSecondary,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = theme.colors.errorColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Delete account",
                        style = theme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Your account will be deleted after a ${deletionGraceDays}-day grace period. Until then your account keeps working normally, and signing in during that period cancels the deletion.",
                        style = theme.typography.bodySmall.copy(
                            color = theme.colors.textSecondary,
                            lineHeight = 17.sp
                        )
                    )
                    Text(
                        text = "After the grace period your profile, messages, and data are permanently removed.",
                        style = theme.typography.bodySmall.copy(
                            color = theme.colors.textSecondary,
                            lineHeight = 17.sp
                        )
                    )
                    if (deletionFlowError != null) {
                        Text(
                            text = deletionFlowError.orEmpty(),
                            style = theme.typography.bodySmall.copy(
                                color = theme.colors.errorColor,
                                fontSize = 12.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isDeletionSubmitting) return@Button
                        isDeletionSubmitting = true
                        deletionFlowError = null
                        scope.launch {
                            when (val response = NebulaApiClient.requestAccountDeletion()) {
                                is NebulaApiClient.ApiResponse.Success -> {
                                    val data = response.data.optJSONObject("data") ?: response.data
                                    val devCode = data.optString("devCode").ifBlank { data.optString("code") }
                                    if (devCode.isNotBlank()) {
                                        otpInput = devCode
                                    } else {
                                        otpInput = ""
                                    }
                                    deletionFlowStage = DeletionFlowStage.OTP
                                }
                                is NebulaApiClient.ApiResponse.Error -> {
                                    deletionFlowError = response.message
                                }
                            }
                            isDeletionSubmitting = false
                        }
                    },
                    enabled = !isDeletionSubmitting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.colors.errorColor.copy(alpha = 0.15f),
                        contentColor = theme.colors.errorColor,
                        disabledContainerColor = theme.colors.surface,
                        disabledContentColor = theme.colors.textTertiary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (isDeletionSubmitting) "Sending code…" else "Continue",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        deletionFlowStage = DeletionFlowStage.IDLE
                        deletionFlowError = null
                    }
                ) {
                    Text(text = "Cancel", color = theme.colors.textSecondary)
                }
            }
        )
    }

    // --- Deletion flow: step 2, 6-digit OTP confirmation ---
    if (deletionFlowStage == DeletionFlowStage.OTP) {
        val isOtpValid = otpInput.length == 6 && otpInput.all { it.isDigit() }
        AlertDialog(
            onDismissRequest = {
                if (!isDeletionSubmitting) {
                    deletionFlowStage = DeletionFlowStage.IDLE
                    deletionFlowError = null
                }
            },
            containerColor = theme.colors.surfaceElevated,
            titleContentColor = theme.colors.textPrimary,
            textContentColor = theme.colors.textSecondary,
            title = {
                Text(
                    text = "Verify deletion",
                    style = theme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter the 6-digit code we sent to your account to schedule deletion. Signing in during the grace period cancels it.",
                        style = theme.typography.bodySmall.copy(
                            color = theme.colors.textSecondary,
                            lineHeight = 17.sp
                        )
                    )

                    OutlinedTextField(
                        value = otpInput,
                        onValueChange = { value -> otpInput = value.filter { it.isDigit() }.take(6) },
                        label = { Text("6-Digit OTP") },
                        placeholder = { Text("• • • • • •") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.colors.primaryAccent,
                            unfocusedBorderColor = theme.colors.surfaceBorder,
                            focusedTextColor = theme.colors.textPrimary,
                            unfocusedTextColor = theme.colors.textPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (deletionFlowError != null) {
                        Text(
                            text = deletionFlowError.orEmpty(),
                            style = theme.typography.bodySmall.copy(
                                color = theme.colors.errorColor,
                                fontSize = 12.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isDeletionSubmitting || !isOtpValid) return@Button
                        isDeletionSubmitting = true
                        deletionFlowError = null
                        scope.launch {
                            when (val response = NebulaApiClient.confirmAccountDeletion(otpInput.trim())) {
                                is NebulaApiClient.ApiResponse.Success -> {
                                    applyDeletionStatus(response.data)
                                    deletionNotice = "Deletion scheduled."
                                    deletionStatusError = null
                                    deletionFlowStage = DeletionFlowStage.IDLE
                                    otpInput = ""
                                }
                                is NebulaApiClient.ApiResponse.Error -> {
                                    deletionFlowError = response.message
                                }
                            }
                            isDeletionSubmitting = false
                        }
                    },
                    enabled = isOtpValid && !isDeletionSubmitting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.colors.errorColor.copy(alpha = 0.15f),
                        contentColor = theme.colors.errorColor,
                        disabledContainerColor = theme.colors.surface,
                        disabledContentColor = theme.colors.textTertiary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (isDeletionSubmitting) "Confirming…" else "Confirm deletion",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        deletionFlowStage = DeletionFlowStage.IDLE
                        deletionFlowError = null
                        otpInput = ""
                    }
                ) {
                    Text(text = "Cancel", color = theme.colors.textSecondary)
                }
            }
        )
    }

    if (sessionToRevoke != null) {
        val target = sessionToRevoke!!
        AlertDialog(
            onDismissRequest = {
                if (revokingSessionId == null) {
                    sessionToRevoke = null
                }
            },
            containerColor = theme.colors.surfaceElevated,
            titleContentColor = theme.colors.textPrimary,
            textContentColor = theme.colors.textSecondary,
            title = {
                Text(
                    text = if (target.isCurrent) "Log out of this device?" else "Log out device?",
                    style = theme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = if (target.isCurrent)
                        "Are you sure you want to log out of this device? You will be signed out immediately."
                    else
                        "Are you sure you want to log out ${target.deviceInfo}? This device will be disconnected and will need to log in again.",
                    style = theme.typography.bodyMedium.copy(
                        color = theme.colors.textSecondary,
                        lineHeight = 18.sp
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toRevoke = target
                        sessionToRevoke = null
                        if (toRevoke.isCurrent) {
                            onSignOut()
                        } else {
                            revokingSessionId = toRevoke.id
                            activeSessions = activeSessions.filterNot { it.id == toRevoke.id }
                            scope.launch {
                                when (val res = NebulaApiClient.revokeSession(toRevoke.id)) {
                                    is NebulaApiClient.ApiResponse.Success -> {
                                        reloadSessionsToken++
                                    }
                                    is NebulaApiClient.ApiResponse.Error -> {
                                        sessionsError = res.message
                                        reloadSessionsToken++
                                    }
                                }
                                revokingSessionId = null
                            }
                        }
                    },
                    enabled = revokingSessionId == null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.colors.errorColor,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Log Out", style = theme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { sessionToRevoke = null },
                    enabled = revokingSessionId == null
                ) {
                    Text("Cancel", style = theme.typography.bodyMedium.copy(color = theme.colors.textSecondary))
                }
            }
        )
    }

    if (showRevokeOthersConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isRevokingAllOthers) showRevokeOthersConfirmDialog = false
            },
            containerColor = theme.colors.surfaceElevated,
            titleContentColor = theme.colors.textPrimary,
            textContentColor = theme.colors.textSecondary,
            title = {
                Text("Log out other devices?", style = theme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            },
            text = {
                Text(
                    text = "Are you sure you want to log out all other devices? Any other phones or browsers will be disconnected immediately.",
                    style = theme.typography.bodyMedium.copy(
                        color = theme.colors.textSecondary,
                        lineHeight = 18.sp
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRevokeOthersConfirmDialog = false
                        isRevokingAllOthers = true
                        scope.launch {
                            when (val res = NebulaApiClient.revokeOtherSessions()) {
                                is NebulaApiClient.ApiResponse.Success -> {
                                    reloadSessionsToken++
                                }
                                is NebulaApiClient.ApiResponse.Error -> {
                                    sessionsError = res.message
                                    reloadSessionsToken++
                                }
                            }
                            isRevokingAllOthers = false
                        }
                    },
                    enabled = !isRevokingAllOthers,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.colors.errorColor,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Log Out All Others", style = theme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRevokeOthersConfirmDialog = false },
                    enabled = !isRevokingAllOthers
                ) {
                    Text("Cancel", style = theme.typography.bodyMedium.copy(color = theme.colors.textSecondary))
                }
            }
        )
    }
}

@Composable
private fun DeletionActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val theme = Theme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(theme.colors.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = theme.colors.errorColor,
                modifier = Modifier.size(19.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = theme.typography.bodyMedium.copy(
                    color = theme.colors.errorColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            )
            Text(
                text = subtitle,
                style = theme.typography.caption.copy(
                    color = theme.colors.textSecondary,
                    fontSize = 12.sp
                )
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = theme.colors.textTertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}

private const val DEFAULT_GRACE_DAYS = 30

/** Mirrors the server's MAX_ACTIVE_DEVICES; used for the "x / 2" label only. */
private const val MAX_ACTIVE_DEVICES = 2

private enum class DeletionFlowStage { IDLE, CONFIRM_EXPLANATION, OTP }

private data class DeletionStatus(
    val pending: Boolean,
    val scheduledFor: String?,
    val daysRemaining: Int?,
    val graceDays: Int
)

private fun parseDeletionStatus(envelope: JSONObject): DeletionStatus {
    val data = envelope.optJSONObject("data") ?: envelope
    val rawScheduledFor = data.optString("deletionScheduledFor")
        .takeIf { it.isNotBlank() && it != "null" }
    val daysRemaining = if (data.has("daysRemaining") && !data.isNull("daysRemaining")) {
        data.optInt("daysRemaining")
    } else null
    val graceDays = data.optInt("graceDays", DEFAULT_GRACE_DAYS).takeIf { it > 0 } ?: DEFAULT_GRACE_DAYS
    return DeletionStatus(
        pending = data.optBoolean("pending", false),
        scheduledFor = rawScheduledFor,
        daysRemaining = daysRemaining,
        graceDays = graceDays
    )
}

private fun deletionPendingSummary(scheduledFor: String?, daysRemaining: Int?): String {
    val date = formatDeletionDate(scheduledFor)
    return when {
        date != null && daysRemaining != null ->
            "Deletion scheduled for $date — $daysRemaining day${if (daysRemaining == 1) "" else "s"} remaining"
        date != null -> "Deletion scheduled for $date"
        daysRemaining != null ->
            "Deletion scheduled — $daysRemaining day${if (daysRemaining == 1) "" else "s"} remaining"
        else -> "Deletion scheduled"
    }
}

private fun formatDeletionDate(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd"
    )
    for (pattern in patterns) {
        try {
            val parser = SimpleDateFormat(pattern, Locale.US)
            parser.timeZone = TimeZone.getTimeZone("UTC")
            val parsed = parser.parse(raw) ?: continue
            return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(parsed)
        } catch (_: Exception) {
            // Try the next pattern.
        }
    }
    return raw.take(10)
}

@Composable
private fun AccountDetailRow(label: String, value: String) {
    val theme = Theme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = theme.typography.bodyMedium.copy(
                color = theme.colors.textSecondary,
                fontSize = 14.sp
            )
        )
        Text(
            text = value,
            style = theme.typography.bodyMedium.copy(
                color = theme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        )
    }
}

@Composable
private fun AccountDivider() {
    val theme = Theme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(theme.colors.surfaceBorder)
    )
}

@Composable
private fun CurrentDeviceCard(
    session: NebulaApiClient.ActiveDeviceSession?,
    userDeviceName: String
) {
    val theme = Theme.current
    val deviceModel = session?.deviceInfo?.takeIf { it.isNotBlank() } ?: userDeviceName.ifBlank { "Android Device" }
    val ip = session?.ipAddress?.takeIf { it.isNotBlank() } ?: "Local"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(theme.colors.primaryAccent.copy(alpha = 0.15f))
                .border(1.5.dp, theme.colors.primaryAccent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PhoneAndroid,
                contentDescription = null,
                tint = theme.colors.primaryAccent,
                modifier = Modifier.size(22.dp)
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = deviceModel,
                    style = theme.typography.bodyMedium.copy(
                        color = theme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF00C853).copy(alpha = 0.15f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "This Device",
                        style = theme.typography.caption.copy(
                            color = Color(0xFF00C853),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp
                        )
                    )
                }
            }

            Text(
                text = "$ip • Active now",
                style = theme.typography.caption.copy(
                    color = theme.colors.textSecondary,
                    fontSize = 12.sp
                )
            )
        }
    }
}

@Composable
private fun OtherSessionRow(
    session: NebulaApiClient.ActiveDeviceSession,
    isRevoking: Boolean,
    onRevoke: () -> Unit
) {
    val theme = Theme.current
    val deviceModel = session.deviceInfo.ifBlank { "Other Device" }
    val ip = session.ipAddress.ifBlank { "Unknown IP" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(theme.colors.surfaceElevated)
            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(theme.colors.surface)
                .border(1.dp, theme.colors.surfaceBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PhoneAndroid,
                contentDescription = null,
                tint = theme.colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = deviceModel,
                style = theme.typography.bodySmall.copy(
                    color = theme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "$ip • ${formatSessionTime(session.lastActiveAt)}",
                style = theme.typography.caption.copy(
                    color = theme.colors.textTertiary,
                    fontSize = 11.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isRevoking) {
            CircularProgressIndicator(
                color = theme.colors.errorColor,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp)
            )
        } else {
            IconButton(
                onClick = onRevoke,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Revoke session",
                    tint = theme.colors.errorColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun formatSessionTime(raw: String?): String {
    if (raw.isNullOrBlank()) return "Recently"
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ssXXX"
    )
    for (pattern in patterns) {
        try {
            val parser = SimpleDateFormat(pattern, Locale.US)
            parser.timeZone = TimeZone.getTimeZone("UTC")
            val date = parser.parse(raw) ?: continue
            val now = System.currentTimeMillis()
            val diff = now - date.time
            return when {
                diff < 60_000L -> "Just now"
                diff < 3600_000L -> "${diff / 60_000L}m ago"
                diff < 86400_000L -> "${diff / 3600_000L}h ago"
                else -> SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(date)
            }
        } catch (_: Exception) {}
    }
    return raw.take(10)
}
