package live.mehiz.mpvkt.ui.player

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import live.mehiz.mpvkt.database.entities.PlaybackStateEntity
import org.junit.Assert.*
import org.junit.Test

class PlaybackSafetyTest {
 @Test fun videoOrientationWaitsForMetadata() {
  assertNull(orientationRequest(PlayerOrientation.Video, null))
  assertNull(orientationRequest(PlayerOrientation.Video, Double.NaN))
  assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT, orientationRequest(PlayerOrientation.Video, .5625))
  assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, orientationRequest(PlayerOrientation.Video, 16.0/9))
 }
 @Test fun manualRotationUsesActualScreenNotRequestedSensorMode() {
  assertEquals(PlayerOrientation.SensorPortrait, nextManualRotation(Configuration.ORIENTATION_LANDSCAPE))
  assertEquals(PlayerOrientation.SensorLandscape, nextManualRotation(Configuration.ORIENTATION_PORTRAIT))
  assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR, orientationRequest(PlayerOrientation.Free, null))
 }
 @Test fun accountsForBothPositiveAndNegativeMetadataRotation() {
  assertEquals(9.0/16, effectiveVideoAspect(16.0/9, 90)!!, .00001)
  assertEquals(9.0/16, effectiveVideoAspect(16.0/9, -90)!!, .00001)
  assertEquals(16.0/9, effectiveVideoAspect(16.0/9, 180)!!, .00001)
  assertNull(effectiveVideoAspect(0.0, 90))
 }
 @Test fun pipRejectsInvalidAndUnsupportedRatios() {
  for (value in listOf(null, 0.0, Double.NaN, Double.POSITIVE_INFINITY, 4.0, .1)) assertNull(validPipAspect(value))
  assertEquals(16.0/9, validPipAspect(16.0/9)!!, .00001)
 }
 @Test fun restoresDistinctPlaybackProperties() {
  val state=PlaybackStateEntity("video",12,1.5,2,250,1.1,3,-500,1,100)
  val properties=restoreProperties(state,0,0,0,1.0)
  assertEquals(1.5,properties["speed"]!!,0.0)
  assertEquals(.25,properties["sub-delay"]!!,0.0)
  assertEquals(-.5,properties["secondary-sub-delay"]!!,0.0)
  assertEquals(.1,properties["audio-delay"]!!,0.0)
  assertEquals(1.1,properties["sub-speed"]!!,0.0)
 }
 @Test fun newVideosUseConfiguredDefaults() {
  val p=restoreProperties(null,100,-200,300,1.0,1.25)
  assertEquals(1.25,p["speed"]!!,0.0)
  assertEquals(-.2,p["secondary-sub-delay"]!!,0.0)
 }
 @Test fun malformedHeadersDoNotCrashOrInjectNewLines() {
  assertEquals(null to "",parseHttpHeaders(emptyArray()))
  assertEquals(null to "Accept: video/*",parseHttpHeaders(arrayOf("Accept","video/*","dangling")))
  assertEquals("player" to "X-Test: a\\,b",parseHttpHeaders(arrayOf("X-Test","a,b","user-agent","player")))
  assertEquals(null to "",parseHttpHeaders(arrayOf("Bad","ok\r\ninjected")))
 }
 @Test fun unknownDecodersCannotCrashControls() {
  assertEquals(Decoder.Auto, Decoder.getDecoderFromValue("vulkan"))
  assertEquals(Decoder.HWPlus, Decoder.getDecoderFromValue("mediacodec"))
  assertEquals(Decoder.SW, Decoder.getDecoderFromValue("no"))
 }
}
