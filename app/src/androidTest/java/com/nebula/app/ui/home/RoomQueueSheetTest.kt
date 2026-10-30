package com.nebula.app.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nebula.app.domain.model.RoomQueueItem
import com.nebula.app.theme.ProvideAppTheme
import com.nebula.app.theme.ThemePresets
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The queue's two destructive actions must stay distinguishable:
 *  - "Skip n/m" is a vote that removes the *queued* item;
 *  - "Play now" is the host advancing playback onto it.
 *
 * A regression that merged them is exactly what the server suite caught once already,
 * so the UI is pinned here as well.
 */
@RunWith(AndroidJUnit4::class)
class RoomQueueSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val myId = "user_me"
    private val otherId = "user_other"

    private val items = listOf(
        RoomQueueItem(
            id = "item_a",
            roomId = "room_1",
            title = "Someone Else's Pick",
            mediaUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            addedByUserId = otherId,
            addedByName = "Other",
            skipVoteCount = 0,
            skipVotesRequired = 2
        ),
        RoomQueueItem(
            id = "item_b",
            roomId = "room_1",
            title = "My Pick",
            mediaUrl = "https://www.youtube.com/watch?v=aaaaaaaaaaa",
            addedByUserId = myId,
            addedByName = "Me",
            skipVoteCount = 0,
            skipVotesRequired = 2
        )
    )

    private fun render(
        isHost: Boolean,
        onVote: (String) -> Unit = {},
        onAdvance: () -> Unit = {},
        onRemove: (String) -> Unit = {}
    ) {
        composeRule.setContent {
            ProvideAppTheme(theme = ThemePresets.Dark) {
                RoomQueueSheet(
                    isVisible = true,
                    queue = items,
                    currentUserId = myId,
                    isHost = isHost,
                    onAddToQueue = { _, _ -> },
                    onVoteSkip = onVote,
                    onAdvance = onAdvance,
                    onRemove = onRemove,
                    onDismiss = {}
                )
            }
        }
    }

    @Test
    fun skipVoteTargetsTheTappedItemForAMember() {
        val voted = mutableListOf<String>()
        render(isHost = false, onVote = { voted.add(it) })

        composeRule.onNodeWithText("Skip 0/2").assertIsDisplayed()
        composeRule.onNodeWithText("Skip 0/2").performClick()

        assertEquals(listOf("item_a"), voted)
    }

    @Test
    fun ownItemOffersRemoveInsteadOfASkipVote() {
        val removed = mutableListOf<String>()
        val voted = mutableListOf<String>()
        render(isHost = false, onVote = { voted.add(it) }, onRemove = { removed.add(it) })

        composeRule.onNodeWithText("Remove").performClick()

        assertEquals(listOf("item_b"), removed)
        assertEquals(emptyList<String>(), voted)
    }

    @Test
    fun hostCanAdvanceTheRoom() {
        var advanced = 0
        render(isHost = true, onAdvance = { advanced++ })

        composeRule.onNodeWithContentDescription("Play this now").performClick()

        assertEquals(1, advanced)
    }
}
