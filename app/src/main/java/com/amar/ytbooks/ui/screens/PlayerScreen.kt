package com.amar.ytbooks.ui.screens

import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.amar.ytbooks.data.Playlist
import com.amar.ytbooks.data.Video
import com.amar.ytbooks.ui.PlayerUi
import com.amar.ytbooks.ui.components.Pill
import com.amar.ytbooks.ui.components.PlayButton
import com.amar.ytbooks.ui.components.QualitySheet
import com.amar.ytbooks.ui.components.qualityLabel
import com.amar.ytbooks.ui.components.RoundIconButton
import com.amar.ytbooks.ui.components.SeekBar
import com.amar.ytbooks.ui.components.SkipMinuteButton
import com.amar.ytbooks.ui.components.SleepSheet
import com.amar.ytbooks.ui.components.SpeedSheet
import com.amar.ytbooks.ui.components.Tag
import com.amar.ytbooks.ui.components.VideosSheet
import com.amar.ytbooks.util.findActivity
import com.amar.ytbooks.util.formatDurationShort
import com.amar.ytbooks.util.formatTime
import com.amar.ytbooks.util.speedLabel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

class PlayerActions(
    val onBack: () -> Unit,
    val onTogglePlay: () -> Unit,
    val onPause: () -> Unit,
    val onSeekBy: (Long) -> Unit,
    val onSeekTo: (Long) -> Unit,
    val onSpeed: (Float) -> Unit,
    val onSleepStart: (Int) -> Unit,
    val onSleepExtend: (Int) -> Unit,
    val onSleepCancel: () -> Unit,
    val onMarkFinished: (Boolean) -> Unit,
    val onRestart: () -> Unit,
    val onPlayVideo: (Video) -> Unit,
    val onNext: () -> Unit,
    val onOpenInYouTube: (String) -> Unit,
    val onTouchMode: (Boolean) -> Unit,
    val onQuality: (String) -> Unit,
)

private data class SeekFeedback(val forward: Boolean, val seconds: Int, val stamp: Long)

@Composable
fun rememberSleepRemaining(endsAt: Long?): Long? {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) {
        while (endsAt != null) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    return endsAt?.let { (it - now).coerceAtLeast(0L) }
}

/**
 * The video itself is a WebView that lives behind all screens (see AppRoot). This screen only
 * reports where the video should be ([onVideoBounds]) and draws the controls around/over it.
 */
