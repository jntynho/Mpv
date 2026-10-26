package live.mehiz.mpvkt.ui.home

import android.content.Context
import android.content.ContentUris
import android.provider.MediaStore
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import live.mehiz.mpvkt.ui.icons.Icons
import live.mehiz.mpvkt.ui.icons.filled.PlayCircle

/** Bounded shared cache; never hold an Activity or decode on the UI thread. */
internal object Thumbnails {
  private val cache = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
  }
  private val permits = Semaphore(2)
  suspend fun load(context: Context, uri: String, revision: Long): Bitmap? = withContext(Dispatchers.IO) {
    val key = "$uri@$revision"
    cache.get(key)?.let { return@withContext it }
    permits.withPermit {
      cache.get(key)?.let { return@withPermit it }
      try {
        val provider = if (Build.VERSION.SDK_INT >= 29) runCatching {
          context.contentResolver.loadThumbnail(uri.toUri(), Size(320, 180), null)
        }.getOrNull() else null
        val bitmap = provider ?: if (Build.VERSION.SDK_INT >= 27) {
          val retriever = MediaMetadataRetriever()
          try {
            retriever.setDataSource(context, uri.toUri())
            retriever.getScaledFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, 180)
          } finally { retriever.release() }
        } else {
          @Suppress("DEPRECATION")
          MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver, ContentUris.parseId(uri.toUri()),
            MediaStore.Video.Thumbnails.MINI_KIND, null)
        }
        bitmap?.also { cache.put(key, it) }
      } catch (cancelled: CancellationException) { throw cancelled }
      catch (_: Exception) { null }
      catch (_: OutOfMemoryError) { null }
    }
  }
}

@Composable
fun VideoThumbnail(context: Context, uri: String, modifier: Modifier = Modifier, revision: Long = 0) {
  val appContext = context.applicationContext
  val bitmap by produceState<Bitmap?>(null, uri, revision) { value = Thumbnails.load(appContext, uri, revision) }
  Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    else Icon(Icons.Default.PlayCircle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
