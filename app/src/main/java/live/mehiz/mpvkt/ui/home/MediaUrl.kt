package live.mehiz.mpvkt.ui.home

import java.net.URI

/** Validate before handing an external stream to the native player. */
object MediaUrl {
  fun isValid(value: String, protocols: Set<String>): Boolean = runCatching {
    val uri = URI(value)
    !uri.isOpaque && uri.isAbsolute && uri.scheme.lowercase() in protocols &&
      (!uri.host.isNullOrBlank() || !uri.path.isNullOrBlank())
  }.getOrDefault(false)
}
