package live.mehiz.mpvkt.ui.player.controls.components.panels

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SubtitleBrowserTest {
  @get:Rule val temporary = TemporaryFolder()

  @Test fun directoriesAndSubtitlesAreVisibleButMediaAndGenericTextAreNot() = runBlocking {
    temporary.newFolder("Season 1")
    temporary.newFile("Episode.SRT")
    temporary.newFile("Episode.ass")
    temporary.newFile("Episode.mkv")
    temporary.newFile("notes.txt")
    assertEquals(listOf("Season 1", "Episode.ass", "Episode.SRT"),
      listSubtitleDirectory(temporary.root).map { it.name })
  }

  @Test fun pairedBitmapSubtitlesShowTheirIndexOnly() = runBlocking {
    temporary.newFile("film.idx")
    temporary.newFile("film.sub")
    assertEquals(listOf("film.idx"), listSubtitleDirectory(temporary.root).map { it.name })
  }

  @Test fun linksCannotEscapeTheDirectoryOrCreateAncestorLoops() = runBlocking {
    val selected = temporary.newFolder("selected")
    val outside = temporary.newFolder("outside")
    Files.createSymbolicLink(selected.resolve("escape").toPath(), outside.toPath())
    Files.createSymbolicLink(selected.resolve("loop").toPath(), selected.toPath())
    assertTrue(listSubtitleDirectory(selected).isEmpty())
  }

  @Test fun missingFolderReportsFailureRatherThanAnEmptySuccessfulListing() = runBlocking {
    val failure = runCatching { listSubtitleDirectory(temporary.root.resolve("missing")) }.exceptionOrNull()
    assertTrue(failure is java.io.IOException)
  }
}
