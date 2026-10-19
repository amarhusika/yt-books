package com.amar.ytbooks.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amar.ytbooks.Graph
import com.amar.ytbooks.data.Video
import com.amar.ytbooks.player.SleepTimer
import com.amar.ytbooks.player.YtPlayer
import com.amar.ytbooks.player.YtPlayer.States
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Route { Library, Book, Player }

data class PlayerUi(
    val mediaId: String? = null,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val buffering: Boolean = false,
    val ended: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val rates: List<Float> = emptyList(),
    val errorCode: Int? = null,
    /** What YouTube is actually playing, e.g. "hd720". */
    val actualQuality: String = "",
) {
    val error: String?
        get() = when (errorCode) {
            null -> null
            101, 150 -> "The owner doesn't allow this video to play outside YouTube."
            100 -> "This video is private or was removed."
            152, 153 -> "YouTube refused the player here (error $errorCode)."
            -2 -> "Can't reach YouTube. Check your internet connection."
            else -> "This video can't be played here (error $errorCode)."
        }
}

/**
 * Plays YouTube videos through [YtPlayer] and does everything BookPlayer's service did:
 * remembers position, smart rewind after a pause, per-video speed, autoplay next, sleep timer.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    val yt = YtPlayer(app)

    private val stack = mutableListOf(Route.Library)
    private val _route = MutableStateFlow(Route.Library)
    val route: StateFlow<Route> = _route.asStateFlow()

    /** Key of the playlist shown on the Book screen. */
    private val _bookKey = MutableStateFlow<String?>(null)
    val bookKey: StateFlow<String?> = _bookKey.asStateFlow()

    private val _ui = MutableStateFlow(PlayerUi())
    val ui: StateFlow<PlayerUi> = _ui.asStateFlow()

    /** Id (playlistKey|videoId) of the loaded video. */
    private var currentId: String? = null

    /** What the user asked for (play/pause) – survives buffering and ads. */
    private var wantPlay = false

    /** Position is only saved once the loaded video has really started (avoids saving 0 while loading). */
    private var started = false

    /** Wall-clock time of the last pause; 0 = no smart rewind pending. */
    private var pausedAtWall = 0L

    init {
        yt.setQuality(Graph.settings.current.quality, reload = false)
        yt.listener = object : YtPlayer.Listener {
            override fun onStateChanged(old: Int, new: Int) = handleState(old, new)
            override fun onError(code: Int) {
                wantPlay = false
                refresh()
            }

            override fun onSpeedChanged(speed: Float) {
                currentId?.let { Graph.library.setSpeed(it, speed) }
            }
        }

        viewModelScope.launch {
            yt.state.collect {
                refresh()
                val id = currentId
                if (id != null && it.durationMs > 0 && started) Graph.library.setDuration(id, it.durationMs)
            }
        }

        // Save position every 2 s while playing
        viewModelScope.launch {
            while (isActive) {
                delay(2_000)
                if (yt.state.value.playerState == States.PLAYING) saveCurrent()
            }
        }

        // Sleep timer: fade out during the last 10 s, then pause
        viewModelScope.launch {
            SleepTimer.endsAt.collectLatest { end ->
                if (end == null) {
                    yt.setVolume(100)
                    return@collectLatest
                }
                var lastVol = 100
                while (isActive) {
                    val left = end - System.currentTimeMillis()
                    if (left <= 0L) {
                        pause()
                        delay(400)
                        yt.setVolume(100)
                        SleepTimer.cancel()
                        break
                    }
                    val vol = if (left < 10_000L) ((left / 10_000f) * 100).toInt().coerceIn(5, 100) else 100
                    if (vol != lastVol) {
                        yt.setVolume(vol)
                        lastVol = vol
                    }
                    delay(250)
                }
            }
        }
    }

    private fun refresh() {
        val s = yt.state.value
        val id = currentId
        _ui.value = PlayerUi(
            mediaId = id,
            isPlaying = s.playerState == States.PLAYING,
            playWhenReady = wantPlay,
            buffering = wantPlay && (s.playerState == States.BUFFERING || s.playerState == States.UNSTARTED) && s.error == null,
            ended = s.playerState == States.ENDED,
            positionMs = s.positionMs,
            durationMs = s.durationMs.takeIf { it > 0 } ?: (Graph.library.video(id)?.durationMs ?: 0L),
            speed = s.speed,
            rates = s.rates,
            errorCode = s.error,
            actualQuality = s.quality,
        )
    }

    private fun handleState(old: Int, new: Int) {
        val id = currentId ?: return
        when (new) {
            States.PLAYING -> {
                started = true
                wantPlay = true
                if (pausedAtWall > 0L) {
                    val pause = System.currentTimeMillis() - pausedAtWall
                    pausedAtWall = 0L
                    val rewind = Graph.settings.current.rewindMsFor(pause)
                    val pos = yt.state.value.positionMs
                    if (rewind > 0L && pos > 0L) yt.seekTo((pos - rewind).coerceAtLeast(0L))
                }
            }

            States.PAUSED -> {
                if (started && (old == States.PLAYING || old == States.BUFFERING)) {
                    pausedAtWall = System.currentTimeMillis()
                    saveCurrent()
                }
            }

            States.ENDED -> {
                // Only a video that really played can end. Right after switching to the next video
                // the player may still report a late "ended" from the previous one – ignore it,
                // otherwise the new video would be skipped (03 → 05).
                if (!started) return
                started = false
                wantPlay = false
                pausedAtWall = 0L
                Graph.library.markFinished(id, true)
                if (Graph.settings.current.autoPlayNext) playNext(id)
            }
        }
        refresh()
    }

    private fun saveCurrent() {
        val id = currentId ?: return
        if (!started) return
        val s = yt.state.value
        if (s.playerState == States.ENDED) return
        Graph.library.saveProgress(id, s.positionMs, s.durationMs.takeIf { it > 0 })
    }

    /** Start position when a video is reopened – with smart rewind applied. */
    private fun startPositionFor(v: Video): Long {
        if (v.finished) return 0L
        if (v.positionMs <= 0L) return 0L
        if (v.durationMs > 0 && v.positionMs >= v.durationMs - 1_500) return 0L
        val pause = if (v.lastPlayedAt > 0) System.currentTimeMillis() - v.lastPlayedAt else Long.MAX_VALUE
        val rewind = Graph.settings.current.rewindMsFor(pause)
        return (v.positionMs - rewind).coerceAtLeast(0L)
    }

    /** Going through the playlist in order: the next video always starts from the beginning. */
    private fun playNext(finishedId: String) {
        val next = Graph.library.nextInPlaylist(finishedId) ?: return
        val speed = yt.state.value.speed
        Graph.library.setSpeed(next.id, speed)
        Graph.library.resetProgress(next.id)
        load(Graph.library.video(next.id) ?: next, play = true, speed = speed, fromStart = true)
    }

    private fun load(v: Video, play: Boolean, speed: Float = v.speed, fromStart: Boolean = false) {
        currentId = v.id
        started = false
        pausedAtWall = 0L
        wantPlay = play
        // The playlist's progress follows the video you are on: everything before it counts as
        // heard, everything after it as not heard yet.
        Graph.library.setCurrent(v.id)
        val start = if (fromStart || v.finished) 0L else startPositionFor(v)
        yt.load(v.videoId, start, play, speed)
        refresh()
    }

    // ------------------------------------------------------------ navigation

    fun navigate(route: Route) {
        if (stack.lastOrNull() == route) return
        stack.remove(route)
        stack.add(route)
        _route.value = route
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
        _route.value = stack.last()
    }

    fun openBook(key: String) {
        _bookKey.value = key
        navigate(Route.Book)
    }

    // ------------------------------------------------------------ controls

    fun open(v: Video, play: Boolean = true) {
        val same = currentId == v.id && yt.state.value.videoId == v.videoId && yt.state.value.error == null
        if (!same) {
            saveCurrent()
            if (v.finished) Graph.library.resetProgress(v.id)
            load(Graph.library.video(v.id) ?: v, play)
            return
        }
        if (play) {
            if (yt.state.value.playerState == States.ENDED) yt.seekTo(0L)
            wantPlay = true
            yt.play()
        }
        refresh()
    }

    fun togglePlay() {
        val id = currentId ?: return
        val s = yt.state.value
        if (s.error != null) {
            Graph.library.video(id)?.let { load(it, play = true) }
            return
        }
        if (wantPlay && s.playerState != States.ENDED) {
            pause()
        } else {
            if (s.playerState == States.ENDED) {
                Graph.library.resetProgress(id)
                yt.seekTo(0L)
            }
            wantPlay = true
            yt.play()
            refresh()
        }
    }

    fun pause() {
        wantPlay = false
        yt.pause()
        refresh()
    }

    fun seekBy(deltaMs: Long) {
        val s = yt.state.value
        var target = s.positionMs + deltaMs
        if (target < 0L) target = 0L
        val d = s.durationMs
        if (d > 0 && target > d) target = (d - 500L).coerceAtLeast(0L)
        seekTo(target)
    }

    fun seekTo(positionMs: Long) {
        // Seeking while paused: don't rewind again on resume
        if (!wantPlay) pausedAtWall = 0L
        yt.seekTo(positionMs.coerceAtLeast(0L))
        if (!wantPlay && started) {
            currentId?.let { Graph.library.saveProgress(it, positionMs.coerceAtLeast(0L), yt.state.value.durationMs.takeIf { d -> d > 0 }) }
        }
        refresh()
    }

    fun setSpeed(speed: Float) {
        yt.setSpeed(speed)
        currentId?.let { Graph.library.setSpeed(it, speed) }
    }

    fun playNextNow() {
        val id = currentId ?: return
        saveCurrent()
        playNext(id)
    }

    fun stopIfCurrent(id: String) {
        if (currentId == id) {
            saveCurrent()
            wantPlay = false
            yt.stop()
            currentId = null
            refresh()
        }
    }

    /** Touch mode: YouTube's own controls are shown and touches go straight to its player. */
    fun setTouchMode(enabled: Boolean) {
        yt.touchEnabled = enabled
        yt.setYouTubeControls(enabled)
    }

    fun setQuality(q: String) {
        Graph.settings.setQuality(q)
        yt.setQuality(q, reload = true)
    }

    /** App went to the background (home button, screen off): pause and write everything to disk. */
    fun onAppBackground() {
        if (currentId != null) {
            saveCurrent()
            if (wantPlay) pause()
        }
        Graph.library.flush()
    }

    override fun onCleared() {
        saveCurrent()
        Graph.library.flush()
        yt.release()
        super.onCleared()
    }
}
