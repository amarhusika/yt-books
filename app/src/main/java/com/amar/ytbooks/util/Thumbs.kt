package com.amar.ytbooks.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.amar.ytbooks.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * YouTube thumbnails: downloaded once from i.ytimg.com, the black 4:3 bars cropped away (→ 16:9),
 * kept on disk + in memory.
 */
object Thumbs {
    private val memory = LruCache<String, ImageBitmap>(160)
    private val gate = Semaphore(4)

    fun cached(videoId: String?): ImageBitmap? = videoId?.let { memory.get(it) }

    suspend fun load(context: Context, videoId: String): ImageBitmap? {
        memory.get(videoId)?.let { return it }
        return withContext(Dispatchers.IO) {
            gate.withPermit {
                memory.get(videoId)?.let { return@withPermit it }
                val dir = File(context.cacheDir, "thumbs").apply { mkdirs() }
                val f = File(dir, "$videoId.jpg")
                val fromDisk: Bitmap? = if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
                val bmp: Bitmap? = fromDisk ?: download(videoId)?.also { fresh ->
                    runCatching { FileOutputStream(f).use { out -> fresh.compress(Bitmap.CompressFormat.JPEG, 85, out) } }
                }
                bmp?.asImageBitmap()?.also { memory.put(videoId, it) }
            }
        }
    }

    private fun download(videoId: String): Bitmap? {
        for (name in listOf("hqdefault", "mqdefault")) {
            val raw = fetch("https://i.ytimg.com/vi/$videoId/$name.jpg") ?: continue
            val src = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: continue
            return cropTo16x9(src)
        }
        return null
    }

    /** hqdefault is 4:3 with black bars – keep the 16:9 middle. */
    private fun cropTo16x9(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val targetH = (w * 9f / 16f).toInt()
        if (targetH >= h - 2) return src
        val top = (h - targetH) / 2
        val out = Bitmap.createBitmap(src, 0, top, w, targetH)
        if (out !== src) src.recycle()
        return out
    }

    private fun fetch(url: String): ByteArray? {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            if (c.responseCode !in 200..299) null else c.inputStream.use { it.readBytes() }
        } catch (_: Exception) {
            null
        } finally {
            c.disconnect()
        }
    }
}

@Composable
fun rememberThumb(videoId: String?): ImageBitmap? {
    val state = produceState(initialValue = Thumbs.cached(videoId), videoId) {
        value = if (videoId == null) null else (Thumbs.cached(videoId) ?: Thumbs.load(Graph.app, videoId))
    }
    return state.value
}
