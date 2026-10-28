package live.mehiz.mpvkt.ui.home

import androidx.compose.runtime.staticCompositionLocalOf

enum class ThumbnailGenerator {
  Auto,
  MediaStore,
  Frame,
}

enum class ThumbnailQuality(val width: Int, val height: Int) {
  Standard(320, 180),
  High(480, 270),
}

data class ThumbnailOptions(
  val enabled: Boolean = true,
  val generator: ThumbnailGenerator = ThumbnailGenerator.Auto,
  val quality: ThumbnailQuality = ThumbnailQuality.Standard,
  val position: Int = 33,
) {
  internal val cacheSuffix
    get() = "${generator.name}:${quality.name}:${position.coerceIn(0, 100)}"
}

internal val LocalThumbnailOptions = staticCompositionLocalOf { ThumbnailOptions() }
internal val LocalThumbnailEpoch = staticCompositionLocalOf { 0L }
