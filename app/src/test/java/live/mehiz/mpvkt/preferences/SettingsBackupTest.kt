package live.mehiz.mpvkt.preferences

import android.app.Application
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SettingsBackupTest {
  @Test
  fun typedValuesAndXmlCharactersRoundTripWithoutCredentials() {
    val values =
      mapOf(
        "string" to "<Text & \"name\">",
        "enabled" to true,
        "int" to 3,
        "long" to 9999999999L,
        "float" to .4f,
        "set" to setOf("en", "ar&x"),
        "wyzie_api_key" to "NEVER EXPORT",
        "password" to "private",
      )
    val output = ByteArrayOutputStream()
    SettingsBackup.write(values, output)
    val text = output.toString("UTF-8")
    assertFalse(text.contains("NEVER EXPORT"))
    assertFalse(text.contains("private"))
    val restored = SettingsBackup.read(output.toByteArray().inputStream())
    assertEquals(values.filterKeys { it != "wyzie_api_key" && it != "password" }, restored)
    assertEquals(restored, SettingsBackup.read((text + "\n  ").byteInputStream()))
  }

  @Test
  fun importingAnUnknownOrWrongTypeNeverPartiallyAppliesValidSettings() {
    val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
    val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
    prefs.edit().clear().putBoolean("show_video_thumbnails", true).commit()
    val file = java.io.File(context.cacheDir, "settings.xml")
    listOf(
        mapOf("show_video_thumbnails" to false, "unknown_setting" to 1),
        mapOf("show_video_thumbnails" to "invalid"),
      )
      .forEach { values ->
        file.outputStream().use { SettingsBackup.write(values, it) }
        assertTrue(
          runCatching { SettingsBackup.restore(context, android.net.Uri.fromFile(file)) }.isFailure
        )
        assertTrue(prefs.getBoolean("show_video_thumbnails", false))
      }
    file.outputStream().use {
      SettingsBackup.write(mapOf("show_video_thumbnails" to false, "icon_style" to "Broken"), it)
    }
    SettingsBackup.restore(context, android.net.Uri.fromFile(file))
    assertFalse(prefs.getBoolean("show_video_thumbnails", true))
    assertEquals("Broken", prefs.getString("icon_style", null))
    file.delete()
  }

  @Test
  fun hostileAndMalformedFilesAreRejectedBeforeTheyCanBeApplied() {
    listOf(
        "<!DOCTYPE x [<!ENTITY a SYSTEM 'file:///etc/passwd'>]><mpvSettings version=\"1\"/>",
        "<mpvSettings version=\"1\"><entry name=\"x\" type=\"int\">oops</entry></mpvSettings>",
        "<mpvSettings version=\"1\"><entry name=\"x\" type=\"boolean\">yes</entry></mpvSettings>",
        "<mpvSettings version=\"1\"><entry name=\"x\" type=\"float\">NaN</entry></mpvSettings>",
        "<mpvSettings version=\"1\"><entry name=\"x\" type=\"int\">1</entry><entry name=\"x\" type=\"int\">2</entry></mpvSettings>",
        "<mpvSettings version=\"1\"><entry name=\"api_key\" type=\"string\">secret</entry></mpvSettings>",
        "<mpvRxSettings/>",
        "<mpvSettings version=\"2\"/>",
      )
      .forEach { source ->
        assertTrue(source, runCatching { SettingsBackup.read(source.byteInputStream()) }.isFailure)
      }
    assertTrue(
      runCatching { SettingsBackup.read(ByteArray(4 * 1024 * 1024 + 1).inputStream()) }.isFailure
    )
  }
}
