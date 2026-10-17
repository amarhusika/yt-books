package com.amar.ytbooks.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Sleep timer – the view model watches it and pauses (fading out during the last 10 s). */
object SleepTimer {
    private val _endsAt = MutableStateFlow<Long?>(null)
    val endsAt: StateFlow<Long?> = _endsAt.asStateFlow()

    fun start(minutes: Int) {
        _endsAt.value = System.currentTimeMillis() + minutes * 60_000L
    }

    fun extend(minutes: Int) {
        val base = maxOf(_endsAt.value ?: System.currentTimeMillis(), System.currentTimeMillis())
        _endsAt.value = base + minutes * 60_000L
    }

    fun cancel() {
        _endsAt.value = null
    }
}
