package live.mehiz.mpvkt.preferences

/** Shared UI/import domain: canonical finite hundredths, no duplicate LazyRow keys. */
internal object SpeedPresets {
  fun parse(value: String): Float? =
    value
      .toFloatOrNull()
      ?.takeIf { it.isFinite() && it in .01f..100f }
      ?.let { kotlin.math.round(it * 100f) / 100f }

  fun safe(values: Set<String>, fallback: Set<String>): List<Float> =
    values.mapNotNull(::parse).distinct().sorted().ifEmpty {
      fallback.mapNotNull(::parse).distinct().sorted()
    }

  fun checked(values: Set<*>): Set<String> =
    values
      .map {
        require(it is String)
        (parse(it) ?: error("Invalid playback speed")).toString()
      }
      .toSet()
}