@Composable
fun PlayerScreen(
    video: Video,
    playlist: Playlist?,
    ui: PlayerUi,
    sleepEndsAt: Long?,
    quality: String,
    actions: PlayerActions,
    onVideoBounds: (Rect) -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val dark = isSystemInDarkTheme()
    val isThis = ui.mediaId == video.id
    val duration = if (isThis && ui.durationMs > 0) ui.durationMs else video.durationMs
    val position = if (isThis) ui.positionMs else video.positionMs
    val speed = if (isThis) ui.speed else video.speed
    val playing = isThis && ui.playWhenReady && !ui.ended
    val sleepLeft = rememberSleepRemaining(sleepEndsAt)

    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var touchMode by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showVideos by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }

    val touch by rememberUpdatedState(actions.onTouchMode)
    LaunchedEffect(touchMode) { touch(touchMode) }

    LaunchedEffect(fullscreen) {
        activity?.requestedOrientation =
            if (fullscreen) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    DisposableEffect(activity) {
        onDispose {
            touch(false)
            activity?.window?.let { w ->
                val c = WindowCompat.getInsetsController(w, w.decorView)
                c.show(WindowInsetsCompat.Type.systemBars())
                c.isAppearanceLightStatusBars = !dark
                c.isAppearanceLightNavigationBars = !dark
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        if (landscape) {
            OverlayLayout(
                video = video,
                playlist = playlist,
                ui = ui,
                isThis = isThis,
                position = position,
                duration = duration,
                speed = speed,
                playing = playing,
                sleepLeft = sleepLeft,
                fullscreen = fullscreen,
                touchMode = touchMode,
                sheetOpen = showSpeed || showSleep || showVideos || showQuality,
                actions = actions,
                onVideoBounds = onVideoBounds,
                onFullscreen = { fullscreen = !fullscreen },
                onTouchMode = { touchMode = it },
                onSpeed = { showSpeed = true },
                onSleep = { showSleep = true },
                onVideos = { showVideos = true },
                onQuality = { showQuality = true },
            )
        } else {
            // Portrait: status bar over the black video strip gets light icons
            LaunchedEffect(activity, dark) {
                activity?.window?.let { w ->
                    val c = WindowCompat.getInsetsController(w, w.decorView)
                    c.show(WindowInsetsCompat.Type.systemBars())
                    c.isAppearanceLightStatusBars = false
                    c.isAppearanceLightNavigationBars = !dark
                }
            }
            PortraitLayout(
                video = video,
                playlist = playlist,
                ui = ui,
                isThis = isThis,
                position = position,
                duration = duration,
                speed = speed,
                playing = playing,
                sleepLeft = sleepLeft,
                compactHeight = maxHeight < 700.dp,
                touchMode = touchMode,
                actions = actions,
                onVideoBounds = onVideoBounds,
                onFullscreen = { fullscreen = true },
                onTouchMode = { touchMode = it },
                onSpeed = { showSpeed = true },
                onSleep = { showSleep = true },
                onVideos = { showVideos = true },
                onQuality = { showQuality = true },
            )
        }
    }

    if (showVideos && playlist != null) {
        VideosSheet(
            playlist = playlist,
            currentId = video.id,
            isPlaying = isThis && ui.isPlaying,
            onPick = {
                showVideos = false
                if (it.id != video.id) actions.onPlayVideo(it)
            },
            onDismiss = { showVideos = false }
        )
    }
    if (showQuality) {
        QualitySheet(
            selected = quality,
            actual = ui.actualQuality,
            onSelect = {
                actions.onQuality(it)
                showQuality = false
            },
            onYouTubeMenu = {
                showQuality = false
                touchMode = true
            },
            onDismiss = { showQuality = false }
        )
    }
    if (showSpeed) {
        SpeedSheet(speed = speed, rates = ui.rates, onSpeed = actions.onSpeed, onDismiss = { showSpeed = false })
    }
    if (showSleep) {
        SleepSheet(
            endsAt = sleepEndsAt,
            onStart = {
                actions.onSleepStart(it)
                showSleep = false
            },
            onExtend = actions.onSleepExtend,
            onCancel = {
                actions.onSleepCancel()
                showSleep = false
            },
            onDismiss = { showSleep = false }
        )
    }
}

// =====================================================================================
// Portrait – video on top, BookPlayer-style controls below
// =====================================================================================

@Composable
private fun PortraitLayout(
    video: Video,
    playlist: Playlist?,
    ui: PlayerUi,
    isThis: Boolean,
    position: Long,
    duration: Long,
    speed: Float,
    playing: Boolean,
    sleepLeft: Long?,
    compactHeight: Boolean,
    touchMode: Boolean,
    actions: PlayerActions,
    onVideoBounds: (Rect) -> Unit,
    onFullscreen: () -> Unit,
    onTouchMode: (Boolean) -> Unit,
    onSpeed: () -> Unit,
    onSleep: () -> Unit,
    onVideos: () -> Unit,
    onQuality: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val multi = playlist != null && playlist.videoCount > 1
    var menu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        // ---------------- black strip with the top bar (the video below is the WebView behind
        //                  this screen, so nothing may be painted over the video area)
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .statusBarsPadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = actions.onBack) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Back", tint = Color.White, modifier = Modifier.width(32.dp))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "NOW PLAYING",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.55f)
                    )
                    val sub = playlist?.title ?: video.channel
                    if (sub.isNotBlank()) {
                        Text(
                            sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onFullscreen) {
                    Icon(Icons.Rounded.Fullscreen, "Fullscreen", tint = Color.White)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Options", tint = Color.White) }
                    PlayerMenu(
                        expanded = menu,
                        video = video,
                        position = position,
                        touchMode = touchMode,
                        actions = actions,
                        onTouchMode = onTouchMode,
                        onQuality = onQuality,
                        onDismiss = { menu = false }
                    )
                }
            }
        }
        VideoArea(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            touchMode = touchMode,
            onVideoBounds = onVideoBounds,
            onTap = actions.onTogglePlay,
            onSeekBy = actions.onSeekBy,
            onTouchMode = onTouchMode,
        )

        // ---------------- controls
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(cs.background)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(if (compactHeight) 14.dp else 24.dp))
            TitleBlock(video, playlist, speed)
            Spacer(Modifier.height(if (compactHeight) 14.dp else 28.dp))
            TimeBlock(position, duration, speed, compactHeight)
            Spacer(Modifier.height(8.dp))
            SeekBar(position, duration, actions.onSeekTo)
            Spacer(Modifier.height(if (compactHeight) 8.dp else 18.dp))
            Controls(playing, isThis && ui.buffering, actions, if (compactHeight) 70.dp else 80.dp)
            Spacer(Modifier.height(if (compactHeight) 16.dp else 26.dp))
            PillsRow(
                speed = speed,
                sleepLeft = sleepLeft,
                finished = video.finished,
                videosLabel = if (multi && playlist != null) "${playlist.indexOf(video.id) + 1}/${playlist.videoCount}" else null,
                onSpeed = onSpeed,
                onSleep = onSleep,
                onVideos = onVideos,
                onFinished = { actions.onMarkFinished(!video.finished) },
                quality = ui.actualQuality,
                onQuality = onQuality,
            )
            val err = ui.error
            if (isThis && err != null) {
                ErrorBlock(
                    error = err,
                    canSkip = multi,
                    onOpenInYouTube = { actions.onOpenInYouTube(video.watchUrl + "&t=${position / 1000}s") },
                    onNext = actions.onNext,
                    onRetry = actions.onTogglePlay
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PlayerMenu(
    expanded: Boolean,
    video: Video,
    position: Long,
    touchMode: Boolean,
    actions: PlayerActions,
    onTouchMode: (Boolean) -> Unit,
    onQuality: () -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Video quality") },
            leadingIcon = { Icon(Icons.Rounded.HighQuality, null) },
            onClick = {
                onDismiss()
                onQuality()
            }
        )
        DropdownMenuItem(
            text = { Text("Open in YouTube") },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
            onClick = {
                onDismiss()
                actions.onPause()
                actions.onOpenInYouTube(video.watchUrl + "&t=${position / 1000}s")
            }
        )
    }
}

