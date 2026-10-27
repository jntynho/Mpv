package live.mehiz.mpvkt.ui.startup

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Request video access only; never request broad all-files access. */
internal object VideoAccess {
  // These permission constants are inlined strings, selected by the supplied SDK level.
  @SuppressLint("InlinedApi")
  fun permissions(sdk: Int = Build.VERSION.SDK_INT): Array<String> = when {
    sdk >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    sdk >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
  }
  fun granted(context: Context) = permissions().any { granted(context, it) }
  fun limited(context: Context) = Build.VERSION.SDK_INT >= 34 && granted(context) &&
    !granted(context, Manifest.permission.READ_MEDIA_VIDEO)
  private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
