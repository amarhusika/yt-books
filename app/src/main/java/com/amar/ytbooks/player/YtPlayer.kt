package com.amar.ytbooks.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import java.util.Locale
import kotlin.math.roundToLong

/**
 * The official YouTube IFrame player inside one long-lived WebView.
 * Kotlin talks to it with small JS calls; the page reports back through [Bridge].
 */
@SuppressLint("SetJavaScriptEnabled")
class YtPlayer(context: Context) {

    object States {
        const val UNSTARTED = -1
        const val ENDED = 0
        const val PLAYING = 1
        const val PAUSED = 2
        const val BUFFERING = 3
        const val CUED = 5
    }

    data class State(
        val videoId: String? = null,
        val ready: Boolean = false,
        val playerState: Int = States.UNSTARTED,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val speed: Float = 1f,
        val rates: List<Float> = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f),
        val error: Int? = null,
        /** Quality YouTube reports it is playing (e.g. "hd720"), empty if unknown. */
        val quality: String = "",
    )

    interface Listener {
        fun onStateChanged(old: Int, new: Int) {}
        fun onError(code: Int) {}
        fun onSpeedChanged(speed: Float) {}
    }

    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    var listener: Listener? = null

    /** When true, touches go straight to the YouTube player (skip ads, captions…). */
    var touchEnabled: Boolean = false

    private var hostReady = false
    private val pending = mutableListOf<String>()

    val origin: String = "https://" + context.packageName.lowercase(Locale.ROOT)

    val webView: WebView = WebView(context.applicationContext).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setBackgroundColor(Color.BLACK)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        addJavascriptInterface(Bridge(), "Android")
        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                hostReady = true
                val queued = pending.toList()
                pending.clear()
                queued.forEach { evaluateJavascript(it, null) }
            }

            // Links inside the player (YouTube logo, "Watch on YouTube") must not navigate the host page.
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                request?.isForMainFrame == true
        }
        setOnTouchListener { _, _ -> !touchEnabled }
        val html = context.assets.open("ytplayer.html").bufferedReader().use { it.readText() }
            .replace("__ORIGIN__", origin)
        loadDataWithBaseURL("$origin/", html, "text/html", "utf-8", null)
    }

    // ------------------------------------------------------------------ commands

    private fun js(code: String) {
        main.post {
            if (hostReady) webView.evaluateJavascript(code, null) else pending += code
        }
    }

    private fun num(v: Double) = String.format(Locale.ROOT, "%.3f", v)

    fun load(videoId: String, startMs: Long, play: Boolean, speed: Float) {
        val safeId = videoId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        _state.update {
            it.copy(
                videoId = safeId,
                playerState = States.UNSTARTED,
                positionMs = startMs,
                durationMs = 0L,
                error = null,
            )
        }
        js("ytLoad('$safeId', ${num(startMs / 1000.0)}, $play, ${num(speed.toDouble())});")
    }

    fun play() = js("ytPlay();")
    fun pause() = js("ytPause();")

    fun seekTo(ms: Long) {
        _state.update { it.copy(positionMs = ms.coerceAtLeast(0L)) }
        js("ytSeek(${num(ms.coerceAtLeast(0L) / 1000.0)});")
    }

    fun setSpeed(speed: Float) = js("ytRate(${num(speed.toDouble())});")

    /** 0–100 */
    fun setVolume(volume: Int) = js("ytVolume(${volume.coerceIn(0, 100)});")

    private var quality = "default"
    private var youtubeControls = false

    /** Suggested quality: default (auto), hd1080, hd720, large, medium, small, tiny. */
    fun setQuality(q: String, reload: Boolean = true) {
        if (q == quality) return
        quality = q
        val safe = q.filter { it.isLetterOrDigit() }
        js("ytSetQuality('$safe', ${reload && _state.value.videoId != null});")
    }

    /** Shows YouTube's own controls (gear → quality, captions, skip ad) – reloads the player at the same spot. */
    fun setYouTubeControls(on: Boolean) {
        if (on == youtubeControls) return
        youtubeControls = on
        js("ytControls($on);")
    }

    fun stop() {
        js("ytStop();")
        _state.update { it.copy(videoId = null, playerState = States.UNSTARTED, positionMs = 0L, durationMs = 0L, error = null) }
    }

    // ------------------------------------------------------------------ callbacks from JS

    private inner class Bridge {
        @JavascriptInterface
        fun onReady() {
            main.post { _state.update { it.copy(ready = true) } }
        }

        @JavascriptInterface
        fun onState(s: Int) {
            main.post {
                val old = _state.value.playerState
                if (old == s) return@post
                _state.update { it.copy(playerState = s, error = if (s == States.PLAYING) null else it.error) }
                listener?.onStateChanged(old, s)
            }
        }

        @JavascriptInterface
        fun onTime(current: Double, duration: Double, rate: Double, s: Int) {
            main.post {
                val prev = _state.value
                val speed = rate.toFloat()
                val newState = if (s == -1 && prev.playerState != -1) prev.playerState else s
                _state.update {
                    it.copy(
                        positionMs = (current * 1000).roundToLong().coerceAtLeast(0L),
                        durationMs = if (duration > 0) (duration * 1000).roundToLong() else it.durationMs,
                        speed = speed,
                        playerState = newState,
                    )
                }
                if (newState != prev.playerState) listener?.onStateChanged(prev.playerState, newState)
                if (speed != prev.speed) listener?.onSpeedChanged(speed)
            }
        }

        @JavascriptInterface
        fun onQuality(q: String) {
            main.post { if (_state.value.quality != q) _state.update { it.copy(quality = q) } }
        }

        @JavascriptInterface
        fun onRates(json: String) {
            main.post {
                val rates = try {
                    val a = JSONArray(json)
                    (0 until a.length()).map { a.getDouble(it).toFloat() }.filter { it > 0f }.sorted()
                } catch (_: Exception) {
                    emptyList()
                }
                if (rates.isNotEmpty()) _state.update { it.copy(rates = rates) }
            }
        }

        @JavascriptInterface
        fun onError(code: Int) {
            main.post {
                _state.update { it.copy(error = code) }
                listener?.onError(code)
            }
        }
    }

    fun release() {
        main.removeCallbacksAndMessages(null)
        webView.destroy()
    }
}
