package com.amar.ytbooks.data

import org.json.JSONArray
import org.json.JSONObject

/** One YouTube video inside a book (playlist), with its remembered position and speed. */
data class Video(
    val id: String,              // "<playlistKey>|<videoId>" – progress is kept per playlist
    val playlistKey: String,
    val videoId: String,
    val title: String,
    val channel: String = "",
    val order: Int = 0,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val speed: Float = 1f,
    val lastPlayedAt: Long = 0L,
    val finished: Boolean = false,
) {
    val progress: Float
        get() = when {
            finished -> 1f
            durationMs > 0 -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
            else -> 0f
        }

    val remainingMs: Long get() = (durationMs - positionMs).coerceAtLeast(0L)
    val inProgress: Boolean get() = !finished && positionMs > 0L
    val watchUrl: String get() = "https://www.youtube.com/watch?v=$videoId"

    fun toJson(): JSONObject = JSONObject().apply {
        put("vid", videoId)
        put("title", title)
        put("ch", channel)
        put("ord", order)
        put("dur", durationMs)
        put("pos", positionMs)
        put("speed", speed.toDouble())
        put("last", lastPlayedAt)
        put("done", finished)
    }

    companion object {
        fun idOf(playlistKey: String, videoId: String) = "$playlistKey|$videoId"

        fun fromJson(playlistKey: String, o: JSONObject): Video? = try {
            val vid = o.getString("vid")
            Video(
                id = idOf(playlistKey, vid),
                playlistKey = playlistKey,
                videoId = vid,
                title = o.optString("title"),
                channel = o.optString("ch"),
                order = o.optInt("ord"),
                durationMs = o.optLong("dur"),
                positionMs = o.optLong("pos"),
                speed = o.optDouble("speed", 1.0).toFloat(),
                lastPlayedAt = o.optLong("last"),
                finished = o.optBoolean("done"),
            )
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * A "book" on the home screen: one YouTube playlist (or a single video that was added on its own).
 * Videos keep the playlist's order.
 */
data class Playlist(
    val key: String,             // "pl:<listId>" or "v:<videoId>"
    val listId: String?,
    val title: String,
    val channel: String,
    val url: String,
    val addedAt: Long,
    val videos: List<Video>,
) {
    val videoCount: Int = videos.size
    val durationMs: Long = videos.sumOf { it.durationMs }
    val positionMs: Long = videos.sumOf { if (it.finished) it.durationMs else it.positionMs }
    val finishedCount: Int = videos.count { it.finished }
    val finished: Boolean = videos.isNotEmpty() && finishedCount == videos.size
    val lastPlayedAt: Long = videos.maxOfOrNull { it.lastPlayedAt } ?: 0L
    val started: Boolean = lastPlayedAt > 0L || videos.any { it.positionMs > 0L }
    val isSingle: Boolean = listId == null

    val progress: Float = when {
        videos.isEmpty() -> 0f
        durationMs > 0 -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        else -> finishedCount.toFloat() / videos.size
    }

    val lastVideo: Video? = videos.filter { it.lastPlayedAt > 0 }.maxByOrNull { it.lastPlayedAt }

    /** Video used for the cover: the one to continue with, otherwise the first. */
    val coverVideoId: String? get() = (resumeVideo() ?: videos.firstOrNull())?.videoId

    /** The video to continue with: last played (if unfinished), otherwise the next unfinished one. */
    fun resumeVideo(): Video? {
        if (videos.isEmpty()) return null
        val last = lastVideo ?: return videos.firstOrNull { !it.finished } ?: videos.first()
        if (!last.finished) return last
        val idx = videos.indexOfFirst { it.id == last.id }
        return videos.drop(idx + 1).firstOrNull { !it.finished }
            ?: videos.firstOrNull { !it.finished }
            ?: videos.first()
    }

    fun indexOf(videoId: String?): Int = videos.indexOfFirst { it.id == videoId }

    fun toJson(): JSONObject = JSONObject().apply {
        put("key", key)
        if (listId != null) put("list", listId)
        put("title", title)
        put("ch", channel)
        put("url", url)
        put("added", addedAt)
        put("videos", JSONArray().apply { videos.forEach { put(it.toJson()) } })
    }

    companion object {
        fun keyForList(listId: String) = "pl:$listId"
        fun keyForVideo(videoId: String) = "v:$videoId"

        fun fromJson(o: JSONObject): Playlist? = try {
            val key = o.getString("key")
            val arr = o.optJSONArray("videos") ?: JSONArray()
            val vids = (0 until arr.length())
                .mapNotNull { Video.fromJson(key, arr.getJSONObject(it)) }
                .sortedBy { it.order }
            Playlist(
                key = key,
                listId = if (o.has("list")) o.getString("list") else null,
                title = o.optString("title"),
                channel = o.optString("ch"),
                url = o.optString("url"),
                addedAt = o.optLong("added"),
                videos = vids,
            )
        } catch (e: Exception) {
            null
        }
    }
}
