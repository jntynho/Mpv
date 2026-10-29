package live.mehiz.mpvkt.ui.player.controls.components

import androidx.annotation.StringRes
import live.mehiz.mpvkt.R

/** Style choices match mpvRx; drawing and interaction use MPV's own Compose/Seeker pipeline. */
enum class SeekbarStyle(@StringRes val titleRes: Int, val trackDp: Float) {
  Normal(R.string.mpv_seek_normal, 4f),
  Standard(R.string.mpv_seek_standard, 8f),
  Wavy(R.string.mpv_seek_wavy, 8f),
  Thick(R.string.mpv_seek_thick, 16f),
  Slim(R.string.mpv_seek_slim, 2f),
  Liquid(R.string.mpv_seek_liquid, 10f),
}
