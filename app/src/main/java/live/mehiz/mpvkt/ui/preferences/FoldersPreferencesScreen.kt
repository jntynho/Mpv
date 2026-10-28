package live.mehiz.mpvkt.ui.preferences

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.FoldersPreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.ui.home.*
import live.mehiz.mpvkt.ui.icons.*
import live.mehiz.mpvkt.ui.motion.ElasticSettingsScaffold
import live.mehiz.mpvkt.ui.motion.ElasticSettingsTopAppBar
import live.mehiz.mpvkt.ui.utils.LocalBackStack
import live.mehiz.mpvkt.ui.utils.LocalNavigateBack
import org.koin.compose.koinInject

@Serializable
object FoldersPreferencesScreen : Screen {
  @Composable
  override fun Content() {
    val preferences = koinInject<FoldersPreferences>()
    val library = LocalMediaLibrary.current
    val context = LocalContext.current
    val navigation = LocalBackStack.current
    val excluded by preferences.excluded.collectAsState()
    val included by preferences.included.collectAsState()
    val includeOnly by preferences.includeOnly.collectAsState()
    val minimumDuration by preferences.minimumDuration.collectAsState()
    val minimumSize by preferences.minimumSizeMb.collectAsState()
    val hidden by preferences.includeHidden.collectAsState()
    val trees by preferences.hiddenTrees.collectAsState()
    val libraryState by library.state.collectAsState()
    var error by remember { mutableStateOf(false) }
    var cleared by remember { mutableStateOf(false) }
    val picker =
      rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null)
          try {
            context.contentResolver.takePersistableUriPermission(
              uri,
              Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            preferences.hiddenTrees.set(preferences.hiddenTrees.get() + uri.toString())
            preferences.includeHidden.set(true)
            error = false
            library.reload(force = true)
          } catch (_: SecurityException) {
            error = true
          }
      }
    FolderScaffold(R.string.mpv_folders) { padding ->
      Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
        PreferenceCategory({ Text(stringResource(R.string.mpv_library_filters)) })
        SettingsGroup {
          ListPreference(
            minimumDuration,
            preferences.minimumDuration::set,
            listOf(0, 5, 10, 30, 60, 120, 300),
            title = { Text(stringResource(R.string.mpv_minimum_duration)) },
            summary = {
              Text(
                if (minimumDuration == 0) stringResource(R.string.mpv_no_limit)
                else "$minimumDuration s"
              )
            },
            valueToText = {
              AnnotatedString(if (it == 0) context.getString(R.string.mpv_no_limit) else "$it s")
            },
          )
          ListPreference(
            minimumSize,
            preferences.minimumSizeMb::set,
            listOf(0, 1, 5, 10, 20, 50, 100, 500),
            title = { Text(stringResource(R.string.mpv_minimum_size)) },
            summary = {
              Text(
                if (minimumSize == 0) stringResource(R.string.mpv_no_limit) else "$minimumSize MB"
              )
            },
            valueToText = {
              AnnotatedString(if (it == 0) context.getString(R.string.mpv_no_limit) else "$it MB")
            },
          )
        }
        PreferenceCategory({ Text(stringResource(R.string.mpv_folder_visibility)) })
        SettingsGroup {
          SettingsRow(
            { Text(stringResource(R.string.mpv_excluded_folders)) },
            { Text(stringResource(R.string.mpv_selected_count, excluded.size)) },
            { Icon(Icons.Folder, null) },
          ) {
            navigation.add(FolderSelectionScreen(false))
          }
          SwitchPreference(
            includeOnly,
            preferences.includeOnly::set,
            { Text(stringResource(R.string.mpv_include_only)) },
            { Text(stringResource(R.string.mpv_include_only_summary)) },
          )
          ExpandableSettingsContent(includeOnly) {
            SettingsRow(
              { Text(stringResource(R.string.mpv_included_folders)) },
              { Text(stringResource(R.string.mpv_selected_count, included.size)) },
            ) {
              navigation.add(FolderSelectionScreen(true))
            }
            if (included.isEmpty())
              Text(
                stringResource(R.string.mpv_included_empty),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
          }
        }
        PreferenceCategory({ Text(stringResource(R.string.mpv_selected_hidden_folders)) })
        SettingsGroup {
          SwitchPreference(
            hidden,
            { enabled ->
              if (enabled && trees.isEmpty()) picker.launch(null)
              else {
                preferences.includeHidden.set(enabled)
                library.reload(force = true)
              }
            },
            { Text(stringResource(R.string.mpv_scan_selected_folders)) },
            { Text(stringResource(R.string.mpv_hidden_access_summary)) },
          )
          ExpandableSettingsContent(hidden) {
            trees.sorted().forEach { tree ->
              val name by
                produceState(tree, tree) {
                  value =
                    withContext(Dispatchers.IO) {
                      runCatching { selectedTreePath(context, tree.toUri()) }.getOrDefault(tree)
                    }
                }
              SettingsRow(
                { Text(name) },
                trailing = {
                  IconButton({
                    preferences.hiddenTrees.set(preferences.hiddenTrees.get() - tree)
                    runCatching {
                      context.contentResolver.releasePersistableUriPermission(
                        tree.toUri(),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                      )
                    }
                    library.reload(force = true)
                  }) {
                    Icon(Icons.Close, stringResource(R.string.generic_close))
                  }
                },
              ) {}
            }
            SettingsRow(
              { Text(stringResource(R.string.mpv_add_folder)) },
              icon = { Icon(Icons.Add, null) },
            ) {
              picker.launch(null)
            }
          }
          if (error)
            Text(
              stringResource(R.string.mpv_folder_permission_failed),
              color = MaterialTheme.colorScheme.error,
            )
          if (libraryState.incomplete)
            Text(
              stringResource(R.string.mpv_folder_scan_incomplete),
              color = MaterialTheme.colorScheme.error,
            )
        }
        PreferenceCategory({ Text(stringResource(R.string.mpv_library_maintenance)) })
        SettingsGroup {
          SettingsRow(
            { Text(stringResource(R.string.mpv_rescan_library)) },
            {
              Text(
                stringResource(
                  if (libraryState.loading) R.string.mpv_refreshing
                  else R.string.mpv_rescan_library_summary
                )
              )
            },
            { Icon(Icons.Refresh, null) },
            enabled = !libraryState.loading,
          ) {
            library.reload(force = true)
          }
          SettingsRow(
            { Text(stringResource(R.string.mpv_clear_library_cache)) },
            {
              Text(
                stringResource(
                  if (cleared) R.string.mpv_cache_cleared
                  else R.string.mpv_clear_library_cache_summary
                )
              )
            },
          ) {
            library.clearPreparedCache()
            Thumbnails.clear()
            cleared = true
          }
        }
      }
    }
  }
}

