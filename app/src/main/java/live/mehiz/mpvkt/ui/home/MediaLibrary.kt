package live.mehiz.mpvkt.ui.home

import android.app.Application
import android.content.ContentUris
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryVideo(val uri: String, val name: String, val folderId: String, val folder: String, val duration: Long)
data class LibraryState(val videos: List<LibraryVideo> = emptyList(), val loading: Boolean = false, val failed: Boolean = false)

class MediaLibrary(application: Application) : AndroidViewModel(application) {
  private val mutableState = MutableStateFlow(LibraryState())
  val state = mutableState.asStateFlow()
  private var job: Job? = null

  fun reload() {
    job?.cancel()
    job = viewModelScope.launch {
      mutableState.value = mutableState.value.copy(loading = true, failed = false)
      try {
        val videos = withContext(Dispatchers.IO) {
          val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
          val projection = arrayOf("_id", "_display_name", "bucket_id", "bucket_display_name", "duration")
          buildList {
            getApplication<Application>().contentResolver.query(collection, projection, null, null, "date_added DESC")?.use { cursor ->
              while (cursor.moveToNext()) {
                add(LibraryVideo(ContentUris.withAppendedId(collection, cursor.getLong(0)).toString(),
                  cursor.getString(1) ?: "Video", cursor.getString(2) ?: "", cursor.getString(3) ?: "Videos", cursor.getLong(4)))
              }
            }
          }
        }
        mutableState.value = LibraryState(videos)
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        mutableState.value = LibraryState(failed = true)
      }
    }
  }
}
