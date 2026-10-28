package live.mehiz.mpvkt.ui.player.controls

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import live.mehiz.mpvkt.R

@Serializable
enum class LandscapeControl(val titleRes: Int) {
  Decoder(R.string.mpv_control_decoder),
  Chapters(R.string.mpv_control_chapters),
  Subtitles(R.string.pref_subtitles),
  Audio(R.string.pref_audio),
  More(R.string.mpv_control_more),
  Custom(R.string.mpv_control_custom),
  Pip(R.string.mpv_control_pip),
  Aspect(R.string.mpv_control_aspect),
  Lock(R.string.mpv_control_lock),
  Rotation(R.string.mpv_control_rotation),
  Speed(R.string.mpv_control_speed),
  ChapterTitle(R.string.mpv_control_chapter_title),
}

@Serializable
enum class ControlRegion(val titleRes: Int) {
  TopRight(R.string.mpv_controls_top_right),
  BottomRight(R.string.mpv_controls_bottom_right),
  BottomLeft(R.string.mpv_controls_bottom_left),
}

@Serializable
data class LandscapeLayout(
  val topRight: List<LandscapeControl> =
    listOf(
      LandscapeControl.Decoder,
      LandscapeControl.Subtitles,
      LandscapeControl.Audio,
      LandscapeControl.More,
    ),
  val bottomRight: List<LandscapeControl> =
    listOf(LandscapeControl.Custom, LandscapeControl.Pip, LandscapeControl.Aspect),
  val bottomLeft: List<LandscapeControl> =
    listOf(
      LandscapeControl.Lock,
      LandscapeControl.Rotation,
      LandscapeControl.Speed,
      LandscapeControl.ChapterTitle,
    ),
) {
  fun buttons(region: ControlRegion) =
    when (region) {
      ControlRegion.TopRight -> topRight
      ControlRegion.BottomRight -> bottomRight
      ControlRegion.BottomLeft -> bottomLeft
    }

  private fun replace(region: ControlRegion, buttons: List<LandscapeControl>) =
    when (region) {
      ControlRegion.TopRight -> copy(topRight = buttons)
      ControlRegion.BottomRight -> copy(bottomRight = buttons)
      ControlRegion.BottomLeft -> copy(bottomLeft = buttons)
    }

  fun assign(button: LandscapeControl, target: ControlRegion?): LandscapeLayout {
    if (target != null && button !in buttons(target) && buttons(target).size >= 6) return this
    var result = this
    ControlRegion.entries.forEach { result = result.replace(it, result.buttons(it) - button) }
    return if (target == null) result else result.replace(target, result.buttons(target) + button)
  }

  fun reorder(region: ControlRegion, index: Int, offset: Int): LandscapeLayout {
    val list = buttons(region).toMutableList()
    val to = index + offset
    if (index !in list.indices || to !in list.indices) return this
    java.util.Collections.swap(list, index, to)
    return replace(region, list)
  }

  fun normalized(): LandscapeLayout {
    val used = hashSetOf<LandscapeControl>()
    var result = this
    ControlRegion.entries.forEach {
      result =
        result.replace(
          it,
          buttons(it)
            .filter { button -> button !in used }
            .distinct()
            .take(6)
            .onEach { button -> used.add(button) },
        )
    }
    return result
  }

  fun serialize(): String = Json.encodeToString(serializer(), normalized())

  companion object {
    fun restore(value: String): LandscapeLayout =
      runCatching { Json.decodeFromString(serializer(), value).normalized() }
        .getOrDefault(LandscapeLayout())
  }
}
