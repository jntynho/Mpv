package live.mehiz.mpvkt.preferences

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Xml
import androidx.preference.PreferenceManager
import java.io.InputStream
import java.io.OutputStream
import org.xmlpull.v1.XmlPullParser

/**
 * Typed, bounded XML. Parse and validate the whole document before a single atomic preference
 * commit.
 */
internal object SettingsBackup {
  private const val LIMIT = 4 * 1024 * 1024
  private val forbidden =
    Regex(
      "(?i).*(password|secret|token|api.?key|credential|secure.?folder|vault|storage.?uri|directory.?uri).*"
    )

  private fun allowed(key: String) = key.length in 1..160 && !forbidden.matches(key)

  fun write(values: Map<String, *>, output: OutputStream) {
    val xml = Xml.newSerializer()
    xml.setOutput(output, "UTF-8")
    xml.startDocument("UTF-8", true)
    xml.startTag(null, "mpvSettings").attribute(null, "version", "1")
    values.toSortedMap().filterKeys(::allowed).forEach { (key, value) ->
      val type =
        when (value) {
          is String -> "string"
          is Boolean -> "boolean"
          is Int -> "int"
          is Long -> "long"
          is Float -> "float"
          is Set<*> -> "set"
          else -> return@forEach
        }
      xml.startTag(null, "entry").attribute(null, "name", key).attribute(null, "type", type)
      if (value is Set<*>)
        value.filterIsInstance<String>().sorted().forEach {
          xml.startTag(null, "item").text(it).endTag(null, "item")
        }
      else xml.text(value.toString())
      xml.endTag(null, "entry")
    }
    xml.endTag(null, "mpvSettings")
    xml.endDocument()
    xml.flush()
  }

  fun read(input: InputStream): Map<String, Any> {
    val bytes = input.readBytesBounded(LIMIT)
    val source = bytes.toString(Charsets.UTF_8)
    require(!source.contains("<!DOCTYPE", true) && !source.contains("<!ENTITY", true))
    val parser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    parser.setInput(source.reader())
    parser.nextTag()
    parser.require(XmlPullParser.START_TAG, null, "mpvSettings")
    require(parser.getAttributeValue(null, "version") == "1")
    val result = linkedMapOf<String, Any>()
    while (parser.nextTag() == XmlPullParser.START_TAG) {
      parser.require(XmlPullParser.START_TAG, null, "entry")
      require(result.size < 2000)
      val key = parser.getAttributeValue(null, "name") ?: error("Missing key")
      require(allowed(key) && key !in result)
      val type = parser.getAttributeValue(null, "type")
      val value: Any =
        if (type == "set") {
          val items = linkedSetOf<String>()
          while (parser.nextTag() == XmlPullParser.START_TAG) {
            parser.require(XmlPullParser.START_TAG, null, "item")
            require(items.size < 2000)
            items.add(parser.nextText().also { require(it.length <= 65536) })
          }
          parser.require(XmlPullParser.END_TAG, null, "entry")
          items
        } else {
          val text = parser.nextText()
          require(text.length <= 1024 * 1024)
          when (type) {
            "string" -> text
            "int" -> text.toInt()
            "long" -> text.toLong()
            "float" -> text.toFloat().also { require(it.isFinite()) }
            "boolean" -> {
              require(text == "true" || text == "false")
              text == "true"
            }
            else -> error("Unsupported preference type")
          }
        }
      result[key] = value
    }
    parser.require(XmlPullParser.END_TAG, null, "mpvSettings")
    while (parser.next() != XmlPullParser.END_DOCUMENT) require(
      parser.eventType == XmlPullParser.TEXT && parser.text.isBlank()
    )
    return result
  }

  private fun InputStream.readBytesBounded(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
      val n = read(buffer)
      if (n < 0) break
      require(out.size() + n <= max)
      out.write(buffer, 0, n)
    }
    return out.toByteArray()
  }

  fun export(context: Context, uri: Uri) {
    context.contentResolver.openOutputStream(uri, "wt")?.use {
      write(PreferenceManager.getDefaultSharedPreferences(context).all, it)
    } ?: error("Cannot open destination")
  }

  fun restore(context: Context, uri: Uri) {
    val values =
      context.contentResolver.openInputStream(uri)?.use(::read) ?: error("Cannot open backup")
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    // Matching known runtime types prevents a foreign backup from causing ClassCastExceptions.
    val baseline = supportedTypes(context)
    values.forEach { (key, value) ->
      require(baseline[key]?.let { sameType(it, value) } == true) {
        "Unknown or incompatible setting: $key"
      }
    }
    val editor = prefs.edit()
    values.forEach { (key, value) -> put(editor, key, value) }
    check(editor.commit())
  }

  private fun supportedTypes(context: Context): Map<String, Any> {
    val entries = linkedMapOf<String, Any>()
    val store =
      object : live.mehiz.mpvkt.preferences.preference.PreferenceStore {
        private fun <T : Any> capture(
          key: String,
          default: T,
        ): live.mehiz.mpvkt.preferences.preference.Preference<T> {
          entries[key] = default
          return object : live.mehiz.mpvkt.preferences.preference.Preference<T> {
            override fun key() = key

            override fun get() = default

            override fun set(value: T) {}

            override fun isSet() = false

            override fun delete() {}

            override fun defaultValue() = default

            override fun changes() = kotlinx.coroutines.flow.flowOf(default)

            override fun stateIn(
              scope: kotlinx.coroutines.CoroutineScope
            ): kotlinx.coroutines.flow.StateFlow<T> =
              kotlinx.coroutines.flow.MutableStateFlow(default)
          }
        }

        override fun getString(key: String, defaultValue: String) = capture(key, defaultValue)

        override fun getLong(key: String, defaultValue: Long) = capture(key, defaultValue)

        override fun getInt(key: String, defaultValue: Int) = capture(key, defaultValue)

        override fun getFloat(key: String, defaultValue: Float) = capture(key, defaultValue)

        override fun getBoolean(key: String, defaultValue: Boolean) = capture(key, defaultValue)

        override fun getStringSet(key: String, defaultValue: Set<String>) =
          capture(key, defaultValue)

        override fun <T> getObject(
          key: String,
          defaultValue: T,
          serializer: (T) -> String,
          deserializer: (String) -> T,
        ): live.mehiz.mpvkt.preferences.preference.Preference<T> {
          entries[key] = serializer(defaultValue)
          return live.mehiz.mpvkt.preferences.preference
            .AndroidPreferenceStore(context)
            .getObject(key, defaultValue, serializer, deserializer)
        }

        override fun getAll() = entries
      }
    AppearancePreferences(store)
    PlayerPreferences(store)
    AudioPreferences(store)
    DecoderPreferences(store)
    GesturePreferences(store)
    AdvancedPreferences(store)
    FoldersPreferences(store)
    SubtitlesPreferences(store)
    return entries
  }

  private fun sameType(a: Any, b: Any) = (a is Set<*> && b is Set<*>) || a.javaClass == b.javaClass

  private fun put(e: SharedPreferences.Editor, k: String, v: Any) {
    when (v) {
      is String -> e.putString(k, v)
      is Boolean -> e.putBoolean(k, v)
      is Int -> e.putInt(k, v)
      is Long -> e.putLong(k, v)
      is Float -> e.putFloat(k, v)
      is Set<*> -> e.putStringSet(k, v.filterIsInstance<String>().toSet())
    }
  }
}
