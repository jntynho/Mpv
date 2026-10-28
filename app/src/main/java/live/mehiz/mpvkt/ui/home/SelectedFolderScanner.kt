package live.mehiz.mpvkt.ui.home

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Only trees explicitly granted by the user. No broad storage permission or filesystem walk. */
internal object SelectedFolderScanner {
  data class Result(val videos: List<LibraryVideo>, val incomplete: Boolean)

  suspend fun scan(context: Context, trees: Set<String>): Result {
    val videos = mutableListOf<LibraryVideo>()
    var incomplete = false
    var visitedCount = 0
    trees.sorted().forEach { tree ->
      if (visitedCount >= 50_000) {
        incomplete = true
        return@forEach
      }
      val uri = tree.toUri()
      val visited = hashSetOf<String>()
      val pending = ArrayDeque<Triple<String, Int, String>>()
      try {
        pending.add(
          Triple(DocumentsContract.getTreeDocumentId(uri), 0, selectedTreePath(context, uri))
        )
      } catch (_: Exception) {
        incomplete = true
        return@forEach
      }
      while (pending.isNotEmpty()) {
        currentCoroutineContext().ensureActive()
        val (id, depth, parentPath) = pending.removeFirst()
        if (!visited.add(id)) continue
        if (++visitedCount > 50_000 || depth > 64) {
          incomplete = true
          pending.clear()
          break
        }
        try {
          val children = DocumentsContract.buildChildDocumentsUriUsingTree(uri, id)
          val columns =
            arrayOf("document_id", "_display_name", "mime_type", "_size", "last_modified")
          val cursor = cancellableThumbnailProvider { signal ->
            context.contentResolver.query(children, columns, null, null, null, signal)
          }
          if (cursor == null) {
            incomplete = true
            continue
          }
          cursor.use {
            while (it.moveToNext()) {
              currentCoroutineContext().ensureActive()
              if (++visitedCount > 50_000) {
                incomplete = true
                pending.clear()
                break
              }
              val child = it.getString(0) ?: continue
              val name = it.getString(1) ?: continue
              if (name == "." || name == ".." || '/' in name) {
                incomplete = true
                continue
              }
              val path = "$parentPath/$name"
              val mime = it.getString(2).orEmpty()
              if (mime == DocumentsContract.Document.MIME_TYPE_DIR)
                pending.add(Triple(child, depth + 1, path))
              else if (
                mime.startsWith("video/") ||
                  name.substringAfterLast('.', "").lowercase() in videoExtensions
              ) {
                videos +=
                  LibraryVideo(
                    DocumentsContract.buildDocumentUriUsingTree(uri, child).toString(),
                    name,
                    "selected|$parentPath",
                    parentPath.substringAfterLast('/').ifBlank { name },
                    0L,
                    path,
                    parentPath,
                    it.getLong(4),
                    it.getLong(4) / 1000,
                    it.getLong(3).coerceAtLeast(0),
                  )
              }
            }
          }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
          throw cancelled
        } catch (_: Exception) {
          incomplete = true
        }
      }
    }
    return Result(videos.distinctBy { it.path }, incomplete)
  }

  private val videoExtensions =
    setOf("mkv", "mp4", "webm", "avi", "mov", "m4v", "ts", "m2ts", "mpg", "mpeg", "3gp", "flv")
}

/** Call on an IO dispatcher. Opaque provider IDs are not mistaken for physical filesystem paths. */
internal fun selectedTreePath(context: Context, tree: Uri): String {
  val id = DocumentsContract.getTreeDocumentId(tree)
  if (
    id.startsWith("primary:") ||
      id.substringBefore(':').matches(Regex("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}"))
  )
    return documentPath(id)
  val root = DocumentsContract.buildDocumentUriUsingTree(tree, id)
  val name =
    context.contentResolver
      .query(root, arrayOf("_display_name"), null, null, null)
      ?.use { if (it.moveToFirst()) it.getString(0) else null }
      ?.replace('/', '_')
      ?.takeIf { it.isNotBlank() } ?: id.substringAfterLast('/').replace(':', '_')
  // Include authority/tree identity so unrelated providers with the same display name cannot merge.
  val identity =
    java.security.MessageDigest.getInstance("SHA-256")
      .digest(tree.toString().toByteArray())
      .take(4)
      .joinToString("") { "%02x".format(it) }
  return "Selected folders/$name · $identity"
}

internal fun documentPath(id: String): String =
  when {
    id.startsWith("primary:") -> "/storage/emulated/0/" + id.substringAfter(':').trimStart('/')
    id.contains(':') ->
      "/storage/" + id.substringBefore(':') + "/" + id.substringAfter(':').trimStart('/')
    else -> id
  }.trimEnd('/')
