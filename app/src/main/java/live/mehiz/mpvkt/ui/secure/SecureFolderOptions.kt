package live.mehiz.mpvkt.ui.secure

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

internal object SecureFolderOptions {
  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences("secure_folder_ui", Context.MODE_PRIVATE)

  fun hidden(context: Context) = prefs(context).getBoolean("hide_entry", false)

  fun hide(context: Context, value: Boolean) {
    prefs(context).edit().putBoolean("hide_entry", value).apply()
  }

  fun observeHidden(context: Context) = callbackFlow {
    val p = prefs(context)
    val listener =
      SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(hidden(context)) }
    p.registerOnSharedPreferenceChangeListener(listener)
    trySend(hidden(context))
    awaitClose { p.unregisterOnSharedPreferenceChangeListener(listener) }
  }
}
