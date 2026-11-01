package live.mehiz.mpvkt.ui.player.controls.components.panels

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import java.io.File
import java.util.Locale
import kotlinx.coroutines.*
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.ui.icons.*
import live.mehiz.mpvkt.ui.player.controls.components.PlayerLazyScrollArea

/** Text and bitmap subtitle formats libmpv understands; no generic txt or binary .sub files. */
internal fun isSubtitleFile(file: File): Boolean =
  file.extension.lowercase(Locale.ROOT) in setOf("srt", "ass", "ssa", "vtt", "smi", "sami", "sbv", "lrc", "mpl", "ttml", "dfxp", "idx") ||
    (file.extension.equals("sub", true) && !File(file.parentFile, file.nameWithoutExtension + ".idx").isFile)

internal suspend fun listSubtitleDirectory(directory: File): List<File> = withContext(Dispatchers.IO) {
  val root = directory.canonicalFile
  val children = root.listFiles() ?: throw java.io.IOException("Unreadable directory")
  children.filter { child ->
    currentCoroutineContext().ensureActive()
    // Reject links out of the selected directory, including loops back into ancestors.
    child.canonicalFile.parentFile == root && (child.isDirectory || child.isFile && isSubtitleFile(child))
  }.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) })
}

@Composable
internal fun ExternalSubtitlesPanel(
  path: List<String>,
  adding: Boolean,
  onEnter: (String) -> Unit,
  onUp: () -> Unit,
  onSelect: (File) -> Unit,
) {
  val context = LocalContext.current
  val owner = LocalLifecycleOwner.current
  var refresh by remember { mutableIntStateOf(0) }
  var permissionFailure by remember { mutableStateOf(false) }
  val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
  DisposableEffect(owner) {
    val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
    owner.lifecycle.addObserver(observer)
    onDispose { owner.lifecycle.removeObserver(observer) }
  }
  val accessible = remember(refresh) {
    if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
    else ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
  }
  val folder = path.lastOrNull()
  // Old directory rows must never remain clickable while a new listing starts.
  var files by remember(folder, refresh, accessible) { mutableStateOf<List<File>>(emptyList()) }
  var loading by remember(folder, refresh, accessible) { mutableStateOf(accessible) }
  var failed by remember(folder, refresh, accessible) { mutableStateOf(false) }
  LaunchedEffect(folder, refresh, accessible) {
    files = emptyList()
    failed = false
    if (!accessible) return@LaunchedEffect
    loading = true
    try {
      files = if (folder != null) listSubtitleDirectory(File(folder)) else withContext(Dispatchers.IO) {
        val primary = Environment.getExternalStorageDirectory()
        val roots = if (Build.VERSION.SDK_INT >= 30)
          context.getSystemService(StorageManager::class.java).storageVolumes.mapNotNull { it.directory }
        else context.getExternalFilesDirs(null).filterNotNull().mapNotNull { it.parentFile?.parentFile?.parentFile?.parentFile }
        (listOf(primary) + roots).filter { it.isDirectory && it.canRead() }.distinctBy { it.canonicalPath }
      }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      failed = true
    } finally {
      loading = false
    }
  }
  Column(Modifier.fillMaxSize()) {
    if (path.isNotEmpty())
      TextButton(onClick = onUp, enabled = !adding) {
        Icon(Icons.ArrowBack, null)
        Spacer(Modifier.width(8.dp))
        Text(folder.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    when {
      !accessible -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.mpv_subtitle_storage_explanation))
        Button(onClick = {
          if (Build.VERSION.SDK_INT >= 30) {
            permissionFailure = runCatching {
              context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                "package:${context.packageName}".toUri()))
            }.recoverCatching {
              context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }.isFailure
          } else permission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }) { Text(stringResource(R.string.mpv_subtitle_storage_grant)) }
        if (permissionFailure) Text(stringResource(R.string.mpv_subtitle_storage_failed))
      }
      loading || adding -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
      }
      failed -> Column(Modifier.padding(20.dp)) {
        Text(stringResource(R.string.mpv_subtitle_storage_failed))
        TextButton({ refresh++ }) { Text(stringResource(R.string.mpv_subtitle_storage_retry)) }
      }
      else -> key(folder) {
        PlayerLazyScrollArea(Modifier.fillMaxSize()) {
          if (files.isEmpty()) item { Text(stringResource(R.string.mpv_subtitle_storage_empty), Modifier.padding(20.dp)) }
          items(files, key = { it.absolutePath }) { file ->
            Row(
              Modifier.fillMaxWidth().clickable(enabled = !adding) {
                if (file.isDirectory) onEnter(file.absolutePath) else onSelect(file)
              }.padding(horizontal = 20.dp, vertical = 14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              Icon(if (file.isDirectory) Icons.FolderOpen else Icons.Subtitles, null)
              Text(file.name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
          }
        }
      }
    }
  }
}
