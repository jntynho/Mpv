package live.mehiz.mpvkt.ui.home

import android.content.Context
import android.content.ContentUris
import android.provider.MediaStore
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.CancellationSignal
import android.os.OperationCanceledException
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
import androidx.core.graphics.scale
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import live.mehiz.mpvkt.ui.icons.Icons
import live.mehiz.mpvkt.ui.icons.PlayCircle

/** Bounded shared cache; never hold an Activity or decode on the UI thread. */
internal object Thumbnails {
  private val cache = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
  }
  private val permits = Semaphore(2)
  private val requestLocks = Array(32) { Mutex() }
  suspend fun load(context: Context, uri: String, revision: Long): Bitmap? = withContext(Dispatchers.IO) {
    val key = "$uri@$revision"
    cache.get(key)?.let { return@withContext it }
    requestLocks[(key.hashCode() and Int.MAX_VALUE) % requestLocks.size].withLock {
      permits.withPermit {
        cache.get(key)?.let { return@withPermit it }
        currentCoroutineContext().ensureActive()
        try {
          // Some providers are briefly busy after a scan. Retry once, never cache failures.
          var bitmap: Bitmap? = null
          repeat(2) { attempt ->
            if (bitmap == null) {
              if (attempt > 0) delay(200)
              currentCoroutineContext().ensureActive()
              bitmap = providerThumbnail(context, uri) ?: extractFrame(context, uri)
            }
          }
          currentCoroutineContext().ensureActive()
          bitmap?.also { cache.put(key, it) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        catch (_: OutOfMemoryError) { cache.evictAll(); null }
      }
    }
  }

  private suspend fun providerThumbnail(context: Context, uri: String): Bitmap? = coroutineScope {
    if (Build.VERSION.SDK_INT < 29) return@coroutineScope null
    try {
      cancellableThumbnailProvider { signal ->
        context.contentResolver.loadThumbnail(uri.toUri(), Size(320, 180), signal)
      }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (cancelled: OperationCanceledException) { currentCoroutineContext().ensureActive(); null }
    catch (_: Exception) { null }
  }

  private suspend fun extractFrame(context: Context, uri: String): Bitmap? {
    if (Build.VERSION.SDK_INT < 27) {
      // Provider thumbnails are sampled on old devices, unlike getFrameAtTime's full-size allocation.
      val legacy = runCatching {
        @Suppress("DEPRECATION")
        MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver, ContentUris.parseId(uri.toUri()),
          MediaStore.Video.Thumbnails.MINI_KIND, null)
      }.getOrNull()
      if (legacy != null) return legacy
    }
    val retriever = MediaMetadataRetriever()
    try {
      retriever.setDataSource(context, uri.toUri())
      val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
      val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
      if (Build.VERSION.SDK_INT < 27 && (width <= 0 || height <= 0 || width.toLong() * height > 1920L * 1080)) return null
      val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.coerceAtLeast(0) ?: 0
      // Representative frame, beginning and a later sync frame: individual failures don't abort all fallbacks.
      for (time in longArrayOf(-1L, 0L, (duration / 3).coerceAtMost(30_000L) * 1000L).distinct()) {
        currentCoroutineContext().ensureActive()
        val frame = runCatching {
          if (Build.VERSION.SDK_INT >= 27) retriever.getScaledFrameAtTime(time, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, 180)
          else retriever.getFrameAtTime(time, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }.getOrNull() ?: continue
        if (frame.width <= 320 && frame.height <= 180) return frame
        val ratio = minOf(320f / frame.width, 180f / frame.height)
        return frame.scale((frame.width * ratio).toInt().coerceAtLeast(1),
          (frame.height * ratio).toInt().coerceAtLeast(1), true).also { if (it !== frame) frame.recycle() }
      }
      return null
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: SecurityException) { return null }
    catch (_: IllegalArgumentException) { return null }
    catch (_: RuntimeException) { return null }
    finally { runCatching { retriever.release() } }
  }
}

/** Register cancellation synchronously, before a provider can start blocking an IO thread. */
internal suspend fun <T> cancellableThumbnailProvider(block: (CancellationSignal) -> T): T = coroutineScope {
  val signal = CancellationSignal()
  val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
    try { awaitCancellation() } finally { signal.cancel() }
  }
  try { withContext(Dispatchers.IO) { block(signal) } }
  finally { cancellation.cancel() }
}

@Composable
fun VideoThumbnail(context: Context, uri: String, modifier: Modifier = Modifier, revision: Long = 0) {
  val appContext = context.applicationContext
  // Key the whole state holder so recycled rows cannot flash a previous video's cover.
  val bitmap by key(uri, revision) {
    produceState<Bitmap?>(null, appContext, uri, revision) { value = Thumbnails.load(appContext, uri, revision) }
  }
  Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    else Icon(Icons.PlayCircle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
