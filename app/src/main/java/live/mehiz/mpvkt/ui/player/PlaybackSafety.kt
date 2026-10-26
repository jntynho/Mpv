package live.mehiz.mpvkt.ui.player

import android.content.pm.ActivityInfo
import android.content.res.Configuration

internal fun effectiveVideoAspect(aspect: Double?, rotation: Int): Double? {
  if (aspect == null || !aspect.isFinite() || aspect <= 0.001) return null
  val angle = ((rotation % 360) + 360) % 360
  return if (angle == 90 || angle == 270) 1.0 / aspect else aspect
}

internal fun orientationRequest(mode: PlayerOrientation, aspect: Double?): Int? = when (mode) {
  PlayerOrientation.Free -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
  PlayerOrientation.Video -> if (aspect == null || !aspect.isFinite() || aspect <= 0) null else
    if (aspect > 1) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
  PlayerOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
  PlayerOrientation.ReversePortrait -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
  PlayerOrientation.SensorPortrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
  PlayerOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
  PlayerOrientation.ReverseLandscape -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
  PlayerOrientation.SensorLandscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
}

internal fun nextManualRotation(actualOrientation: Int) = if (actualOrientation == Configuration.ORIENTATION_LANDSCAPE)
  PlayerOrientation.SensorPortrait else PlayerOrientation.SensorLandscape

internal fun validPipAspect(aspect: Double?): Double? = aspect?.takeIf { it.isFinite() && it in (1.0 / 2.39)..2.39 }

internal fun parseHttpHeaders(headers: Array<String>): Pair<String?, String> {
  val pairs = headers.toList().chunked(2).filter { it.size == 2 && it[0].isNotBlank() &&
    it.none { value -> '\r' in value || '\n' in value } }
  val agent = pairs.firstOrNull { it[0].equals("User-Agent", true) }?.get(1)
  val fields = pairs.filterNot { it[0].equals("User-Agent", true) }
    .joinToString(",") { "${it[0]}: ${it[1].replace(",", "\\,")}" }
  return agent to fields
}

internal fun restoreProperties(
  state: live.mehiz.mpvkt.database.entities.PlaybackStateEntity?,
  subDelayMs: Int, secondaryDelayMs: Int, audioDelayMs: Int, subSpeed: Double, defaultSpeed: Double = 1.0,
): Map<String, Double> = buildMap {
  put("sub-delay", (state?.subDelay ?: subDelayMs) / 1000.0)
  put("secondary-sub-delay", (state?.secondarySubDelay ?: secondaryDelayMs) / 1000.0)
  put("audio-delay", (state?.audioDelay ?: audioDelayMs) / 1000.0)
  put("sub-speed", state?.subSpeed ?: subSpeed)
  put("speed", state?.playbackSpeed?.takeIf { it.isFinite() && it > 0 } ?: defaultSpeed.takeIf { it.isFinite() && it > 0 } ?: 1.0)
}
