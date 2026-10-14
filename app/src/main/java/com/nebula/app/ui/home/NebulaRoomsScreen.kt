package com.nebula.app.ui.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nebula.app.domain.model.NebulaRoom
import com.nebula.app.domain.model.RoomPrivacy
import com.nebula.app.domain.model.SharedMediaItem
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.NebulaCompactRoomCard

// 50+ Comprehensive room tags for Amazon/Flipkart style filter
val ALL_ROOM_TAGS = listOf(
    // Entertainment & Cinema
    "Cinema", "Watch Party", "Anime", "Movies", "Sci-Fi", "Action", "Comedy", "Horror", "Drama", "Documentary",
    // Music & Vibes
    "Music", "Lo-Fi", "Chill", "Ambient", "Hip-Hop", "Rock", "Pop", "EDM", "Jazz", "Classical", "K-Pop", "Bollywood",
    // Gaming & Esports
    "Gaming", "Minecraft", "Valorant", "GTA V", "Fortnite", "Apex", "Roblox", "Cyberpunk", "Retro Gaming", "Speedrun",
    // Productivity & Education
    "Study", "Coding", "Deep Work", "Tech", "AI", "Reading", "Co-Working", "Focus", "Language Learning", "Math",
    // Lifestyle & Hobbies
    "Podcast", "Fitness", "Workout", "Cooking", "Food", "Travel", "Vlog", "Art", "Design", "Photography", "Meditation", "Trivia", "Crypto", "Web3", "18+"
)

enum class RoomSortOption(val label: String, val shortLabel: String) {
    HIGH_TO_LOW("Members: High to Low (Most Popular)", "High to Low"),
    LOW_TO_HIGH("Members: Low to High (Cozy Rooms)", "Low to High")
}

/**
 * NebulaRoomsScreen:
 * Revamped social discovery space.
 * Features:
 * - Removed "+ Create" button (accessible via top bar TV icon)
 * - Removed "Active Now & Friends" section (located on Friends/Home screens)
 * - Real-time pre-existing room search bar
 * - Flipkart/Amazon style Filter & Sort menu (50+ tags, max 3 selectable, High to Low / Low to High sorting)
 * - Recently used filter chips row for 1-tap quick filtering
 * - Compact minimalist room cards with real live rooms from repository
 */
