package live.mehiz.mpvkt.ui.player

import live.mehiz.mpvkt.ui.icons.IconKey
import live.mehiz.mpvkt.ui.icons.IconStyle

/** A single style snapshot for each notification/PiP update, shared with Compose. */
internal data class MediaControlIcons(
  val rewind: Int,
  val play: Int,
  val pause: Int,
  val forward: Int,
  val stop: Int,
)

internal fun mediaControlIcons(style: IconStyle) = MediaControlIcons(
  rewind = IconKey.Backward.drawable(style),
  play = IconKey.Play.drawable(style),
  pause = IconKey.Pause.drawable(style),
  forward = IconKey.Forward.drawable(style),
  stop = IconKey.Stop.drawable(style),
)
