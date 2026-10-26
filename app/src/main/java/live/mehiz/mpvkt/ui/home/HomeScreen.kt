// Modified for MPV: an in-app MediaStore video and folder library.
package live.mehiz.mpvkt.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import `is`.xyz.mpv.Utils.PROTOCOLS
import kotlinx.serialization.Serializable
import live.mehiz.mpvkt.preferences.AppearancePreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import org.koin.compose.koinInject
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.presentation.Screen
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
    val appearance = koinInject<AppearancePreferences>()
    val compact by appearance.compactHome.collectAsState()
    val tips by appearance.showTips.collectAsState()
    val library: MediaLibrary = viewModel()
    val state by library.state.collectAsState()
    var folders by rememberSaveable { mutableStateOf(true) }
    var selectedFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var streamDialog by rememberSaveable { mutableStateOf(false) }
    var url by rememberSaveable { mutableStateOf("") }
    val permissions = if (Build.VERSION.SDK_INT >= 34) arrayOf(Manifest.permission.READ_MEDIA_VIDEO,
      Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) else if (Build.VERSION.SDK_INT >= 33)
      arrayOf(Manifest.permission.READ_MEDIA_VIDEO) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    fun hasAccess() = permissions.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    var access by remember { mutableStateOf(hasAccess()) }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      access = hasAccess()
      library.reload()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      uri?.let { playFile(it.toString(), context) }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, library) {
      val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) { access = hasAccess(); library.reload() }
      }
      owner.lifecycle.addObserver(observer)
      onDispose { owner.lifecycle.removeObserver(observer) }
    }
    BackHandler(selectedFolder != null) { selectedFolder = null }
    val visible = state.videos.filter { (selectedFolder == null || it.folderId == selectedFolder) &&
      (it.name.contains(query, true) || it.folder.contains(query, true)) }
    val colors = MaterialTheme.colorScheme
    Scaffold(topBar = {
      TopAppBar(title = { Text(if (selectedFolder == null) "MPV" else
        state.videos.firstOrNull { it.folderId == selectedFolder }?.folder ?: stringResource(R.string.mpv_folders)) },
        navigationIcon = { if (selectedFolder != null) IconButton(onClick = { selectedFolder = null }) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.mpv_back))
        } }, actions = {
          IconButton(onClick = { picker.launch(arrayOf("video/*", "audio/*")) }) { Icon(Icons.Default.Add, stringResource(R.string.luma_open)) }
          IconButton(onClick = { streamDialog = true }) { Icon(Icons.Default.Link, stringResource(R.string.luma_stream)) }
          IconButton(onClick = { backstack.add(PreferencesScreen) }) { Icon(Icons.Default.Settings, stringResource(R.string.luma_settings)) }
        })
    }) { padding ->
      Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
          placeholder = { Text(stringResource(R.string.mpv_search)) }, leadingIcon = { Icon(Icons.Default.Search, null) },
          shape = RoundedCornerShape(24.dp))
        Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          FilterChip(folders, { folders = true; selectedFolder = null }, label = { Text(stringResource(R.string.mpv_folders)) })
          FilterChip(!folders, { folders = false; selectedFolder = null }, label = { Text(stringResource(R.string.mpv_videos)) })
          IconButton(onClick = { library.reload() }) { Icon(Icons.Default.Refresh, stringResource(R.string.mpv_refresh)) }
        }
        if (tips) Text(stringResource(R.string.luma_tagline), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        if (!access) {
          Text(stringResource(R.string.mpv_permission))
          Button(onClick = { permissionRequest.launch(permissions) }) { Text(stringResource(R.string.mpv_allow)) }
        } else if (Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(context,
            Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
          TextButton(onClick = { permissionRequest.launch(permissions) }) { Text(stringResource(R.string.mpv_manage_access)) }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.failed) Text(stringResource(R.string.mpv_library_error))
        if (access && !state.loading && !state.failed && visible.isEmpty()) Text(stringResource(R.string.mpv_empty))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
          if (folders && selectedFolder == null) {
            items(visible.groupBy { it.folderId }.entries.sortedBy { it.value.first().folder.lowercase() }, key = { it.key }) { entry ->
              Card(onClick = { selectedFolder = entry.key }, shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)) {
                Row(Modifier.fillMaxWidth().padding(if (compact) 12.dp else 18.dp), verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                  Icon(Icons.Default.Folder, null, Modifier.size(40.dp), tint = colors.primary.copy(alpha = .85f))
                  Column(Modifier.weight(1f)) {
                    Text(entry.value.first().folder, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.mpv_video_count, entry.value.size), style = MaterialTheme.typography.bodySmall,
                      color = colors.onSurfaceVariant)
                  }
                }
              }
            }
          } else {
            items(visible, key = { it.uri }) { video ->
              Card(onClick = { playFile(video.uri, context) }, shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)) {
                Row(Modifier.fillMaxWidth().padding(if (compact) 12.dp else 18.dp), verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                  Icon(Icons.Default.PlayCircle, null, Modifier.size(36.dp), tint = colors.primary)
                  Column(Modifier.weight(1f)) {
                    Text(video.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${video.duration / 60000}:${(video.duration / 1000 % 60).toString().padStart(2, '0')}",
                      style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                  }
                }
              }
            }
          }
        }
      }
    }
    if (streamDialog) AlertDialog(onDismissRequest = { streamDialog = false }, title = { Text(stringResource(R.string.luma_stream)) },
      text = { OutlinedTextField(url, { url = it }, singleLine = true, label = { Text(stringResource(R.string.home_url_input_label)) }) },
      confirmButton = { TextButton(enabled = MediaUrl.isValid(url.trim(), PROTOCOLS.toSet()), onClick = {
        playFile(url.trim(), context); streamDialog = false
      }) { Text(stringResource(R.string.home_open_url)) } },
      dismissButton = { TextButton(onClick = { streamDialog = false }) { Text(stringResource(android.R.string.cancel)) } })
  }

  fun playFile(filepath: String, context: Context) {
    context.startActivity(Intent(Intent.ACTION_VIEW, filepath.toUri()).setClass(context, PlayerActivity::class.java))
  }
}
