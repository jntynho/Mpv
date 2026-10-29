package live.mehiz.mpvkt.ui.player.controls.components.panels

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.*
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.SubtitlesPreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.subtitles.*
import live.mehiz.mpvkt.ui.icons.*
import live.mehiz.mpvkt.ui.player.Panels
import live.mehiz.mpvkt.ui.player.controls.components.*
import live.mehiz.mpvkt.ui.player.controls.components.PanelTextField as OutlinedTextField
import live.mehiz.mpvkt.ui.player.controls.components.sheets.AudioTrackRow
import live.mehiz.mpvkt.ui.preferences.ProviderKeyRow
import live.mehiz.mpvkt.ui.preferences.SubtitleSettingRow
import live.mehiz.mpvkt.ui.preferences.SubtitleSettingsCard
import live.mehiz.mpvkt.ui.preferences.SubtitleSettingsDivider
import live.mehiz.mpvkt.ui.preferences.toggleSubtitleChoice

@Composable
internal fun SubtitleSearchPanel(
  controller: SubtitleSearchController,
  preferences: SubtitlesPreferences,
  open: (Panels) -> Unit,
  apply: (File, Int) -> Unit,
  reduceMotion: Boolean = false,
) {
  val state by controller.state.collectAsState()
  val keyboard = LocalSoftwareKeyboardController.current
  val posterState = rememberLazyListState()
  val motionDirection = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
  LaunchedEffect(state.page) { keyboard?.hide() }
  val edit: (String) -> Unit = {
    if (controller.state.value.page == SubtitleSearchPage.Query) controller.editQuery(it)
  }
  val navigate: (Panels) -> Unit = { panel ->
    val current = controller.state.value.page
    val allowed =
      when (panel) {
        Panels.SubtitleSearchLanguages,
        Panels.SubtitleSearchOptions -> current == SubtitleSearchPage.Query
        Panels.SubtitleSearchSeason,
        Panels.SubtitleSearchEpisode -> current == SubtitleSearchPage.Subtitles
        else -> false
      }
    if (allowed) {
      keyboard?.hide()
      open(panel)
    }
  }
  val languages by preferences.searchLanguages.collectAsState()
  val mode by preferences.searchMode.collectAsState()
  val find = {
    keyboard?.hide()
    if (controller.state.value.page == SubtitleSearchPage.Query) {
      posterState.requestScrollToItem(0)
      controller.findMedia()
    }
  }
  AnimatedContent(
    targetState = state.page,
    modifier = Modifier.fillMaxSize().clipToBounds(),
    transitionSpec = {
      val direction = (if (targetState.ordinal > initialState.ordinal) 1 else -1) * motionDirection
      val duration = if (reduceMotion) 0 else 240
      ((fadeIn(tween(duration)) +
          slideInHorizontally(tween(duration)) { direction * it / 12 }) togetherWith
          (fadeOut(tween(if (reduceMotion) 0 else 150)) +
            slideOutHorizontally(tween(duration)) { -direction * it / 12 }))
        .using(SizeTransform(clip = true, sizeAnimationSpec = { _, _ -> tween(0) }))
    },
    label = "subtitle_search_page",
  ) { page ->
    when (page) {
      SubtitleSearchPage.Query ->
        PlayerScrollArea(Modifier.fillMaxSize()) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
              state.query,
              edit,
              Modifier.fillMaxWidth(),
              label = { Text(stringResource(R.string.subtitle_movie_series)) },
              singleLine = true,
              trailingIcon = {
                if (state.query.isNotEmpty())
                  IconButton(onClick = { edit("") }) {
                    Icon(Icons.Clear, stringResource(R.string.subtitle_clear_query))
                  }
              },
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
              keyboardActions = KeyboardActions(onSearch = { find() }),
            )
            OutlinedButton(
              onClick = { navigate(Panels.SubtitleSearchLanguages) },
              modifier = Modifier.fillMaxWidth().height(PanelFieldHeight),
              shape = RoundedCornerShape(16.dp),
            ) {
              Icon(Icons.Subtitles, null, Modifier.size(20.dp))
              Spacer(Modifier.width(10.dp))
              Text(
                languages.joinToString(", ") { SubtitleCatalog.languages[it] ?: it },
                Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Icon(Icons.ArrowDropDown, null, Modifier.size(18.dp))
            }
            TextButton(
              onClick = { navigate(Panels.SubtitleSearchOptions) },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Icon(Icons.Tune, null, Modifier.size(20.dp))
              Spacer(Modifier.width(10.dp))
              Text(stringResource(R.string.subtitle_filters))
            }
            FilledTonalButton(
              onClick = { find() },
              enabled = state.query.isNotBlank(),
              modifier = Modifier.fillMaxWidth().height(PanelFieldHeight),
              shape = RoundedCornerShape(16.dp),
            ) {
              Icon(Icons.Search, null, Modifier.size(20.dp))
              Spacer(Modifier.width(10.dp))
              Text(stringResource(R.string.subtitle_find))
            }
          }
        }
      SubtitleSearchPage.Titles -> {
        if (state.matches.isNotEmpty())
          SubtitlePosterResults(state.matches, reduceMotion, posterState) { match ->
            val current = controller.state.value
            if (
              current.page == SubtitleSearchPage.Titles &&
                current.matches.any { it.id == match.id && it.television == match.television }
            )
              controller.selectMedia(match)
          }
        else
          Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              if (state.loading) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                Text(
                  stringResource(R.string.subtitle_loading),
                  style = MaterialTheme.typography.bodyMedium,
                )
              } else {
                state.message?.let {
                  Text(
                    stringResource(subtitleNoticeResource(it)),
                    style = MaterialTheme.typography.bodyMedium,
                  )
                }
                TextButton(onClick = { controller.back() }) {
                  Text(stringResource(R.string.subtitle_change_title))
                }
                if (mode != "wyzie")
                  TextButton(onClick = { controller.search() }) {
                    Text(stringResource(R.string.subtitle_direct))
                  }
              }
            }
          }
      }
      SubtitleSearchPage.Subtitles ->
        Column(Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxWidth().height(4.dp)) {
            if (state.loading || state.loadingEpisodes)
              LinearProgressIndicator(Modifier.fillMaxWidth())
          }
          SubtitleResults(state, controller, apply, reduceMotion, Modifier.weight(1f)) {
            if (state.series) {
              state.media?.let { media ->
                Text(
                  media.title,
                  Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                  style = MaterialTheme.typography.titleSmall,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                NumberSelector(
                  stringResource(R.string.subtitle_season),
                  state.season,
                  state.seasons.isNotEmpty(),
                  Modifier.weight(1f),
                  { navigate(Panels.SubtitleSearchSeason) },
                  {
                    if (controller.state.value.page == SubtitleSearchPage.Subtitles)
                      controller.setSeason(it)
                  },
                  !state.loadingEpisodes,
                )
                NumberSelector(
                  stringResource(R.string.subtitle_episode),
                  state.episode,
                  state.episodes.isNotEmpty(),
                  Modifier.weight(1f),
                  { navigate(Panels.SubtitleSearchEpisode) },
                  {
                    if (controller.state.value.page == SubtitleSearchPage.Subtitles)
                      controller.setEpisode(it)
                  },
                  !state.loadingEpisodes,
                )
                IconButton(
                  onClick = {
                    keyboard?.hide()
                    controller.search()
                  },
                  enabled =
                    !state.loading &&
                      !state.loadingEpisodes &&
                      state.season != null &&
                      state.episode != null,
                  modifier = Modifier.size(PanelFieldHeight),
                ) {
                  Icon(Icons.Search, stringResource(R.string.subtitle_find))
                }
              }
            }
          }
        }
    }
  }
}

