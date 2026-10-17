package com.amar.ytbooks.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Library: all playlists + per-video positions, stored as JSON in the app's internal storage. */
class LibraryRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val file = File(context.filesDir, "library.json")

    private val _playlists = MutableStateFlow(load())

    /** Books (playlists) sorted A–Z, each with its videos in playlist order. */
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Emits the key of a playlist right after it was added (so the UI can open it). */
    private val _added = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val added: SharedFlow<String> = _added.asSharedFlow()

    private var saveJob: Job? = null
    private val writeLock = Any()

    fun video(id: String?): Video? {
        if (id == null) return null
        for (p in _playlists.value) p.videos.firstOrNull { it.id == id }?.let { return it }
        return null
    }

    fun playlist(key: String?): Playlist? = _playlists.value.firstOrNull { it.key == key }

    fun playlistOf(videoId: String?): Playlist? =
        if (videoId == null) null else _playlists.value.firstOrNull { p -> p.videos.any { it.id == videoId } }

    // ---------- Import ----------

    /** Accepts a pasted/shared link (playlist or single video). */
    fun addFromText(text: String) {
        val link = YouTubeFetcher.parse(text)
        if (link == null) {
            _messages.tryEmit("That doesn't look like a YouTube link")
            return
        }
        scope.launch {
            _importing.value = true
            try {
                val listId = link.listId
                when {
                    listId != null && !YouTubeFetcher.isUnsupportedList(listId) -> importPlaylist(listId, refreshOnly = false)
                    link.videoId != null -> {
                        if (listId != null) _messages.tryEmit("Mixes and private lists can't be read – adding just the video")
                        importSingle(link.videoId)
                    }
                    else -> _messages.tryEmit("This list is private or a YouTube Mix – it can't be added")
                }
            } catch (e: YouTubeFetcher.FetchException) {
                _messages.tryEmit(e.message ?: "Could not read the playlist")
            } catch (e: Exception) {
                _messages.tryEmit("No connection to YouTube. Check your internet and try again.")
            } finally {
                _importing.value = false
            }
        }
    }

    /** Re-reads the playlist from YouTube: new videos are added, progress stays. */
    fun refresh(key: String) {
        val p = playlist(key) ?: return
        val listId = p.listId ?: return
        scope.launch {
            _importing.value = true
            try {
                importPlaylist(listId, refreshOnly = true)
            } catch (e: YouTubeFetcher.FetchException) {
                _messages.tryEmit(e.message ?: "Could not read the playlist")
            } catch (e: Exception) {
                _messages.tryEmit("No connection to YouTube. Check your internet and try again.")
            } finally {
                _importing.value = false
            }
        }
    }

    private fun importPlaylist(listId: String, refreshOnly: Boolean) {
        val fetched = YouTubeFetcher.fetchPlaylist(listId)
        val key = Playlist.keyForList(listId)
        val existing = playlist(key)
        val old = existing?.videos?.associateBy { it.videoId } ?: emptyMap()

        val videos = fetched.videos.mapIndexed { i, f ->
            val prev = old[f.videoId]
            prev?.copy(
                title = f.title,
                channel = f.channel.ifBlank { prev.channel },
                order = i,
                durationMs = if (f.durationSec > 0) f.durationSec * 1000 else prev.durationMs,
            ) ?: Video(
                id = Video.idOf(key, f.videoId),
                playlistKey = key,
                videoId = f.videoId,
                title = f.title,
                channel = f.channel,
                order = i,
                durationMs = f.durationSec * 1000,
            )
        }
        // Videos removed from the playlist on YouTube but already started here are kept at the end.
        val fetchedIds = fetched.videos.map { it.videoId }.toHashSet()
        val kept = old.values
            .filter { o -> o.videoId !in fetchedIds && (o.positionMs > 0 || o.finished) }
            .mapIndexed { i, v -> v.copy(order = videos.size + i) }

        val pl = Playlist(
            key = key,
            listId = listId,
            title = fetched.title,
            channel = fetched.channel,
            url = "https://www.youtube.com/playlist?list=$listId",
            addedAt = existing?.addedAt ?: System.currentTimeMillis(),
            videos = videos + kept,
        )
        upsert(pl)

        val newCount = videos.count { it.videoId !in old }
        _messages.tryEmit(
            when {
                existing == null -> "Added “${pl.title}” · ${videos.size} videos"
                newCount == 0 -> if (refreshOnly) "Playlist is up to date" else "Already in your library – up to date"
                newCount == 1 -> "1 new video added"
                else -> "$newCount new videos added"
            }
        )
        if (existing == null) _added.tryEmit(key)
    }

    private fun importSingle(videoId: String) {
        val key = Playlist.keyForVideo(videoId)
        if (playlist(key) != null) {
            _messages.tryEmit("This video is already in your library")
            return
        }
        val info = YouTubeFetcher.fetchVideoInfo(videoId)
        val pl = Playlist(
            key = key,
            listId = null,
            title = info.title,
            channel = info.channel,
            url = "https://www.youtube.com/watch?v=$videoId",
            addedAt = System.currentTimeMillis(),
            videos = listOf(
                Video(
                    id = Video.idOf(key, videoId),
                    playlistKey = key,
                    videoId = videoId,
                    title = info.title,
                    channel = info.channel,
                )
            ),
        )
        upsert(pl)
        _messages.tryEmit("Added “${pl.title}”")
        _added.tryEmit(key)
    }

    private fun upsert(pl: Playlist) {
        _playlists.update { list -> sort(list.filterNot { it.key == pl.key } + pl) }
        persist()
    }

    private fun sort(list: List<Playlist>): List<Playlist> =
        list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

    // ---------- Changes ----------

    private fun updateVideo(id: String, transform: (Video) -> Video) {
        _playlists.update { list ->
            list.map { p ->
                if (p.videos.none { it.id == id }) p
                else p.copy(videos = p.videos.map { if (it.id == id) transform(it) else it })
            }
        }
        persist()
    }

    fun saveProgress(id: String, positionMs: Long, durationMs: Long?) {
        val now = System.currentTimeMillis()
        updateVideo(id) {
            val dur = if (durationMs != null && durationMs > 0) durationMs else it.durationMs
            val stillFinished = it.finished && (dur <= 0 || positionMs >= dur - 5_000)
            it.copy(
                positionMs = positionMs.coerceAtLeast(0L),
                durationMs = dur,
                lastPlayedAt = now,
                finished = stillFinished,
            )
        }
    }

    fun setDuration(id: String, durationMs: Long) {
        val v = video(id) ?: return
        if (durationMs > 0 && kotlin.math.abs(v.durationMs - durationMs) > 1_500) updateVideo(id) { it.copy(durationMs = durationMs) }
    }

    fun setSpeed(id: String, speed: Float) {
        val v = video(id) ?: return
        if (v.speed != speed) updateVideo(id) { it.copy(speed = speed) }
    }

    fun markFinished(id: String, finished: Boolean) = updateVideo(id) {
        it.copy(
            finished = finished,
            positionMs = if (finished) it.durationMs else it.positionMs,
            lastPlayedAt = System.currentTimeMillis(),
        )
    }

    fun resetProgress(id: String) = updateVideo(id) { it.copy(positionMs = 0L, finished = false) }

    fun resetPlaylist(key: String) {
        _playlists.update { list ->
            list.map { p ->
                if (p.key != key) p else p.copy(videos = p.videos.map { it.copy(positionMs = 0L, finished = false) })
            }
        }
        persist()
    }

    fun removePlaylist(key: String) {
        val title = playlist(key)?.title
        _playlists.update { list -> list.filterNot { it.key == key } }
        persist()
        if (title != null) _messages.tryEmit("Removed “$title”")
    }

    /** Next video in the same playlist. */
    fun nextInPlaylist(id: String): Video? {
        val p = playlistOf(id) ?: return null
        val idx = p.indexOf(id)
        return if (idx >= 0 && idx + 1 < p.videos.size) p.videos[idx + 1] else null
    }

    fun lastPlayed(): Video? =
        _playlists.value.flatMap { it.videos }.filter { it.lastPlayedAt > 0 }.maxByOrNull { it.lastPlayedAt }

    // ---------- Persistence ----------

    private fun load(): List<Playlist> = try {
        if (file.exists()) {
            val root = JSONObject(file.readText())
            val arr = root.optJSONArray("playlists") ?: JSONArray()
            sort((0 until arr.length()).mapNotNull { Playlist.fromJson(arr.getJSONObject(it)) })
        } else emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    @Synchronized
    private fun persist() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(400)
            writeNow()
        }
    }

    /** Write immediately (e.g. when the app goes to the background). */
    fun flush() {
        synchronized(this) { saveJob?.cancel() }
        scope.launch { writeNow() }
    }

    private fun writeNow() {
        synchronized(writeLock) {
            try {
                val arr = JSONArray()
                _playlists.value.forEach { arr.put(it.toJson()) }
                val text = JSONObject().put("version", 1).put("playlists", arr).toString()
                val tmp = File(file.parentFile, "library.json.tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(file)) {
                    file.writeText(text)
                    tmp.delete()
                }
            } catch (_: Exception) {
            }
        }
    }
}
