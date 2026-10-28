package live.mehiz.mpvkt.ui.home

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.provider.MediaStore
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import live.mehiz.mpvkt.ui.icons.Icons
import live.mehiz.mpvkt.ui.icons.PlayCircle

/** Bounded shared cache; never hold an Activity or decode on the UI thread. */
internal object Thumbnails {
  private val cache =
    object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
      override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
  private val mutableEpoch = kotlinx.coroutines.flow.MutableStateFlow(0L)
  val epoch = mutableEpoch.asStateFlow()

  @Synchronized
  fun clear() {
    cache.evictAll()
    mutableEpoch.value += 1
  }

  private val permits = Semaphore(2)
  private val requestLocks = Array(32) { Mutex() }

  suspend fun load(
    context: Context,
    uri: String,
    revision: Long,
    options: ThumbnailOptions = ThumbnailOptions(),
  ): Bitmap? =
    withContext(Dispatchers.IO) {
      if (!options.enabled) return@withContext null
      val generation = epoch.value
      val key = "$uri@$revision:${options.cacheSuffix}:$generation"
      cache.get(key)?.let {
        return@withContext it
      }
      requestLocks[(key.hashCode() and Int.MAX_VALUE) % requestLocks.size].withLock {
        permits.withPermit {
          cache.get(key)?.let {
            return@withPermit it
          }
          currentCoroutineContext().ensureActive()
          try {
            // Some providers are briefly busy after a scan. Retry once, never cache failures.
            var bitmap: Bitmap? = null
            repeat(2) { attempt ->
              if (bitmap == null) {
                if (attempt > 0) delay(200)
                currentCoroutineContext().ensureActive()
                bitmap =
                  when (options.generator) {
                    ThumbnailGenerator.Auto ->
                      providerThumbnail(context, uri, options)
                        ?: extractFrame(context, uri, options)
                    ThumbnailGenerator.MediaStore -> providerThumbnail(context, uri, options)
                    ThumbnailGenerator.Frame -> extractFrame(context, uri, options)
                  }
              }
            }
            currentCoroutineContext().ensureActive()
            bitmap?.also {
              synchronized(Thumbnails) { if (generation == epoch.value) cache.put(key, it) }
            }
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (_: Exception) {
            null
          } catch (_: OutOfMemoryError) {
            cache.evictAll()
            null
          }
        }
      }
    }

  private suspend fun providerThumbnail(
    context: Context,
    uri: String,
    options: ThumbnailOptions,
  ): Bitmap? = coroutineScope {
    if (Build.VERSION.SDK_INT < 29)
      return@coroutineScope runCatching {
          @Suppress("DEPRECATION")
          MediaStore.Video.Thumbnails.getThumbnail(
            context.contentResolver,
            ContentUris.parseId(uri.toUri()),
            MediaStore.Video.Thumbnails.MINI_KIND,
            null,
          )
        }
        .getOrNull()
    try {
      cancellableThumbnailProvider { signal ->
        context.contentResolver.loadThumbnail(
          uri.toUri(),
          Size(options.quality.width, options.quality.height),
          signal,
        )
      }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (cancelled: OperationCanceledException) {
      currentCoroutineContext().ensureActive()
      null
    } catch (_: Exception) {
      null
    }
  }

  private suspend fun extractFrame(
    context: Context,
    uri: String,
    options: ThumbnailOptions,
  ): Bitmap? {
    if (Build.VERSION.SDK_INT < 27) {
      // Provider thumbnails are sampled on old devices, unlike getFrameAtTime's full-size
      // allocation.
      val legacy =
        runCatching {
            @Suppress("DEPRECATION")
            MediaStore.Video.Thumbnails.getThumbnail(
              context.contentResolver,
              ContentUris.parseId(uri.toUri()),
              MediaStore.Video.Thumbnails.MINI_KIND,
              null,
            )
          }
          .getOrNull()
      if (legacy != null) return legacy
    }
    val retriever = MediaMetadataRetriever()
    try {
      retriever.setDataSource(context, uri.toUri())
      val width =
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
          ?: 0
      val height =
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
          ?: 0
      if (
        Build.VERSION.SDK_INT < 27 &&
          (width <= 0 || height <= 0 || width.toLong() * height > 1920L * 1080)
      )
        return null
      val duration =
        retriever
          .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
          ?.toLongOrNull()
          ?.coerceAtLeast(0) ?: 0
      // Representative frame, beginning and a later sync frame: individual failures don't abort all
      // fallbacks.
      val requestedTime =
        (duration.toDouble() * options.position.coerceIn(0, 100) / 100 * 1000).toLong()
      val targetWidth = options.quality.width
      val targetHeight = options.quality.height
      for (time in longArrayOf(requestedTime, -1L, 0L).distinct()) {
        currentCoroutineContext().ensureActive()
        val frame =
          runCatching {
              if (Build.VERSION.SDK_INT >= 27)
                retriever.getScaledFrameAtTime(
                  time,
                  MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                  targetWidth,
                  targetHeight,
                )
              else retriever.getFrameAtTime(time, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
            .getOrNull() ?: continue
        if (frame.width <= targetWidth && frame.height <= targetHeight) return frame
        val ratio =
          minOf(targetWidth.toFloat() / frame.width, targetHeight.toFloat() / frame.height)
        return frame
          .scale(
            (frame.width * ratio).toInt().coerceAtLeast(1),
            (frame.height * ratio).toInt().coerceAtLeast(1),
            true,
          )
          .also { if (it !== frame) frame.recycle() }
      }
      return null
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: SecurityException) {
      return null
    } catch (_: IllegalArgumentException) {
      return null
    } catch (_: RuntimeException) {
      return null
    } finally {
      runCatching { retriever.release() }
    }
  }
}

/** Register cancellation synchronously, before a provider can start blocking an IO thread. */
internal suspend fun <T> cancellableThumbnailProvider(block: (CancellationSignal) -> T): T =
  coroutineScope {
    val signal = CancellationSignal()
    val cancellation =
      launch(start = CoroutineStart.UNDISPATCHED) {
        try {
          awaitCancellation()
        } finally {
          signal.cancel()
        }
      }
    try {
      withContext(Dispatchers.IO) { block(signal) }
    } finally {
      cancellation.cancel()
    }
  }

@Composable
fun VideoThumbnail(
  context: Context,
  uri: String,
  modifier: Modifier = Modifier,
  revision: Long = 0,
) {
  val appContext = context.applicationContext
  // Key the whole state holder so recycled rows cannot flash a previous video's cover.
  val options = LocalThumbnailOptions.current
  val epoch = LocalThumbnailEpoch.current
  val bitmap by
    key(uri, revision, options, epoch) {
      produceState<Bitmap?>(null, appContext, uri, revision, options, epoch) {
        if (options.enabled) value = Thumbnails.load(appContext, uri, revision, options)
      }
    }
  Box(
    modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
    contentAlignment = Alignment.Center,
  ) {
    if (bitmap != null)
      Image(
        bitmap!!.asImageBitmap(),
        null,
        Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
      )
    else Icon(Icons.PlayCircle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
