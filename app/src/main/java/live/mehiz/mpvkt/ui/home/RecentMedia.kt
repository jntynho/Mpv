package live.mehiz.mpvkt.ui.home

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class RecentMediaEntry(val uri: String, val title: String, val playedAt: Long)

internal object RecentMedia {
  private val json = Json { ignoreUnknownKeys = true }
  private val lock = Any()

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences("recent_media", Context.MODE_PRIVATE)

  internal fun decode(value: String): List<RecentMediaEntry> =
    runCatching { json.decodeFromString<List<RecentMediaEntry>>(value) }
      .getOrDefault(emptyList())
      .filter { it.uri.isNotBlank() }
      .distinctBy { it.uri }
      .take(100)

  internal fun updated(entries: List<RecentMediaEntry>, next: RecentMediaEntry) =
    (listOf(next) + entries.filter { it.uri != next.uri }).filter { it.uri.isNotBlank() }.take(100)

  fun clear(context: Context) {
    synchronized(lock) { prefs(context).edit().remove("items").apply() }
  }

  fun record(context: Context, uri: String, title: String) {
    if (uri.isBlank() || uri.length > 8192 || uri.startsWith("fd")) return
    synchronized(lock) {
      val p = prefs(context)
      p.edit()
        .putString(
          "items",
          json.encodeToString(
            updated(
              decode(p.getString("items", "[]").orEmpty()),
              RecentMediaEntry(uri, title.take(300), System.currentTimeMillis()),
            )
          ),
        )
        .apply()
    }
  }

  fun observe(context: Context): Flow<List<RecentMediaEntry>> =
    callbackFlow<String> {
        val p = prefs(context)
        fun emit() {
          trySend(p.getString("items", "[]").orEmpty())
        }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> emit() }
        p.registerOnSharedPreferenceChangeListener(listener)
        emit()
        awaitClose { p.unregisterOnSharedPreferenceChangeListener(listener) }
      }
      .map { decode(it) }
      .flowOn(Dispatchers.IO)
}

internal fun validMediaLink(value: String): Boolean {
  if (value.length !in 1..8192 || value.any(Char::isWhitespace)) return false
  return runCatching {
      val uri = java.net.URI(value)
      uri.scheme?.lowercase() in
        setOf(
          "https",
          "http",
          "rtsp",
          "rtsps",
          "rtmp",
          "rtmps",
          "udp",
          "tcp",
          "ftp",
          "sftp",
          "smb",
        ) && !uri.host.isNullOrBlank()
    }
    .getOrDefault(false)
}
