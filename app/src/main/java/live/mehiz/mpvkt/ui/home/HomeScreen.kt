// Modified for MPV: folder-first library, thumbnails, paths and pull-to-refresh.
package live.mehiz.mpvkt.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import live.mehiz.mpvkt.ui.utils.navigationMotion
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.serialization.Serializable
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.ui.icons.Icons
import live.mehiz.mpvkt.ui.icons.*
import live.mehiz.mpvkt.ui.icons.ArrowBack
import live.mehiz.mpvkt.ui.player.PlayerActivity
import live.mehiz.mpvkt.ui.preferences.PreferencesScreen
import live.mehiz.mpvkt.ui.utils.LocalBackStack

@Serializable
object HomeScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val library: MediaLibrary = viewModel()
    val state by library.state.collectAsState()
    var selectedFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var sortMenu by remember { mutableStateOf(false) }
    val savedSort = remember { context.getSharedPreferences("library_ui", Context.MODE_PRIVATE) }
    var sort by rememberSaveable { mutableStateOf(savedSort.getInt("sort", 0).coerceIn(0, 3)) }
    val permissions = if (Build.VERSION.SDK_INT >= 34) arrayOf(Manifest.permission.READ_MEDIA_VIDEO,
      Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) else if (Build.VERSION.SDK_INT >= 33)
      arrayOf(Manifest.permission.READ_MEDIA_VIDEO) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    fun hasAccess() = permissions.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    var access by remember { mutableStateOf(hasAccess()) }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      access = hasAccess(); library.reload()
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, library) {
      val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) { access = hasAccess(); library.reload() }
      }
      owner.lifecycle.addObserver(observer)
      onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val goBack: () -> Unit = {
      if (searching) { searching = false; query = "" } else selectedFolder = null
    }
    BackHandler(searching || selectedFolder != null, onBack = goBack)
    val colors = MaterialTheme.colorScheme
    Scaffold(topBar = {
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        TopAppBar(title = {
          if (searching) {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            TextField(query, { query = it }, Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
              placeholder = { Text(stringResource(R.string.mpv_search)) },
              colors = TextFieldDefaults.colors(focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surface,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
          } else {
            val title = if (selectedFolder == null) "MPV" else state.videos.firstOrNull { it.folderId == selectedFolder }?.folder ?: "MPV"
            AnimatedContent(targetState = title, transitionSpec = {
              (fadeIn(tween(240)) togetherWith fadeOut(tween(160)))
                .using(SizeTransform(clip = true, sizeAnimationSpec = { _, _ -> tween(280) }))
            }, label = "library_header_title") {
              Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
          }
        }, navigationIcon = {
          AnimatedVisibility(visible = searching || selectedFolder != null,
            enter = fadeIn(tween(240)) + expandHorizontally(tween(280)),
            exit = fadeOut(tween(180)) + shrinkHorizontally(tween(280))) {
            IconButton(onClick = goBack) {
              Icon(if (searching) Icons.Close else Icons.AutoMirrored.ArrowBack, stringResource(R.string.mpv_back))
            }
          }
        }, actions = {
          if (!searching) {
            IconButton(onClick = { searching = true }) { Icon(Icons.Search, stringResource(R.string.mpv_search)) }
            Box {
              IconButton(onClick = { sortMenu = true }) { Icon(Icons.Sort, stringResource(R.string.mpv_sort)) }
              DropdownMenu(sortMenu, { sortMenu = false }) {
                listOf(R.string.mpv_recent, R.string.mpv_name_asc, R.string.mpv_name_desc, R.string.mpv_duration).forEachIndexed { index, label ->
                  DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                    sort = index; savedSort.edit().putInt("sort", index).apply(); sortMenu = false
                  }, trailingIcon = { if (sort == index) Icon(Icons.Star, null, Modifier.size(16.dp)) })
                }
              }
            }
            IconButton(onClick = { backstack.add(PreferencesScreen) }) { Icon(Icons.Settings, stringResource(R.string.luma_settings)) }
          }
        })
      }
    }) { padding ->
      Column(Modifier.fillMaxSize().padding(padding)) {
        if (!access) {
          Text(stringResource(R.string.mpv_library_permission), Modifier.padding(horizontal = 20.dp))
          Button(onClick = { permissionRequest.launch(permissions) }, modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.mpv_allow))
          }
        } else if (Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(context,
            Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
          TextButton(onClick = { permissionRequest.launch(permissions) }) { Text(stringResource(R.string.mpv_manage_access)) }
        }
        PullToRefreshBox(isRefreshing = state.loading, onRefresh = { if (!state.loading) library.reload() }, modifier = Modifier.weight(1f)) {
          AnimatedContent(targetState = selectedFolder, modifier = Modifier.fillMaxSize(), transitionSpec = {
            navigationMotion(back = targetState == null)
          }, label = "library_navigation") { folder ->
            val videos = state.videos.filter { (folder == null || it.folderId == folder) &&
              (it.name.contains(query, true) || it.path.contains(query, true) || it.folder.contains(query, true)) }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 64.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)) {
              if (state.failed) item { Text(stringResource(R.string.mpv_library_error)) }
              else if (access && !state.loading && videos.isEmpty()) item { Text(stringResource(R.string.mpv_empty)) }
              if (folder == null) {
                val groups = videos.groupBy { it.folderId }.entries.let { groups -> when (sort) {
                  1 -> groups.sortedBy { it.value.first().folder.lowercase() }
                  2 -> groups.sortedByDescending { it.value.first().folder.lowercase() }
                  3 -> groups.sortedByDescending { it.value.sumOf { video -> video.duration } }
                  else -> groups.toList()
                } }
                items(groups, key = { it.key }) { group ->
                  val entry = group.value.first()
                  Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { selectedFolder = group.key }
                    .padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.library_folder), null, Modifier.size(width = 88.dp, height = 76.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                      Text(entry.folder, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                      if (entry.folderPath.isNotBlank()) Text(entry.folderPath, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                  }
                }
              } else {
                val sorted = when (sort) {
                  1 -> videos.sortedBy { it.name.lowercase() }
                  2 -> videos.sortedByDescending { it.name.lowercase() }
                  3 -> videos.sortedByDescending { it.duration }
                  else -> videos
                }
                items(sorted, key = { it.uri }) { video ->
                  Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { playFile(video.uri, context) }
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

  fun playFile(filepath: String, context: Context) {
    context.startActivity(Intent(Intent.ACTION_VIEW, filepath.toUri()).setClass(context, PlayerActivity::class.java))
  }
}

internal fun formatDuration(milliseconds: Long): String {
  val seconds = milliseconds.coerceAtLeast(0) / 1000
  return if (seconds >= 3600) "%d:%02d:%02d".format(java.util.Locale.ROOT, seconds / 3600, seconds / 60 % 60, seconds % 60)
  else "%d:%02d".format(java.util.Locale.ROOT, seconds / 60, seconds % 60)
}
