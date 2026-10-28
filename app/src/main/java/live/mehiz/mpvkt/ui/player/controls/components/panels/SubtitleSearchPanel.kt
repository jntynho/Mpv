package live.mehiz.mpvkt.ui.player.controls.components.panels

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.*
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.SubtitlesPreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.presentation.components.RoundedTextField as OutlinedTextField
import live.mehiz.mpvkt.subtitles.*
import live.mehiz.mpvkt.ui.icons.*
import live.mehiz.mpvkt.ui.player.Panels
import live.mehiz.mpvkt.ui.player.controls.components.*
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
  val navigate: (Panels) -> Unit = {
    keyboard?.hide()
    open(it)
  }
  val languages by preferences.searchLanguages.collectAsState()
  val mode by preferences.searchMode.collectAsState()
  PlayerLazyScrollArea(Modifier.fillMaxSize()) {
    item("query") {
      OutlinedTextField(
        state.query,
        controller::editQuery,
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        label = { Text(stringResource(R.string.subtitle_movie_series)) },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        trailingIcon = {
          Row {
            if (state.query.isNotEmpty())
              IconButton(onClick = { controller.editQuery("") }) {
                Icon(Icons.Clear, stringResource(R.string.subtitle_clear_query))
              }
            IconButton(
              onClick = {
                keyboard?.hide()
                controller.findMedia()
              },
              enabled = state.query.isNotBlank() && !state.loading,
            ) {
              Icon(Icons.Search, stringResource(R.string.subtitle_find))
            }
          }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions =
          KeyboardActions(
            onSearch = {
              keyboard?.hide()
              controller.findMedia()
            }
          ),
      )
    }
    item("media") {
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (state.media != null) {
          SubtitlePoster(state.media!!.posterUrl, reduceMotion)
          Spacer(Modifier.width(12.dp))
          Column(Modifier.weight(1f)) {
            Text(
              state.media!!.title,
              style = MaterialTheme.typography.titleSmall,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              (if (state.series) stringResource(R.string.subtitle_series)
              else stringResource(R.string.subtitle_movie)) + " · " + state.media!!.year,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          IconButton(onClick = { controller.editQuery(state.query) }) {
            Icon(Icons.Edit, stringResource(R.string.subtitle_change_title), Modifier.size(20.dp))
          }
        } else {
          Text(
            stringResource(R.string.subtitle_series),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
          )
          val seriesLabel = stringResource(R.string.subtitle_series)
          Switch(
            state.series,
            {
              keyboard?.hide()
              controller.setSeries(it)
            },
            Modifier.semantics { contentDescription = seriesLabel },
          )
        }
      }
    }
    item("episode") {
      Column(Modifier.fillMaxWidth().animateContentSize(tween(if (reduceMotion) 0 else 220))) {
        if (state.series) {
          Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            NumberSelector(
              stringResource(R.string.subtitle_season),
              state.season,
              state.seasons.isNotEmpty(),
              Modifier.weight(1f),
              { navigate(Panels.SubtitleSearchSeason) },
              controller::setSeason,
            )
            NumberSelector(
              stringResource(R.string.subtitle_episode),
              state.episode,
              state.episodes.isNotEmpty(),
              Modifier.weight(1f),
              { navigate(Panels.SubtitleSearchEpisode) },
              controller::setEpisode,
            )
          }
          state.episodes
            .firstOrNull { it.number == state.episode }
            ?.title
            ?.takeIf(String::isNotBlank)
            ?.let {
              Text(
                it,
                Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
        }
      }
    }
    item("filters") {
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(
          onClick = { navigate(Panels.SubtitleSearchLanguages) },
          modifier = Modifier.weight(1f),
        ) {
          Icon(Icons.Subtitles, null, Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(
            languages.joinToString(", ") { SubtitleCatalog.languages[it] ?: it },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Text(
          SubtitleCatalog.modes[mode].orEmpty(),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = { navigate(Panels.SubtitleSearchOptions) }) {
          Icon(Icons.Tune, stringResource(R.string.subtitle_search_settings))
        }
      }
      if (state.loading || state.loadingEpisodes)
        LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp))
      state.message?.let {
        Text(
          stringResource(subtitleNoticeResource(it)),
          Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    if (state.matches.isNotEmpty())
      item("matches-title") { PlayerPanelSection(stringResource(R.string.subtitle_choose_title)) }
    items(state.matches, key = { "media:${it.id}:${it.television}" }) { match ->
      SubtitleMediaRow(match, reduceMotion) {
        keyboard?.hide()
        controller.selectMedia(match)
      }
    }
    if (state.media == null && !state.loading && mode != "wyzie")
      item("direct") {
        TextButton(
          onClick = {
            keyboard?.hide()
            controller.search()
          },
          enabled = state.query.isNotBlank(),
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
          Text(stringResource(R.string.subtitle_direct))
        }
      }
    if (state.media != null && !state.loading)
      item("submit") {
        FilledTonalButton(
          onClick = {
            keyboard?.hide()
            controller.search()
          },
          modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
          shape = RoundedCornerShape(18.dp),
          enabled = !state.loadingEpisodes,
        ) {
          Icon(Icons.Search, null, Modifier.size(18.dp))
          Spacer(Modifier.width(10.dp))
          Text(stringResource(R.string.subtitle_find))
        }
      }
    if (state.subtitles.isNotEmpty())
      item("results-title") {
        PlayerPanelSection(stringResource(R.string.subtitle_results) + " (${state.subtitles.size})")
      }
    items(state.subtitles, key = { "subtitle:${it.url}" }) { subtitle ->
      Row(
        Modifier.animateItem(
            fadeInSpec = tween(if (reduceMotion) 0 else 180),
            placementSpec = tween(if (reduceMotion) 0 else 240),
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
          onClick = { controller.download(subtitle, apply) },
          enabled = state.downloading == null,
          modifier =
            Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        ) {
          if (state.downloading == subtitle.url)
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
          else Icon(Icons.Add, stringResource(R.string.subtitle_download))
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
    item("slot") {
      Text(
        stringResource(R.string.subtitle_add_to),
        Modifier.padding(start = 20.dp, top = 16.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Row(
        Modifier.fillMaxWidth()
          .padding(16.dp)
          .clip(RoundedCornerShape(16.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerHighest)
      ) {
        listOf(
            stringResource(R.string.subtitle_primary),
            stringResource(R.string.subtitle_secondary_track),
          )
          .forEachIndexed { index, label ->
            Box(
              Modifier.weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(
                  if (state.slot == index) MaterialTheme.colorScheme.primaryContainer
                  else MaterialTheme.colorScheme.surfaceContainerHighest
                )
                .selectable(
                  state.slot == index,
                  role = Role.Tab,
                  onClick = { controller.setSlot(index) },
                )
                .padding(vertical = 13.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(label, style = MaterialTheme.typography.labelLarge)
            }
          }
      }
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
) {
  if (choices)
    OutlinedButton(
      onClick = pick,
      modifier = modifier.heightIn(min = 56.dp),
      shape = RoundedCornerShape(16.dp),
    ) {
      Text("$title ${value ?: "—"}", Modifier.weight(1f))
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
