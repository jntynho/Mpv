package live.mehiz.mpvkt.ui.player.controls.components

import androidx.annotation.StringRes
import live.mehiz.mpvkt.R

/** Style choices match mpvRx; drawing and interaction use MPV's own Compose/Seeker pipeline. */
enum class SeekbarStyle(@StringRes val titleRes: Int) {
  Normal(R.string.mpv_seek_normal),
  Standard(R.string.mpv_seek_standard),
  Wavy(R.string.mpv_seek_wavy),
  Thick(R.string.mpv_seek_thick),
  Slim(R.string.mpv_seek_slim),
  Liquid(R.string.mpv_seek_liquid),
}
