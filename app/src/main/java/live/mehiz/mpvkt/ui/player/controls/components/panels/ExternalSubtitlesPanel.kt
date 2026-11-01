package live.mehiz.mpvkt.ui.player.controls.components.panels

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.Locale
import kotlinx.coroutines.*
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.ui.icons.*
import live.mehiz.mpvkt.ui.motion.LocalReduceMotion
import live.mehiz.mpvkt.ui.player.SubtitleStorage
import live.mehiz.mpvkt.ui.player.controls.components.PlayerLazyScrollArea

/** Text and bitmap subtitle formats libmpv understands; no generic txt or binary .sub files. */
internal fun isSubtitleFile(file: File): Boolean =
  file.extension.lowercase(Locale.ROOT) in
    setOf("srt", "ass", "ssa", "vtt", "smi", "sami", "sbv", "lrc", "mpl", "ttml", "dfxp", "idx") ||
    (file.extension.equals("sub", true) &&
      !File(file.parentFile, file.nameWithoutExtension + ".idx").isFile)

internal suspend fun listSubtitleDirectory(directory: File): List<File> =
  withContext(Dispatchers.IO) {
    val root = directory.canonicalFile
    val children = root.listFiles() ?: throw java.io.IOException("Unreadable directory")
    children
      .filter { child ->
        currentCoroutineContext().ensureActive()
        // Reject links out of the selected directory, including loops back into ancestors.
        child.canonicalFile.parentFile == root &&
          (child.isDirectory || child.isFile && isSubtitleFile(child))
      }
      .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) })
  }

private data class SubtitleDirectoryPage(
  val folder: String?,
  val entries: List<File> = emptyList(),
  val failed: Boolean = false,
)

