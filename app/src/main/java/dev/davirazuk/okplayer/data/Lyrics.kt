package dev.davirazuk.okplayer.data

import android.content.Context
import android.net.Uri
import dev.davirazuk.okplayer.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

data class LyricLine(val timeMs: Long?, val text: String)

data class Lyrics(val lines: List<LyricLine>, val synced: Boolean) {
    /** Index of the line being sung at [positionMs], or -1 before the first one. */
    fun indexAt(positionMs: Long): Int {
        if (!synced) return -1
        var found = -1
        for (i in lines.indices) {
            val t = lines[i].timeMs ?: continue
            if (t <= positionMs) found = i else break
        }
        return found
    }
}

object Lrc {
    private val stamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    /** Parses LRC text; lines without timestamps make it plain lyrics. */
    fun parse(text: String): Lyrics? {
        val timed = mutableListOf<LyricLine>()
        val plain = mutableListOf<LyricLine>()
        for (raw in text.lineSequence()) {
            val stamps = stamp.findAll(raw).toList()
            val body = raw.replace(stamp, "").trim()
            if (stamps.isEmpty()) {
                // Skip LRC tags like [ar:...] and [length:...].
                if (!raw.trimStart().startsWith("[")) plain += LyricLine(null, raw.trim())
                continue
            }
            for (m in stamps) {
                val (min, sec, frac) = m.destructured
                val fracMs = when (frac.length) {
                    0 -> 0
                    1 -> frac.toInt() * 100
                    2 -> frac.toInt() * 10
                    else -> frac.take(3).toInt()
                }
                timed += LyricLine(min.toLong() * 60_000 + sec.toLong() * 1000 + fracMs, body)
            }
        }
        return when {
            timed.isNotEmpty() -> Lyrics(timed.sortedBy { it.timeMs }, synced = true)
            plain.any { it.text.isNotBlank() } -> Lyrics(plain.dropWhile { it.text.isBlank() }.dropLastWhile { it.text.isBlank() }, synced = false)
            else -> null
        }
    }
}

/**
 * Finds lyrics for a song. Results, including "nothing found", are cached on disk so
 * each song is looked up once. Online lookups go to lrclib.net, a free and open
 * lyrics database, and only happen when the user leaves that option on.
 */
class LyricsRepository(context: Context) {
    private val dir = File(context.filesDir, "lyrics").apply { mkdirs() }

    suspend fun find(mediaId: String, artist: String, title: String, album: String, durationMs: Long): Lyrics? =
        withContext(Dispatchers.IO) {
            val cached = File(dir, "$mediaId.lrc")
            val missing = File(dir, "$mediaId.none")
            if (cached.exists()) return@withContext Lrc.parse(cached.readText())
            if (missing.exists() && System.currentTimeMillis() - missing.lastModified() < RETRY_AFTER_MS) return@withContext null
            if (!Preferences.onlineLyrics.value || title.isBlank()) return@withContext null

            val text = runCatching { lookUp(artist, title, album, durationMs) }.getOrNull()
            if (text.isNullOrBlank()) {
                missing.writeText("")
                null
            } else {
                cached.writeText(text)
                missing.delete()
                Lrc.parse(text)
            }
        }

    private fun lookUp(artist: String, title: String, album: String, durationMs: Long): String? {
        val seconds = (durationMs / 1000).toString()
        val exact = Uri.parse("$API/get").buildUpon()
            .appendQueryParameter("artist_name", artist)
            .appendQueryParameter("track_name", title)
            .appendQueryParameter("album_name", album)
            .appendQueryParameter("duration", seconds)
            .build().toString()
        get(exact)?.let { return pick(JSONObject(it)) }

        // No exact match: search by title and artist, and keep a result of about the same length.
        val search = Uri.parse("$API/search").buildUpon()
            .appendQueryParameter("track_name", title)
            .appendQueryParameter("artist_name", artist)
            .build().toString()
        val results = get(search)?.let(::JSONArray) ?: return null
        for (i in 0 until results.length()) {
            val r = results.getJSONObject(i)
            if (durationMs <= 0 || abs(r.optDouble("duration", 0.0) * 1000 - durationMs) < 4000) {
                pick(r)?.let { return it }
            }
        }
        return null
    }

    private fun pick(r: JSONObject): String? =
        r.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
            ?: r.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }

    private fun get(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "okplayer/${BuildConfig.VERSION_NAME} (https://github.com/davirazuk/okplayer)")
            if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            c.disconnect()
        }
    }

    private companion object {
        const val API = "https://lrclib.net/api"
        const val RETRY_AFTER_MS = 7L * 24 * 60 * 60 * 1000
    }
}
