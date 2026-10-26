package live.mehiz.mpvkt.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaUrlTest {
  private val protocols = setOf("http", "https", "rtsp", "file")

  @Test fun acceptsMediaSources() {
    listOf("https://example.com/video.mp4", "rtsp://192.168.1.1/live", "file:///storage/movie.mkv",
      "HTTPS://example.com/video.m3u8?token=test").forEach {
      assertTrue(it, MediaUrl.isValid(it, protocols))
    }
  }

  @Test fun rejectsInvalidOrUnsupportedInput() {
    listOf("", "   ", "example.com/movie.mp4", "https://", "https:", "https://bad host/movie",
      "javascript:alert(1)", "https://example.com/\nvideo", "custom://host/video").forEach {
      assertFalse(it, MediaUrl.isValid(it, protocols))
    }
  }
}
