package live.mehiz.mpvkt.subtitles

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class SubtitleFilesTest {
  private val text = "1\n00:00:01,000 --> 00:00:03,000\nHello\n".toByteArray()

  private fun archive(vararg items: Pair<String, ByteArray>): ByteArray =
    ByteArrayOutputStream()
      .also { bytes ->
        ZipOutputStream(bytes).use { zip ->
          items.forEach { (name, body) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(body)
            zip.closeEntry()
          }
        }
      }
      .toByteArray()

  @Test
  fun utf16CaptionsRemainSupportedAndJsonErrorsAreRejected() {
    val bytes = "1\n00:00:01,000 --> 00:00:03,000\nCaption".toByteArray(Charsets.UTF_16)
    assertArrayEquals(
      bytes,
      SubtitleFiles.unpack(bytes, "subtitle.srt", SubtitleRequest("Movie")).single().bytes,
    )
    assertTrue(
      runCatching {
          SubtitleFiles.unpack(
            "{\"message\":\"Download denied\"}".toByteArray(),
            "subtitle.srt",
            SubtitleRequest("Movie"),
          )
        }
        .isFailure
    )
  }

  @Test
  fun archiveSelectsTheRightEpisodeBeforeSavingAndNeverUsesArchivePaths() {
    val entries =
      SubtitleFiles.unpack(
        archive("../../Show.S01E02.srt" to text, "Show.S01E03.srt" to text),
        "pack.zip",
        SubtitleRequest("Show", season = 1, episode = 2),
      )
    assertEquals(1, entries.size)
    val root = kotlin.io.path.createTempDirectory().toFile()
    try {
      val file = SubtitleFiles.save(root, "https://example.com/pack.zip", entries.single())
      assertEquals(root.canonicalFile, file.parentFile.canonicalFile)
      assertArrayEquals(text, file.readBytes())
    } finally {
      root.deleteRecursively()
    }
  }

  @Test
  fun differentLanguageFilesAreKeptForExplicitSelection() {
    val entries =
      SubtitleFiles.unpack(
        archive("Arabic.srt" to text, "English.srt" to text),
        "pack.zip",
        SubtitleRequest("Show", season = 1, episode = 2),
      )
    assertEquals(2, entries.size)
  }

  @Test
  fun noWrongEpisodeOrHtmlCanBeReturnedAsASuccessfulSubtitle() {
    assertTrue(
      runCatching {
          SubtitleFiles.unpack(
            archive("Show.S01E03.srt" to text),
            "pack.zip",
            SubtitleRequest("Show", season = 1, episode = 2),
          )
        }
        .isFailure
    )
    assertTrue(
      runCatching {
          SubtitleFiles.unpack(
            "<!doctype html><html>Denied</html>".toByteArray(),
            "test.srt",
            SubtitleRequest("Show"),
          )
        }
        .isFailure
    )
  }

  @Test
  fun compressedBombIsBoundedDuringInflation() {
    val bomb = archive("large.srt" to ByteArray(SubtitleFiles.MAX_FILE + 1))
    assertTrue(
      runCatching { SubtitleFiles.unpack(bomb, "pack.zip", SubtitleRequest("Show")) }.isFailure
    )
  }

  @Test
  fun repeatedBasenameWithDifferentContentDoesNotOverwriteAnotherSubtitle() {
    val root = kotlin.io.path.createTempDirectory().toFile()
    try {
      val one =
        SubtitleFiles.save(root, "https://example.com", SubtitleFiles.Entry("same.srt", text))
      val two =
        SubtitleFiles.save(
          root,
          "https://example.com",
          SubtitleFiles.Entry("same.srt", text + "Other".toByteArray()),
        )
      assertNotEquals(one, two)
      assertArrayEquals(text, one.readBytes())
    } finally {
      root.deleteRecursively()
    }
  }

  @Test
  fun directoryEntriesCannotBypassTheInflationLimit() {
    val bomb = archive("fake/" to ByteArray(SubtitleFiles.MAX_FILE + 1), "valid.srt" to text)
    assertTrue(
      runCatching { SubtitleFiles.unpack(bomb, "pack.zip", SubtitleRequest("Show")) }.isFailure
    )
  }
}
