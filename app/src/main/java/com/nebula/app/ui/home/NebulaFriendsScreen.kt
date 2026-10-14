package com.nebula.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.domain.model.NebulaFriendRequest
import com.nebula.app.domain.model.NebulaUser
import com.nebula.app.ui.components.NebulaAvatarImage
import com.nebula.app.theme.Theme
import kotlinx.coroutines.delay

/**
 * NebulaFriendsScreen:
 * Social graph for friends, requests, and suggested people.
 * Actions:
 * - Message friend -> opens chat
 * - Invite to room -> invites to active room
 * - View profile -> opens FriendProfileSheet
 * - Accept/Decline requests
 * - Add suggested people
 */
@Composable
fun NebulaFriendsScreen(
    friends: List<NebulaUser>,
    friendRequests: List<NebulaFriendRequest>,
    suggestedPeople: List<NebulaUser>,
    searchResults: List<NebulaUser> = emptyList(),
    onSearchQuery: (String) -> Unit = {},
    onRefreshSuggestions: () -> Unit = {},
    onMessageFriend: (NebulaUser) -> Unit,
    onInviteToRoom: (NebulaUser) -> Unit,
    onFriendClick: (NebulaUser) -> Unit,
    onAcceptRequest: (String) -> Unit,
    onDeclineRequest: (String) -> Unit,
    onAddSuggested: (NebulaUser) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = Theme.current
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(searchQuery) {
        delay(250)
        onSearchQuery(searchQuery)
    }

    var showNewChatDialog by remember { mutableStateOf(false) }

    val filteredFriends = remember(searchQuery, friends, searchResults) {
        if (searchQuery.length >= 2) searchResults
        else if (searchQuery.isBlank()) friends
        else friends.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) || it.username.contains(searchQuery, ignoreCase = true)
        }
    }

    if (showNewChatDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showNewChatDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(theme.colors.surface)
                    .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "New Message",
                            style = theme.typography.titleMedium.copy(
                                color = theme.colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        )
                        IconButton(
                            onClick = { showNewChatDialog = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = theme.colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Text(
                        text = "Select a friend to start chatting:",
                        style = theme.typography.caption.copy(color = theme.colors.textSecondary)
                    )

                    if (friends.isEmpty()) {
                        Text(
                            text = "You don't have any friends connected yet.",
                            style = theme.typography.bodySmall.copy(color = theme.colors.textTertiary),
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            friends.forEach { friend ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(theme.colors.surfaceElevated)
                                        .clickable {
                                            showNewChatDialog = false
                                            onMessageFriend(friend)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(theme.colors.surface)
                                            .border(1.dp, theme.colors.surfaceBorder, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        NebulaAvatarImage(friend.avatarUrl, friend.avatarLetter, modifier = Modifier.size(38.dp))
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = friend.displayName,
                                            style = theme.typography.bodyMedium.copy(
                                                color = theme.colors.textPrimary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        )
                                        Text(
                                            text = friend.username,
                                            style = theme.typography.caption.copy(
                                                color = theme.colors.textSecondary,
                                                fontSize = 11.5.sp
                                            )
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Chat,
                                        contentDescription = "Message",
                                        tint = theme.colors.primaryAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 280.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. TOP HEADER & SEARCH
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Friends",
                            style = theme.typography.titleLarge.copy(
                                color = theme.colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                            )
                        )
                        Text(
                            text = "${friends.size} friends connected",
                            style = theme.typography.bodySmall.copy(
                                color = theme.colors.textSecondary,
                                fontSize = 13.sp
                            )
                        )
                    }

                    // Functional Top-Right Add Chat button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(theme.colors.surface)
                            .border(1.dp, theme.colors.surfaceBorder, CircleShape)
                            .clickable { showNewChatDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddComment,
                            contentDescription = "New chat",
                            tint = theme.colors.primaryAccent,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search by name or @username...",
                            style = theme.typography.bodyMedium.copy(color = theme.colors.textTertiary, fontSize = 13.5.sp)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = theme.colors.textTertiary,
                            modifier = Modifier.size(18.dp)
                        )
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
        }

        // 2. ACTIVE NOW (Just like the home page)
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "ACTIVE NOW",
                        style = theme.typography.caption.copy(
                            color = theme.colors.textSecondary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            fontSize = 11.5.sp
                        )
                    )

                    Text(
                        text = "${friends.count { it.isOnline }} friends online",
                        style = theme.typography.caption.copy(
                            color = theme.colors.textTertiary,
                            fontSize = 11.5.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                val activeFriends = friends.filter { it.isOnline }
                if (activeFriends.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(theme.colors.surface)
                            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(12.dp))
                            .padding(vertical = 12.dp, horizontal = 14.dp)
                    ) {
                        Text(
                            text = "No friends active right now • Connect with people below",
                            style = theme.typography.bodySmall.copy(color = theme.colors.textSecondary, fontSize = 12.sp)
                        )
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(activeFriends) { friend ->
                            Column(
                                modifier = Modifier
                                    .width(72.dp)
                                    .clickable { onFriendClick(friend) },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(contentAlignment = Alignment.BottomEnd) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(CircleShape)
                                            .background(theme.colors.surfaceElevated)
                                            .border(
                                                width = if (friend.currentRoomId != null) 2.dp else 1.dp,
                                                color = if (friend.currentRoomId != null) theme.colors.primaryAccent else theme.colors.surfaceBorder,
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        NebulaAvatarImage(friend.avatarUrl, friend.avatarLetter, modifier = Modifier.size(52.dp))
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(13.dp)
                                            .clip(CircleShape)
                                            .background(Color(friend.presenceStatus.hexColor))
                                            .border(2.dp, theme.colors.background, CircleShape)
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = friend.displayName.split(" ").first(),
                                    style = theme.typography.caption.copy(
                                        color = theme.colors.textPrimary,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = friend.presenceStatus.label,
                                    style = theme.typography.caption.copy(
                                        color = Color(friend.presenceStatus.hexColor),
                                        fontSize = 10.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. FRIEND REQUESTS (if any)
        if (friendRequests.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "FRIEND REQUESTS (${friendRequests.size})",
                        style = theme.typography.caption.copy(
                            color = theme.colors.textSecondary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            fontSize = 11.5.sp
                        )
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(theme.colors.surface)
                            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
                    ) {
                        friendRequests.forEach { req ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(theme.colors.surfaceElevated),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        NebulaAvatarImage(req.sender.avatarUrl, req.sender.avatarLetter, modifier = Modifier.size(42.dp))
                                    }

                                    Column {
                                        Text(
                                            text = req.sender.displayName,
                                            style = theme.typography.bodyMedium.copy(
                                                color = theme.colors.textPrimary,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp
                                            )
                                        )
                                        Text(
                                            text = "${req.sender.mutualFriendsCount} mutual friends",
                                            style = theme.typography.caption.copy(
                                                color = theme.colors.textSecondary,
                                                fontSize = 11.5.sp
                                            )
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { onAcceptRequest(req.id) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = theme.colors.primaryAccent,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "Accept",
                                            style = theme.typography.caption.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        )
                                    }

                                    Button(
                                        onClick = { onDeclineRequest(req.id) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = theme.colors.surfaceElevated,
                                            contentColor = theme.colors.textSecondary
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "Ignore",
                                            style = theme.typography.caption.copy(
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 12.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. MY FRIENDS LIST
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "MY FRIENDS",
                    style = theme.typography.caption.copy(
                        color = theme.colors.textSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        fontSize = 11.5.sp
                    )
                )

                if (filteredFriends.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(theme.colors.surface)
                            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (searchQuery.isBlank()) "0 friends connected" else "No matching friends",
                                style = theme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = theme.colors.textSecondary
                                )
                            )
                            Text(
                                text = if (searchQuery.isBlank()) "Invite your friends or search by their @handle to get connected" else "Check spelling or search for someone else",
                                style = theme.typography.caption.copy(
                                    color = theme.colors.textTertiary,
                                    fontSize = 11.5.sp
                                )
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(theme.colors.surface)
                            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
                    ) {
                        filteredFriends.forEachIndexed { index, friend ->
                            if (searchQuery.length >= 2 && !friend.isFriend) {
                                SearchPersonItem(
                                    user = friend,
                                    onProfileClick = { onFriendClick(friend) },
                                    onAdd = { onAddSuggested(friend) },
                                    onAccept = { friend.friendRequestId?.let(onAcceptRequest) },
                                    onWithdraw = { friend.friendRequestId?.let(onDeclineRequest) }
                                )
                            } else {
                                FriendListItem(
                                    user = friend,
                                    onMessage = { onMessageFriend(friend) },
                                    onInvite = { onInviteToRoom(friend) },
                                    onProfileClick = { onFriendClick(friend) }
                                )
                            }
                            if (index < filteredFriends.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(0.6.dp)
                                        .background(theme.colors.surfaceBorder.copy(alpha = 0.4f))
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. SUGGESTED PEOPLE
        if (suggestedPeople.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = "SUGGESTED PEOPLE",
                            style = theme.typography.caption.copy(color = theme.colors.textSecondary, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, fontSize = 11.5.sp)
                        )
                        IconButton(onClick = onRefreshSuggestions, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh suggestions", tint = theme.colors.primaryAccent, modifier = Modifier.size(18.dp))
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(theme.colors.surface)
                            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(14.dp))
                    ) {
                        suggestedPeople.forEachIndexed { index, person ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onFriendClick(person) },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(theme.colors.surfaceElevated),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        NebulaAvatarImage(person.avatarUrl, person.avatarLetter, modifier = Modifier.size(42.dp))
                                    }

                                    Column {
                                        Text(
                                            text = person.displayName,
                                            style = theme.typography.bodyMedium.copy(
                                                color = theme.colors.textPrimary,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${person.mutualFriendsCount} mutual friends • ${person.username}",
                                            style = theme.typography.caption.copy(
                                                color = theme.colors.textSecondary,
                                                fontSize = 11.5.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Button(
                                    onClick = { onAddSuggested(person) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = theme.colors.primaryAccent.copy(alpha = 0.15f),
                                        contentColor = theme.colors.primaryAccent
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PersonAdd,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "Add",
                                            style = theme.typography.caption.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        )
                                    }
                                }
                            }
                            if (index < suggestedPeople.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(0.6.dp)
                                        .background(theme.colors.surfaceBorder.copy(alpha = 0.4f))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchPersonItem(
    user: NebulaUser,
    onProfileClick: () -> Unit,
    onAdd: () -> Unit,
    onAccept: () -> Unit,
    onWithdraw: () -> Unit
) {
    val theme = Theme.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.weight(1f).clickable(onClick = onProfileClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NebulaAvatarImage(avatarUrl = user.avatarUrl, fallbackLetter = user.avatarLetter, modifier = Modifier.size(42.dp))
            Column {
                Text(user.displayName, style = theme.typography.bodyMedium.copy(color = theme.colors.textPrimary, fontWeight = FontWeight.SemiBold))
                Text("${user.username} · ${user.status}", style = theme.typography.caption.copy(color = theme.colors.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val label = when {
            user.friendRequestReceived -> "Accept"
            user.friendRequestPending -> "Requested"
            else -> "Add"
        }
        TextButton(onClick = when {
            user.friendRequestReceived -> onAccept
            user.friendRequestPending -> onWithdraw
            else -> onAdd
        }) {
            Text(label, color = theme.colors.primaryAccent, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FriendListItem(
    user: NebulaUser,
    onMessage: () -> Unit,
    onInvite: () -> Unit,
    onProfileClick: () -> Unit
) {
    val theme = Theme.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onProfileClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(theme.colors.surfaceElevated)
                        .border(1.dp, theme.colors.surfaceBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    NebulaAvatarImage(avatarUrl = user.avatarUrl, fallbackLetter = user.avatarLetter, modifier = Modifier.size(44.dp))
                }

                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            if (user.currentRoomId != null) theme.colors.primaryAccent
                            else if (user.isOnline) theme.colors.successColor
                            else theme.colors.textTertiary
                        )
                        .border(2.dp, theme.colors.background, CircleShape)
                )
            }

            Column {
                Text(
                    text = user.displayName,
                    style = theme.typography.bodyLarge.copy(
                        color = theme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.5.sp
                    )
                )
                Text(
                    text = "${user.username} • ${user.status}",
                    style = theme.typography.caption.copy(
                        color = if (user.currentRoomId != null) theme.colors.primaryAccent else theme.colors.textSecondary,
                        fontSize = 11.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Action Buttons: Invite to room + Message
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(theme.colors.surfaceElevated)
                    .clickable(onClick = onInvite),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = "Invite to room",
                    tint = theme.colors.primaryAccent,
                    modifier = Modifier.size(16.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(theme.colors.surfaceElevated)
                    .clickable(onClick = onMessage),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ChatBubbleOutline,
                    contentDescription = "Message",
                    tint = theme.colors.textPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
