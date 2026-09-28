package dev.davirazuk.okplayer.library

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Track(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val albumArtist: String,
    val trackNumber: Int,
    val discNumber: Int,
    val durationMs: Long,
    val year: Int?,
    val mimeType: String?,
) {
    val artUri: Uri get() = albumArtUri(albumId)

    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setAlbumArtist(albumArtist)
                .setTrackNumber(trackNumber)
                .setDiscNumber(discNumber)
                .setArtworkUri(artUri)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build(),
        )
        .build()
}

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val year: Int?,
    val tracks: List<Track>,
) {
    val artUri: Uri get() = albumArtUri(id)
    val durationMs: Long get() = tracks.sumOf { it.durationMs }
}

private val ALBUM_ART = Uri.parse("content://media/external/audio/albumart")
fun albumArtUri(albumId: Long): Uri = ContentUris.withAppendedId(ALBUM_ART, albumId)

class LibraryRepository(private val context: Context) {

    suspend fun loadAlbums(): List<Album> = withContext(Dispatchers.IO) {
        val tracks = queryTracks("${MediaStore.Audio.Media.IS_MUSIC} != 0", null)
        tracks.groupBy { it.albumId }
            .map { (albumId, items) ->
                val sorted = items.sortedWith(compareBy<Track>({ it.discNumber }, { it.trackNumber }, { it.title.lowercase() }))
                val first = sorted.first()
                Album(
                    id = albumId,
                    title = first.album,
                    artist = first.albumArtist,
                    year = sorted.firstNotNullOfOrNull { it.year },
                    tracks = sorted,
                )
            }
            .sortedWith(compareBy<Album>({ it.artist.lowercase() }, { it.year ?: 0 }, { it.title.lowercase() }))
    }

    /** Looks tracks up by id, keeping the order given and dropping ones that no longer exist. */
    suspend fun tracksById(ids: List<Long>): List<Track> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        val found = ids.distinct().chunked(500).flatMap { chunk ->
            queryTracks("${MediaStore.Audio.Media._ID} IN (${chunk.joinToString(",") { "?" }})", chunk.map { it.toString() }.toTypedArray())
        }.associateBy { it.id }
        ids.mapNotNull { found[it] }
    }

    private fun queryTracks(selection: String, args: Array<String>?): List<Track> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            COLUMN_ALBUM_ARTIST,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.MIME_TYPE,
        )
        val result = mutableListOf<Track>()
        context.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val albumArtistCol = c.getColumnIndex(COLUMN_ALBUM_ARTIST)
            val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val yearCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val artist = c.getString(artistCol).orUnknown()
                // MediaStore packs the disc into the thousands: 2005 is disc 2, track 5.
                val packed = c.getInt(trackCol)
                result += Track(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    title = c.getString(titleCol) ?: "Untitled",
                    artist = artist,
                    album = c.getString(albumCol).orUnknown(),
                    albumId = c.getLong(albumIdCol),
                    albumArtist = (if (albumArtistCol >= 0) c.getString(albumArtistCol) else null)
                        ?.takeIf { it.isNotBlank() } ?: artist,
                    trackNumber = packed % 1000,
                    discNumber = (packed / 1000).coerceAtLeast(1),
                    durationMs = c.getLong(durationCol),
                    year = c.getInt(yearCol).takeIf { it > 0 },
                    mimeType = c.getString(mimeCol),
                )
            }
        }
        return result
    }

    private fun String?.orUnknown() =
        if (this.isNullOrBlank() || this == MediaStore.UNKNOWN_STRING) "Unknown" else this

    private companion object {
        // Public constant only exists from API 30; the column itself is older.
        const val COLUMN_ALBUM_ARTIST = "album_artist"
    }
}
