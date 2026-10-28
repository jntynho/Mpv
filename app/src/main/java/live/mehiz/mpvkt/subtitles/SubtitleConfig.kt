package live.mehiz.mpvkt.subtitles

import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import live.mehiz.mpvkt.preferences.AdvancedPreferences

internal object SubtitleConfig {
  @Volatile var active: Set<String> = emptySet()

  fun read(input: java.io.InputStream): String {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    while (true) {
      val count = input.read(buffer)
      if (count < 0) break
      check(out.size() + count <= 256 * 1024) { "Configuration is too large" }
      out.write(buffer, 0, count)
    }
    return out.toString("UTF-8")
  }

  fun options(text: String): Set<String> =
    text
      .lineSequence()
      .map(String::trim)
      .filter { it.isNotBlank() && !it.startsWith('#') && !it.startsWith('[') }
      .map {
        it.removePrefix("--").substringBefore('=').substringBefore(' ').trim().removePrefix("no-")
      }
      .toSet()

  suspend fun owned(context: Context, preferences: AdvancedPreferences): Set<String> =
    withContext(Dispatchers.IO) {
      val uri = preferences.mpvConfStorageUri.get()
      if (uri.isNotBlank()) {
        val root = DocumentFile.fromTreeUri(context, uri.toUri())
        val file = root?.findFile("mpv.conf")
        if (file != null)
          context.contentResolver.openInputStream(file.uri)?.use { input ->
            return@withContext options(read(input))
          }
      }
      options(preferences.mpvConf.get())
    }
}
