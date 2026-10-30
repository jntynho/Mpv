package live.mehiz.mpvkt.ui.player

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.Closeable
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The descriptor is retained until its media ends, not leaked through a detached integer. */
internal class ExternalTrackFile(
  val path: String,
  val generation: Long,
  private val descriptor: ParcelFileDescriptor?,
) : Closeable {
  override fun close() {
    runCatching { descriptor?.close() }
  }

  companion object {
    suspend fun open(
      context: Context,
      uri: Uri,
      generation: Long,
      subtitle: Boolean,
    ): ExternalTrackFile {
      var undelivered: ExternalTrackFile? = null
      try {
        val result =
          withContext(Dispatchers.IO) {
            require(uri.scheme == "content" || uri.scheme == "file")
            val resolver = context.contentResolver
            val name =
              if (uri.scheme == "file") File(requireNotNull(uri.path)).name
              else
                resolver
                  .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                  ?.use { cursor ->
                    val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && column >= 0) cursor.getString(column) else null
                  }
                  .orEmpty()
            val mime = if (uri.scheme == "content") resolver.getType(uri).orEmpty() else ""
            if (subtitle)
              require(
                name.substringAfterLast('.', "").lowercase(Locale.ROOT) in
                  setOf(
                    "srt",
                    "ass",
                    "ssa",
                    "vtt",
                    "sub",
                    "idx",
                    "sup",
                    "smi",
                    "sami",
                    "lrc",
                    "txt",
                    "ttml",
                    "dfxp",
                    "sbv",
                  ) ||
                  mime.startsWith("text/") ||
                  mime in
                    setOf(
                      "application/x-subrip",
                      "application/ttml+xml",
                      "application/x-ass",
                      "application/x-ssa",
                      "application/vobsub",
                    )
              )
            else
              require(
                mime.startsWith("audio/") ||
                  mime.startsWith("video/") ||
                  name.substringAfterLast('.', "").lowercase(Locale.ROOT) in
                    setOf(
                      "aac",
                      "ac3",
                      "eac3",
                      "dts",
                      "flac",
                      "m4a",
                      "mka",
                      "mkv",
                      "mp3",
                      "mp4",
                      "ogg",
                      "opus",
                      "wav",
                      "wma",
                      "aiff",
                      "alac",
                      "webm",
                      "m4b",
                    )
              )
            if (uri.scheme == "file") {
              val file = File(requireNotNull(uri.path))
              require(file.isFile && file.canRead())
              ExternalTrackFile(file.canonicalPath, generation, null).also { undelivered = it }
            } else {
              val fd = resolver.openFileDescriptor(uri, "r") ?: error("Cannot read file")
              ExternalTrackFile("fd://${fd.fd}", generation, fd).also { undelivered = it }
            }
          }
        undelivered = null
        return result
      } finally {
        undelivered?.close()
      }
    }
  }
}