/**
 * Transparent box over the video. Tap = play/pause, double-tap left/right = ∓10 s.
 * In "touch mode" it lets touches through to the YouTube player (to skip an ad, etc.).
 */
@Composable
private fun VideoArea(
    modifier: Modifier,
    touchMode: Boolean,
    onVideoBounds: (Rect) -> Unit,
    onTap: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onTouchMode: (Boolean) -> Unit,
) {
    var feedback by remember { mutableStateOf<SeekFeedback?>(null) }
    val tap by rememberUpdatedState(onTap)
    val seekBy by rememberUpdatedState(onSeekBy)
    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(700)
            feedback = null
        }
    }
    Box(modifier.onGloballyPositioned { onVideoBounds(it.boundsInRoot()) }) {
        if (!touchMode) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { tap() },
                            onDoubleTap = { offset ->
                                val forward = offset.x > size.width / 2f
                                seekBy(if (forward) 10_000L else -10_000L)
                                val prev = feedback
                                val secs = if (prev != null && prev.forward == forward) prev.seconds + 10 else 10
                                feedback = SeekFeedback(forward, secs, System.nanoTime())
                            }
                        )
                    }
            )
            feedback?.let { f -> SeekBubble(f, Modifier.align(if (f.forward) Alignment.CenterEnd else Alignment.CenterStart)) }
        } else {
            Surface(
                onClick = { onTouchMode(false) },
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.7f),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.TouchApp, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Done", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun SeekBubble(f: SeekFeedback, modifier: Modifier) {
    Box(
        modifier
            .padding(horizontal = 32.dp)
            .size(84.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (f.forward) Icons.Rounded.FastForward else Icons.Rounded.FastRewind,
                null, tint = Color.White
            )
            Text(
                (if (f.forward) "+" else "−") + "${f.seconds} s",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TitleBlock(video: Video, playlist: Playlist?, speed: Float) {
    val cs = MaterialTheme.colorScheme
    Column {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tag("YOUTUBE")
            if (playlist != null && playlist.videoCount > 1) Tag("VIDEO ${playlist.indexOf(video.id) + 1} / ${playlist.videoCount}")
            if (speed != 1f) Tag(speedLabel(speed))
            if (video.finished) Tag("WATCHED")
        }
        Spacer(Modifier.height(14.dp))
        Text(
            video.title,
            style = MaterialTheme.typography.headlineSmall,
            color = cs.onBackground,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        if (video.channel.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                video.channel,
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TimeBlock(position: Long, duration: Long, speed: Float, compact: Boolean) {
    val cs = MaterialTheme.colorScheme
    val pct = if (duration > 0) (position * 100f / duration).roundToInt().coerceIn(0, 100) else 0
    val remainingAtSpeed = if (speed > 0f) ((duration - position).coerceAtLeast(0L) / speed).toLong() else 0L
    Column {
        Text(
            formatTime(position),
            style = if (compact) MaterialTheme.typography.displaySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Light)
            else MaterialTheme.typography.displayMedium,
            color = cs.onBackground,
            maxLines = 1
        )
        Text(
            if (duration > 0) "of ${formatTime(duration)}  ·  $pct%  ·  ${formatDurationShort(remainingAtSpeed)} left"
            else "Loading…",
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant
        )
    }
}

@Composable
private fun Controls(playing: Boolean, buffering: Boolean, actions: PlayerActions, playSize: Dp) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SkipMinuteButton(forward = false, size = 50.dp, onClick = { actions.onSeekBy(-60_000) })
        RoundIconButton(
            Icons.Rounded.Replay10, "Back 10 seconds", 58.dp,
            onClick = { actions.onSeekBy(-10_000) },
            iconScale = 0.56f
        )
        PlayButton(
            playing = playing,
            buffering = buffering,
            size = playSize,
            onClick = actions.onTogglePlay,
            container = cs.onBackground,
            content = cs.background
        )
        RoundIconButton(
            Icons.Rounded.Forward10, "Forward 10 seconds", 58.dp,
            onClick = { actions.onSeekBy(10_000) },
            iconScale = 0.56f
        )
        SkipMinuteButton(forward = true, size = 50.dp, onClick = { actions.onSeekBy(60_000) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PillsRow(
    speed: Float,
    sleepLeft: Long?,
    finished: Boolean,
    videosLabel: String?,
    onSpeed: () -> Unit,
    onSleep: () -> Unit,
    onVideos: () -> Unit,
    onFinished: () -> Unit,
    quality: String,
    onQuality: () -> Unit,
) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Pill(speedLabel(speed), onClick = onSpeed, icon = Icons.Rounded.Speed)
        Pill(
            if (sleepLeft != null) formatTime(sleepLeft) else "Timer",
            onClick = onSleep,
            icon = Icons.Rounded.Bedtime,
            selected = sleepLeft != null
        )
        if (videosLabel != null) {
            Pill(videosLabel, onClick = onVideos, icon = Icons.AutoMirrored.Rounded.QueueMusic)
        } else {
            Pill(
                if (finished) "Watched" else "Mark watched",
                onClick = onFinished,
                icon = if (finished) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ErrorBlock(
    error: String,
    canSkip: Boolean,
    onOpenInYouTube: () -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = cs.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(error, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Pill("Open in YouTube", onClick = onOpenInYouTube, icon = Icons.AutoMirrored.Rounded.OpenInNew, selected = true)
            Pill("Try again", onClick = onRetry, icon = Icons.Rounded.Replay)
            if (canSkip) Pill("Next video", onClick = onNext, icon = Icons.Rounded.SkipNext)
        }
    }
}

// =====================================================================================
// Landscape / fullscreen – video fills the screen, controls fade in on tap
// =====================================================================================

@Composable
private fun OverlayLayout(
    video: Video,
    playlist: Playlist?,
    ui: PlayerUi,
    isThis: Boolean,
    position: Long,
    duration: Long,
    speed: Float,
    playing: Boolean,
    sleepLeft: Long?,
    fullscreen: Boolean,
    touchMode: Boolean,
    sheetOpen: Boolean,
    actions: PlayerActions,
    onVideoBounds: (Rect) -> Unit,
    onFullscreen: () -> Unit,
    onTouchMode: (Boolean) -> Unit,
    onSpeed: () -> Unit,
    onSleep: () -> Unit,
    onVideos: () -> Unit,
    onQuality: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val multi = playlist != null && playlist.videoCount > 1
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf<SeekFeedback?>(null) }
    var menu by remember { mutableStateOf(false) }
    val seekBy by rememberUpdatedState(actions.onSeekBy)

    // System bars hidden while the controls are hidden
    LaunchedEffect(controlsVisible, activity) {
        val w = activity?.window ?: return@LaunchedEffect
        val c = WindowCompat.getInsetsController(w, w.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.isAppearanceLightStatusBars = false
        c.isAppearanceLightNavigationBars = false
        if (controlsVisible) c.show(WindowInsetsCompat.Type.systemBars())
        else c.hide(WindowInsetsCompat.Type.systemBars())
    }
    // Auto-hide controls
    LaunchedEffect(controlsVisible, playing, interaction, sheetOpen, menu) {
        if (controlsVisible && playing && !sheetOpen && !menu) {
            delay(3_500)
            controlsVisible = false
        }
    }
    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(700)
            feedback = null
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { onVideoBounds(it.boundsInRoot()) }
    ) {
        if (touchMode) {
            Surface(
                onClick = { onTouchMode(false) },
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.7f),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(12.dp)
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.TouchApp, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Done", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (!touchMode) {

        // Gesture layer: tap = controls, double-tap left/right = ±10 s
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            controlsVisible = !controlsVisible
                            interaction++
                        },
                        onDoubleTap = { offset ->
                            val forward = offset.x > size.width / 2f
                            seekBy(if (forward) 10_000L else -10_000L)
                            val prev = feedback
                            val secs = if (prev != null && prev.forward == forward) prev.seconds + 10 else 10
                            feedback = SeekFeedback(forward, secs, System.nanoTime())
                            interaction++
                        }
                    )
                }
        )

        feedback?.let { f -> SeekBubble(f, Modifier.align(if (f.forward) Alignment.CenterEnd else Alignment.CenterStart)) }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
            ) {
                // ---------- top bar
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
                        .safeDrawingPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (fullscreen) onFullscreen() else actions.onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            video.title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val sub = if (multi && playlist != null) "${playlist.title} · ${playlist.indexOf(video.id) + 1}/${playlist.videoCount}" else video.channel
                        if (sub.isNotBlank()) {
                            Text(
                                sub,
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    OverlayChip(speedLabel(speed)) {
                        onSpeed()
                        interaction++
                    }
                    Spacer(Modifier.width(6.dp))
                    IconButton(onClick = {
                        onSleep()
                        interaction++
                    }) {
                        Icon(
                            Icons.Rounded.Bedtime,
                            "Sleep timer",
                            tint = if (sleepLeft != null) Color.White else Color.White.copy(alpha = 0.7f)
                        )
                    }
                    if (multi) {
                        IconButton(onClick = {
                            onVideos()
                            interaction++
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Videos", tint = Color.White)
                        }
                    }
                    Box {
                        IconButton(onClick = {
                            menu = true
                            interaction++
                        }) { Icon(Icons.Rounded.MoreVert, "Options", tint = Color.White) }
                        PlayerMenu(
                            expanded = menu,
                            video = video,
                            position = position,
                            touchMode = touchMode,
                            actions = actions,
                            onTouchMode = onTouchMode,
                            onQuality = onQuality,
                            onDismiss = { menu = false }
                        )
                    }
                }

                // ---------- center controls
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SkipMinuteButton(
                        forward = false, size = 48.dp,
                        onClick = {
                            actions.onSeekBy(-60_000)
                            interaction++
                        },
                        tint = Color.White,
                        borderColor = Color.White.copy(alpha = 0.35f)
                    )
                    RoundIconButton(
                        Icons.Rounded.Replay10, "Back 10 seconds", 56.dp,
                        onClick = {
                            actions.onSeekBy(-10_000)
                            interaction++
                        },
                        tint = Color.White,
                        iconScale = 0.58f
                    )
                    PlayButton(
                        playing = playing,
                        buffering = isThis && ui.buffering,
                        size = 76.dp,
                        onClick = {
                            actions.onTogglePlay()
                            interaction++
                        },
                        container = Color.White,
                        content = Color.Black
                    )
                    RoundIconButton(
                        Icons.Rounded.Forward10, "Forward 10 seconds", 56.dp,
                        onClick = {
                            actions.onSeekBy(10_000)
                            interaction++
                        },
                        tint = Color.White,
                        iconScale = 0.58f
                    )
                    SkipMinuteButton(
                        forward = true, size = 48.dp,
                        onClick = {
                            actions.onSeekBy(60_000)
                            interaction++
                        },
                        tint = Color.White,
                        borderColor = Color.White.copy(alpha = 0.35f)
                    )
                }

                // ---------- bottom bar
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                        .safeDrawingPadding()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    SeekBar(
                        positionMs = position,
                        durationMs = duration,
                        onSeek = {
                            actions.onSeekTo(it)
                            interaction++
                        },
                        color = Color.White,
                        track = Color.White.copy(alpha = 0.25f),
                        showLabels = false
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${formatTime(position)} / ${formatTime(duration)}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge
                        )
                        if (sleepLeft != null) {
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "· sleep ${formatTime(sleepLeft)}",
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = {
                            onFullscreen()
                            interaction++
                        }) {
                            Icon(
                                if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                "Fullscreen",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        val err = ui.error
        if (isThis && err != null) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .heightIn(min = 0.dp)
                    .padding(24.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(err, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OverlayChip("Open in YouTube") { actions.onOpenInYouTube(video.watchUrl + "&t=${position / 1000}s") }
                    if (multi) OverlayChip("Next video") { actions.onNext() }
                }
            }
        }
        } // !touchMode
    }
}

@Composable
private fun OverlayChip(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.14f),
        contentColor = Color.White
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

