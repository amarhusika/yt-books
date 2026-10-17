package com.amar.ytbooks.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import java.util.Locale
import kotlin.math.roundToInt

fun formatTime(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 1000L
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.ROOT, "%d:%02d", m, s)
}

fun formatDurationShort(ms: Long): String {
    val totalSec = ms.coerceAtLeast(0L) / 1000L
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    return when {
        h > 0 -> if (m > 0) "$h h $m min" else "$h h"
        m > 0 -> "$m min"
        else -> "$totalSec s"
    }
}

fun speedLabel(speed: Float): String {
    val s = String.format(Locale.ROOT, "%.2f", speed).trimEnd('0').trimEnd('.')
    return "$s×"
}

/** YouTube allows 0.25× – 2×. */
fun snapSpeed(v: Float): Float = ((v * 20f).roundToInt() / 20f).coerceIn(0.25f, 2f)

fun secondsLabel(sec: Int): String = when {
    sec == 0 -> "0 s"
    sec % 60 == 0 -> "${sec / 60} min"
    else -> "$sec s"
}

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
