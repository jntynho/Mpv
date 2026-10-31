package live.mehiz.mpvkt.ui.home

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** URI/path identity prevents identically named videos in different folders sharing progress. */
internal object LibraryProgress {
  private val lock = Any()
  private val json = Json { ignoreUnknownKeys = true }

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences("library_progress", Context.MODE_PRIVATE)

  private fun decode(raw: String) =
    runCatching { json.decodeFromString<Map<String, Int>>(raw) }.getOrDefault(emptyMap())

  fun record(context: Context, uri: String?, seconds: Int) {
    if (uri.isNullOrBlank() || uri.startsWith("fd") || uri.startsWith("/proc/self/fd")) return
    synchronized(lock) {
      val p = prefs(context)
      // Keep a bounded, recent-first snapshot; normal updates do not grow without limit.
      val values = linkedMapOf(uri to seconds.coerceAtLeast(0))
      decode(p.getString("positions", "{}").orEmpty()).forEach { (key, value) ->
        if (values.size < 1000 && key !in values) values[key] = value.coerceAtLeast(0)
      }
      p.edit().putString("positions", json.encodeToString(values.toMap())).apply()
    }
  }

  fun observe(context: Context) =
    callbackFlow<String> {
        val p = prefs(context)
        fun emit() {
          trySend(p.getString("positions", "{}").orEmpty())
        }
        val listener =
          SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "positions") emit()
          }
        p.registerOnSharedPreferenceChangeListener(listener)
        emit()
        awaitClose { p.unregisterOnSharedPreferenceChangeListener(listener) }
      }
      .map(::decode)
      .flowOn(Dispatchers.IO)
}

internal fun libraryProgressFraction(positionSeconds: Int, durationMillis: Long): Float =
  if (positionSeconds <= 0 || durationMillis <= 0) 0f
  else (positionSeconds.toDouble() * 1000 / durationMillis.toDouble()).toFloat().coerceIn(0f, 1f)
