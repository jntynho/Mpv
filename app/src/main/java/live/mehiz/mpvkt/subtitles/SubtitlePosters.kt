package live.mehiz.mpvkt.subtitles

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/** TMDB-sized images only, with no original-resolution downloads or arbitrary metadata hosts. */
internal fun subtitlePosterUrl(raw: String): String? {
  if (raw.isBlank()) return null
  val path =
    if (raw.startsWith("/") && !raw.startsWith("//")) raw
    else
      runCatching {
          val uri = validatedSubtitleUri(raw)
          if (!uri.host.equals("image.tmdb.org", true) || uri.query != null || uri.fragment != null)
            return null
          uri.path
        }
        .getOrNull() ?: return null
  val file =
    path.removePrefix("/t/p/").let {
      if ('/' in it.removePrefix("/")) it.substringAfter('/') else it.removePrefix("/")
    }
  if (!file.matches(Regex("[A-Za-z0-9_-]{1,160}\\.(?:jpg|jpeg|png|webp)", RegexOption.IGNORE_CASE)))
    return null
  return "https://image.tmdb.org/t/p/w185/$file"
}

internal class SubtitlePosters(private val http: SubtitleTransport = SubtitleHttp()) {
  private val cache =
    object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
      override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
  private val permits = Semaphore(2)
  private val locks = Array(16) { Mutex() }

  fun peek(url: String?): Bitmap? = url?.let(cache::get)

  suspend fun load(raw: String?): Bitmap? =
    withContext(Dispatchers.IO) {
      val url = raw?.let(::subtitlePosterUrl) ?: return@withContext null
      cache.get(url)?.let {
        return@withContext it
      }
      locks[(url.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
        permits.withPermit {
          cache.get(url)?.let {
            return@withPermit it
          }
          try {
            withTimeoutOrNull(15_000) {
              var result: Bitmap? = null
              repeat(2) { attempt ->
                if (result == null) {
                  ensureActive()
                  if (attempt > 0) delay(250)
                  try {
                    val bytes = http.get(url, limit = 768 * 1024)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
                      throw java.io.IOException("Invalid poster image")
                    if (bounds.outWidth.toLong() * bounds.outHeight > 2_000_000)
                      return@withTimeoutOrNull null
                    val options =
                      BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                        inSampleSize = 1
                        while (
                          bounds.outWidth / inSampleSize > 240 ||
                            bounds.outHeight / inSampleSize > 360
                        ) inSampleSize *= 2
                      }
                    result = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                  } catch (cancelled: CancellationException) {
                    throw cancelled
                  } catch (_: Exception) {
                    /* One bounded retry; do not cache a broken image. */
                  }
                }
              }
              ensureActive()
              result?.also { cache.put(url, it) }
            }
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (_: OutOfMemoryError) {
            cache.evictAll()
            null
          }
        }
      }
    }

  companion object {
    val Shared = SubtitlePosters()
  }
}
