package com.nebula.app.ui.home

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import android.app.Activity
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.ChatBubbleOutline
import com.nebula.app.domain.model.UserProfile
import com.nebula.app.ui.components.NebulaAvatarImage
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import com.nebula.app.ui.dialogs.InRoomMediaSearchSheet
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.domain.model.NebulaRoom
import com.nebula.app.domain.model.NebulaUser
import com.nebula.app.domain.model.RoomChatMessage
import com.nebula.app.domain.model.RoomParticipant
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.PABottomSheet
import com.nebula.app.ui.dialogs.AttachmentPickerSheet
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * The YouTube surface: an in-app iframe player with the room's sync contract.
 *
 * The host is the clock and samples its own playhead; a guest follows the broadcast
 * timeline and only corrects beyond the drift budget. Kept apart from the room screen
 * so playback and layout can change independently.
 */


@Composable
internal fun NebulaYoutubePlayer(
    roomId: String,
    videoId: String,
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
    // Realme's app-clone profile is a separate Android user on the same audio
    // device. Keep its YouTube renderer silent so it cannot steal audio focus
    // from the original profile and pause the room's shared playback.
    val muteAudioForAndroidProfile = android.os.Process.myUid() / 100000 >= 900
    val player = remember(roomId, videoId) { mutableStateOf<WebView?>(null) }
    val latestSample = remember { AtomicReference(onHostSample) }
    val latestIsHost = remember { AtomicBoolean(isHost) }
    val latestOnVideoEnded = remember { AtomicReference(onHostVideoEnded) }
    SideEffect {
        latestSample.set(onHostSample)
        latestIsHost.set(isHost)
        latestOnVideoEnded.set(onHostVideoEnded)
    }
    DisposableEffect(roomId, videoId) {
        onDispose {
            player.value?.apply {
                stopLoading()
                removeJavascriptInterface("Nebula")
                destroy()
            }
            player.value = null
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = WebViewClient()
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun sample(seconds: Double, state: Int) {
                        if (latestIsHost.get() && (state == 1 || state == 3)) {
                            latestSample.get().invoke((seconds * 1000.0).toLong().coerceAtLeast(0L), true)
                        } else if (latestIsHost.get() && state == 2) {
                            latestSample.get().invoke((seconds * 1000.0).toLong().coerceAtLeast(0L), false)
                        }
                    }
                    @JavascriptInterface
                    fun ended(seconds: Double) {
                        if (latestIsHost.get()) latestOnVideoEnded.get().invoke((seconds * 1000.0).toLong().coerceAtLeast(0L))
                    }
                }, "Nebula")
                player.value = this
                val html = """
                    <!doctype html><html style="width:100%;height:100%;margin:0"><head><meta name="viewport" content="width=device-width,height=device-height,initial-scale=1,maximum-scale=1,user-scalable=no"><style>html,body{width:100%;height:100%;margin:0;padding:0;overflow:hidden;background:#000}body{position:fixed;inset:0}#p{position:absolute;top:0;left:0}</style></head>
                    <body><div id="p"></div><script>
                    let player, pending={position:$playbackPositionMs,playing:$isPlaying,timestamp:Date.now()}, localPause=false, ready=false, ended=$mediaEnded, muteAudioForProfile=$muteAudioForAndroidProfile;
                    function onYouTubeIframeAPIReady(){let w=Math.max(200,window.innerWidth),h=Math.max(200,window.innerHeight),mount=document.getElementById('p');mount.style.width=w+'px';mount.style.height=h+'px';player=new YT.Player('p',{width:w,height:h,videoId:'$videoId',playerVars:{autoplay:0,controls:${if (isHost) 1 else 0},disablekb:1,playsinline:1,rel:0,iv_load_policy:3,enablejsapi:1,origin:'https://com.nebula.app'},events:{onReady:()=>{let i=player.getIframe();i.setAttribute('width',String(w));i.setAttribute('height',String(h));i.style.width=w+'px';i.style.height=h+'px';i.style.display='block';ready=true;pending=window.__nebulaCommand||pending;applyPending();},onStateChange:(e)=>{if(e.data===0){ended=true;if(window.Nebula){try{Nebula.ended(player.getCurrentTime()||0)}catch(_){}}return;}if(window.Nebula){try{Nebula.sample(player.getCurrentTime()||0,e.data)}catch(_){}}}}});}
                    function applyPending(){if(!ready||!pending)return;let s=pending; if(muteAudioForProfile)player.mute(); if(localPause){player.pauseVideo();return;}if(ended&&!s.playing){player.pauseVideo();return;}let target=Math.max(0,(s.position+(s.playing?Math.max(0,Date.now()-s.timestamp):0))/1000);let now=player.getCurrentTime()||0; if(Math.abs(now-target)>0.65)player.seekTo(target,true); if(s.playing){if(ended&&target>1){player.pauseVideo();return;}ended=false;if(player.getPlayerState()!==1)player.playVideo();}else if(player.getPlayerState()===1||player.getPlayerState()===3)player.pauseVideo();}
                    function sync(position,playing,timestamp,pauseLocal){pending={position:position,playing:playing,timestamp:timestamp};window.__nebulaCommand=pending;localPause=pauseLocal;if(position<1000&&playing)ended=false;applyPending();}
                    setInterval(()=>{if(ready&&player&&window.Nebula){try{Nebula.sample(player.getCurrentTime()||0,player.getPlayerState())}catch(_){}}},250);
                    let apiScript=document.createElement('script');apiScript.src='https://www.youtube.com/iframe_api';document.head.appendChild(apiScript);
                    </script></body></html>
                """.trimIndent()
                loadDataWithBaseURL("https://com.nebula.app/", html, "text/html", "UTF-8", null)
            }
        },
        update = { /* UI recompositions never issue playback commands. */ }
    )
    val syncPosition = if (isHost) 0L else playbackPositionMs
    LaunchedEffect(player.value, videoId, syncPosition, isPlaying, locallyPaused) {
        player.value?.evaluateJavascript(
            "window.__nebulaCommand={position:$playbackPositionMs,playing:$isPlaying,timestamp:${System.currentTimeMillis()}};if(typeof sync==='function')sync($playbackPositionMs,$isPlaying,${System.currentTimeMillis()},$locallyPaused);",
            null
        )
    }
    LaunchedEffect(player.value, hostSeekCommand?.first) {
        val positionMs = hostSeekCommand?.second ?: return@LaunchedEffect
        player.value?.evaluateJavascript("if(typeof player!=='undefined'&&player&&typeof player.seekTo==='function'){player.seekTo(${positionMs.coerceAtLeast(0L) / 1000.0},true);}", null)
    }
    LaunchedEffect(player.value, mediaEnded) {
        player.value?.evaluateJavascript(
            "if(typeof player!=='undefined'&&player){ended=$mediaEnded;if(ended)player.pauseVideo();else{player.seekTo(0,true);player.playVideo();}}",
            null
        )
    }
    if (mediaEnded) {
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
}