@Composable
fun NebulaRoomsScreen(
    rooms: List<NebulaRoom>,
    onJoinRoom: (String) -> Unit,
    onCreateRoomClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedSort by remember { mutableStateOf(RoomSortOption.HIGH_TO_LOW) }
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var showFilterSheet by remember { mutableStateOf(false) }

    // Curated category filter row starting with "All", "Chill", "Study", etc.
    val recentFilters = listOf("All", "Chill", "Study", "Cinema", "Lo-Fi", "Gaming", "Tech", "Anime")

    // Filter and Sort rooms directly from repository / backend
    val displayedRooms = remember(searchQuery, selectedTags, selectedSort, rooms) {
        rooms.filter { room ->
            // Search query filter
            val matchesSearch = searchQuery.isBlank() ||
                room.name.contains(searchQuery, ignoreCase = true) ||
                room.hostName.contains(searchQuery, ignoreCase = true) ||
                room.tags.any { it.contains(searchQuery, ignoreCase = true) } ||
                (room.mediaItem?.title?.contains(searchQuery, ignoreCase = true) == true)

            // Tag filter (if any selected, must match at least one)
            val matchesTags = selectedTags.isEmpty() ||
                room.tags.any { it in selectedTags }

            matchesSearch && matchesTags
        }.sortedWith { a, b ->
            when (selectedSort) {
                RoomSortOption.HIGH_TO_LOW -> b.participantCount.compareTo(a.participantCount)
                RoomSortOption.LOW_TO_HIGH -> a.participantCount.compareTo(b.participantCount)
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. HEADER (No "+ Create" button)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "Rooms",
                    style = theme.typography.titleLarge.copy(
                        color = theme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                )
                Text(
                    text = "Watch together & hang out in sync",
                    style = theme.typography.bodySmall.copy(
                        color = theme.colors.textSecondary,
                        fontSize = 13.sp
                    )
                )
            }
        }

        // 2. SEARCH PRE-EXISTING ROOMS
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Search rooms by name, tag, or topic...",
                        style = theme.typography.bodyMedium.copy(color = theme.colors.textTertiary, fontSize = 13.5.sp)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = theme.colors.textTertiary,
                        modifier = Modifier.size(19.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = theme.colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = theme.colors.surface,
                    unfocusedContainerColor = theme.colors.surface,
                    focusedBorderColor = theme.colors.primaryAccent,
                    unfocusedBorderColor = theme.colors.surfaceBorder,
                    focusedTextColor = theme.colors.textPrimary,
                    unfocusedTextColor = theme.colors.textPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 3. FLIPKART/AMAZON STYLE FILTERS & RECENTLY USED TAGS BAR
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Main Amazon/Flipkart "Filters" Button with badge
                val hasActiveFilters = selectedTags.isNotEmpty() || selectedSort != RoomSortOption.HIGH_TO_LOW
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (hasActiveFilters) theme.colors.primaryAccent.copy(alpha = 0.18f) else theme.colors.surface)
                        .border(
                            1.dp,
                            if (hasActiveFilters) theme.colors.primaryAccent else theme.colors.surfaceBorder,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { showFilterSheet = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Filters",
                            tint = if (hasActiveFilters) theme.colors.primaryAccent else theme.colors.textPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (selectedTags.isEmpty()) "Filters" else "Filters (${selectedTags.size})",
                            style = theme.typography.caption.copy(
                                color = if (hasActiveFilters) theme.colors.primaryAccent else theme.colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                // Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(theme.colors.surfaceBorder)
                )

                // Recently Used Filter Chips
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(recentFilters) { tag ->
                        val isSelected = if (tag == "All") selectedTags.isEmpty() else tag in selectedTags
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) theme.colors.primaryAccent else theme.colors.surface)
                                .border(
                                    0.8.dp,
                                    if (isSelected) theme.colors.primaryAccent else theme.colors.surfaceBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    if (tag == "All") {
                                        selectedTags = emptySet()
                                    } else {
                                        if (tag in selectedTags) {
                                            selectedTags = selectedTags - tag
                                        } else {
                                            if (selectedTags.size < 3) {
                                                selectedTags = selectedTags + tag
                                            } else {
                                                Toast.makeText(context, "Max 3 filters allowed", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (tag == "All") "All" else "#$tag",
                                style = theme.typography.caption.copy(
                                    color = if (isSelected) Color.White else theme.colors.textSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.5.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        // 4. ACTIVE FILTERS SUMMARY & COUNT STRIP
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "LIVE ROOMS (${displayedRooms.size})",
                    style = theme.typography.caption.copy(
                        color = theme.colors.textSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        fontSize = 11.sp
                    )
                )

                if (selectedTags.isNotEmpty() || selectedSort != RoomSortOption.HIGH_TO_LOW) {
                    Text(
                        text = "Reset",
                        style = theme.typography.caption.copy(
                            color = theme.colors.primaryAccent,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp
                        ),
                        modifier = Modifier.clickable {
                            selectedTags = emptySet()
                            selectedSort = RoomSortOption.HIGH_TO_LOW
                        }
                    )
                }
            }
        }

        // 5. LIVE ROOMS CARDS (Up to 10 rooms)
        if (displayedRooms.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(theme.colors.surface)
                        .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (rooms.isEmpty()) "No active rooms right now" else "No rooms match your filter",
                            style = theme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = theme.colors.textPrimary
                            )
                        )
                        Text(
                            text = if (rooms.isEmpty()) "Tap the TV icon above to create a watch party and invite friends" else "Try clearing filters or searching for something else",
                            style = theme.typography.caption.copy(color = theme.colors.textSecondary)
                        )
                        if (rooms.isNotEmpty() && (searchQuery.isNotEmpty() || selectedTags.isNotEmpty() || selectedSort != RoomSortOption.HIGH_TO_LOW)) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = {
                                    searchQuery = ""
                                    selectedTags = emptySet()
                                    selectedSort = RoomSortOption.HIGH_TO_LOW
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = theme.colors.surfaceElevated,
                                    contentColor = theme.colors.primaryAccent
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Reset All Filters", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        } else {
            items(displayedRooms) { room ->
                NebulaCompactRoomCard(
                    room = room,
                    onJoin = { onJoinRoom(room.id) }
                )
            }
        }
    }

    // 6. FLIPKART / AMAZON STYLE FILTER & SORT MODAL SHEET
    if (showFilterSheet) {
        Dialog(
            onDismissRequest = { showFilterSheet = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { showFilterSheet = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f)
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .background(theme.colors.surface)
                        .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .clickable(enabled = false) {} // Prevent backdrop dismiss on card click
                        .padding(20.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Modal Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Filters & Sorting",
                                style = theme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = theme.colors.textPrimary,
                                    fontSize = 18.sp
                                )
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Clear All",
                                    style = theme.typography.caption.copy(
                                        color = theme.colors.errorColor,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.5.sp
                                    ),
                                    modifier = Modifier.clickable {
                                        selectedTags = emptySet()
                                        selectedSort = RoomSortOption.HIGH_TO_LOW
                                    }
                                )

                                IconButton(
                                    onClick = { showFilterSheet = false },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = theme.colors.textSecondary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Scrollable Filters Body
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            // Section 1: SORT BY
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = "SORT BY MEMBERS",
                                        style = theme.typography.caption.copy(
                                            color = theme.colors.textSecondary,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp,
                                            fontSize = 11.5.sp
                                        )
                                    )

                                    RoomSortOption.values().forEach { option ->
                                        val isChosen = selectedSort == option
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isChosen) theme.colors.primaryAccent.copy(alpha = 0.12f) else theme.colors.surfaceElevated)
                                                .border(
                                                    1.dp,
                                                    if (isChosen) theme.colors.primaryAccent else theme.colors.surfaceBorder,
                                                    RoundedCornerShape(10.dp)
                                                )
                                                .clickable { selectedSort = option }
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = option.label,
                                                style = theme.typography.bodyMedium.copy(
                                                    color = if (isChosen) theme.colors.primaryAccent else theme.colors.textPrimary,
                                                    fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 13.5.sp
                                                )
                                            )
                                            if (isChosen) {
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
                            }

                            // Section 2: 50+ TAGS (MAX 3)
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "ROOM TAGS (50+ TAGS)",
                                            style = theme.typography.caption.copy(
                                                color = theme.colors.textSecondary,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp,
                                                fontSize = 11.5.sp
                                            )
                                        )

                                        Text(
                                            text = "${selectedTags.size}/3 selected",
                                            style = theme.typography.caption.copy(
                                                color = if (selectedTags.size == 3) theme.colors.primaryAccent else theme.colors.textTertiary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp
                                            )
                                        )
                                    }

                                    androidx.compose.foundation.layout.FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ALL_ROOM_TAGS.forEach { tag ->
                                            val isSelected = tag in selectedTags
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSelected) theme.colors.primaryAccent else theme.colors.surfaceElevated)
                                                    .border(
                                                        1.dp,
                                                        if (isSelected) theme.colors.primaryAccent else theme.colors.surfaceBorder,
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable {
                                                        if (isSelected) {
                                                            selectedTags = selectedTags - tag
                                                        } else {
                                                            if (selectedTags.size < 3) {
                                                                selectedTags = selectedTags + tag
                                                            } else {
                                                                Toast.makeText(context, "Maximum 3 tags allowed", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                                    .padding(horizontal = 11.dp, vertical = 7.dp)
                                            ) {
                                                Text(
                                                    text = "#$tag",
                                                    style = theme.typography.caption.copy(
                                                        color = if (isSelected) Color.White else theme.colors.textPrimary,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        fontSize = 12.sp
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Footer Apply Button
                        Button(
                            onClick = { showFilterSheet = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.colors.primaryAccent,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Apply Filters (${displayedRooms.size} Rooms)",
                                style = theme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
