package live.mehiz.mpvkt.subtitles

import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.yubyf.truetypeparser.TTFFile
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class SubtitleFont(val family: String, val label: String, val file: File? = null)

internal class SubtitleFonts(context: Context) {
  private val app = context.applicationContext
  private val directory = File(app.cacheDir, "fonts")
  private val mutex = Mutex()

  suspend fun scan(uri: String): List<SubtitleFont> =
    withContext(Dispatchers.IO) {
      mutex.withLock {
        check(directory.isDirectory || directory.mkdirs())
        if (uri.isBlank()) return@withLock emptyList()
        val root =
          DocumentFile.fromTreeUri(app, uri.toUri())
            ?: throw IOException("Font folder is unavailable")
        val files =
          root
            .listFiles()
            .filter {
              it.isFile && it.name?.substringAfterLast('.')?.lowercase() in setOf("ttf", "otf")
            }
            .take(200)
        val keep = mutableSetOf<String>()
        var total = 0L
        val fonts = mutableListOf<SubtitleFont>()
        for (document in files) {
          ensureActive()
          if (document.length() > 16 * 1024 * 1024) continue
          val digest =
            MessageDigest.getInstance("SHA-256").apply {
              update(document.uri.toString().toByteArray())
            }
          val temp = File.createTempFile("font-", ".part", directory)
          try {
            app.contentResolver.openInputStream(document.uri)?.use { input ->
              temp.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var size = 0
                while (true) {
                  ensureActive()
                  val count = input.read(buffer)
                  if (count < 0) break
                  size += count
                  total += count
                  if (size > 16 * 1024 * 1024 || total > 64 * 1024 * 1024)
                    throw IOException("Font directory is too large")
                  output.write(buffer, 0, count)
                  digest.update(buffer, 0, count)
                }
              }
            } ?: continue
            val family =
              runCatching {
                  temp.inputStream().use { TTFFile.open(it).families.values.firstOrNull() }
                }
                .getOrNull()
                ?.takeIf { it.isNotBlank() } ?: continue
            val hash = digest.digest().take(12).joinToString("") { "%02x".format(it) }
            val target =
              File(directory, "import-$hash.${document.name!!.substringAfterLast('.').lowercase()}")
            if (target.exists() && !target.delete())
              throw IOException("Could not update cached font")
            check(temp.renameTo(target))
            keep += target.name
            fonts += SubtitleFont(family, document.name.orEmpty(), target)
          } finally {
            temp.delete()
          }
        }
        directory
          .listFiles()
          ?.filter { it.name.startsWith("import-") && it.name !in keep }
          ?.forEach { it.delete() }
        fonts.distinctBy { it.family to it.label }.sortedBy { it.label.lowercase() }
      }
    }

  suspend fun clear() =
    withContext(Dispatchers.IO) {
      mutex.withLock {
        directory
          .listFiles()
          ?.filter { it.name.startsWith("import-") }
          ?.forEach { check(it.delete()) }
      }
    }

  suspend fun prepare(uri: String) =
    withContext(Dispatchers.IO) {
      check(directory.isDirectory || directory.mkdirs())
      val fallback = File(directory, "subfont.ttf")
      if (!fallback.exists())
        app.assets.open("subfont.ttf").use { input ->
          fallback.outputStream().use { input.copyTo(it) }
        }
      if (uri.isNotBlank()) scan(uri)
    }
}
