package com.amar.ytbooks.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amar.ytbooks.Graph
import com.amar.ytbooks.data.Playlist
import com.amar.ytbooks.player.SleepTimer
import com.amar.ytbooks.ui.components.AddSheet
import com.amar.ytbooks.ui.screens.BookActions
import com.amar.ytbooks.ui.screens.BookScreen
import com.amar.ytbooks.ui.screens.LibraryActions
import com.amar.ytbooks.ui.screens.LibraryScreen
import com.amar.ytbooks.ui.screens.PlayerActions
import com.amar.ytbooks.ui.screens.PlayerScreen
import com.amar.ytbooks.util.findActivity
import kotlin.math.roundToInt

@Composable
fun AppRoot(vm: MainViewModel) {
    val context = LocalContext.current
    val route by vm.route.collectAsStateWithLifecycle()
    val playlists by Graph.library.playlists.collectAsStateWithLifecycle()
    val importing by Graph.library.importing.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val bookKey by vm.bookKey.collectAsStateWithLifecycle()
    val sleepEndsAt by SleepTimer.endsAt.collectAsStateWithLifecycle()
    val settings by Graph.settings.state.collectAsStateWithLifecycle()

    var showAdd by remember { mutableStateOf(false) }
    var lastMessage by remember { mutableStateOf<String?>(null) }
    var videoRect by remember { mutableStateOf<Rect?>(null) }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        Graph.library.messages.collect {
            lastMessage = it
            snackbar.showSnackbar(it)
        }
    }
    // A freshly added playlist closes the sheet and opens the book
    LaunchedEffect(Unit) {
        Graph.library.added.collect { key ->
            showAdd = false
            vm.openBook(key)
        }
    }

    val nowId = ui.mediaId
    val nowVideo = playlists.firstNotNullOfOrNull { p -> p.videos.firstOrNull { it.id == nowId } }
    val nowPlaylist = playlists.firstOrNull { p -> p.videos.any { it.id == nowId } }
    val openPlaylist = playlists.firstOrNull { it.key == bookKey }

    // Screen stays on while something plays (YouTube stops when the screen goes off)
    val activity = remember(context) { context.findActivity() }
    LaunchedEffect(ui.playWhenReady, activity) {
        val w = activity?.window ?: return@LaunchedEffect
        if (ui.playWhenReady) w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    BackHandler(enabled = route != Route.Library) { vm.back() }

    val openInYouTube: (String) -> Unit = remember(context) {
        { url: String ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
            }
        }
    }

    val resume: (Playlist) -> Unit = remember(vm) {
        { p: Playlist ->
            p.resumeVideo()?.let { v ->
                vm.open(v)
                vm.navigate(Route.Player)
            }
        }
    }

    val libraryActions = remember(vm) {
        LibraryActions(
            onOpenBook = { p -> vm.openBook(p.key) },
            onResume = resume,
            onTogglePlay = vm::togglePlay,
            onSeekBy = vm::seekBy,
            onOpenPlayer = { vm.navigate(Route.Player) },
            onAdd = {
                lastMessage = null
                showAdd = true
            },
            onRefresh = { p -> Graph.library.refresh(p.key) },
            onReset = { p ->
                Graph.library.resetPlaylist(p.key)
                if (p.videos.any { it.id == vm.ui.value.mediaId }) vm.seekTo(0L)
            },
            onRemove = { p ->
                p.videos.forEach { vm.stopIfCurrent(it.id) }
                Graph.library.removePlaylist(p.key)
            },
        )
    }

    val bookActions = remember(vm) {
        BookActions(
            onBack = { vm.back() },
            onPlayVideo = { v ->
                vm.open(v)
                vm.navigate(Route.Player)
            },
            onResume = resume,
            onTogglePlay = vm::togglePlay,
            onSeekBy = vm::seekBy,
            onOpenPlayer = { vm.navigate(Route.Player) },
            onRefresh = { p -> Graph.library.refresh(p.key) },
            onOpenInYouTube = openInYouTube,
            onRemoveBook = { p ->
                p.videos.forEach { vm.stopIfCurrent(it.id) }
                Graph.library.removePlaylist(p.key)
                vm.back()
            },
            onResetBook = { p ->
                Graph.library.resetPlaylist(p.key)
                if (p.videos.any { it.id == vm.ui.value.mediaId }) vm.seekTo(0L)
            },
            onMarkFinished = { v, done ->
                if (done && vm.ui.value.mediaId == v.id) vm.pause()
                Graph.library.markFinished(v.id, done)
            },
            onResetVideo = { v ->
                Graph.library.resetProgress(v.id)
                if (vm.ui.value.mediaId == v.id) vm.seekTo(0L)
            },
        )
    }

    val playerActions = remember(vm) {
        PlayerActions(
            onBack = { vm.back() },
            onTogglePlay = vm::togglePlay,
            onPause = vm::pause,
            onSeekBy = vm::seekBy,
            onSeekTo = vm::seekTo,
            onSpeed = vm::setSpeed,
            onSleepStart = { SleepTimer.start(it) },
            onSleepExtend = { SleepTimer.extend(it) },
            onSleepCancel = { SleepTimer.cancel() },
            onMarkFinished = { done ->
                val id = vm.ui.value.mediaId
                if (id != null) {
                    if (done) vm.pause()
                    Graph.library.markFinished(id, done)
                }
            },
            onRestart = {
                val id = vm.ui.value.mediaId
                if (id != null) Graph.library.resetProgress(id)
                vm.seekTo(0L)
            },
            onPlayVideo = { v -> vm.open(v) },
            onNext = vm::playNextNow,
            onOpenInYouTube = { url ->
                vm.pause()
                openInYouTube(url)
            },
            onTouchMode = vm::setTouchMode,
            onQuality = vm::setQuality,
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ---- The YouTube player: one WebView that lives behind every screen, so playback
        //      continues while you browse the library. On the player screen it is moved to
        //      the video area; elsewhere the opaque screens cover it.
        //      (YouTube needs a real-sized player, so before the first visit it fills the screen.)
        val density = LocalDensity.current
        val rect = videoRect?.takeIf { it.width > 1f && it.height > 1f }
        val playerModifier = if (rect == null) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
        }
        AndroidView(
            factory = {
                vm.yt.webView.also { wv -> (wv.parent as? ViewGroup)?.removeView(wv) }
            },
            modifier = playerModifier
        )

        AnimatedContent(
            targetState = route,
            transitionSpec = {
                if (targetState.ordinal > initialState.ordinal) {
                    (slideInVertically(tween(320)) { it / 8 } + fadeIn(tween(260))) togetherWith
                        fadeOut(tween(160))
                } else {
                    fadeIn(tween(220)) togetherWith
                        (slideOutVertically(tween(260)) { it / 8 } + fadeOut(tween(200)))
                }
            },
            label = "route"
        ) { r ->
            when (r) {
                Route.Library -> LibraryScreen(
                    playlists = playlists,
                    nowVideo = nowVideo,
                    nowPlaylist = nowPlaylist,
                    ui = ui,
                    importing = importing,
                    actions = libraryActions,
                )

                Route.Book -> {
                    val p = openPlaylist
                    if (p == null) {
                        LaunchedEffect(Unit) { vm.back() }
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                    } else {
                        BookScreen(
                            playlist = p,
                            nowVideo = nowVideo,
                            nowPlaylist = nowPlaylist,
                            ui = ui,
                            importing = importing,
                            actions = bookActions,
                        )
                    }
                }

                Route.Player -> {
                    val v = nowVideo
                    if (v == null) {
                        LaunchedEffect(Unit) { vm.back() }
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                    } else {
                        PlayerScreen(
                            video = v,
                            playlist = nowPlaylist,
                            ui = ui,
                            sleepEndsAt = sleepEndsAt,
                            quality = settings.quality,
                            actions = playerActions,
                            onVideoBounds = { videoRect = it },
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 96.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
    }

    if (showAdd) {
        AddSheet(
            importing = importing,
            message = lastMessage,
            onAdd = {
                lastMessage = null
                Graph.library.addFromText(it)
            },
            onDismiss = { showAdd = false }
        )
    }
}
