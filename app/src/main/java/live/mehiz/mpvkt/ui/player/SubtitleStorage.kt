package live.mehiz.mpvkt.ui.player

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import java.io.File

internal object SubtitleStorage {
  fun granted(context: Context) =
    if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
    else
      ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
        PackageManager.PERMISSION_GRANTED

  fun request(context: Context) {
    if (Build.VERSION.SDK_INT >= 30) {
      runCatching {
          context.startActivity(
            Intent(
              Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
              "package:${context.packageName}".toUri(),
            )
          )
        }
        .getOrElse {
          context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }
  }

  fun roots(context: Context): List<File> {
    val primary = Environment.getExternalStorageDirectory()
    val extra =
      if (Build.VERSION.SDK_INT >= 30)
        context.getSystemService(StorageManager::class.java).storageVolumes.mapNotNull {
          it.directory
        }
      else
        context.getExternalFilesDirs(null).filterNotNull().mapNotNull {
          it.parentFile?.parentFile?.parentFile?.parentFile
        }
    return (listOf(primary) + extra)
      .filter { it.isDirectory && it.canRead() }
      .distinctBy { it.canonicalPath }
  }

  /** A path stack always starts at a storage root, so Back cannot escape shared storage. */
  fun restore(context: Context): List<String> {
    val primary = Environment.getExternalStorageDirectory()
    val roots = roots(context)
    val saved =
      context
        .getSharedPreferences("subtitle_browser", Context.MODE_PRIVATE)
        .getString("last_folder", null)
    val requested = saved?.let(::File)?.canonicalFile
    val folder = requested?.takeIf { it.isDirectory && it.canRead() } ?: primary.canonicalFile
    val root =
      roots.firstOrNull {
        folder == it.canonicalFile || folder.path.startsWith(it.canonicalPath + File.separator)
      } ?: return listOf(primary.absolutePath)
    val canonicalRoot = root.canonicalFile
    val result = mutableListOf(canonicalRoot.path)
    var cursor = canonicalRoot
    if (folder != canonicalRoot)
      folder.relativeTo(canonicalRoot).forEachPart { part ->
        cursor = File(cursor, part)
        result += cursor.path
      }
    return result
  }

  fun save(context: Context, folder: String) {
    context
      .getSharedPreferences("subtitle_browser", Context.MODE_PRIVATE)
      .edit()
      .putString("last_folder", folder)
      .apply()
  }
}

private inline fun File.forEachPart(block: (String) -> Unit) {
  path.split(File.separatorChar).filter(String::isNotEmpty).forEach(block)
}
