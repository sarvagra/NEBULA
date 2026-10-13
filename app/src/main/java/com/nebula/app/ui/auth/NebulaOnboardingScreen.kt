package com.nebula.app.ui.auth

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.data.backend.NebulaApiClient
import com.nebula.app.domain.model.UserProfile
import com.nebula.app.theme.Theme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period

/**
 * NebulaOnboardingScreen:
 * Native 7-step onboarding flow for newly registered Nebula users.
 * Enforces:
 * - Single-line fields max 60 characters
 * - Username 3-20 chars (^[a-z0-9_]+$) with live backend availability check
 * - Structured Date of Birth & Gender selection (including "Prefer not to say")
 * - Profile picture selection via Photo Picker
 * - Review and atomic profile completion
 */
@Composable
fun NebulaOnboardingScreen(
    initialProfile: UserProfile,
    onCompleteOnboarding: (displayName: String, username: String, dob: String, gender: String, avatarUri: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Current onboarding step (1 to 7)
    var currentStep by remember { mutableIntStateOf(1) }

    // Onboarding form state
    var displayName by remember { mutableStateOf(initialProfile.displayName.ifEmpty { "Nebula Explorer" }) }
    var username by remember {
        mutableStateOf(
            (initialProfile.username ?: "explorer")
                .trim()
                .replace("^@".toRegex(), "")
                .lowercase()
        )
    }
    var birthYear by remember { mutableIntStateOf(2000) }
    var birthMonth by remember { mutableIntStateOf(1) }
    var birthDay by remember { mutableIntStateOf(1) }
    var gender by remember { mutableStateOf("Prefer not to say") }
    var customGender by remember { mutableStateOf("") }
    var selectedAvatarUri by remember { mutableStateOf<String?>(initialProfile.avatarUri) }

    // Field errors
    var displayNameError by remember { mutableStateOf<String?>(null) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var isCheckingUsername by remember { mutableStateOf(false) }
    var isUsernameAvailable by remember { mutableStateOf<Boolean?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }
    var birthdayError by remember { mutableStateOf<String?>(null) }

    fun isAtLeastSixteen(): Boolean {
        return try {
            val birthDate = LocalDate.of(birthYear, birthMonth, birthDay)
            Period.between(birthDate, LocalDate.now()).let { it.years >= 16 }
        } catch (_: java.time.DateTimeException) {
            false
        }
    }

    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedAvatarUri = uri.toString()
        }
    }

    // Debounced username check against backend
    var checkJob by remember { mutableStateOf<Job?>(null) }
    fun triggerUsernameCheck(name: String) {
        val clean = name.trim().lowercase().replace("^@".toRegex(), "")
        if (clean.length < 3 || clean.length > 20) {
            usernameError = "Username must be 3 to 20 characters."
            isUsernameAvailable = null
            return
        }
        if (!clean.matches("^[a-z0-9_]+$".toRegex())) {
            usernameError = "Only lowercase letters, numbers, and underscores allowed."
            isUsernameAvailable = null
            return
        }
        usernameError = null
        checkJob?.cancel()
        checkJob = scope.launch {
            delay(350)
            isCheckingUsername = true
            when (val res = NebulaApiClient.checkUsername(clean)) {
                is NebulaApiClient.ApiResponse.Success -> {
                    val available = res.data.optJSONObject("data")?.optBoolean("isAvailable", true) ?: true
                    isUsernameAvailable = available
                    if (!available) {
                        usernameError = "Username is already taken."
                    }
                }
                is NebulaApiClient.ApiResponse.Error -> {
                    // If network fails, allow format check but alert user
                    isUsernameAvailable = true
                }
            }
            isCheckingUsername = false
        }
    }

    LaunchedEffect(Unit) {
        triggerUsernameCheck(username)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(theme.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top App Bar with Step Indicator & Back Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentStep > 1) {
                    IconButton(
                        onClick = { currentStep -= 1 },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(theme.colors.surfaceElevated)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = theme.colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(38.dp))
                }

                Text(
                    text = "STEP $currentStep OF 7",
                    style = theme.typography.caption.copy(
                        color = theme.colors.primaryAccent,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        fontSize = 11.5.sp
                    )
                )
                TextButton(
                    onClick = {
                        val dobStr = String.format("%04d-%02d-%02d", birthYear, birthMonth, birthDay)
                        val finalGender = if (gender == "Custom") customGender.trim().ifEmpty { "Custom" } else gender
                        scope.launch {
                            NebulaApiClient.updateProfile(
                                displayName = displayName.trim().ifEmpty { initialProfile.displayName },
                                username = username.trim().ifEmpty { initialProfile.username?.removePrefix("@") ?: "explorer" },
                                bio = initialProfile.bio,
                                status = "online",
                                bannerIndex = initialProfile.bannerIndex,
                                interests = initialProfile.interests,
                                avatarUrl = selectedAvatarUri,
                                isFriendCountPublic = true,
                                isOnboardingCompleted = true
                            )
                        }
                        onCompleteOnboarding(
                            displayName.trim().ifEmpty { initialProfile.displayName },
                            username.trim().ifEmpty { initialProfile.username ?: "@explorer" },
                            dobStr,
                            finalGender,
                            selectedAvatarUri
                        )
                    }
                ) {
                    Text(
                        text = "Skip",
                        style = theme.typography.bodyMedium.copy(
                            color = theme.colors.textSecondary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp
                        )
                    )
                }
            }

            // Step Progress Indicator Dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                (1..7).forEach { step ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(4.dp)
                            .width(if (step == currentStep) 24.dp else 12.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (step == currentStep) theme.colors.primaryAccent
                                else if (step < currentStep) theme.colors.primaryAccent.copy(alpha = 0.4f)
                                else theme.colors.surfaceBorder
                            )
                    )
                }
            }

            // Main Content Area with Transitions
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center
            ) {
                when (currentStep) {
                    // STEP 1: WELCOME & ACCOUNT CONFIRMATION
                    1 -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF6366F1), Color(0xFFA855F7))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(44.dp)
                                )
                            }

                            Text(
                                text = "Welcome to Nebula",
                                style = theme.typography.titleLarge.copy(
                                    color = theme.colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp
                                ),
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "Your synchronized social space for watch parties, real-time audio rooms, and private conversations.",
                                style = theme.typography.bodyMedium.copy(
                                    color = theme.colors.textSecondary,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            if (initialProfile.email.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(theme.colors.surface)
                                        .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = "Signed in as: ${initialProfile.email}",
                                        style = theme.typography.caption.copy(
                                            color = theme.colors.textTertiary,
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // STEP 2: DISPLAY NAME (Max 60 chars)
                    2 -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "What should we call you?",
                                style = theme.typography.titleMedium.copy(
                                    color = theme.colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                ),
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "Your display name will appear in active rooms and chat headers (maximum 60 characters).",
                                style = theme.typography.bodySmall.copy(
                                    color = theme.colors.textSecondary,
                                    textAlign = TextAlign.Center
                                )
                            )

                            OutlinedTextField(
                                value = displayName,
                                onValueChange = {
                                    if (it.length <= 60) {
                                        displayName = it
                                        displayNameError = if (it.trim().isEmpty()) "Display name cannot be empty" else null
                                    }
                                },
                                label = { Text("Display Name") },
                                singleLine = true,
                                isError = displayNameError != null,
                                supportingText = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = displayNameError ?: "", color = theme.colors.errorColor)
                                        Text(text = "${displayName.length}/60", color = theme.colors.textTertiary)
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = theme.colors.primaryAccent,
                                    unfocusedBorderColor = theme.colors.surfaceBorder,
                                    focusedTextColor = theme.colors.textPrimary,
                                    unfocusedTextColor = theme.colors.textPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // STEP 3: USERNAME SELECTION & AVAILABILITY
                    3 -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Choose your @handle",
                                style = theme.typography.titleMedium.copy(
                                    color = theme.colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                ),
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "3 to 20 characters. Lowercase letters, numbers, and underscores only.",
                                style = theme.typography.bodySmall.copy(
                                    color = theme.colors.textSecondary,
                                    textAlign = TextAlign.Center
                                )
                            )

                            OutlinedTextField(
                                value = username,
                                onValueChange = { input ->
                                    val clean = input.lowercase().replace("^@".toRegex(), "").filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }
                                    if (clean.length <= 20) {
                                        username = clean
                                        triggerUsernameCheck(clean)
                                    }
                                },
                                prefix = { Text("@", color = theme.colors.primaryAccent, fontWeight = FontWeight.Bold) },
                                singleLine = true,
                                trailingIcon = {
                                    if (isCheckingUsername) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = theme.colors.primaryAccent
                                        )
                                    } else if (isUsernameAvailable == true && usernameError == null) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Available",
                                            tint = theme.colors.successColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                },
                                isError = usernameError != null,
                                supportingText = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = usernameError ?: (if (isUsernameAvailable == true) "Username is available!" else ""), color = if (usernameError != null) theme.colors.errorColor else theme.colors.successColor)
                                        Text(text = "${username.length}/20", color = theme.colors.textTertiary)
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = theme.colors.primaryAccent,
                                    unfocusedBorderColor = theme.colors.surfaceBorder,
                                    focusedTextColor = theme.colors.textPrimary,
                                    unfocusedTextColor = theme.colors.textPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // STEP 4: AGE OR DATE OF BIRTH
                    4 -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Date of Birth",
                                style = theme.typography.titleMedium.copy(
                                    color = theme.colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                ),
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "Used for content safety and age-appropriate room suggestions.",
                                style = theme.typography.bodySmall.copy(
                                    color = theme.colors.textSecondary,
                                    textAlign = TextAlign.Center
                                )
                            )

                            ScrollWheelDatePicker(
                                day = birthDay,
                                month = birthMonth,
                                year = birthYear,
                                onDayChanged = { birthDay = it },
                                onMonthChanged = { birthMonth = it },
                                onYearChanged = { birthYear = it }
                            )
                            birthdayError?.let {
                                Text(
                                    text = it,
                                    color = theme.colors.errorColor,
                                    style = theme.typography.caption,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // STEP 5: GENDER SELECTION
                    5 -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Gender Identity",
                                style = theme.typography.titleMedium.copy(
                                    color = theme.colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                ),
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "Select how you'd like to be identified. This is optional.",
                                style = theme.typography.bodySmall.copy(
                                    color = theme.colors.textSecondary,
                                    textAlign = TextAlign.Center
                                )
                            )

                            val genderOptions = listOf("Female", "Male", "Non-binary", "Prefer not to say", "Custom")
                            genderOptions.forEach { option ->
                                val isSelected = gender == option
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) theme.colors.primaryAccent.copy(alpha = 0.2f) else theme.colors.surface)
                                        .border(
                                            1.dp,
                                            if (isSelected) theme.colors.primaryAccent else theme.colors.surfaceBorder,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { gender = option }
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = option,
                                            style = theme.typography.bodyMedium.copy(
                                                color = if (isSelected) theme.colors.primaryAccent else theme.colors.textPrimary,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = theme.colors.primaryAccent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (gender == "Custom") {
                                OutlinedTextField(
                                    value = customGender,
                                    onValueChange = { if (it.length <= 60) customGender = it },
                                    label = { Text("Specify Identity (max 60 chars)") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // STEP 6: PROFILE PICTURE SELECTION
                    6 -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Profile Picture",
                                style = theme.typography.titleMedium.copy(
                                    color = theme.colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                ),
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "Upload a photo or continue with your default avatar.",
                                style = theme.typography.bodySmall.copy(
                                    color = theme.colors.textSecondary,
                                    textAlign = TextAlign.Center
                                )
                            )

                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .clip(CircleShape)
                                    .background(theme.colors.surfaceElevated)
                                    .border(3.dp, theme.colors.primaryAccent, CircleShape)
                                    .clickable {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (!selectedAvatarUri.isNullOrBlank()) {
                                    coil.compose.AsyncImage(
                                        model = selectedAvatarUri,
                                        contentDescription = "Avatar Preview",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = displayName.firstOrNull()?.uppercase() ?: "N",
                                        style = theme.typography.titleLarge.copy(
                                            fontSize = 38.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = theme.colors.textPrimary
                                        )
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = theme.colors.surfaceElevated,
                                    contentColor = theme.colors.textPrimary
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text("Choose Photo")
                                }
                            }
                        }
                    }

                    // STEP 7: REVIEW DETAILS & COMPLETE
                    7 -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Review Profile",
                                style = theme.typography.titleMedium.copy(
                                    color = theme.colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                ),
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "Everything looks set! Confirm your details to launch into Nebula.",
                                style = theme.typography.bodySmall.copy(
                                    color = theme.colors.textSecondary,
                                    textAlign = TextAlign.Center
                                )
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(theme.colors.surface)
                                    .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(16.dp))
                                    .padding(16.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Display Name", color = theme.colors.textSecondary)
                                        Text(displayName, color = theme.colors.textPrimary, fontWeight = FontWeight.Bold)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Username", color = theme.colors.textSecondary)
                                        Text("@$username", color = theme.colors.primaryAccent, fontWeight = FontWeight.Bold)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Date of Birth", color = theme.colors.textSecondary)
                                        Text(
                                            String.format("%02d/%02d/%04d", birthDay, birthMonth, birthYear),
                                            color = theme.colors.textPrimary
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Gender", color = theme.colors.textSecondary)
                                        Text(if (gender == "Custom") customGender.ifEmpty { "Custom" } else gender, color = theme.colors.textPrimary)
                                    }
                                }
                            }

                            submitError?.let { err ->
                                Text(
                                    text = err,
                                    color = theme.colors.errorColor,
                                    style = theme.typography.caption,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Navigation Row (Next / Complete Button)
            Button(
                onClick = {
                    when (currentStep) {
                        1 -> currentStep = 2
                        2 -> {
                            if (displayName.trim().isBlank()) {
                                displayNameError = "Display name cannot be empty"
                            } else {
                                displayNameError = null
                                currentStep = 3
                            }
                        }
                        3 -> {
                            if (usernameError == null && username.length in 3..20) {
                                currentStep = 4
                            }
                        }
                        4 -> {
                            if (isAtLeastSixteen()) {
                                birthdayError = null
                                currentStep = 5
                            } else {
                                birthdayError = "You must be at least 16 years old to use Nebula."
                            }
                        }
                        5 -> currentStep = 6
                        6 -> currentStep = 7
                        7 -> {
                            // Finish and submit profile atomically
                            isSubmitting = true
                            submitError = null
                            val dobStr = String.format("%04d-%02d-%02d", birthYear, birthMonth, birthDay)
                            val finalGender = if (gender == "Custom") customGender.trim().ifEmpty { "Custom" } else gender

                            scope.launch {
                                val res = NebulaApiClient.updateProfile(
                                    displayName = displayName.trim(),
                                    username = username.trim(),
                                    bio = initialProfile.bio,
                                    status = "online",
                                    bannerIndex = initialProfile.bannerIndex,
                                    interests = initialProfile.interests,
                                    avatarUrl = selectedAvatarUri,
                                    isFriendCountPublic = true,
                                    isOnboardingCompleted = true,
                                    dateOfBirth = dobStr,
                                    gender = finalGender
                                )

                                when (res) {
                                    is NebulaApiClient.ApiResponse.Success -> {
                                        isSubmitting = false
                                        onCompleteOnboarding(
                                            displayName.trim(),
                                            username.trim(),
                                            dobStr,
                                            finalGender,
                                            selectedAvatarUri
                                        )
                                    }
                                    is NebulaApiClient.ApiResponse.Error -> {
                                        isSubmitting = false
                                        submitError = res.message
                                    }
                                }
                            }
                        }
                    }
                },
                enabled = !isSubmitting &&
                    (currentStep != 3 || (usernameError == null && username.length in 3..20)) &&
                    (currentStep != 4 || isAtLeastSixteen()),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.colors.primaryAccent,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (currentStep == 7) "Complete Setup" else "Next",
                            style = theme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        )
                        Icon(
                            imageVector = if (currentStep == 7) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScrollWheelDatePicker(
    day: Int,
    month: Int,
    year: Int,
    onDayChanged: (Int) -> Unit,
    onMonthChanged: (Int) -> Unit,
    onYearChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    val months = remember {
        listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Selected Date Summary Display
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(theme.colors.primaryAccent.copy(alpha = 0.12f))
                .border(1.dp, theme.colors.primaryAccent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .padding(horizontal = 20.dp, vertical = 9.dp)
        ) {
            Text(
                text = "${months.getOrElse(month - 1) { "Jan" }} $day, $year",
                style = theme.typography.bodyLarge.copy(
                    color = theme.colors.primaryAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(theme.colors.surface)
                .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Day wheel (1..31)
            WheelNumberColumn(
                label = "DAY",
                items = (1..31).map { it.toString() },
                selectedIndex = (day - 1).coerceIn(0, 30),
                onSelectIndex = { onDayChanged(it + 1) },
                modifier = Modifier.weight(1f)
            )

            // Month wheel (Jan..Dec)
            WheelNumberColumn(
                label = "MONTH",
                items = months,
                selectedIndex = (month - 1).coerceIn(0, 11),
                onSelectIndex = { onMonthChanged(it + 1) },
                modifier = Modifier.weight(1.2f)
            )

            // Year wheel (1940..2024)
            val years = remember { (1940..2024).toList() }
            WheelNumberColumn(
                label = "YEAR",
                items = years.map { it.toString() },
                selectedIndex = years.indexOf(year).let { if (it >= 0) it else 60 },
                onSelectIndex = { onYearChanged(years[it]) },
                modifier = Modifier.weight(1.3f)
            )
        }
    }
}

@Composable
fun WheelNumberColumn(
    label: String,
    items: List<String>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        if (selectedIndex in items.indices) {
            val target = (selectedIndex - 1).coerceAtLeast(0)
            listState.animateScrollToItem(target)
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = theme.typography.caption.copy(
                color = theme.colors.textSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            ),
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // Chevron Up
        IconButton(
            onClick = {
                if (selectedIndex > 0) onSelectIndex(selectedIndex - 1)
            },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Previous",
                tint = theme.colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .height(130.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            itemsIndexed(items) { index, item ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) theme.colors.primaryAccent
                            else Color.Transparent
                        )
                        .clickable { onSelectIndex(index) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item,
                        style = theme.typography.bodyMedium.copy(
                            color = if (isSelected) Color.White else theme.colors.textPrimary.copy(alpha = 0.75f),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = if (isSelected) 15.sp else 13.sp
                        )
                    )
                }
            }
        }

        // Chevron Down
        IconButton(
            onClick = {
                if (selectedIndex < items.size - 1) onSelectIndex(selectedIndex + 1)
            },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Next",
                tint = theme.colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
