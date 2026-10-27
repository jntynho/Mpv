package live.mehiz.mpvkt.ui.home

import android.app.Application
import android.content.ContentUris
import android.provider.MediaStore
import android.os.Build
import kotlinx.coroutines.ensureActive
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryVideo(val uri: String, val name: String, val folderId: String, val folder: String, val duration: Long, val path: String, val folderPath: String, val revision: Long, val dateAdded: Long = 0, val sizeBytes: Long = 0)
data class LibraryState(val videos: List<LibraryVideo> = emptyList(), val loading: Boolean = false, val failed: Boolean = false, val loaded: Boolean = false)

internal val LocalMediaLibrary = staticCompositionLocalOf<MediaLibrary> { error("Library session is missing") }

class MediaLibrary(application: Application) : AndroidViewModel(application) {
  private val mutableState = MutableStateFlow(LibraryState())
  val state = mutableState.asStateFlow()
  internal var enteredHome by mutableStateOf(false)
  private var job: Job? = null
  private var rootSource: List<LibraryVideo>? = null
  private var rootListing: LibraryListing? = null

  internal fun preparedRoot(source: List<LibraryVideo>, sort: LibrarySort): LibraryListing? =
    rootListing?.takeIf { rootSource === source && it.sort == sort }

  fun clearAccess() {
    job?.cancel()
    job = null
    rootSource = null
    rootListing = null
    mutableState.value = LibraryState()
  }

  fun reload(): Job {
    job?.takeIf { it.isActive }?.let { return it }
    val scan = viewModelScope.launch {
      mutableState.value = mutableState.value.copy(loading = true, failed = false)
      try {
        val videos = withContext(Dispatchers.IO) {
          val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
          val pathColumn = if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.RELATIVE_PATH else MediaStore.Video.Media.DATA
          val projection = arrayOf("_id", "_display_name", "bucket_id", "bucket_display_name", "duration", pathColumn, "date_modified") +
            (if (Build.VERSION.SDK_INT >= 29) arrayOf(MediaStore.Video.Media.DATA) else emptyArray()) +
            arrayOf(MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.SIZE)
          val addedIndex = projection.indexOf(MediaStore.MediaColumns.DATE_ADDED)
          val sizeIndex = projection.indexOf(MediaStore.MediaColumns.SIZE)
          buildList {
            getApplication<Application>().contentResolver.query(collection, projection, null, null, "date_added DESC")?.use { cursor ->
              while (cursor.moveToNext()) {
                ensureActive()
                val name = cursor.getString(1) ?: "Video"
                val storedPath = cursor.getString(5).orEmpty()
                val indexedPath = if (Build.VERSION.SDK_INT >= 29) cursor.getString(7).orEmpty() else storedPath
                val parent = if (indexedPath.isNotBlank()) indexedPath.substringBeforeLast('/', "") else storedPath.trimEnd('/')
                val path = indexedPath.ifBlank { listOf(parent, name).filter { it.isNotBlank() }.joinToString("/") }

                add(LibraryVideo(ContentUris.withAppendedId(collection, cursor.getLong(0)).toString(),
                  name, cursor.getString(2).orEmpty() + "|" + parent, cursor.getString(3) ?: "Videos", cursor.getLong(4).coerceAtLeast(0), path, parent, cursor.getLong(6),
                  cursor.getLong(addedIndex).coerceAtLeast(0), cursor.getLong(sizeIndex).coerceAtLeast(0)))
              }
            }
          }
        }
        val prepared = withContext(Dispatchers.Default) {
          val saved = getApplication<Application>().getSharedPreferences("library_ui", android.content.Context.MODE_PRIVATE)
          val sort = LibrarySort.restore(saved.getString("sort_key", null), saved.getBoolean("sort_ascending", true),
            if (saved.contains("sort")) saved.getInt("sort", 0) else null)
          buildLibraryListing(videos, null, "", sort)
        }
        rootSource = videos
        rootListing = prepared
        mutableState.value = LibraryState(videos, loaded = true)
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        mutableState.value = mutableState.value.copy(loading = false, failed = true, loaded = true)
      }
    }
    job = scan
    return scan
  }
}