@Composable
private fun SubtitlePosterResults(
  matches: List<MediaMatch>,
  reduceMotion: Boolean,
  state: LazyListState,
  select: (MediaMatch) -> Unit,
) {
  PlayerLazyScrollArea(Modifier.fillMaxSize(), state) {
    items(matches, key = { "${it.id}:${it.television}" }) { media ->
      Row(
        Modifier.fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 6.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerLow)
          .clickable(role = Role.Button, onClick = { select(media) })
          .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        SubtitlePoster(media.posterUrl, reduceMotion, Modifier.size(104.dp, 156.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            media.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            listOf(
                if (media.television) stringResource(R.string.subtitle_series)
                else stringResource(R.string.subtitle_movie),
                media.year,
              )
              .filter(String::isNotBlank)
              .joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun SubtitleResults(
  state: SubtitleSearchState,
  controller: SubtitleSearchController,
  apply: (File, Int) -> Unit,
  reduceMotion: Boolean,
  modifier: Modifier = Modifier,
  header: @Composable () -> Unit = {},
) {
  val listState = rememberLazyListState()
  LaunchedEffect(state.loading) { if (state.loading) listState.scrollToItem(0) }
  PlayerLazyScrollArea(modifier.fillMaxWidth(), listState) {
    item("search-controls") { header() }
    if (state.message != null)
      item("notice") {
        Text(
          stringResource(subtitleNoticeResource(state.message)),
          Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (
          !state.series &&
            state.message in setOf(SubtitleNotice.NoResults, SubtitleNotice.SearchUnavailable)
        )
          TextButton(
            onClick = controller::search,
            modifier = Modifier.padding(horizontal = 12.dp),
          ) {
            Text(stringResource(R.string.subtitle_find))
          }
      }
    items(state.subtitles, key = { it.url }) { subtitle ->
      Row(
        Modifier.animateItem(
            fadeInSpec = tween(if (reduceMotion) 0 else 180),
            placementSpec = tween(if (reduceMotion) 0 else 220),
            fadeOutSpec = tween(if (reduceMotion) 0 else 120),
          )
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          Icons.InsertDriveFile,
          null,
          Modifier.size(23.dp),
          tint = MaterialTheme.colorScheme.primary,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            subtitle.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            listOf(
                SubtitleCatalog.languages[subtitle.language]
                  ?: stringResource(R.string.subtitle_unknown_language),
                subtitle.format.uppercase(),
                subtitle.source,
              )
              .filter(String::isNotBlank)
              .joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(
          onClick = {
            val current = controller.state.value
            if (
              current.page == SubtitleSearchPage.Subtitles &&
                current.subtitles.any { it.url == subtitle.url }
            )
              controller.download(subtitle, apply)
          },
          enabled = state.downloading == null,
          modifier = Modifier.size(48.dp),
        ) {
          if (state.downloading == subtitle.url)
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
          else Icon(Icons.Download, stringResource(R.string.subtitle_download))
        }
      }
      HorizontalDivider(
        Modifier.padding(horizontal = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .25f),
      )
    }
    if (state.failures.isNotEmpty())
      item("failures") {
        Text(
          stringResource(R.string.subtitle_source_problem),
          Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
          style = MaterialTheme.typography.labelMedium,
        )
        Text(
          state.failures.joinToString("\n"),
          Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
  }
}

@Composable
private fun NumberSelector(
  title: String,
  value: Int?,
  choices: Boolean,
  modifier: Modifier,
  pick: () -> Unit,
  change: (Int?) -> Unit,
  enabled: Boolean = true,
) {
  if (choices)
    OutlinedButton(
      onClick = pick,
      modifier = modifier.height(PanelFieldHeight),
      enabled = enabled,
      shape = RoundedCornerShape(16.dp),
    ) {
      Text(
        "$title ${value ?: "—"}",
        Modifier.weight(1f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Icon(Icons.ArrowDropDown, null, Modifier.size(18.dp))
    }
  else
    OutlinedTextField(
      value?.toString().orEmpty(),
      { raw ->
        if (raw.isEmpty() || raw.all(Char::isDigit) && raw.length <= 3)
          change(raw.toIntOrNull()?.coerceIn(0, 999))
      },
      modifier = modifier,
      label = { Text(title) },
      singleLine = true,
      enabled = enabled,
      shape = RoundedCornerShape(16.dp),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
internal fun SubtitleSearchChoicePanel(
  panel: Panels,
  controller: SubtitleSearchController,
  preferences: SubtitlesPreferences,
  back: () -> Unit,
  apply: (File, Int) -> Unit,
) {
  val state by controller.state.collectAsState()
  val languages by preferences.searchLanguages.collectAsState()
  var query by remember { mutableStateOf("") }
  PlayerLazyScrollArea(Modifier.fillMaxSize()) {
    if (panel == Panels.SubtitleSearchLanguages)
      item("filter") {
        OutlinedTextField(
          query,
          { query = it },
          Modifier.fillMaxWidth().padding(16.dp),
          singleLine = true,
          label = { Text(stringResource(R.string.subtitle_choice_search)) },
          leadingIcon = { Icon(Icons.Search, null) },
          shape = RoundedCornerShape(18.dp),
        )
      }
    when (panel) {
      Panels.SubtitleSearchLanguages ->
        items(
          SubtitleCatalog.languages.entries.filter {
            it.value.contains(query, true) || it.key.contains(query, true)
          },
          key = { it.key },
        ) { entry ->
          AudioTrackRow(
            entry.value,
            entry.key in languages,
            {
              preferences.searchLanguages.set(
                toggleSubtitleChoice(languages, entry.key, emptySet())
              )
            },
          )
        }
      Panels.SubtitleSearchSeason ->
        items(state.seasons, key = { it.number }) { season ->
          AudioTrackRow(
            "${stringResource(R.string.subtitle_season)} ${season.number}",
            state.season == season.number,
            {
              controller.setSeason(season.number)
              back()
            },
            subtitle = season.title,
          )
        }
      Panels.SubtitleSearchEpisode ->
        items(state.episodes, key = { it.number }) { episode ->
          AudioTrackRow(
            "${stringResource(R.string.subtitle_episode)} ${episode.number}",
            state.episode == episode.number,
            {
              controller.setEpisode(episode.number)
              back()
              controller.search()
            },
            subtitle = episode.title,
          )
        }
      Panels.SubtitleSearchFiles ->
        items(state.files, key = { it.second.absolutePath }) { entry ->
          PlayerActionRow(
            entry.first,
            Icons.InsertDriveFile,
            {
              controller.chooseFile(entry.second, apply)
              back()
            },
          )
        }
      else -> Unit
    }
    item("done") {
      TextButton(
        onClick = back,
        Modifier.fillMaxWidth().padding(12.dp),
        enabled = panel != Panels.SubtitleSearchLanguages || languages.isNotEmpty(),
      ) {
        Text(stringResource(R.string.subtitle_done))
      }
    }
  }
}

@Composable
internal fun SubtitleSearchOptionsPanel(
  preferences: SubtitlesPreferences,
  keys: SubtitleKeys,
  service: SubtitleService,
) {
  val mode by preferences.searchMode.collectAsState()
  val sources by preferences.hubSources.collectAsState()
  val formats by preferences.searchFormats.collectAsState()
  val encodings by preferences.searchEncodings.collectAsState()
  val ai by preferences.aiResults.collectAsState()
  val hearing by preferences.hearingImpaired.collectAsState()
  var expanded by remember { mutableStateOf("") }
  val scope = rememberCoroutineScope()
  val wyzieSources by preferences.wyzieSources.collectAsState()
  var wyzieOptions by remember {
    mutableStateOf<Map<String, String>>(mapOf("all" to "All sources"))
  }
  var loading by remember { mutableStateOf(false) }
  val aiValues =
    mapOf(
      "all" to stringResource(R.string.subtitle_ai_all),
      "human" to stringResource(R.string.subtitle_ai_human),
      "ai" to stringResource(R.string.subtitle_ai_only),
    )
  PlayerScrollArea(Modifier.fillMaxSize()) {
    Column(Modifier.padding(12.dp)) {
      SubtitleSettingsCard {
        SubtitleSettingRow(
          stringResource(R.string.subtitle_mode),
          SubtitleCatalog.modes[mode].orEmpty(),
          Icons.Search,
        ) {
          expanded = if (expanded == "mode") "" else "mode"
        }
        if (expanded == "mode")
          InlineOptions(SubtitleCatalog.modes, setOf(mode)) {
            preferences.searchMode.set(it)
            expanded = ""
          }
        SubtitleSettingsDivider()
        if (mode != "wyzie") {
          SubtitleSettingRow(
            stringResource(R.string.subtitle_hub),
            sources.joinToString(", ") { SubtitleCatalog.hub[it] ?: it },
            Icons.Link,
          ) {
            expanded = if (expanded == "sources") "" else "sources"
          }
          if (expanded == "sources")
            InlineOptions(SubtitleCatalog.hub, sources) {
              preferences.hubSources.set(
                toggleSubtitleChoice(sources, it, SubtitleCatalog.keys.keys - "wyzie")
              )
            }
          SubtitleSettingsDivider()
        }
        if (mode != "subhub") {
          SubtitleSettingRow(
            stringResource(R.string.subtitle_wyzie),
            if (loading) stringResource(R.string.subtitle_loading)
            else wyzieSources.joinToString(", ") { wyzieOptions[it] ?: it },
            Icons.Link,
            enabled = !loading,
          ) {
            if (expanded == "wyzie") expanded = ""
            else
              scope.launch {
                loading = true
                try {
                  wyzieOptions = withContext(Dispatchers.IO) { service.sources() }
                } catch (c: CancellationException) {
                  throw c
                } catch (_: Exception) {} finally {
                  loading = false
                }
                expanded = "wyzie"
              }
          }
          if (expanded == "wyzie")
            InlineOptions(
              wyzieOptions + wyzieSources.associateWith { wyzieOptions[it] ?: it },
              wyzieSources,
            ) {
              preferences.wyzieSources.set(toggleSubtitleChoice(wyzieSources, it, emptySet()))
            }
          SubtitleSettingsDivider()
        }
        SubtitleSettingRow(
          stringResource(R.string.subtitle_hearing),
          stringResource(R.string.subtitle_hearing_summary),
          Icons.Headset,
          toggle = hearing,
        ) {
          preferences.hearingImpaired.set(!hearing)
        }
        SubtitleSettingsDivider()
        SubtitleSettingRow(
          stringResource(R.string.subtitle_ai),
          aiValues[ai].orEmpty(),
          Icons.Memory,
        ) {
          expanded = if (expanded == "ai") "" else "ai"
        }
        if (expanded == "ai")
          InlineOptions(aiValues, setOf(ai)) {
            preferences.aiResults.set(it)
            expanded = ""
          }
        SubtitleSettingsDivider()
        SubtitleSettingRow(
          stringResource(R.string.subtitle_formats),
          formats.joinToString(", "),
          Icons.InsertDriveFile,
        ) {
          expanded = if (expanded == "formats") "" else "formats"
        }
        if (expanded == "formats")
          InlineOptions(SubtitleCatalog.formats, formats) {
            preferences.searchFormats.set(toggleSubtitleChoice(formats, it, emptySet()))
          }
        SubtitleSettingsDivider()
        SubtitleSettingRow(
          stringResource(R.string.subtitle_encodings),
          encodings.joinToString(", "),
          Icons.Code,
        ) {
          expanded = if (expanded == "encoding") "" else "encoding"
        }
        if (expanded == "encoding")
          InlineOptions(SubtitleCatalog.encodings, encodings) {
            preferences.searchEncodings.set(toggleSubtitleChoice(encodings, it, emptySet()))
          }
        PlayerPanelSection(stringResource(R.string.subtitle_keys))
        SubtitleCatalog.keys
          .filterKeys { it == "wyzie" && mode != "subhub" || it in sources && mode != "wyzie" }
          .forEach { (key, title) -> ProviderKeyRow(key, title, keys, inline = true) }
      }
    }
  }
}

@Composable
private fun InlineOptions(
  values: Map<String, String>,
  selected: Set<String>,
  onSelect: (String) -> Unit,
) {
  values.forEach { (key, title) -> AudioTrackRow(title, key in selected, { onSelect(key) }) }
}

internal fun subtitleNoticeResource(notice: SubtitleNotice): Int =
  when (notice) {
    SubtitleNotice.EpisodesUnavailable -> R.string.subtitle_notice_episodes
    SubtitleNotice.NoTitle -> R.string.subtitle_notice_no_title
    SubtitleNotice.TitleLookupUnavailable -> R.string.subtitle_notice_lookup
    SubtitleNotice.ChooseEpisode -> R.string.subtitle_notice_choose_episode
    SubtitleNotice.NoResults -> R.string.subtitle_notice_empty
    SubtitleNotice.SearchUnavailable -> R.string.subtitle_notice_search
    SubtitleNotice.NoFormat -> R.string.subtitle_notice_format
    SubtitleNotice.DownloadUnavailable -> R.string.subtitle_notice_download
    SubtitleNotice.Added -> R.string.subtitle_notice_added
    SubtitleNotice.AddUnavailable -> R.string.subtitle_notice_add_failed
  }
