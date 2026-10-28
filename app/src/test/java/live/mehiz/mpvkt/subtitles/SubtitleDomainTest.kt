package live.mehiz.mpvkt.subtitles

import live.mehiz.mpvkt.ui.player.TrackNode
import live.mehiz.mpvkt.ui.player.controls.subtitlePinchTarget
import live.mehiz.mpvkt.ui.preferences.toggleSubtitleChoice
import org.junit.Assert.*
import org.junit.Test

class SubtitleDomainTest {
  @Test
  fun filenamesKeepEpisodeAndCleanOnlyKnownExtensions() {
    assertEquals(
      MediaQuery("Planet Earth II", 1, 2, 2016),
      MediaQuery.parse("Planet.Earth.II.2016.S01E02.1080p.mkv"),
    )
    assertEquals(MediaQuery("Show", 1, 123), MediaQuery.parse("Show.S01E123"))
    assertEquals(MediaQuery("Show", 2, 7), MediaQuery.parse("Show_2x07.ass"))
    assertEquals("Interstellar", MediaQuery.parse("Interstellar.2014.2160p.mp4").title)
    assertEquals("Mr Robot", MediaQuery.parse("Mr.Robot.S01E01.srt").title)
  }

  @Test
  fun exactEpisodeMismatchIsRejectedButUnknownPackEntriesRemainSelectable() {
    val r = SubtitleRequest("Show", season = 1, episode = 2)
    assertTrue(episodeMatches("Show.S01E02.srt", r))
    assertFalse(episodeMatches("Show.S01E03.srt", r))
    assertFalse(episodeMatches("Show.S02E02.srt", r))
    assertTrue(episodeMatches("Arabic.srt", r))
  }

  @Test
  fun languageAndTitlePriorityDoesNotOverrideWithoutAMatch() {
    val tracks =
      listOf(
        TrackNode(1, "sub", title = "Signs", lang = "eng"),
        TrackNode(2, "sub", title = "Full Dialogue", lang = "ara"),
      )
    assertEquals(2, preferredSubtitleTrack(tracks, "Full,eng,ar"))
    assertEquals(1, preferredSubtitleTrack(tracks, "en,ar"))
    assertNull(preferredSubtitleTrack(tracks, "French"))
    assertEquals("ar,en", subtitleLanguageCodes("Full,ara,eng,en"))
  }

  @Test
  fun strictFiltersDoNotInventMetadataAndRegionalLanguagesStaySpecific() {
    val sub =
      OnlineSubtitle("https://example.com/a.srt", "a.srt", "pt-br", "Test", "srt", ai = null)
    assertTrue(SubtitleCatalog.accepts(sub, SubtitleSearchOptions(languages = setOf("pt"))))
    assertTrue(SubtitleCatalog.accepts(sub, SubtitleSearchOptions(languages = setOf("pt-br"))))
    assertFalse(SubtitleCatalog.accepts(sub, SubtitleSearchOptions(languages = setOf("en"))))
    assertFalse(
      SubtitleCatalog.accepts(sub, SubtitleSearchOptions(languages = setOf("pt"), ai = "human"))
    )
    assertFalse(
      SubtitleCatalog.accepts(
        sub,
        SubtitleSearchOptions(languages = setOf("pt"), hearingImpaired = true),
      )
    )
    assertEquals("ar", SubtitleCatalog.language("ARA"))
    assertEquals("zh-tw", SubtitleCatalog.language("zh-TW"))
  }

  @Test
  fun selectionAllCanCoexistOnlyWithAuthenticatedSources() {
    assertEquals(setOf("all", "subdl"), toggleSubtitleChoice(setOf("all"), "subdl", setOf("subdl")))
    assertEquals(
      setOf("subtitlecat"),
      toggleSubtitleChoice(setOf("all"), "subtitlecat", setOf("subdl")),
    )
    assertEquals(setOf("en"), toggleSubtitleChoice(setOf("en"), "en", emptySet()))
  }

  @Test
  fun subtitlePinchDoesNotStealCenterVideoZoomOrUnsupportedSecondary() {
    assertEquals(0, subtitlePinchTarget(.5f, .5f, "Text", "", 100, false))
    assertEquals(1, subtitlePinchTarget(.5f, .94f, "Text", "", 100, false))
    assertEquals(0, subtitlePinchTarget(.5f, .94f, "", "", 100, false))
    assertEquals(0, subtitlePinchTarget(.5f, .1f, "Text", "Second", 100, false))
    assertEquals(2, subtitlePinchTarget(.5f, .1f, "Text", "Second", 100, true))
  }

  @Test
  fun configurationOwnershipIgnoresCommentsAndSupportsNoPrefix() {
    assertEquals(
      setOf("sub-font", "sub-auto", "slang"),
      SubtitleConfig.options("# sub-scale=3\nsub-font=Example\nno-sub-auto\n[profile]\nslang=ar\n"),
    )
  }

  @Test
  fun downloadsRejectLocalSchemesCredentialsAndNumericHosts() {
    listOf(
        "http://example.com/a",
        "file:///tmp/a",
        "https://127.0.0.1/a",
        "https://user:key@example.com/a",
        "https://localhost/a",
        "https://host.local/a",
        "https://example.com:8443/a",
      )
      .forEach { assertTrue(runCatching { validatedSubtitleUri(it) }.isFailure) }
    assertEquals("example.com", validatedSubtitleUri("https://example.com/a.srt").host)
  }
}