@Composable
internal fun ExternalSubtitlesPanel(
  path: List<String>,
  adding: Boolean,
  onEnter: (String) -> Unit,
  onUp: () -> Unit,
  onSelect: (File) -> Unit,
  onStorageRoot: (String) -> Unit = {},
) {
  val context = LocalContext.current
  val owner = LocalLifecycleOwner.current
  var refresh by remember { mutableIntStateOf(0) }
  var permissionFailure by remember { mutableStateOf(false) }
  val permission =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
  DisposableEffect(owner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) refresh++
    }
    owner.lifecycle.addObserver(observer)
    onDispose { owner.lifecycle.removeObserver(observer) }
  }
  val accessible = remember(refresh) { SubtitleStorage.granted(context) }
  val folder = path.lastOrNull() ?: SubtitleStorage.primaryDirectory()?.absolutePath
  val reduced = LocalReduceMotion.current
  var roots by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
  var storageMenu by remember { mutableStateOf(false) }
  val internalStorage = stringResource(R.string.mpv_internal_storage)
  LaunchedEffect(accessible, refresh, internalStorage) {
    roots =
      if (accessible)
        withContext(Dispatchers.IO) {
          runCatching {
              SubtitleStorage.roots(context).map { root ->
                val label =
                  if (root.canonicalFile == SubtitleStorage.primaryDirectory()?.canonicalFile)
                    internalStorage
                  else if (Build.VERSION.SDK_INT >= 24)
                    context
                      .getSystemService(android.os.storage.StorageManager::class.java)
                      .getStorageVolume(root)
                      ?.getDescription(context) ?: root.name
                  else root.name
                root.absolutePath to label
              }
            }
            .getOrDefault(emptyList())
        }
      else emptyList()
  }
  var page by remember { mutableStateOf(SubtitleDirectoryPage(folder)) }
  // Keep the previous page drawn during IO, but disable it as soon as navigation starts.
  var loading by
    remember(folder, refresh, accessible) { mutableStateOf(accessible && folder != null) }
  LaunchedEffect(folder, refresh, accessible) {
    if (!accessible || folder == null) return@LaunchedEffect
    loading = true
    try {
      val entries = listSubtitleDirectory(File(folder))
      withContext(Dispatchers.IO) { SubtitleStorage.save(context, folder) }
      page = SubtitleDirectoryPage(folder, entries)
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      page = SubtitleDirectoryPage(folder, failed = true)
    } finally {
      loading = false
    }
  }
  Column(Modifier.fillMaxSize()) {
    if (roots.size > 1 && accessible)
      Box {
        TextButton(onClick = { storageMenu = true }, enabled = !adding && !loading) {
          Icon(Icons.Folder, null)
          Spacer(Modifier.width(8.dp))
          Text(roots.firstOrNull { it.first == path.firstOrNull() }?.second ?: internalStorage)
          Icon(Icons.ArrowDropDown, null)
        }
        DropdownMenu(expanded = storageMenu, onDismissRequest = { storageMenu = false }) {
          roots.forEach { (root, label) ->
            DropdownMenuItem(
              text = { Text(label) },
              onClick = {
                storageMenu = false
                onStorageRoot(root)
              },
            )
          }
        }
      }
    if (path.size > 1)
      TextButton(onClick = onUp, enabled = !adding) {
        Icon(Icons.ArrowBack, null)
        Spacer(Modifier.width(8.dp))
        Text(File(path.last()).name, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    when {
      folder == null ->
        Column(Modifier.padding(20.dp)) {
          Text(stringResource(R.string.mpv_subtitle_storage_failed))
          TextButton({ refresh++ }) { Text(stringResource(R.string.mpv_subtitle_storage_retry)) }
        }
      !accessible ->
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(stringResource(R.string.mpv_subtitle_storage_explanation))
          Button(
            onClick = {
              if (Build.VERSION.SDK_INT >= 30) {
                permissionFailure = runCatching { SubtitleStorage.request(context) }.isFailure
              } else permission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
          ) {
            Text(stringResource(R.string.mpv_subtitle_storage_grant))
          }
          if (permissionFailure) Text(stringResource(R.string.mpv_subtitle_storage_failed))
        }
      else -> {
        Box(Modifier.fillMaxWidth().height(2.dp)) {
          if (loading || adding) LinearProgressIndicator(Modifier.fillMaxSize())
        }
        AnimatedContent(
          targetState = page,
          contentKey = { it.folder },
          modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(),
          transitionSpec = {
            val backwards =
              initialState.folder?.startsWith(targetState.folder.orEmpty() + File.separator) == true
            val direction = if (backwards) -1 else 1
            val duration = if (reduced) 0 else 220
            ((fadeIn(tween(duration)) +
                slideInHorizontally(tween(duration)) { direction * it / 12 }) togetherWith
                (fadeOut(tween(if (reduced) 0 else 140)) +
                  slideOutHorizontally(tween(duration)) { -direction * it / 12 }))
              .using(SizeTransform(clip = true, sizeAnimationSpec = { _, _ -> tween(0) }))
          },
          label = "subtitle_folder_contents",
        ) { directory ->
          val enabled = !adding && !loading && directory.folder == folder
          if (directory.failed) {
            Column(Modifier.padding(20.dp)) {
              Text(stringResource(R.string.mpv_subtitle_storage_failed))
              TextButton({ refresh++ }, enabled = enabled) {
                Text(stringResource(R.string.mpv_subtitle_storage_retry))
              }
            }
          } else
            PlayerLazyScrollArea(Modifier.fillMaxSize()) {
              if (directory.entries.isEmpty() && !loading)
                item {
                  Text(stringResource(R.string.mpv_subtitle_storage_empty), Modifier.padding(20.dp))
                }
              items(directory.entries, key = { it.absolutePath }) { file ->
                Row(
                  Modifier.fillMaxWidth()
                    .clickable(enabled = enabled) {
                      if (file.isDirectory) onEnter(file.absolutePath) else onSelect(file)
                    }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                  Icon(if (file.isDirectory) Icons.FolderOpen else Icons.Subtitles, null)
                  Text(
                    file.name,
                    Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
        }
      }
    }
  }
}
