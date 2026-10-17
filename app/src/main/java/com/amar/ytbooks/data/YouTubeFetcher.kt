package com.amar.ytbooks.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Reads a YouTube playlist without an API key:
 *  1. downloads the playlist page and reads the `ytInitialData` JSON embedded in it (first ~100 videos),
 *  2. then asks YouTube's own web endpoint (`youtubei/v1/browse`) for the remaining pages.
 */
object YouTubeFetcher {

    class FetchException(message: String) : IOException(message)

    data class Link(val listId: String?, val videoId: String?)

    data class FetchedVideo(
        val videoId: String,
        val title: String,
        val channel: String,
        val durationSec: Long,
    )

    data class FetchedPlaylist(
        val listId: String,
        val title: String,
        val channel: String,
        val videos: List<FetchedVideo>,
    )

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

    // EU consent screen would replace the page otherwise
    private const val COOKIE = "CONSENT=YES+cb; SOCS=CAI"

    private val ID_CHARS = Regex("^[A-Za-z0-9_-]+$")

    // ------------------------------------------------------------------ links

    /** Finds a playlist id and/or a video id in any pasted/shared text. */
    fun parse(text: String): Link? {
        val t = text.trim()
        if (t.isEmpty()) return null

        val list = Regex("[?&]list=([A-Za-z0-9_-]{10,})").find(t)?.groupValues?.get(1)
        val video = Regex("[?&]v=([A-Za-z0-9_-]{11})").find(t)?.groupValues?.get(1)
            ?: Regex("youtu\\.be/([A-Za-z0-9_-]{11})").find(t)?.groupValues?.get(1)
            ?: Regex("/(?:shorts|live|embed|v)/([A-Za-z0-9_-]{11})").find(t)?.groupValues?.get(1)

        if (list != null || video != null) return Link(list, video)

        // A bare id was pasted
        if (!t.contains(' ') && ID_CHARS.matches(t)) {
            return when {
                t.length == 11 -> Link(null, t)
                t.length >= 12 -> Link(t, null)
                else -> null
            }
        }
        return null
    }

    /** Mixes / private lists have no public playlist page. */
    fun isUnsupportedList(listId: String): Boolean =
        listId.startsWith("RD") || listId == "WL" || listId == "LL" || listId == "LM"

    // ------------------------------------------------------------------ playlist

