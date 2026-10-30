package com.nebula.app.ui.home

import com.nebula.app.domain.model.ChatMessage
import com.nebula.app.domain.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Room invite parsing and media routing.
 *
 * These decide which surface plays, and whether a message is a card or a file, so they
 * are worth pinning down outside the UI: getting them wrong either plays a black
 * rectangle or tries to load `nebula://room/CODE` as an image.
 */
class RoomMediaRoutingTest {

    @Test
    fun youtubeWinsOverDirectPlaybackEvenWhenUrlIsNotAPlayableFile() {
        assertTrue(
            isDirectMediaUrl("https://cdn.example.com/movie.mp4")
        )
        assertFalse(
            isDirectMediaUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        )
    }

    @Test
    fun signedStreamLinksAreStillRecognised() {
        assertTrue(
            isDirectMediaUrl("https://cdn.example.com/live/index.m3u8?token=abc123&expires=999")
        )
        assertTrue(
            isDirectMediaUrl("https://cdn.example.com/asset.webm#t=10")
        )
    }

    @Test
    fun unknownSchemesAreNotHandedToThePlayer() {
        assertFalse(isDirectMediaUrl("javascript:alert(1)"))
        assertFalse(isDirectMediaUrl("ftp://example.com/movie.mp4"))
        assertFalse(isDirectMediaUrl(""))
        assertFalse(isDirectMediaUrl(null))
    }

    @Test
    fun roomCardCarriesACodeAndNothingElse() {
        val card = ChatMessage(
            id = "m1",
            role = MessageRole.USER,
            content = "Movie night",
            mediaUrl = "nebula://room/abcd1234",
            messageType = "room_card"
        )
        assertEquals("ABCD1234", roomCardCode(card))
    }

    @Test
    fun aDeletedOrForeignMessageIsNotACard() {
        val deleted = ChatMessage(
            id = "m2",
            role = MessageRole.USER,
            content = "Movie night",
            mediaUrl = "nebula://room/ABCD1234",
            messageType = "room_card",
            deletedAt = System.currentTimeMillis()
        )
        assertNull(roomCardCode(deleted))

        val image = ChatMessage(
            id = "m3",
            role = MessageRole.USER,
            content = "",
            mediaUrl = "https://cdn.example.com/photo.jpg",
            messageType = "media"
        )
        assertNull(roomCardCode(image))

        // A card-typed message pointing somewhere other than a room code is rejected
        // rather than rendered as a join button.
        val spoofed = ChatMessage(
            id = "m4",
            role = MessageRole.USER,
            content = "click me",
            mediaUrl = "https://evil.example.com/room/ABCD1234",
            messageType = "room_card"
        )
        assertNull(roomCardCode(spoofed))
    }

    @Test
    fun latestInviteIsTheOneBannerShows() {
        val older = ChatMessage(
            id = "a",
            role = MessageRole.USER,
            content = "Old room",
            mediaUrl = "nebula://room/OLD00001",
            messageType = "room_card"
        )
        val newer = ChatMessage(
            id = "b",
            role = MessageRole.USER,
            content = "New room",
            mediaUrl = "nebula://room/NEW00002",
            messageType = "room_card"
        )
        val text = ChatMessage(id = "c", role = MessageRole.USER, content = "hey")

        assertEquals("NEW00002", roomCardCode(latestRoomCard(listOf(older, newer, text))!!))
        assertNull(latestRoomCard(listOf(text)))
    }

    @Test
    fun guestsOnlyReseekBeyondTheTolerance() {
        assertEquals(false, guestNeedsReseek(10_000L, 10_400L))
        assertEquals(true, guestNeedsReseek(10_000L, 12_000L))
        assertEquals(true, guestNeedsReseek(12_000L, 10_000L))
        // The boundary itself is tolerated; only strictly larger drift corrects.
        assertEquals(false, guestNeedsReseek(10_000L, 10_000L + GUEST_DRIFT_TOLERANCE_MS))
    }
}
