package live.mehiz.mpvkt.ui.player.controls.components.panels

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.presentation.components.AppTextField
import live.mehiz.mpvkt.ui.icons.*
import live.mehiz.mpvkt.ui.player.controls.components.PlayerLazyScrollArea

internal val subtitleFileExtensions =
  setOf(
    "srt",
    "ass",
    "ssa",
    "vtt",
    "sub",
    "idx",
    "sup",
    "smi",
    "sami",
    "lrc",
    "txt",
    "ttml",
    "dfxp",
    "sbv",
  )

internal fun isSubtitleFile(name: String) =
  name.substringAfterLast('.', "").lowercase(Locale.ROOT) in subtitleFileExtensions

private data class SubtitleDirectory(
  val files: List<File> = emptyList(),
  val error: Boolean = false,
  val loading: Boolean = true,
)

/**
 * Local browsing stays inside the existing pane. Only an explicit permission action opens system
 * settings.
 */
@Composable
internal fun SubtitleLocalFilesPanel(onPick: (Uri) -> Unit) {
  val context = LocalContext.current
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  var revision by remember { mutableIntStateOf(0) }
  DisposableEffect(lifecycle) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) revision++
    }
    lifecycle.addObserver(observer)
    onDispose { lifecycle.removeObserver(observer) }
  }
  val permission =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { revision++ }
  val primaryRoot = remember { Environment.getExternalStorageDirectory() }
  val app = context.applicationContext
  val roots by
    produceState(listOf(primaryRoot), app) {
      value =
        withContext(Dispatchers.IO) {
          runCatching {
              (listOf(primaryRoot) +
                  app.getExternalFilesDirs(null).filterNotNull().mapNotNull {
                    it.parentFile?.parentFile?.parentFile?.parentFile
                  })
                .distinctBy { it.absolutePath }
            }
            .getOrDefault(listOf(primaryRoot))
        }
    }
  var rootPath by rememberSaveable { mutableStateOf(primaryRoot.absolutePath) }
  var path by rememberSaveable { mutableStateOf(rootPath) }
  var query by rememberSaveable { mutableStateOf("") }
  var opening by remember { mutableStateOf(false) }
  var launchFailed by remember { mutableStateOf(false) }
  val access =
    if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
    else
      ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
  val listing by
    key(path, rootPath, revision, access) {
      produceState(SubtitleDirectory(), path, rootPath, revision, access) {
        value =
          withContext(Dispatchers.IO) {
            try {
              val root = File(rootPath).canonicalFile
              val directory = File(path).canonicalFile
              require(directory == root || directory.path.startsWith(root.path + File.separator))
              val files =
                directory.listFiles()
                  ?: return@withContext SubtitleDirectory(error = true, loading = false)
              val visible =
                files
                  .filter { file ->
                    ensureActive()
                    !file.name.startsWith('.') &&
                      (file.isDirectory || isSubtitleFile(file.name)) &&
                      runCatching { file.canonicalPath.startsWith(root.path + File.separator) }
                        .getOrDefault(false)
                  }
                  .sortedWith(
                    compareBy<File> { !it.isDirectory }
                      .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                  )
              SubtitleDirectory(visible, loading = false)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
              throw cancelled
            } catch (_: Exception) {
              SubtitleDirectory(error = true, loading = false)
            }
          }
      }
    }
  val visible =
    remember(listing, query) {
      listing.files.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
  fun navigate(file: File) {
    query = ""
    path = file.absolutePath
    opening = false
  }
  val listState = androidx.compose.foundation.lazy.rememberLazyListState()
  LaunchedEffect(path, query, rootPath) { listState.requestScrollToItem(0) }
  PlayerLazyScrollArea(Modifier.fillMaxSize(), state = listState) {
    item("search") {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppTextField(
          query,
          { query = it },
          label = { Text(stringResource(R.string.local_subtitle_search)) },
          leadingIcon = { Icon(Icons.Search, null) },
        )
        Text(
          path,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          if (path != rootPath)
            TextButton({ File(path).parentFile?.let(::navigate) }) {
              Icon(Icons.ArrowBack, null)
              Text(stringResource(R.string.local_subtitle_up))
            }
          TextButton({ revision++ }) {
            Icon(Icons.Refresh, null)
            Text(stringResource(R.string.panel_retry))
          }
        }
        if (roots.size > 1)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            roots.forEachIndexed { index, root ->
              FilterChip(
                rootPath == root.absolutePath,
                {
                  rootPath = root.absolutePath
                  navigate(root)
                },
                label = { Text(stringResource(R.string.local_subtitle_storage, index + 1)) },
              )
            }
          }
        if (!access) {
          Text(
            stringResource(R.string.local_subtitle_permission),
            style = MaterialTheme.typography.bodySmall,
          )
          OutlinedButton({
            if (Build.VERSION.SDK_INT >= 30) {
              launchFailed =
                runCatching {
                    val perApp =
                      Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                      )
                    if (perApp.resolveActivity(context.packageManager) != null)
                      context.startActivity(perApp)
                    else
                      context.startActivity(
                        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                      )
                  }
                  .isFailure
            } else permission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
          }) {
            Text(stringResource(R.string.local_subtitle_grant))
          }
        }
        if (launchFailed || listing.error)
          Text(
            stringResource(R.string.local_subtitle_unreadable),
            color = MaterialTheme.colorScheme.error,
          )
      }
    }
    if (listing.loading)
      item("loading") {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
      }
    else if (visible.isEmpty() && !listing.error)
      item("empty") { Text(stringResource(R.string.local_subtitle_empty), Modifier.padding(24.dp)) }
    items(visible, key = { it.absolutePath }) { file ->
      Row(
        Modifier.fillMaxWidth()
          .heightIn(min = 64.dp)
          .clickable(enabled = !opening) {
            if (opening) return@clickable
            if (file.isDirectory) navigate(file)
            else {
              opening = true
              onPick(Uri.fromFile(file))
            }
          }
          .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Icon(
          if (file.isDirectory) Icons.Folder else Icons.Subtitles,
          null,
          Modifier.size(26.dp),
          tint = MaterialTheme.colorScheme.primary,
        )
        Text(file.name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (file.isDirectory)
          Icon(Icons.AutoMirrored.ArrowBack, null, Modifier.size(18.dp).rotate(180f))
      }
    }
  }
}
