package dev.davirazuk.okplayer.data

import android.content.Context
import android.net.Uri
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.charset.Charset

/**
 * Reads lyrics stored in the file's own tags: the LYRICS / UNSYNCEDLYRICS Vorbis
 * comment in FLAC, the USLT frame in MP3, and the ©lyr atom in M4A. Many taggers write
 * LRC timestamps into these, which then show as synced lyrics.
 */
object EmbeddedLyrics {

    fun read(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).channel.use { ch ->
                val head = ch.readAt(0, 12) ?: return@use null
                val sig = String(head.array(), 0, 4, Charsets.ISO_8859_1)
                when {
                    sig == "fLaC" -> flac(ch)
                    sig.startsWith("ID3") -> id3(ch)
                    String(head.array(), 4, 4, Charsets.ISO_8859_1) == "ftyp" -> mp4(ch)
                    else -> null
                }
            }
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun FileChannel.readAt(position: Long, length: Int): ByteBuffer? {
        if (length <= 0 || length > MAX_BLOCK) return null
        val buf = ByteBuffer.allocate(length)
        var pos = position
        while (buf.hasRemaining()) {
            val n = read(buf, pos)
            if (n <= 0) break
            pos += n
        }
        return if (buf.hasRemaining()) null else buf.also { it.flip() }
    }

    private fun flac(ch: FileChannel): String? {
        var pos = 4L
        while (true) {
            val h = ch.readAt(pos, 4) ?: return null
            val header = h.get(0).toInt() and 0xFF
            val len = ((h.get(1).toInt() and 0xFF) shl 16) or ((h.get(2).toInt() and 0xFF) shl 8) or (h.get(3).toInt() and 0xFF)
            pos += 4
            if ((header and 0x7F) == 4) {
                val block = ch.readAt(pos, len)?.order(ByteOrder.LITTLE_ENDIAN) ?: return null
                block.position(4 + block.getInt(0))
                val count = block.int
                repeat(count) {
                    val size = block.int
                    val bytes = ByteArray(size).also { block.get(it) }
                    val comment = String(bytes, Charsets.UTF_8)
                    val key = comment.substringBefore('=').uppercase()
                    if (key == "LYRICS" || key == "UNSYNCEDLYRICS") return comment.substringAfter('=')
                }
                return null
            }
            if ((header and 0x80) != 0) return null
            pos += len
        }
    }

    private fun id3(ch: FileChannel): String? {
        val h = ch.readAt(0, 10) ?: return null
        val version = h.get(3).toInt()
        if (version < 3) return null
        val size = syncsafe(h, 6)
        val tag = ch.readAt(10, size) ?: return null
        var p = 0
        if ((h.get(5).toInt() and 0x40) != 0) p += if (version == 4) syncsafe(tag, 0) else tag.getInt(0) + 4
        while (p + 10 <= size) {
            val id = String(ByteArray(4).also { tag.position(p); tag.get(it) }, Charsets.ISO_8859_1)
            if (!id.all { it.isLetterOrDigit() }) break
            val len = if (version == 4) syncsafe(tag, p + 4) else tag.getInt(p + 4)
            val start = p + 10
            if (len <= 0 || start + len > size) break
            if (id == "USLT") {
                val enc = tag.get(start).toInt()
                val body = ByteArray(len - 4).also { tag.position(start + 4); tag.get(it) }
                // Skip the content descriptor, which is terminated like any string in this encoding.
                val wide = enc == 1 || enc == 2
                var i = 0
                while (i < body.size) {
                    if (!wide && body[i].toInt() == 0) { i += 1; break }
                    if (wide && i + 1 < body.size && body[i].toInt() == 0 && body[i + 1].toInt() == 0) { i += 2; break }
                    i += if (wide) 2 else 1
                }
                return String(body, i, body.size - i, charset(enc)).trimEnd('\u0000')
            }
            p = start + len
        }
        return null
    }

    private fun charset(enc: Int): Charset = when (enc) {
        0 -> Charsets.ISO_8859_1
        1 -> Charsets.UTF_16
        2 -> Charsets.UTF_16BE
        else -> Charsets.UTF_8
    }

    private fun syncsafe(b: ByteBuffer, at: Int) =
        ((b.get(at).toInt() and 0x7F) shl 21) or ((b.get(at + 1).toInt() and 0x7F) shl 14) or
            ((b.get(at + 2).toInt() and 0x7F) shl 7) or (b.get(at + 3).toInt() and 0x7F)

    private fun mp4(ch: FileChannel): String? {
        val size = ch.size()
        var pos = 0L
        while (pos + 8 <= size) {
            val h = ch.readAt(pos, 8) ?: return null
            var atom = h.getInt(0).toLong() and 0xFFFFFFFFL
            val type = String(h.array(), 4, 4, Charsets.ISO_8859_1)
            if (atom == 1L) atom = ch.readAt(pos + 8, 8)?.long ?: return null
            if (atom == 0L) atom = size - pos
            if (atom < 8) return null
            if (type == "moov") {
                val moov = ch.readAt(pos, atom.toInt()) ?: return null
                return findLyrics(moov, 8, moov.limit(), listOf("udta", "meta", "ilst", "©lyr"))
            }
            pos += atom
        }
        return null
    }

    private fun findLyrics(b: ByteBuffer, from: Int, to: Int, path: List<String>): String? {
        var p = from
        while (p + 8 <= to) {
            val len = b.getInt(p)
            if (len < 8 || p + len > to) return null
            val type = String(ByteArray(4).also { b.position(p + 4); b.get(it) }, Charsets.ISO_8859_1)
            if (type == path.first()) {
                // "meta" carries four bytes of version and flags before its children.
                val childStart = p + 8 + if (type == "meta") 4 else 0
                if (path.size == 1) {
                    // ©lyr holds a "data" atom: size, "data", type, locale, then the text.
                    val dataLen = b.getInt(childStart) - 16
                    if (dataLen <= 0) return null
                    val bytes = ByteArray(dataLen).also { b.position(childStart + 16); b.get(it) }
                    return String(bytes, Charsets.UTF_8)
                }
                return findLyrics(b, childStart, p + len, path.drop(1))
            }
            p += len
        }
        return null
    }

    private const val MAX_BLOCK = 32 * 1024 * 1024
}
