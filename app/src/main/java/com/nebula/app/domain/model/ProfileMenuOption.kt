package com.nebula.app.domain.model

enum class ProfileMenuDestination {
    PROFILE,
    SETTINGS,
    PERMISSIONS,
    PRIVACY,
    THEMES,
    MEMORY,
    NOTIFICATIONS,
    ADD_ONS,
    PREMIUM,
    ABOUT
}

data class ProfileMenuOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconKey: String,
    val destination: ProfileMenuDestination,
    val badge: String? = null
) {
    companion object {
        val DefaultOptions = listOf(
            ProfileMenuOption(
                id = "opt_profile",
                title = "Profile",
                subtitle = "Account details, avatar and biography",
                iconKey = "person",
                destination = ProfileMenuDestination.PROFILE
            ),
            ProfileMenuOption(
                id = "opt_settings",
                title = "Settings",
                subtitle = "Nebula AI personality, intelligence & tone",
                iconKey = "settings",
                destination = ProfileMenuDestination.SETTINGS
            ),
            ProfileMenuOption(
                id = "opt_permissions",
                title = "Permissions",
                subtitle = "Hardware access & system privacy status",
                iconKey = "security",
                destination = ProfileMenuDestination.PERMISSIONS,
                badge = "Verified"
            ),
            ProfileMenuOption(
                id = "opt_privacy",
                title = "Privacy & Local Vault",
                subtitle = "On-device encryption & sandboxed storage",
                iconKey = "lock",
                destination = ProfileMenuDestination.PRIVACY,
                badge = "Encrypted"
            ),
            ProfileMenuOption(
                id = "opt_themes",
                title = "Themes",
                subtitle = "Light, Dark, Lavender, Red-Hot-Pink",
                iconKey = "palette",
                destination = ProfileMenuDestination.THEMES,
                badge = "4 Themes"
            ),
            ProfileMenuOption(
                id = "opt_memory",
                title = "Memory & Data",
                subtitle = "Knowledge graph, facts & notes",
                iconKey = "psychology",
                destination = ProfileMenuDestination.MEMORY
            ),
            ProfileMenuOption(
                id = "opt_notifications",
                title = "Notifications",
                subtitle = "Proactive alerts, quiet hours & priorities",
                iconKey = "notifications",
                destination = ProfileMenuDestination.NOTIFICATIONS
            ),
            ProfileMenuOption(
                id = "opt_addons",
                title = "Add-ons & Integrations",
                subtitle = "Local bridges, calendars & custom tools",
                iconKey = "extension",
                destination = ProfileMenuDestination.ADD_ONS
            ),
            ProfileMenuOption(
                id = "opt_premium",
                title = "Premium",
                subtitle = "Exclusive themes, badges & local acceleration",
                iconKey = "star",
                destination = ProfileMenuDestination.PREMIUM,
                badge = "Pro"
            ),
            ProfileMenuOption(
                id = "opt_about",
                title = "About",
                subtitle = "Nebula OS • v1.0.0 Architecture",
                iconKey = "info",
                destination = ProfileMenuDestination.ABOUT
            )
        )
    }
}
