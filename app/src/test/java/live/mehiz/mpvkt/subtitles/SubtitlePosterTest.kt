package live.mehiz.mpvkt.subtitles

import android.app.Application
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SubtitlePosterTest {
  @Test
  fun posterMetadataNormalizesOnlySupportedTmdbAddresses() = runBlocking {
    assertEquals("https://image.tmdb.org/t/p/w342/test.jpg", subtitlePosterUrl("/test.jpg"))
    assertEquals(
      subtitlePosterUrl("/test.jpg"),
      subtitlePosterUrl("https://image.tmdb.org/t/p/original/test.jpg"),
    )
    for (invalid in
      listOf(
        "//evil.example/test.jpg",
        "https://evil.example/test.jpg",
        "https://image.tmdb.org@evil.example/t/p/w185/a.jpg",
        "/../test.jpg",
        "https://image.tmdb.org/t/p/w185/a.jpg?key=secret",
      )) assertNull(subtitlePosterUrl(invalid))
    val directory = kotlin.io.path.createTempDirectory().toFile()
    try {
      val service =
        SubtitleService(
          FakeSubtitleTransport { _, _ ->
            """{"results":[{"id":1,"title":"Show","mediaType":"tv","poster":"/test.jpg"}]}"""
              .toByteArray()
          },
          { "" },
          directory,
        )
      assertEquals(subtitlePosterUrl("/test.jpg"), service.media("Show").single().posterUrl)
    } finally {
      directory.deleteRecursively()
    }
  }

  @Test
  fun failedImagesRetryAndDecodedImagesAreReusedAtBoundedSize() = runBlocking {
    val source = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888)
    val output = ByteArrayOutputStream()
    source.compress(Bitmap.CompressFormat.PNG, 100, output)
    source.recycle()
    var calls = 0
    val loader =
      SubtitlePosters(
        FakeSubtitleTransport { _, _ ->
          calls++
          if (calls == 1) byteArrayOf(1, 2) else output.toByteArray()
        }
      )
    val first = loader.load("https://image.tmdb.org/t/p/w185/test.jpg")
    assertNotNull(first)
    assertTrue(first!!.width <= 384 && first.height <= 576)
    assertSame(first, loader.load("https://image.tmdb.org/t/p/w185/test.jpg"))
    assertEquals(2, calls)
    assertSame(first, loader.peek("https://image.tmdb.org/t/p/original/test.jpg"))
    assertNull(loader.load(null))
  }
}
