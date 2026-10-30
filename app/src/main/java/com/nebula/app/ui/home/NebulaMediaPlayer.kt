package com.nebula.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.nebula.app.theme.Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.concurrent.atomic.AtomicReference

/**
 * Media3 player for room media that is not hosted on YouTube.
 *
 * Rooms previously only supported YouTube, so a direct HLS or MP4 link was accepted
 * by the server and then showed the "idle" placeholder. This plays those sources with
 * the same host/guest contract the YouTube surface uses:
 *
 *  - the host is the only clock. It samples its own position every half second and the
 *    server broadcasts that to the room;
 *  - a guest never drives time. It seeks only when its position has drifted further
 *    than [GUEST_DRIFT_TOLERANCE_MS], which keeps normal decode jitter from fighting
 *    the shared timeline;
 *  - a guest that is behind a live edge resumes from the shared position instead of
 *    restarting the item.
 */

/** Drift a guest tolerates before correcting. Matches the YouTube surface's budget. */
const val GUEST_DRIFT_TOLERANCE_MS = 700L

/** How often a host publishes its playhead while playing. */
private const val HOST_SAMPLE_INTERVAL_MS = 500L

/**
 * Whether a guest should seek to the shared position.
 *
 * A guest that corrected every millisecond would fight its own decoder and stutter;
 * one that never corrected would drift out of sync. The tolerance is the line between
 * the two, and it is a pure function so it can be pinned by a test.
 */
fun guestNeedsReseek(currentPositionMs: Long, targetPositionMs: Long): Boolean =
    kotlin.math.abs(currentPositionMs - targetPositionMs) > GUEST_DRIFT_TOLERANCE_MS

private val DIRECT_MEDIA_EXTENSIONS = listOf(
    ".m3u8", ".mpd", ".mp4", ".m4v", ".webm", ".mkv", ".mov", ".ts"
)

/**
 * True when [url] addresses a media file the player can stream directly.
 *
 * Query strings are stripped first because signed CDN links routinely append tokens
 * after the extension.
 */
fun isDirectMediaUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val lower = url.trim().lowercase()
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
    val withoutQuery = lower.substringBefore('?').substringBefore('#')
    return DIRECT_MEDIA_EXTENSIONS.any { withoutQuery.endsWith(it) } ||
        withoutQuery.contains(".m3u8") ||
        withoutQuery.contains(".mpd")
}

/** What the room is currently able to play, if anything. */
sealed interface RoomPlaybackTarget {
    data class YouTube(val videoId: String) : RoomPlaybackTarget
    data class Direct(val mediaUrl: String) : RoomPlaybackTarget
    data object None : RoomPlaybackTarget
}

/**
 * Picks the surface for the room's current media.
 *
 * A YouTube link always wins because the in-app YouTube surface is the only one that
 * keeps chat, sync and the host's controls on the same screen. Everything else that
 * looks like a streamable file plays through Media3, and anything unrecognised is
 * treated as idle instead of handing an unplayable URL to a player that would show a
 * black rectangle forever.
 */
fun resolveRoomPlayback(room: com.nebula.app.domain.model.NebulaRoom): RoomPlaybackTarget {
    val media = room.mediaItem ?: return RoomPlaybackTarget.None
    val videoId = media.youtubeVideoId?.takeIf { it.isNotBlank() }
        ?: com.nebula.app.domain.model.SharedMediaItem.extractYouTubeId(media.mediaUrl)
    if (!videoId.isNullOrBlank()) return RoomPlaybackTarget.YouTube(videoId)
    return if (isDirectMediaUrl(media.mediaUrl)) {
        RoomPlaybackTarget.Direct(media.mediaUrl)
    } else {
        RoomPlaybackTarget.None
    }
}

/**
 * The room's single playback surface: YouTube for YouTube links, Media3 for direct
 * streams, nothing otherwise. Kept in one place so portrait and landscape can never
 * drift apart.
 */
@Composable
fun RoomPlayerSurface(
    room: com.nebula.app.domain.model.NebulaRoom,
    isHost: Boolean,
    locallyPaused: Boolean,
    hostSeekCommand: Pair<Long, Long>?,
    onHostSample: (Long, Boolean) -> Unit,
    onHostVideoEnded: (Long) -> Unit,
    onRestartVideo: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (val target = resolveRoomPlayback(room)) {
        is RoomPlaybackTarget.YouTube -> NebulaYoutubePlayer(
            roomId = room.id,
            videoId = target.videoId,
            playbackPositionMs = room.playbackPositionMs,
            isPlaying = room.isPlaying,
            isHost = isHost,
            locallyPaused = locallyPaused,
            hostSeekCommand = hostSeekCommand,
            onHostSample = onHostSample,
            onHostVideoEnded = onHostVideoEnded,
            mediaEnded = room.mediaEnded,
            onRestartVideo = onRestartVideo,
            modifier = modifier
        )
        is RoomPlaybackTarget.Direct -> NebulaMediaPlayer(
            roomId = room.id,
            mediaUrl = target.mediaUrl,
            playbackPositionMs = room.playbackPositionMs,
            isPlaying = room.isPlaying,
            isHost = isHost,
            locallyPaused = locallyPaused,
            hostSeekCommand = hostSeekCommand,
            onHostSample = onHostSample,
            onHostVideoEnded = onHostVideoEnded,
            mediaEnded = room.mediaEnded,
            onRestartVideo = onRestartVideo,
            modifier = modifier
        )
        RoomPlaybackTarget.None -> Unit
    }
}

