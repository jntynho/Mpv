// Modified for MPV: folder-first library, thumbnails, paths and pull-to-refresh.
package live.mehiz.mpvkt.ui.home

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.PlayerPreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.ui.motion.ElasticRefreshBox
import live.mehiz.mpvkt.ui.player.PlayerActivity
import live.mehiz.mpvkt.ui.preferences.PreferencesScreen
import live.mehiz.mpvkt.ui.utils.LocalBackStack
import live.mehiz.mpvkt.ui.utils.navigationMotion
import org.koin.compose.koinInject

private data class LibraryPage(
  val folder: String?,
  val listing: LibraryListing,
  val mode: LibraryViewMode = LibraryViewMode.Folder,
)

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
    val reduceMotion by
      koinInject<PlayerPreferences>().reduceMotion.changes().collectAsState(initial = false)
    var selectedFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val folders = koinInject<live.mehiz.mpvkt.preferences.FoldersPreferences>()
    val excluded by folders.excluded.collectAsState()
    val included by folders.included.collectAsState()
    val includeOnly by folders.includeOnly.collectAsState()
    val minimumDuration by folders.minimumDuration.collectAsState()
    val minimumSize by folders.minimumSizeMb.collectAsState()
    val policy = FolderPolicy(excluded, included, includeOnly, minimumDuration, minimumSize)
    // Keep the prepared root fast path free of another cold asynchronous filtering pass.
    val visibleVideos =
      if (policy.unrestricted) state.videos
      else {
        val filtered by
          produceState(emptyList<LibraryVideo>(), state.videos, policy) {
            value = withContext(Dispatchers.Default) { policy.apply(state.videos) }
          }
        filtered
      }
    val savedSort = remember { context.getSharedPreferences("library_ui", Context.MODE_PRIVATE) }
    var viewMode by rememberSaveable {
      mutableStateOf(LibraryViewMode.restore(savedSort.getString("view_mode", null)))
    }
    val initialSort =
      remember(savedSort) {
        LibrarySort.restore(
          savedSort.getString("sort_key", null),
          savedSort.getBoolean("sort_ascending", true),
          if (savedSort.contains("sort")) savedSort.getInt("sort", 0) else null,
        )
      }
    var sortKey by rememberSaveable { mutableStateOf(initialSort.key) }
    var sortAscending by rememberSaveable { mutableStateOf(initialSort.ascending) }
    val sorting = LibrarySort(sortKey, sortAscending)
    val goBack: () -> Unit = {
      if (searching) {
        searching = false
        query = ""
      } else
        selectedFolder =
          if (viewMode == LibraryViewMode.Tree) selectedFolder?.let(::parentTreeFolder) else null
    }
    BackHandler(searching || selectedFolder != null, onBack = goBack)
    val appearance = koinInject<live.mehiz.mpvkt.preferences.AppearancePreferences>()
    val listFields by appearance.libraryFields.collectAsState()
    val cardFields by appearance.libraryCardFields.collectAsState()
    val grid by appearance.libraryGrid.collectAsState()
    val columns by appearance.libraryGridColumns.collectAsState()
    val fields = if (grid) cardFields else listFields
    val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val maximumColumns = ((screenWidth - 40) / 144).coerceIn(1, 5)
    val effectiveColumns = if (columns in 2..5) columns.coerceAtMost(maximumColumns) else 0
    val display = LibraryDisplayOptions(fields, grid, effectiveColumns)
    val positions =
      if (fields.has(LibraryField.Progress)) {
        val dao = koinInject<live.mehiz.mpvkt.database.MpvKtDatabase>().videoDataDao()
        val flow = remember(dao) { dao.observeAll() }
        val records by flow.collectAsState(initial = emptyList())
        remember(records) { records.associate { it.mediaTitle to it.lastPosition } }
      } else emptyMap()
    val colors = MaterialTheme.colorScheme
    val title =
      remember(state.videos, selectedFolder, viewMode) {
        if (selectedFolder == null) "MPV"
        else if (viewMode == LibraryViewMode.Tree) selectedFolder!!.substringAfterLast('/')
        else state.videos.firstOrNull { it.folderId == selectedFolder }?.folder ?: "MPV"
      }
    Scaffold(
      floatingActionButton = { HomeQuickActions(reduceMotion) },
      contentWindowInsets = WindowInsets.safeDrawing,
      topBar = {
        LibraryHeader(
          title,
          searching,
          selectedFolder != null,
          query,
          if (
            viewMode == LibraryViewMode.Videos ||
              (viewMode == LibraryViewMode.Folder && selectedFolder != null)
          )
            sorting.forFiles()
          else sorting,
          reduceMotion,
          onQuery = { query = it },
          onBack = goBack,
          onSearch = { searching = true },
          onSort = { choice ->
            sortKey = choice.key
            sortAscending = choice.ascending
            savedSort.edit {
              putString("sort_key", choice.key.storedName)
              putBoolean("sort_ascending", choice.ascending)
            }
          },
          onSettings = { backstack.add(PreferencesScreen) },
          onSecureFolder = { backstack.add(live.mehiz.mpvkt.ui.secure.SecureFolderScreen) },
          viewMode = viewMode,
          display = display,
          onDisplay = { choice ->
            // Layout changes keep the independent field profile for each view.
            if (choice.grid == grid) {
              if (grid) appearance.libraryCardFields.set(choice.fields)
              else appearance.libraryFields.set(choice.fields)
            }
            appearance.libraryGrid.set(choice.grid)
            appearance.libraryGridColumns.set(choice.columns)
          },
          onViewMode = { mode ->
            viewMode = mode
            selectedFolder = null
            savedSort.edit { putString("view_mode", mode.storedName) }
          },
        )
      },
    ) { padding ->
      Column(Modifier.fillMaxSize().padding(padding)) {
        if (!access) {
          Text(
            stringResource(R.string.mpv_library_permission),
            Modifier.padding(horizontal = 20.dp),
          )
          Button(
            onClick = { mediaAccess.request() },
            modifier = Modifier.padding(horizontal = 20.dp),
          ) {
            Text(stringResource(R.string.mpv_allow))
          }
        } else if (mediaAccess.limited) {
          TextButton(onClick = { mediaAccess.request() }) {
            Text(stringResource(R.string.mpv_manage_access))
          }
        }
        ElasticRefreshBox(
          loading = state.loading,
          onRefresh = { library.reload().join() },
          reduceMotion = reduceMotion,
          modifier = Modifier.weight(1f),
        ) {
          if (state.loading && state.videos.isEmpty())
            CircularProgressIndicator(Modifier.align(Alignment.Center), strokeWidth = 2.dp)
          val sourceVideos = visibleVideos
          val searchQuery = query
          val requestedFolder = selectedFolder
          val initialListing =
            if (viewMode == LibraryViewMode.Folder && selectedFolder == null && query.isEmpty())
              library.preparedRoot(sourceVideos, sorting)
            else null
          val savedLists = rememberSaveableStateHolder()
          val page by
            produceState(
              LibraryPage(
                selectedFolder,
                initialListing ?: LibraryListing(sort = sorting),
                viewMode,
              ),
              sourceVideos,
              requestedFolder,
              searchQuery,
              sorting,
              viewMode,
            ) {
              val cached =
                if (
                  viewMode == LibraryViewMode.Folder &&
                    requestedFolder == null &&
                    searchQuery.isEmpty()
                )
                  library.preparedRoot(sourceVideos, sorting)
                else null
              value =
                withContext(Dispatchers.Default) {
                  LibraryPage(
                    requestedFolder,
                    cached
                      ?: buildViewListing(
                        sourceVideos,
                        requestedFolder,
                        searchQuery,
                        sorting,
                        viewMode,
                      ),
                    viewMode,
                  )
                }
            }
          AnimatedContent(
            targetState = page,
            contentKey = { it.mode.storedName + ":" + it.folder },
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
              if (targetState.mode != initialState.mode) {
                (fadeIn(tween(if (reduceMotion) 0 else 220)) togetherWith
                    fadeOut(tween(if (reduceMotion) 0 else 160)))
                  .using(SizeTransform(clip = true, sizeAnimationSpec = { _, _ -> tween(0) }))
              } else
                navigationMotion(
                  back =
                    targetState.folder == null ||
                      (initialState.folder != null &&
                        targetState.folder != null &&
                        initialState.folder!!.startsWith(targetState.folder!! + "/")),
                  reduceMotion = reduceMotion,
                )
            },
            label = "library_navigation",
          ) { destination ->
            val folder = destination.folder
            val listing = destination.listing
            savedLists.SaveableStateProvider(
              destination.mode.storedName + ":" + (folder ?: "root")
            ) {
              val listState = rememberLazyGridState()
              LaunchedEffect(display.grid, display.columns) { listState.requestScrollToItem(0) }
              ResetScrollOnLibrarySort(listState, listing.sort)
              BoxWithConstraints(Modifier.fillMaxSize()) {
                val maxColumns = ((maxWidth - 40.dp) / 144.dp).toInt().coerceAtLeast(1)
                LazyVerticalGrid(
                  columns =
                    if (!display.grid) GridCells.Fixed(1)
                    else if (display.columns in 2..5)
                      GridCells.Fixed(display.columns.coerceAtMost(maxColumns))
                    else GridCells.Adaptive(160.dp),
                  modifier = Modifier.fillMaxSize(),
                  state = listState,
                  contentPadding =
                    PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  if (state.failed) item { Text(stringResource(R.string.mpv_library_error)) }
                  else if (
                    access &&
                      !state.loading &&
                      listing.ready &&
                      listing.folders.isEmpty() &&
                      listing.videos.isEmpty()
                  )
                    item { Text(stringResource(R.string.mpv_empty)) }
                  if (state.incomplete)
                    item {
                      Text(
                        stringResource(R.string.mpv_folder_scan_incomplete),
                        color = colors.onSurfaceVariant,
                      )
                    }
                  items(listing.folders, key = { "folder:" + it.id }) { entry ->
                    LibraryFolderItem(
                      entry,
                      display,
                      Modifier.animateItem(
                        fadeInSpec = tween(if (reduceMotion) 0 else 180),
                        placementSpec = tween(if (reduceMotion) 0 else 320),
                        fadeOutSpec = tween(if (reduceMotion) 0 else 120),
                      ),
                    ) {
                      selectedFolder = entry.id
                    }
                  }
                  items(listing.videos, key = { "video:" + it.uri }) { video ->
                    LibraryVideoItem(
                      video,
                      display,
                      positions[video.name] ?: 0,
                      Modifier.animateItem(
                        fadeInSpec = tween(if (reduceMotion) 0 else 180),
                        placementSpec = tween(if (reduceMotion) 0 else 320),
                        fadeOutSpec = tween(if (reduceMotion) 0 else 120),
                      ),
                    ) {
                      playFile(video.uri, context)
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
    context.startActivity(
      Intent(Intent.ACTION_VIEW, filepath.toUri()).setClass(context, PlayerActivity::class.java)
    )
  }
}

internal fun formatDuration(milliseconds: Long): String {
  val seconds = milliseconds.coerceAtLeast(0) / 1000
  return if (seconds >= 3600)
    "%d:%02d:%02d".format(java.util.Locale.ROOT, seconds / 3600, seconds / 60 % 60, seconds % 60)
  else "%d:%02d".format(java.util.Locale.ROOT, seconds / 60, seconds % 60)
}

/** Frozen home folder artwork; all view modes reuse the same original shape and dimensions. */
@Composable
internal fun LibraryFolderArtwork() {
  Image(
    painterResource(R.drawable.library_folder),
    null,
    Modifier.size(width = 88.dp, height = 76.dp),
  )
}
