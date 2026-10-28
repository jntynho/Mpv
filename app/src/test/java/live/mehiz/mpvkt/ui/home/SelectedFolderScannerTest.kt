package live.mehiz.mpvkt.ui.home

import android.app.Application
import android.content.*
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SelectedFolderScannerTest {
  @Test
  fun onlyGrantedTreesAreReadAndHiddenChildrenKeepPlayableDocumentUris() = runBlocking {
    var queries = 0
    val provider =
      object : ContentProvider() {
        override fun onCreate() = true

        override fun query(
          uri: Uri,
          projection: Array<out String>?,
          selection: String?,
          args: Array<out String>?,
          sort: String?,
        ): Cursor {
          queries++
          val id = DocumentsContract.getDocumentId(uri)
          return MatrixCursor(projection).apply {
            if (id == "primary:Hidden") {
              addRow(
                arrayOf(
                  "primary:Hidden/.series",
                  ".series",
                  DocumentsContract.Document.MIME_TYPE_DIR,
                  0L,
                  1000L,
                )
              )
              addRow(
                arrayOf(
                  "primary:Hidden/.nomedia",
                  ".nomedia",
                  "application/octet-stream",
                  0L,
                  1000L,
                )
              )
            } else {
              addRow(
                arrayOf(
                  "primary:Hidden/.series/film.mkv",
                  "film.mkv",
                  "video/x-matroska",
                  99L,
                  5000L,
                )
              )
              // A provider cycle must not cause an infinite traversal.
              addRow(
                arrayOf(
                  "primary:Hidden",
                  "cycle",
                  DocumentsContract.Document.MIME_TYPE_DIR,
                  0L,
                  1000L,
                )
              )
            }
          }
        }

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0

        override fun update(
          uri: Uri,
          values: ContentValues?,
          selection: String?,
          args: Array<out String>?,
        ) = 0
      }
    ShadowContentResolver.registerProviderInternal("mpv-test", provider)
    val app = RuntimeEnvironment.getApplication()
    assertTrue(SelectedFolderScanner.scan(app, emptySet()).videos.isEmpty())
    assertEquals(0, queries)
    val tree = DocumentsContract.buildTreeDocumentUri("mpv-test", "primary:Hidden").toString()
    val result = SelectedFolderScanner.scan(app, setOf(tree))
    assertFalse(result.incomplete)
    assertEquals(2, queries)
    val video = result.videos.single()
    assertEquals("/storage/emulated/0/Hidden/.series/film.mkv", video.path)
    assertEquals(
      "primary:Hidden/.series/film.mkv",
      DocumentsContract.getDocumentId(Uri.parse(video.uri)),
    )
    assertEquals(99L, video.sizeBytes)
  }

  @Test
  fun disabledThumbnailsNeverReadTheProvider() = runBlocking {
    val guarded =
      object : ContextWrapper(RuntimeEnvironment.getApplication()) {
        override fun getContentResolver(): ContentResolver =
          error("Disabled covers must not touch a provider")
      }
    assertNull(
      Thumbnails.load(guarded, "content://not-readable/film", 1, ThumbnailOptions(enabled = false))
    )
  }
}
