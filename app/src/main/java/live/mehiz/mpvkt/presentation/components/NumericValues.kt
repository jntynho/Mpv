package live.mehiz.mpvkt.presentation.components

/** Normalize editable localized numbers without admitting non-finite or overflowing values. */
internal fun normalizedNumber(text: String): String = buildString {
  text.trim().forEach { char ->
    append(
      when {
        char.digitToIntOrNull() != null -> ('0'.code + char.digitToInt()).toChar()
        char == ',' || char == '\u066b' -> '.'
        char == '\u2212' -> '-'
        else -> char
      }
    )
  }
}

internal fun validInteger(text: String, min: Int, max: Int): Int? =
  normalizedNumber(text).toIntOrNull()?.takeIf { it in min..max }

internal fun validDecimal(text: String, min: Float, max: Float): Float? =
  normalizedNumber(text).toFloatOrNull()?.takeIf { it.isFinite() && it in min..max }

internal fun steppedInteger(value: Int, delta: Long, min: Int, max: Int): Int =
  (value.toLong() + delta).coerceIn(min.toLong(), max.toLong()).toInt()
