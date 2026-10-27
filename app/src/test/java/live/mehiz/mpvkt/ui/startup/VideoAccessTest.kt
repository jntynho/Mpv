package live.mehiz.mpvkt.ui.startup

import android.Manifest
import org.junit.Assert.*
import org.junit.Test

class VideoAccessTest {
  @Test fun olderAndroidRequestsReadStorageOnly() {
    for (sdk in listOf(21, 23, 29, 32)) assertArrayEquals(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), VideoAccess.permissions(sdk))
  }
  @Test fun android13RequestsVideosWithoutPhotoOrBroadFileAccess() {
    assertArrayEquals(arrayOf(Manifest.permission.READ_MEDIA_VIDEO), VideoAccess.permissions(33))
  }
  @Test fun android14AndLaterSupportSelectedVideoAccess() {
    for (sdk in listOf(34, 35, 36)) assertArrayEquals(
      arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED), VideoAccess.permissions(sdk))
  }
}
