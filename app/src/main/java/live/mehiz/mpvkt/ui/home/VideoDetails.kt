package live.mehiz.mpvkt.ui.home

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.util.LruCache
import androidx.core.net.toUri
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal data class VideoDetails(
  val width: Int,
  val height: Int,
  val fps: Float?,
  val subtitles: Boolean,
  val codec: String,
  val hardware: Boolean,
)

internal object VideoDetailsLoader {
  private val cache = LruCache<String, VideoDetails>(100)
  private val permits = Semaphore(2)

  suspend fun load(context: Context, video: LibraryVideo): VideoDetails? =
    withContext(Dispatchers.IO) {
      val key = "${video.uri}:${video.revision}"
      cache.get(key)?.let {
        return@withContext it
      }
      permits.withPermit {
        cache.get(key)?.let {
          return@withPermit it
        }
        ensureActive()
        val extractor = MediaExtractor()
        try {
          extractor.setDataSource(context, video.uri.toUri(), null)
          var width = video.width
          var height = video.height
          var fps: Float? = null
          var mime = video.mimeType
          var subs = false
          for (i in 0 until extractor.trackCount) {
            ensureActive()
            val f = extractor.getTrackFormat(i)
            val type = f.getString(MediaFormat.KEY_MIME).orEmpty()
            if (type.startsWith("video/")) {
              mime = type
              if (f.containsKey(MediaFormat.KEY_WIDTH)) width = f.getInteger(MediaFormat.KEY_WIDTH)
              if (f.containsKey(MediaFormat.KEY_HEIGHT))
                height = f.getInteger(MediaFormat.KEY_HEIGHT)
              if (f.containsKey(MediaFormat.KEY_FRAME_RATE))
                fps =
                  runCatching { f.getFloat(MediaFormat.KEY_FRAME_RATE) }
                    .getOrElse {
                      runCatching { f.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }.getOrNull()
                    }
            }
            if (
              type.startsWith("text/") ||
                "subtitle" in type ||
                "subrip" in type ||
                "ttml" in type ||
                "cea-" in type
            )
              subs = true
          }
          val hardware =
            runCatching {
                MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.any { c ->
                  !c.isEncoder &&
                    c.supportedTypes.any { it.equals(mime, true) } &&
                    if (Build.VERSION.SDK_INT >= 29) c.isHardwareAccelerated
                    else !c.name.contains("google", true) && !c.name.contains("android", true)
                }
              }
              .getOrDefault(false)
          ensureActive()
          VideoDetails(width, height, fps, subs, mime.removePrefix("video/"), hardware).also {
            cache.put(key, it)
          }
        } catch (c: CancellationException) {
          throw c
        } catch (_: Exception) {
          null
        } finally {
          extractor.release()
        }
      }
    }
}
