package live.mehiz.mpvkt.ui.icons

import android.content.res.Resources
import android.util.LruCache
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Our catalogue contains fixed-colour vectors, without theme/configuration qualifiers. */
internal object IconVectors {
  private data class Key(val id: Int, val density: Int)
  // Enough for both styles and RTL variants; bounded across density changes.
  private val cache = LruCache<Key, ImageVector>(192)

  fun get(resources: Resources, id: Int): ImageVector {
    val key = Key(id, resources.configuration.densityDpi)
    cache.get(key)?.let { return it }
    return ImageVector.vectorResource(res = resources, resId = id).also { cache.put(key, it) }
  }

  suspend fun prepare(resources: Resources, style: IconStyle) = withContext(Dispatchers.Default) {
    IconKey.entries.flatMap { listOf(it.drawable(style), it.drawable(style, true)) }.distinct().forEach {
      coroutineContext.ensureActive()
      get(resources, it)
    }
  }
}
