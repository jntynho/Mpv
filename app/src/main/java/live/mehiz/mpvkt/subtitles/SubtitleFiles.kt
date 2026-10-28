package live.mehiz.mpvkt.subtitles

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

internal object SubtitleFiles {
  const val MAX_FILE = 10 * 1024 * 1024
  private val extensions = setOf("srt", "ass", "ssa", "vtt", "sub", "txt")

  data class Entry(val name: String, val bytes: ByteArray)

  /**
   * Extract in memory, never using archive paths as filesystem paths. No RAR/7z native dependency.
   */
  fun unpack(bytes: ByteArray, name: String, request: SubtitleRequest): List<Entry> {
    val all = mutableListOf<Entry>()
    if (bytes.take(2) == listOf(0x50.toByte(), 0x4b.toByte())) {
      var total = 0
      ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        repeat(256) {
          val entry = zip.nextEntry ?: return@use
          if (!entry.isDirectory && entry.name.substringAfterLast('.').lowercase() in extensions) {
            if (entry.size > MAX_FILE) throw IOException("Subtitle archive entry is too large")
            val body = limited(zip, MAX_FILE)
            total += body.size
            if (total > 30 * 1024 * 1024) throw IOException("Subtitle archive is too large")
            all +=
              Entry(
                entry.name
                  .replace('\\', '/')
                  .split('/')
                  .filter { it.isNotBlank() && it != ".." && it != "." }
                  .takeLast(3)
                  .joinToString("/"),
                body,
              )
          } else {
            // Reject archives hiding huge ignored entries too; closeEntry may otherwise
            // inflate unbounded data.
            total += limited(zip, MAX_FILE).size
            if (total > 30 * 1024 * 1024) throw IOException("Subtitle archive is too large")
          }
          zip.closeEntry()
        }
        throw IOException("Too many files in subtitle archive")
      }
    } else if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
      val decoded = GZIPInputStream(ByteArrayInputStream(bytes)).use { limited(it, MAX_FILE) }
      all += Entry(name.removeSuffix(".gz"), decoded)
    } else all += Entry(name, bytes)
    val selected =
      all.filter { entry ->
        val parsed = MediaQuery.parse(entry.name)
        request.episode == null ||
          parsed.episode == null ||
          (parsed.episode == request.episode &&
            (parsed.season == null || parsed.season == request.season))
      }
    if (selected.isEmpty()) throw IOException("No subtitle for this episode in the download")
    return selected.map { entry ->
      val charset =
        when {
          entry.bytes.size >= 2 &&
            entry.bytes[0] == 0xff.toByte() &&
            entry.bytes[1] == 0xfe.toByte() -> Charsets.UTF_16LE
          entry.bytes.size >= 2 &&
            entry.bytes[0] == 0xfe.toByte() &&
            entry.bytes[1] == 0xff.toByte() -> Charsets.UTF_16BE
          else -> Charsets.UTF_8
        }
      val prefix =
        String(entry.bytes.take(8192).toByteArray(), charset)
          .trimStart('\ufeff', ' ', '\n', '\r', '\t')
          .lowercase(Locale.ROOT)
      if (
        prefix.startsWith("<!doctype html") ||
          prefix.startsWith("<html") ||
          prefix.startsWith("{\"error") ||
          entry.bytes.isEmpty() ||
          entry.bytes.size > MAX_FILE
      )
        throw IOException("Provider returned a page instead of subtitles")
      if (
        "-->" !in prefix &&
          !prefix.startsWith("webvtt") &&
          "[events]" !in prefix &&
          !prefix.startsWith("[script info]") &&
          !Regex("\\{\\d+}\\{\\d+}").containsMatchIn(prefix)
      )
        throw IOException("Provider returned unsupported subtitle content")
      val ext =
        entry.name.substringAfterLast('.').lowercase().takeIf { it in extensions }
          ?: detectExtension(prefix)
      Entry(entry.name.substringBeforeLast('.', entry.name).take(140) + "." + ext, entry.bytes)
    }
  }

  private fun detectExtension(prefix: String) =
    when {
      prefix.startsWith("[script info]") || "[events]" in prefix -> "ass"
      prefix.startsWith("webvtt") -> "vtt"
      "-->" in prefix -> "srt"
      else -> throw IOException("Unsupported subtitle file or archive")
    }

  private fun limited(input: java.io.InputStream, max: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
      val n = input.read(buffer)
      if (n < 0) break
      if (out.size() + n > max) throw IOException("Subtitle file is too large")
      out.write(buffer, 0, n)
    }
    return out.toByteArray()
  }

  fun save(directory: File, source: String, entry: Entry): File {
    check(directory.exists() || directory.mkdirs())
    val hash =
      MessageDigest.getInstance("SHA-256")
        .digest(
          (source + entry.name).toByteArray() +
            MessageDigest.getInstance("SHA-256").digest(entry.bytes)
        )
        .take(12)
        .joinToString("") { "%02x".format(it) }
    val target = File(directory, hash + "." + entry.name.substringAfterLast('.'))
    val temp = File.createTempFile("subtitle-", ".part", directory)
    try {
      temp.outputStream().use { it.write(entry.bytes) }
      if (target.exists() || temp.renameTo(target)) return target
      throw IOException("Could not save subtitle")
    } finally {
      temp.delete()
    }
  }
}
