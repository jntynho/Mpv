package live.mehiz.mpvkt.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.presentation.components.StableBottomSheet
import live.mehiz.mpvkt.ui.icons.*
import live.mehiz.mpvkt.ui.motion.LibraryOverscrollFactory

internal data class LibraryDisplayOptions(
  val fields: Set<String> = setOf("Path", "Thumbnails", "Extension", "Duration"),
  val grid: Boolean = false,
  val columns: Int = 0,
)

@Composable
internal fun LibrarySortButton(
  sort: LibrarySort,
  countEnabled: Boolean,
  reduceMotion: Boolean,
  enabled: Boolean = true,
  viewMode: LibraryViewMode = LibraryViewMode.Folder,
  onViewMode: (LibraryViewMode) -> Unit = {},
  display: LibraryDisplayOptions = LibraryDisplayOptions(),
  onDisplay: (LibraryDisplayOptions) -> Unit = {},
  onSelect: (LibrarySort) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  var closing by remember { mutableStateOf(false) }
  IconButton(
    onClick = {
      closing = false
      expanded = true
    },
    enabled = enabled,
  ) {
    Icon(Icons.Sort, stringResource(R.string.mpv_sort))
  }
  if (expanded)
    StableBottomSheet(
      onDismissRequest = { expanded = false },
      dismissRequested = closing,
      reduceMotion = reduceMotion,
      compact = true,
      title = stringResource(R.string.mpv_sort),
    ) {
      CompositionLocalProvider(
        LocalOverscrollFactory provides if (reduceMotion) null else LibraryOverscrollFactory
      ) {
        LibrarySortMenuContent(
          sort,
          countEnabled,
          Modifier.fillMaxWidth().weight(1f, false),
          reduceMotion,
          viewMode,
          onViewMode = { if (!closing) onViewMode(it) },
          display = display,
          onDisplay = { if (!closing) onDisplay(it) },
          onDone = { closing = true },
          onSelect = { if (!closing) onSelect(it) },
        )
      }
    }
}

@Composable
internal fun LibrarySortMenuContent(
  sort: LibrarySort,
  countEnabled: Boolean,
  modifier: Modifier = Modifier,
  reduceMotion: Boolean = false,
  viewMode: LibraryViewMode = LibraryViewMode.Folder,
  onViewMode: (LibraryViewMode) -> Unit = {},
  display: LibraryDisplayOptions = LibraryDisplayOptions(),
  onDisplay: (LibraryDisplayOptions) -> Unit = {},
  onDone: () -> Unit = {},
  onSelect: (LibrarySort) -> Unit,
) {
  var fieldsExpanded by remember { mutableStateOf(true) }
  val duration = if (reduceMotion) 0 else 220
  val keyChoices =
    if (countEnabled)
      listOf(
        LibrarySortKey.Title,
        LibrarySortKey.Date,
        LibrarySortKey.Size,
        LibrarySortKey.Count,
        LibrarySortKey.Duration,
      )
    else
      listOf(
        LibrarySortKey.Title,
        LibrarySortKey.Duration,
        LibrarySortKey.Date,
        LibrarySortKey.Size,
      )
  Column(modifier) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 20.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        stringResource(R.string.mpv_sort),
        Modifier.weight(1f),
        style = MaterialTheme.typography.titleLarge,
      )
      TextButton(onClick = onDone) { Text(stringResource(R.string.subtitle_done)) }
    }
    Column(
      Modifier.weight(1f, false)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      FadingHorizontalRow(Modifier.fillMaxWidth().selectableGroup()) {
        keyChoices.forEach { key ->
          SortChip(stringResource(key.titleRes), sort.key == key, duration, key.icon()) {
            onSelect(sort.copy(key = key))
          }
        }
      }
      val labels = sort.orderLabels()
      Row(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        SortChip(
          stringResource(labels.first),
          sort.ascending,
          duration,
          Icons.ArrowDropUp,
          modifier = Modifier.weight(1f),
        ) {
          onSelect(sort.copy(ascending = true))
        }
        SortChip(
          stringResource(labels.second),
          !sort.ascending,
          duration,
          Icons.ArrowDropDown,
          modifier = Modifier.weight(1f),
        ) {
          onSelect(sort.copy(ascending = false))
        }
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
      Text(
        stringResource(R.string.mpv_view_mode),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Row(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        LibraryViewMode.entries.forEach { mode ->
          SortChip(
            stringResource(
              when (mode) {
                LibraryViewMode.Folder -> R.string.mpv_view_folders
                LibraryViewMode.Tree -> R.string.mpv_view_tree
                LibraryViewMode.Videos -> R.string.mpv_view_videos
              }
            ),
            viewMode == mode,
            duration,
            icon =
              when (mode) {
                LibraryViewMode.Folder -> Icons.Folder
                LibraryViewMode.Tree -> Icons.Layers
                LibraryViewMode.Videos -> Icons.Movie
              },
            modifier = Modifier.weight(1f),
          ) {
            onViewMode(mode)
          }
        }
      }
      Row(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        SortChip(
          stringResource(R.string.mpv_layout_list),
          !display.grid,
          duration,
          Icons.List,
          Modifier.weight(1f),
        ) {
          onDisplay(display.copy(grid = false))
        }
        SortChip(
          stringResource(R.string.update_card),
          display.grid,
          duration,
          Icons.Grid,
          Modifier.weight(1f),
        ) {
          onDisplay(display.copy(grid = true))
        }
      }
      if (display.grid) {
        FadingHorizontalRow(Modifier.fillMaxWidth().selectableGroup()) {
          val width = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
          val limit = ((width - 40) / 144).coerceIn(1, 5)
          (listOf(0) + (2..limit).toList()).forEach { count ->
            SortChip(
              if (count == 0) stringResource(R.string.mpv_grid_auto) else count.toString(),
              display.columns == count,
              duration,
            ) {
              onDisplay(display.copy(columns = count))
            }
          }
        }
      }
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Tune, null, Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
          stringResource(R.string.mpv_fields),
          Modifier.weight(1f),
          style = MaterialTheme.typography.titleSmall,
        )
        IconButton({ fieldsExpanded = !fieldsExpanded }) {
          Icon(
            if (fieldsExpanded) Icons.ArrowDropUp else Icons.ArrowDropDown,
            stringResource(R.string.mpv_fields),
          )
        }
      }
      if (fieldsExpanded) {
        FadingHorizontalRow(Modifier.fillMaxWidth()) {
          LibraryField.entries
            .filter {
              (it.folder && countEnabled) ||
                (it.video && (!countEnabled || viewMode == LibraryViewMode.Tree))
            }
            .forEach { field ->
              FilterChip(
                display.fields.has(field),
                onClick = {
                  onDisplay(
                    display.copy(
                      fields =
                        if (display.fields.has(field)) display.fields - field.name
                        else display.fields + field.name
                    )
                  )
                },
                label = { Text(stringResource(field.titleRes)) },
                leadingIcon = { Icon(field.icon(), null, Modifier.size(18.dp)) },
                shape = RoundedCornerShape(18.dp),
              )
            }
        }
      }
      Spacer(Modifier.height(8.dp))
    }
  }
}

