package com.nebula.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.theme.Theme

@Composable
fun NebulaPrivacyPolicyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current

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
                text = "Privacy Policy",
                style = theme.typography.titleMedium.copy(
                    color = theme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            )
        }

        // FULL 32-SECTION POLICY
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column {
                    Text(
                        text = "Nebula Privacy Policy",
                        style = theme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = theme.colors.textPrimary,
                            fontSize = 20.sp
                        )
                    )
                    Text(
                        text = "Last Updated: October 8, 2026",
                        style = theme.typography.caption.copy(
                            color = theme.colors.primaryAccent,
                            fontSize = 12.sp
                        ),
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                    )
                    Text(
                        text = "Nebula (\"Nebula\", \"we\", \"us\", or \"our\") provides a social platform centered around shared Rooms where users can watch, listen, talk, chat, discover content, and interact with other users.\n\nThis Privacy Policy explains what information we collect, why we collect it, how we use and share it, how long we retain it, and the choices available to you.\n\nBy using Nebula, you acknowledge the practices described in this Privacy Policy.",
                        style = theme.typography.bodyMedium.copy(
                            color = theme.colors.textSecondary,
                            lineHeight = 20.sp
                        )
                    )
                }
            }

            item { PolicySection("1. Information We Collect", "We collect information that you provide directly, information generated when you use Nebula, and limited information provided by third-party services when you choose to use them.\n\nWe do not collect more information than we reasonably need to operate and improve Nebula.\n\n1.1 Account Information:\nWhen you create or use a Nebula account, we may collect email address, phone number (if using phone auth), username/handle, display name, profile picture, biography, account creation date, and auth identifiers. We do not store third-party passwords.") }

            item { PolicySection("2. Profile and Social Information", "Nebula is a social application. Information that you choose to make part of your public or social profile may be visible to other Nebula users (Username, display name, profile picture, biography, badges, public statistics, favorite categories, public room activity).\n\nDo not publish information on Nebula that you would not want other people to see.") }

            item { PolicySection("3. Rooms and Room Activity", "When you create or join a Room, we may collect room identifier, name, host, participants, join/leave timestamps, activity type, media metadata, playback state, queue, polls, and reactions to synchronize activity between participants and prevent abuse.") }

            item { PolicySection("4. Content and Messages", "Public Room chat, reactions, chat timestamps, and private conversation metadata are processed to deliver real-time social interaction. Nebula cannot guarantee that info shared with another user remains confidential if screenshotted by that user.") }

            item { PolicySection("5. Media and Content Information", "We process media title, identifier, thumbnail, provider, duration, and playback position. Nebula is not a general video-hosting service and does not store copies of third-party streaming content or bypass DRM.") }

            item { PolicySection("6. Third-Party Streaming Services", "When using YouTube or other supported services, authentication occurs through third-party mechanisms. Nebula does not ask for or store third-party passwords.") }

            item { PolicySection("7. Usage and Activity Information", "Activity events (rooms joined, hosted, media started, skipped, liked, polls voted, messages sent) are collected solely to operate features, prevent spam/abuse, and power room discovery.") }

            item { PolicySection("8. Personalization & Recommendations", "Activity may be used to recommend rooms, organize discovery, and generate personalized statistics without accessing unrelated device info.") }

            item { PolicySection("9. Artificial Intelligence and Machine Learning", "Future AI features (room recommendations, summaries) will use only reasonably necessary structured data. We will NEVER automatically send private messages or personal files to external AI services.") }

            item { PolicySection("10. Device and Technical Information", "Limited technical info (device type, OS version, app version, approximate network location) is collected to maintain security and diagnose crashes. Nebula NEVER accesses phone call logs, SMS, WhatsApp databases, or unrelated personal files.") }

            item { PolicySection("11. Location Information", "Nebula does not require precise device location for core room functionality. Precise GPS tracking is never enabled merely because you installed Nebula.") }

            item { PolicySection("12. Microphone and Camera", "Nebula does not access your microphone or camera. Watch parties share a video link and typed chat only; no audio or video is captured from your device.") }

            item { PolicySection("13. Contacts", "Nebula does not require or scan your entire contact list. We do not access contacts merely to operate rooms.") }

            item { PolicySection("14. Files, Photos, and Media", "Requested only when you explicitly select a profile picture or save media from chat into on-device saved media. We do not automatically scan your media library.") }

            item { PolicySection("15. Cookies and Similar Technologies", "The application uses standard platform identifiers for authentication session tokens and crash telemetry.") }

            item { PolicySection("16. Advertising", "Free users may see non-intrusive ads. Ads never interrupt active room playback or private chats. Premium and Premium Plus subscriptions remove all advertisements.") }

            item { PolicySection("17. Payments and Subscriptions", "Google Play processes payments securely. Nebula does not receive or store payment card numbers.") }

            item { PolicySection("18. How We Use Information", "To provide rooms, synchronize playback, operate messaging, maintain friendships, personalize discovery, moderate the platform, and protect user security.") }

            item { PolicySection("19. How We Share Information", "We do not sell personal information. Information is shared with room participants (presence, chat) and essential infrastructure providers (hosting, databases, notifications).") }

            item { PolicySection("20. Information We Do Not Collect", "Nebula strictly excludes SMS history, call logs, WhatsApp databases, unrelated photos, third-party passwords, and continuous GPS tracking.") }

            item { PolicySection("21. Security", "Protected using standard encryption in transit, server authorization, and rate limiting.") }

            item { PolicySection("22. Data Retention", "Information is retained only as long as necessary to operate your account and comply with legal obligations.") }

            item { PolicySection("23. Account Deletion", "Users can request account deletion at any time via the app settings. Associated personal data is purged or anonymized.") }

            item { PolicySection("24. Your Privacy Choices and Rights", "You may access, update, correct, or delete your account information, control notification settings, and toggle public/private profile visibility.") }

            item { PolicySection("25. Children's Privacy", "Nebula is intended for users who meet the minimum legal age for social services in their jurisdiction.") }

            item { PolicySection("26. User Safety", "Nebula provides blocking, reporting, muting, and moderation tools. Always exercise judgment when communicating with new people.") }

            item { PolicySection("27. Third-Party Links", "External streaming providers operate under their own independent terms and privacy policies.") }

            item { PolicySection("28. International Transfers", "Data is processed with standard contractual safeguards across Google Cloud Platform regions.") }

            item { PolicySection("29. Regional Rights", "Compliant with GDPR, DPDP (India), and applicable global data protection regulations.") }

            item { PolicySection("30. Changes to Policy", "Material updates will be notified via in-app banner and updated effective date.") }

            item { PolicySection("31. Contact Us", "Nebula Privacy Team\nOfficial Email: privacy@nebula.social\nSupport: support@nebula.social") }

            item { PolicySection("32. Effective Date", "Effective as of October 8, 2026. Last updated: October 8, 2026.") }
        }
    }
}

@Composable
private fun PolicySection(title: String, body: String) {
    val theme = Theme.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = theme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = theme.colors.textPrimary,
                fontSize = 14.sp
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = body,
            style = theme.typography.bodySmall.copy(
                color = theme.colors.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        )
    }
}
