package live.mehiz.mpvkt.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.presentation.components.StableBottomSheet
import live.mehiz.mpvkt.ui.icons.Icons
import live.mehiz.mpvkt.ui.icons.Sort
import live.mehiz.mpvkt.ui.motion.LibraryOverscrollFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibrarySortButton(
  sort: LibrarySort,
  countEnabled: Boolean,
  reduceMotion: Boolean,
  enabled: Boolean = true,
  viewMode: LibraryViewMode = LibraryViewMode.Folder,
  onViewMode: (LibraryViewMode) -> Unit = {},
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
  if (expanded) {
    StableBottomSheet(
      onDismissRequest = { expanded = false },
      dismissRequested = closing,
      reduceMotion = reduceMotion,
      title = stringResource(R.string.mpv_sort),
    ) {
      CompositionLocalProvider(
        LocalOverscrollFactory provides if (reduceMotion) null else LibraryOverscrollFactory
      ) {
        LibrarySortMenuContent(
          sort,
          countEnabled,
          reduceMotion = reduceMotion,
          modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
          viewMode = viewMode,
          onViewMode = { mode ->
            if (!closing) {
              onViewMode(mode)
              closing = true
            }
          },
          onSelect = { choice ->
            if (!closing) {
              onSelect(choice)
              closing = true
            }
          },
        )
      }
    }
  }
}

/** Fixed row geometry keeps selection movement independent from dismissal. */
@Composable
internal fun LibrarySortMenuContent(
  sort: LibrarySort,
  countEnabled: Boolean,
  modifier: Modifier = Modifier,
  reduceMotion: Boolean = false,
  viewMode: LibraryViewMode = LibraryViewMode.Folder,
  onViewMode: (LibraryViewMode) -> Unit = {},
  onSelect: (LibrarySort) -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val duration = if (reduceMotion) 0 else 240
  val rowHeight =
    with(LocalDensity.current) { MaterialTheme.typography.titleMedium.lineHeight.toDp() + 24.dp }
      .coerceAtLeast(52.dp)
  val selected = colors.primaryContainer
  val index by
    animateFloatAsState(
      sort.key.ordinal.toFloat(),
      tween(duration, easing = FastOutSlowInEasing),
      label = "sort_selection",
    )
  Column(
    modifier
      .verticalScroll(rememberScrollState())
      .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
  ) {
    Text(
      stringResource(R.string.mpv_view_mode),
      style = MaterialTheme.typography.labelLarge,
      color = colors.onSurfaceVariant,
      modifier = Modifier.padding(start = 12.dp, bottom = 12.dp),
    )
    Row(
      Modifier.fillMaxWidth().selectableGroup(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      LibraryViewMode.entries.forEach { mode ->
        SortChoice(
          stringResource(
            when (mode) {
              LibraryViewMode.Folder -> R.string.mpv_view_folders
              LibraryViewMode.Tree -> R.string.mpv_view_tree
              LibraryViewMode.Videos -> R.string.mpv_view_videos
            }
          ),
          viewMode == mode,
          true,
          Modifier.weight(1f).heightIn(min = rowHeight),
          tonal = true,
          duration = duration,
        ) {
          onViewMode(mode)
        }
      }
    }
    Spacer(Modifier.height(24.dp))
    Text(
      stringResource(R.string.mpv_sort),
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.padding(start = 12.dp, bottom = 16.dp),
    )
    Box(Modifier.fillMaxWidth().selectableGroup()) {
      Box(
        Modifier.fillMaxWidth()
          .height(rowHeight)
          .graphicsLayer { translationY = index * rowHeight.toPx() }
          .background(selected, RoundedCornerShape(20.dp))
      )
      Column {
        LibrarySortKey.entries.forEach { key ->
          val enabled = key != LibrarySortKey.Count || countEnabled
          val disabledDescription = stringResource(R.string.mpv_sort_count_folders)
          SortChoice(
            stringResource(
              when (key) {
                LibrarySortKey.Title -> R.string.mpv_sort_title
                LibrarySortKey.Date -> R.string.mpv_sort_date
                LibrarySortKey.Size -> R.string.mpv_sort_size
                LibrarySortKey.Count -> R.string.mpv_sort_count
              }
            ),
            sort.key == key,
            enabled,
            Modifier.fillMaxWidth().height(rowHeight).semantics {
              if (!enabled) stateDescription = disabledDescription
            },
            duration = duration,
          ) {
            onSelect(sort.copy(key = key))
          }
        }
      }
    }
    Text(
      stringResource(R.string.mpv_sort_order),
      style = MaterialTheme.typography.labelLarge,
      color = colors.onSurfaceVariant,
      modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 12.dp),
    )
    Row(
      Modifier.fillMaxWidth().selectableGroup(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      SortChoice(
        stringResource(R.string.mpv_sort_ascending),
        sort.ascending,
        true,
        Modifier.weight(1f).height(rowHeight),
        tonal = true,
        duration = duration,
      ) {
        onSelect(sort.copy(ascending = true))
      }
      SortChoice(
        stringResource(R.string.mpv_sort_descending),
        !sort.ascending,
        true,
        Modifier.weight(1f).height(rowHeight),
        tonal = true,
        duration = duration,
      ) {
        onSelect(sort.copy(ascending = false))
      }
    }
  }
}

@Composable
private fun SortChoice(
  title: String,
  selected: Boolean,
  enabled: Boolean,
  modifier: Modifier,
  tonal: Boolean = false,
  duration: Int,
  onClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val background by
    animateColorAsState(
      if (!tonal) Color.Transparent
      else if (selected) colors.primaryContainer else colors.surfaceContainerHighest,
      tween(duration),
      label = "sort_order_selection",
    )
  val foreground by
    animateColorAsState(
      if (selected) colors.onPrimaryContainer else colors.onSurface,
      tween(duration),
      label = "sort_text",
    )
  Box(
    modifier
      .clip(RoundedCornerShape(20.dp))
      .background(background)
      .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
      .padding(horizontal = if (tonal) 10.dp else 20.dp),
    contentAlignment = if (tonal) Alignment.Center else Alignment.CenterStart,
  ) {
    Text(
      title,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Medium,
      color = foreground.copy(alpha = if (enabled) 1f else .38f),
    )
  }
}