@Composable
private fun SortChip(
  title: String,
  selected: Boolean,
  duration: Int,
  icon: ImageVector? = null,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  val bg by
    animateColorAsState(
      if (selected) MaterialTheme.colorScheme.primaryContainer
      else MaterialTheme.colorScheme.surfaceContainerHigh,
      tween(duration),
      label = "sort_chip",
    )
  val fg by
    animateColorAsState(
      if (selected) MaterialTheme.colorScheme.onPrimaryContainer
      else MaterialTheme.colorScheme.onSurfaceVariant,
      tween(duration),
      label = "sort_chip_text",
    )
  Row(
    modifier
      .heightIn(min = 48.dp)
      .clip(RoundedCornerShape(18.dp))
      .background(bg)
      .selectable(selected, role = Role.RadioButton, onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
  ) {
    if (icon != null) {
      Icon(icon, null, Modifier.size(20.dp), tint = fg)
      Spacer(Modifier.width(8.dp))
    }
    Text(
      title,
      color = fg,
      style = MaterialTheme.typography.labelLarge,
      maxLines = 1,
      overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    )
  }
}

internal val LibrarySortKey.titleRes
  get() =
    when (this) {
      LibrarySortKey.Title -> R.string.mpv_sort_title
      LibrarySortKey.Date -> R.string.mpv_sort_date
      LibrarySortKey.Size -> R.string.mpv_sort_size
      LibrarySortKey.Count -> R.string.mpv_sort_count
      LibrarySortKey.Duration -> R.string.mpv_field_duration
    }

@Composable
private fun LibrarySortKey.icon() =
  when (this) {
    LibrarySortKey.Title -> Icons.FormatSize
    LibrarySortKey.Date -> Icons.Calendar
    LibrarySortKey.Size -> Icons.Data
    LibrarySortKey.Count -> Icons.Movie
    LibrarySortKey.Duration -> Icons.Schedule
  }

internal fun LibrarySort.orderLabels() =
  when (key) {
    LibrarySortKey.Title -> R.string.mpv_sort_ascending to R.string.mpv_sort_descending
    LibrarySortKey.Date -> R.string.mpv_order_oldest to R.string.mpv_order_newest
    LibrarySortKey.Size -> R.string.mpv_order_smallest to R.string.mpv_order_largest
    LibrarySortKey.Count -> R.string.mpv_order_fewest to R.string.mpv_order_most
    LibrarySortKey.Duration -> R.string.mpv_order_shortest to R.string.mpv_order_longest
  }

@Composable
private fun LibraryField.icon() =
  when (this) {
    LibraryField.FullName,
    LibraryField.Extension -> Icons.FormatSize
    LibraryField.Path -> Icons.FolderOpen
    LibraryField.Count -> Icons.Movie
    LibraryField.TotalDuration,
    LibraryField.Duration -> Icons.Schedule
    LibraryField.Size -> Icons.Data
    LibraryField.Date -> Icons.Calendar
    LibraryField.Thumbnails -> Icons.Image
    LibraryField.Resolution -> Icons.Resolution
    LibraryField.Framerate -> Icons.Speed
    LibraryField.Subtitles -> Icons.Subtitles
    LibraryField.Codec -> Icons.Memory
    LibraryField.Progress -> Icons.History
    LibraryField.CenterTitles -> Icons.FormatAlignCenter
  }