@Composable
fun NebulaMediaPlayer(
    roomId: String,
    mediaUrl: String,
    playbackPositionMs: Long,
    isPlaying: Boolean,
    isHost: Boolean,
    locallyPaused: Boolean,
    hostSeekCommand: Pair<Long, Long>?,
    onHostSample: (Long, Boolean) -> Unit,
    onHostVideoEnded: (Long) -> Unit,
    mediaEnded: Boolean,
    onRestartVideo: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Same rule as the YouTube surface: an app-clone profile is a separate Android
    // user sharing the audio device, so it must not take focus from the original.
    val muteAudioForProfile = android.os.Process.myUid() / 100000 >= 900
    val context = androidx.compose.ui.platform.LocalContext.current

    val player = remember(roomId, mediaUrl) {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = false
            volume = if (muteAudioForProfile) 0f else 1f
        }
    }

    // Callbacks are kept in refs so the player is never rebuilt when state changes.
    val latestSample = remember { AtomicReference(onHostSample) }
    val latestIsHost = remember { AtomicReference(isHost) }
    val latestEnded = remember { AtomicReference(onHostVideoEnded) }
    SideEffect {
        latestSample.set(onHostSample)
        latestIsHost.set(isHost)
        latestEnded.set(onHostVideoEnded)
    }

    DisposableEffect(roomId, mediaUrl) {
        player.setMediaItem(MediaItem.fromUri(mediaUrl))
        player.prepare()
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && latestIsHost.get()) {
                    latestEnded.get().invoke(player.currentPosition.coerceAtLeast(0L))
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // A dead link must not stop the room; the surface shows the idle state
                // and the host can pick something else.
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Only the host drives the clock. It publishes what it is actually doing so the
    // room follows reality rather than the last requested command.
    LaunchedEffect(player, isHost, mediaUrl) {
        if (!isHost) return@LaunchedEffect
        while (isActive) {
            delay(HOST_SAMPLE_INTERVAL_MS)
            if (!latestIsHost.get()) continue
            val position = player.currentPosition.coerceAtLeast(0L)
            latestSample.get().invoke(position, player.isPlaying)
        }
    }

    // Apply the room's intent: host toggles come from the server, guests apply the
    // broadcast timeline.
    LaunchedEffect(player, playbackPositionMs, isPlaying, locallyPaused, isHost, mediaUrl) {
        if (locallyPaused) {
            player.playWhenReady = false
            return@LaunchedEffect
        }
        if (isHost) {
            player.playWhenReady = isPlaying
            return@LaunchedEffect
        }
        val target = playbackPositionMs.coerceAtLeast(0L)
        if (guestNeedsReseek(player.currentPosition, target)) {
            player.seekTo(target)
        }
        player.playWhenReady = isPlaying
    }

    LaunchedEffect(player, hostSeekCommand?.first) {
        val target = hostSeekCommand?.second ?: return@LaunchedEffect
        player.seekTo(target.coerceAtLeast(0L))
    }

    LaunchedEffect(player, mediaEnded) {
        if (mediaEnded) {
            player.pause()
        } else if (!isPlaying) {
            // Restarting for everyone returns the item to its first frame.
            player.seekTo(0L)
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            },
            update = { view -> view.player = player }
        )
        if (mediaEnded) {
            EndedOverlay(isHost = isHost, onRestartVideo = onRestartVideo)
        }
    }
}

/** Shared "video ended" card, so every player surface ends the same way. */
@Composable
internal fun EndedOverlay(isHost: Boolean, onRestartVideo: () -> Unit) {
    val theme = Theme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .clickable(enabled = true, onClick = {}),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(theme.colors.surface)
                .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(16.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Video ended",
                style = theme.typography.titleMedium.copy(
                    color = theme.colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            )
            if (isHost) {
                Button(
                    onClick = onRestartVideo,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.colors.primaryAccent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Restart for everyone", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Text(
                    text = "Waiting for host to restart",
                    style = theme.typography.bodySmall.copy(color = theme.colors.textSecondary),
                    fontSize = 12.sp
                )
            }
        }
    }
}
