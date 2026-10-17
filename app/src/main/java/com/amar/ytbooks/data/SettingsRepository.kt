package com.amar.ytbooks.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Fixed playback behaviour (there is no settings screen) – same rules as BookPlayer. */
data class AppSettings(
    /** A pause shorter than this counts as "short". */
    val pauseThresholdSec: Int = 5,
    /** How far to rewind after a short pause. */
    val shortRewindSec: Int = 5,
    /** How far to rewind after a longer pause. */
    val longRewindSec: Int = 10,
    /** When a video ends, continue with the next video of the same playlist. */
    val autoPlayNext: Boolean = true,
    /** Video quality: "default" = YouTube decides (auto). */
    val quality: String = "default",
) {
    fun rewindMsFor(pauseMs: Long): Long =
        if (pauseMs < pauseThresholdSec * 1000L) shortRewindSec * 1000L else longRewindSec * 1000L
}

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(AppSettings(quality = prefs.getString("quality", "default") ?: "default"))
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    val current: AppSettings get() = _state.value

    fun setQuality(q: String) {
        prefs.edit().putString("quality", q).apply()
        _state.update { it.copy(quality = q) }
    }
}
