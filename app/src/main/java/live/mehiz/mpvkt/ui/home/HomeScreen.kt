// Modified for MPV: folder-first library, thumbnails, paths and pull-to-refresh.
package live.mehiz.mpvkt.ui.home

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import live.mehiz.mpvkt.ui.utils.navigationMotion
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import live.mehiz.mpvkt.ui.motion.ElasticRefreshBox
import live.mehiz.mpvkt.preferences.PlayerPreferences
import org.koin.compose.koinInject
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.ui.player.PlayerActivity
import live.mehiz.mpvkt.ui.preferences.PreferencesScreen
import live.mehiz.mpvkt.ui.utils.LocalBackStack

private data class LibraryPage(val folder: String?, val listing: LibraryListing)

@Serializable
object HomeScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val library = LocalMediaLibrary.current
    val mediaAccess = live.mehiz.mpvkt.ui.startup.LocalLibraryAccess.current
    val access = mediaAccess.granted
    val state by library.state.collectAsState()
    val reduceMotion by koinInject<PlayerPreferences>().reduceMotion.changes().collectAsState(initial = false)
    var selectedFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val savedSort = remember { context.getSharedPreferences("library_ui", Context.MODE_PRIVATE) }
    val initialSort = remember(savedSort) {
      LibrarySort.restore(savedSort.getString("sort_key", null), savedSort.getBoolean("sort_ascending", true),
        if (savedSort.contains("sort")) savedSort.getInt("sort", 0) else null)
    }
    var sortKey by rememberSaveable { mutableStateOf(initialSort.key) }
    var sortAscending by rememberSaveable { mutableStateOf(initialSort.ascending) }
    val sorting = LibrarySort(sortKey, sortAscending)
    val goBack: () -> Unit = {
      if (searching) { searching = false; query = "" } else selectedFolder = null
    }
    BackHandler(searching || selectedFolder != null, onBack = goBack)
    val colors = MaterialTheme.colorScheme
    val title = remember(state.videos, selectedFolder) {
      if (selectedFolder == null) "MPV" else state.videos.firstOrNull { it.folderId == selectedFolder }?.folder ?: "MPV"
    }
    Scaffold(topBar = {
      LibraryHeader(title, searching, selectedFolder != null, query,
        if (selectedFolder == null) sorting else sorting.forFiles(), reduceMotion,
        onQuery = { query = it }, onBack = goBack, onSearch = { searching = true }, onSort = { choice ->
          sortKey = choice.key; sortAscending = choice.ascending
          savedSort.edit {
            putString("sort_key", choice.key.storedName)
            putBoolean("sort_ascending", choice.ascending)
          }
        }, onSettings = { backstack.add(PreferencesScreen) })
    }) { padding ->
      Column(Modifier.fillMaxSize().padding(padding)) {
        if (!access) {
          Text(stringResource(R.string.mpv_library_permission), Modifier.padding(horizontal = 20.dp))
          Button(onClick = { mediaAccess.request() }, modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.mpv_allow))
          }
        } else if (mediaAccess.limited) {
          TextButton(onClick = { mediaAccess.request() }) { Text(stringResource(R.string.mpv_manage_access)) }
        }
        ElasticRefreshBox(loading = state.loading, onRefresh = { library.reload().join() }, reduceMotion = reduceMotion, modifier = Modifier.weight(1f)) {
          if (state.loading && state.videos.isEmpty()) CircularProgressIndicator(Modifier.align(Alignment.Center), strokeWidth = 2.dp)
          val sourceVideos = state.videos
          val searchQuery = query
          val requestedFolder = selectedFolder
          val initialListing = if (selectedFolder == null && query.isEmpty()) library.preparedRoot(sourceVideos, sorting) else null
          val savedLists = rememberSaveableStateHolder()
          val page by produceState(LibraryPage(selectedFolder, initialListing ?: LibraryListing(sort = sorting)), sourceVideos, requestedFolder, searchQuery, sorting) {
            val cached = if (requestedFolder == null && searchQuery.isEmpty()) library.preparedRoot(sourceVideos, sorting) else null
            value = withContext(Dispatchers.Default) {
              LibraryPage(requestedFolder, cached ?: buildLibraryListing(sourceVideos, requestedFolder, searchQuery, sorting))
            }
          }
          AnimatedContent(targetState = page, contentKey = { it.folder }, modifier = Modifier.fillMaxSize(), transitionSpec = {
            navigationMotion(back = targetState.folder == null, reduceMotion = reduceMotion)
          }, label = "library_navigation") { destination ->
            val folder = destination.folder
            val listing = destination.listing
            savedLists.SaveableStateProvider(folder ?: "root") {
              val listState = rememberLazyListState()
              var appliedSort by remember { mutableStateOf(listing.sort) }
              LaunchedEffect(listing.sort) {
                if (appliedSort != listing.sort) { listState.requestScrollToItem(0); appliedSort = listing.sort }
              }
              LazyColumn(Modifier.fillMaxSize(), state = listState,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 64.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.failed) item { Text(stringResource(R.string.mpv_library_error)) }
                else if (access && !state.loading && listing.ready && listing.folders.isEmpty() && listing.videos.isEmpty()) item { Text(stringResource(R.string.mpv_empty)) }
                if (folder == null) {
                  items(listing.folders, key = { it.id }) { entry ->
                    Row(Modifier.animateItem(
                      fadeInSpec = tween(if (reduceMotion) 0 else 180),
                      placementSpec = tween(if (reduceMotion) 0 else 260),
                      fadeOutSpec = tween(if (reduceMotion) 0 else 120))
                      .fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { selectedFolder = entry.id }
                      .padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                      Image(painterResource(R.drawable.library_folder), null, Modifier.size(width = 88.dp, height = 76.dp))
                      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        if (entry.path.isNotBlank()) Text(entry.path, maxLines = 2, overflow = TextOverflow.Ellipsis,
                          style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                      }
                    }
                  }
                } else {
                  items(listing.videos, key = { it.uri }) { video ->
                    Row(Modifier.animateItem(
                      fadeInSpec = tween(if (reduceMotion) 0 else 180),
                      placementSpec = tween(if (reduceMotion) 0 else 260),
                      fadeOutSpec = tween(if (reduceMotion) 0 else 120))
                      .fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { playFile(video.uri, context) }
                      .padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                      Box(Modifier.width(136.dp).height(88.dp).clip(RoundedCornerShape(12.dp))) {
                        VideoThumbnail(context, video.uri, Modifier.fillMaxSize(), video.revision)
                        Text(formatDuration(video.duration), Modifier.align(Alignment.BottomEnd).padding(4.dp)
                          .clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = .75f)).padding(horizontal = 4.dp, vertical = 2.dp),
                          color = Color.White, style = MaterialTheme.typography.labelSmall)
                      }
                      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(video.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        if (video.path.isNotBlank()) Text(video.path, maxLines = 2, overflow = TextOverflow.Ellipsis,
                          color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  fun playFile(filepath: String, context: Context) {
    context.startActivity(Intent(Intent.ACTION_VIEW, filepath.toUri()).setClass(context, PlayerActivity::class.java))
  }
}

internal fun formatDuration(milliseconds: Long): String {
  val seconds = milliseconds.coerceAtLeast(0) / 1000
  return if (seconds >= 3600) "%d:%02d:%02d".format(java.util.Locale.ROOT, seconds / 3600, seconds / 60 % 60, seconds % 60)
  else "%d:%02d".format(java.util.Locale.ROOT, seconds / 60, seconds % 60)
}
