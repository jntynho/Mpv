package live.mehiz.mpvkt.ui.preferences

import java.io.File
import java.io.IOException

/**
 * Only copies rebuilt by PlayerActivity; never delete downloads, scripts, or the whole filesDir.
 */
internal fun clearConfigurationCache(filesDirectory: File) {
  listOf("mpv.conf", "input.conf").forEach { name ->
    val file = File(filesDirectory, name)
    if (file.exists() && (!file.isFile || (!file.delete() && file.exists())))
      throw IOException("Could not clear cached configuration")
  }
}