    fun fetchPlaylist(listId: String): FetchedPlaylist {
        val html = get("https://www.youtube.com/playlist?list=$listId&hl=en&persist_hl=1")
        val data = extractJson(html, "ytInitialData")
            ?: throw FetchException("YouTube didn't return the playlist. Check the link and your internet connection.")

        val apiKey = Regex("\"INNERTUBE_API_KEY\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
        val clientVersion = Regex("\"INNERTUBE_CLIENT_VERSION\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
            ?: "2.20250101.00.00"
        val visitorData = Regex("\"VISITOR_DATA\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)

        val videos = LinkedHashMap<String, FetchedVideo>()
        var token = collect(data, videos)

        if (videos.isEmpty()) {
            val alert = findAlert(data)
            throw FetchException(alert ?: "This playlist is empty, private or doesn't exist.")
        }

        // Remaining pages (100 videos each)
        var pages = 0
        while (token != null && pages < 100) {
            pages++
            val body = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB")
                        put("clientVersion", clientVersion)
                        put("hl", "en")
                        put("gl", "US")
                        if (visitorData != null) put("visitorData", visitorData)
                    })
                })
                put("continuation", token)
            }
            val url = "https://www.youtube.com/youtubei/v1/browse?prettyPrint=false" +
                (if (apiKey != null) "&key=$apiKey" else "")
            val resp = try {
                JSONObject(post(url, body.toString(), clientVersion, visitorData))
            } catch (e: Exception) {
                break // keep what we have
            }
            val before = videos.size
            token = collect(resp, videos)
            if (videos.size == before) break
        }

        val title = playlistTitle(data) ?: "YouTube playlist"
        val channel = playlistChannel(data)
            ?: videos.values.groupingBy { it.channel }.eachCount()
                .filterKeys { it.isNotBlank() }
                .maxByOrNull { it.value }?.key
            ?: ""

        return FetchedPlaylist(listId, title, channel, videos.values.toList())
    }

    /** Title + channel of a single video (oEmbed – tiny, no key needed). */
    fun fetchVideoInfo(videoId: String): FetchedVideo {
        return try {
            val watch = "https://www.youtube.com/watch?v=$videoId"
            val json = JSONObject(
                get("https://www.youtube.com/oembed?format=json&url=" + URLEncoder.encode(watch, "UTF-8"))
            )
            FetchedVideo(
                videoId = videoId,
                title = json.optString("title").ifBlank { "YouTube video" },
                channel = json.optString("author_name"),
                durationSec = 0L,
            )
        } catch (e: Exception) {
            FetchedVideo(videoId, "YouTube video", "", 0L)
        }
    }

    // ------------------------------------------------------------------ JSON walking

    /** Adds every playable video found anywhere in [root]; returns the next continuation token. */
    private fun collect(root: Any, out: LinkedHashMap<String, FetchedVideo>): String? {
        var token: String? = null
        val lockups = mutableListOf<JSONObject>()

        fun walk(node: Any?, depth: Int) {
            if (depth > 60 || node == null) return
            when (node) {
                is JSONObject -> {
                    node.optJSONObject("playlistVideoRenderer")?.let { addRenderer(it, out) }
                    node.optJSONObject("lockupViewModel")?.let { lockups += it }
                    node.optJSONObject("continuationItemRenderer")?.let { c ->
                        val t = c.optJSONObject("continuationEndpoint")
                            ?.optJSONObject("continuationCommand")
                            ?.optString("token")
                        if (!t.isNullOrBlank()) token = t
                    }
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        if (k == "playlistVideoRenderer") continue
                        walk(node.opt(k), depth + 1)
                    }
                }
                is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), depth + 1)
            }
        }
        walk(root, 0)

        // Newer page layout fallback
        if (out.isEmpty()) lockups.forEach { addLockup(it, out) }
        return token
    }

    private fun addRenderer(r: JSONObject, out: LinkedHashMap<String, FetchedVideo>) {
        val id = r.optString("videoId")
        if (id.isBlank() || out.containsKey(id)) return
        if (r.has("isPlayable") && !r.optBoolean("isPlayable", true)) return // private / deleted
        val title = text(r.optJSONObject("title"))
        if (title.isBlank()) return
        val lengthSec = r.optString("lengthSeconds").toLongOrNull()
            ?: parseClock(text(r.optJSONObject("lengthText")))
        out[id] = FetchedVideo(
            videoId = id,
            title = title,
            channel = text(r.optJSONObject("shortBylineText")),
            durationSec = lengthSec,
        )
    }

    private fun addLockup(l: JSONObject, out: LinkedHashMap<String, FetchedVideo>) {
        if (l.optString("contentType") != "LOCKUP_CONTENT_TYPE_VIDEO") return
        val id = l.optString("contentId")
        if (id.length != 11 || out.containsKey(id)) return
        val meta = l.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
        val title = meta?.optJSONObject("title")?.optString("content").orEmpty()
        if (title.isBlank()) return
        // Duration lives in a thumbnail badge somewhere below contentImage, e.g. "12:34"
        var dur = 0L
        fun findClock(n: Any?, d: Int) {
            if (dur > 0 || n == null || d > 20) return
            when (n) {
                is JSONObject -> {
                    val txt = n.optString("text")
                    if (txt.matches(Regex("\\d{1,2}(:\\d{2}){1,2}"))) dur = parseClock(txt)
                    val ks = n.keys()
                    while (ks.hasNext()) findClock(n.opt(ks.next()), d + 1)
                }
                is JSONArray -> for (i in 0 until n.length()) findClock(n.opt(i), d + 1)
            }
        }
        findClock(l.optJSONObject("contentImage"), 0)
        out[id] = FetchedVideo(id, title, "", dur)
    }

    private fun playlistTitle(data: JSONObject): String? {
        data.optJSONObject("metadata")?.optJSONObject("playlistMetadataRenderer")
            ?.optString("title")?.takeIf { it.isNotBlank() }?.let { return it }
        val header = data.optJSONObject("header")
        header?.optJSONObject("playlistHeaderRenderer")?.optJSONObject("title")
            ?.let { text(it) }?.takeIf { it.isNotBlank() }?.let { return it }
        header?.optJSONObject("pageHeaderRenderer")?.optString("pageTitle")
            ?.takeIf { it.isNotBlank() }?.let { return it }
        data.optJSONObject("microformat")?.optJSONObject("microformatDataRenderer")
            ?.optString("title")?.takeIf { it.isNotBlank() }?.let { return it }
        return null
    }

    private fun playlistChannel(data: JSONObject): String? {
        val h = data.optJSONObject("header")?.optJSONObject("playlistHeaderRenderer") ?: return null
        return text(h.optJSONObject("ownerText")).takeIf { it.isNotBlank() }
    }

    private fun findAlert(data: JSONObject): String? {
        val alerts = data.optJSONArray("alerts") ?: return null
        for (i in 0 until alerts.length()) {
            val a = alerts.optJSONObject(i) ?: continue
            val r = a.optJSONObject("alertRenderer") ?: a.optJSONObject("alertWithButtonRenderer") ?: continue
            val t = text(r.optJSONObject("text"))
            if (t.isNotBlank()) return t
        }
        return null
    }

    /** YouTube text object: {"simpleText": "..."} or {"runs": [{"text": "..."}]}. */
    private fun text(o: JSONObject?): String {
        if (o == null) return ""
        val simple = o.optString("simpleText")
        if (simple.isNotEmpty()) return simple
        val runs = o.optJSONArray("runs") ?: return o.optString("content")
        val sb = StringBuilder()
        for (i in 0 until runs.length()) sb.append(runs.optJSONObject(i)?.optString("text").orEmpty())
        return sb.toString()
    }

    private fun parseClock(s: String): Long {
        val parts = s.trim().split(':').mapNotNull { it.trim().toLongOrNull() }
        if (parts.isEmpty() || parts.size > 3) return 0L
        return parts.fold(0L) { acc, p -> acc * 60 + p }
    }

    /** Extracts `var <name> = {...};` (or `window["<name>"] = {...}`) from the page HTML. */
    internal fun extractJson(html: String, name: String): JSONObject? {
        val markers = listOf("var $name = ", "window[\"$name\"] = ", "$name = ")
        for (m in markers) {
            var from = 0
            while (true) {
                val idx = html.indexOf(m, from)
                if (idx < 0) break
                val start = html.indexOf('{', idx + m.length)
                if (start < 0 || start - (idx + m.length) > 3) {
                    from = idx + m.length
                    continue
                }
                val end = matchBrace(html, start)
                if (end > start) {
                    try {
                        return JSONObject(html.substring(start, end + 1))
                    } catch (_: Exception) {
                    }
                }
                from = idx + m.length
            }
        }
        return null
    }

    /** Index of the brace that closes the one at [start], respecting JSON strings. */
    private fun matchBrace(s: String, start: Int): Int {
        var depth = 0
        var inStr = false
        var esc = false
        var i = start
        while (i < s.length) {
            val c = s[i]
            if (inStr) {
                when {
                    esc -> esc = false
                    c == '\\' -> esc = true
                    c == '"' -> inStr = false
                }
            } else {
                when (c) {
                    '"' -> inStr = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return i
                    }
                }
            }
            i++
        }
        return -1
    }

    // ------------------------------------------------------------------ HTTP

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            c.setRequestProperty("Cookie", COOKIE)
            val code = c.responseCode
            if (code !in 200..299) throw FetchException("YouTube answered with error $code.")
            return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun post(url: String, json: String, clientVersion: String, visitorData: String?): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            c.setRequestProperty("Cookie", COOKIE)
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Origin", "https://www.youtube.com")
            c.setRequestProperty("X-YouTube-Client-Name", "1")
            c.setRequestProperty("X-YouTube-Client-Version", clientVersion)
            if (visitorData != null) c.setRequestProperty("X-Goog-Visitor-Id", visitorData)
            c.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            if (code !in 200..299) throw FetchException("YouTube answered with error $code.")
            return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