@Serializable
data class FolderSelectionScreen(val inclusion: Boolean) : Screen {
  @Composable
  override fun Content() {
    val preferences = koinInject<FoldersPreferences>()
    val library by LocalMediaLibrary.current.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val preference = if (inclusion) preferences.included else preferences.excluded
    val selected by preference.collectAsState()
    val options =
      remember(library.videos, selected) {
        (library.videos.map { it.folderPath } + selected)
          .filter { it.isNotBlank() }
          .distinct()
          .sortedWith(String.CASE_INSENSITIVE_ORDER)
      }
    val picker =
      rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null)
          scope.launch {
            val path =
              withContext(Dispatchers.IO) {
                runCatching { selectedTreePath(context, uri) }.getOrNull()
              }
            if (path != null) preference.set(preference.get() + path)
          }
      }
    FolderScaffold(
      if (inclusion) R.string.mpv_included_folders else R.string.mpv_excluded_folders
    ) { padding ->
      LazyColumn(Modifier.fillMaxSize().padding(padding)) {
        item {
          SettingsGroup {
            SettingsRow(
              { Text(stringResource(R.string.mpv_add_folder)) },
              icon = { Icon(Icons.Add, null) },
            ) {
              picker.launch(null)
            }
          }
        }
        items(options, key = { it }) { path ->
          SettingsGroup {
            SettingsRow(
              { Text(path.substringAfterLast('/')) },
              { Text(path) },
              { Icon(Icons.Folder, null) },
              toggle = path in selected,
            ) {
              preference.set(if (path in selected) selected - path else selected + path)
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderScaffold(title: Int, content: @Composable (PaddingValues) -> Unit) {
  ElasticSettingsScaffold(
    topBar = {
      ElasticSettingsTopAppBar(
        { Text(stringResource(title)) },
        navigationIcon = {
          IconButton(LocalNavigateBack.current) {
            Icon(Icons.AutoMirrored.ArrowBack, stringResource(R.string.mpv_back))
          }
        },
      )
    },
    content = content,
  )
}
