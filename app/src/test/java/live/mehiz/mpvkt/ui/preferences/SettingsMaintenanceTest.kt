package live.mehiz.mpvkt.ui.preferences

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsMaintenanceTest {
  @get:Rule val temporary = TemporaryFolder()

  @Test
  fun clearingConfigurationCachePreservesDownloadsScriptsAndOtherPrivateFiles() {
    val root = temporary.newFolder()
    File(root, "mpv.conf").writeText("vo=gpu")
    File(root, "input.conf").writeText("SPACE cycle pause")
    val keep = listOf("subtitle-downloads/film.srt", "scripts/custombuttons.lua", "private.txt")
    keep.forEach {
      File(root, it).apply {
        parentFile.mkdirs()
        writeText("keep")
      }
    }
    clearConfigurationCache(root)
    clearConfigurationCache(root)
    assertFalse(File(root, "mpv.conf").exists())
    assertFalse(File(root, "input.conf").exists())
    keep.forEach { assertEquals("keep", File(root, it).readText()) }
  }
}
