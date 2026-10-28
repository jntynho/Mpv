package live.mehiz.mpvkt.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.ui.icons.*

/** Only bounded slots resize: a long title never supplies an intrinsic animation width. */
@Composable
internal fun LibraryHeader(
  title: String,
  searching: Boolean,
  inFolder: Boolean,
  query: String,
  sort: LibrarySort,
  reduceMotion: Boolean,
  onQuery: (String) -> Unit,
  onBack: () -> Unit,
  onSearch: () -> Unit,
  onSort: (LibrarySort) -> Unit,
  onSettings: () -> Unit,
  viewMode: LibraryViewMode = LibraryViewMode.Folder,
  onViewMode: (LibraryViewMode) -> Unit = {},
) {
  val preparing = live.mehiz.mpvkt.ui.startup.LocalInterfacePreparation.current
  val duration = if (reduceMotion) 0 else 260
  val headerHeight =
    with(LocalDensity.current) { MaterialTheme.typography.bodyLarge.lineHeight.toDp() + 40.dp }
      .coerceAtLeast(64.dp)
  val back by
    animateFloatAsState(
      if (searching || inFolder) 1f else 0f,
      tween(duration, easing = FastOutSlowInEasing),
      label = "header_back_slot",
    )
  val search by
    animateFloatAsState(
      if (searching) 1f else 0f,
      tween(duration, easing = FastOutSlowInEasing),
      label = "header_search_slot",
    )
  val focus = remember { FocusRequester() }
  val focusManager = LocalFocusManager.current
  val keyboard = LocalSoftwareKeyboardController.current
  LaunchedEffect(searching, preparing) {
    if (!searching) {
      focusManager.clearFocus()
      keyboard?.hide()
    }
  }
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Surface {
      BoxWithConstraints {
        // Measure both outgoing and incoming titles at the final reserved width.
        // Only their viewport moves; ellipsis does not change on every animation frame.
        val titleWidth = (maxWidth - 212.dp).coerceAtLeast(1.dp)
        Row(
          Modifier.fillMaxWidth()
            .statusBarsPadding()
            .height(headerHeight)
            .padding(start = 12.dp, end = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            Modifier.width(48.dp * back).height(48.dp).clipToBounds(),
            contentAlignment = Alignment.CenterStart,
          ) {
            if (back > 0f)
              IconButton(
                onClick = onBack,
                enabled = !preparing && (searching || inFolder),
                modifier =
                  Modifier.wrapContentSize(Alignment.CenterStart, unbounded = true)
                    .requiredSize(48.dp)
                    .graphicsLayer { alpha = back },
              ) {
                androidx.compose.animation.Crossfade(
                  searching,
                  animationSpec = tween(if (reduceMotion) 0 else 140),
                  label = "header_back_icon",
                ) {
                  Icon(
                    if (it) Icons.Close else Icons.AutoMirrored.ArrowBack,
                    stringResource(R.string.mpv_back),
                  )
                }
              }
          }
          AnimatedContent(
            targetState = searching,
            modifier = Modifier.weight(1f).height(headerHeight - 8.dp).clipToBounds(),
            transitionSpec = {
              ((fadeIn(tween(if (reduceMotion) 0 else 200)) +
                  slideInHorizontally(tween(duration)) {
                    if (reduceMotion) 0 else it / 24
                  }) togetherWith
                  (fadeOut(tween(if (reduceMotion) 0 else 120)) +
                    slideOutHorizontally(tween(duration)) { if (reduceMotion) 0 else -it / 24 }))
                .using(SizeTransform(clip = true, sizeAnimationSpec = { _, _ -> tween(0) }))
            },
            contentAlignment = Alignment.CenterStart,
            label = "header_search_content",
          ) { editing ->
            if (editing) {
              LaunchedEffect(searching, preparing) {
                if (!searching || preparing) return@LaunchedEffect
                // Let the header settle before the keyboard starts its own inset animation.
                if (!reduceMotion) delay(260)
                focus.requestFocus()
              }
              TextField(
                query,
                onQuery,
                Modifier.fillMaxWidth().focusRequester(focus),
                enabled = searching && !preparing,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = { Text(stringResource(R.string.mpv_search)) },
                colors =
                  TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                  ),
              )
            } else {
              androidx.compose.animation.Crossfade(
                title,
                Modifier.fillMaxWidth(),
                tween(if (reduceMotion) 0 else 180),
                label = "bounded_header_title",
              ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                  Text(
                    it,
                    Modifier.widthIn(max = titleWidth).fillMaxWidth(),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
          Box(Modifier.width(144.dp * (1f - search)).height(48.dp).clipToBounds()) {
            if (search < 1f)
              Row(
                Modifier.wrapContentSize(Alignment.CenterStart, unbounded = true)
                  .requiredWidth(144.dp)
                  .graphicsLayer { alpha = 1f - search }
              ) {
                IconButton(onClick = onSearch, enabled = !searching && !preparing) {
                  Icon(Icons.Search, stringResource(R.string.mpv_search))
                }
                LibrarySortButton(
                  sort,
                  countEnabled =
                    viewMode != LibraryViewMode.Videos &&
                      (!inFolder || viewMode == LibraryViewMode.Tree),
                  reduceMotion = reduceMotion,
                  enabled = !searching && !preparing,
                  onSelect = onSort,
                  viewMode = viewMode,
                  onViewMode = onViewMode,
                )
                IconButton(onClick = onSettings, enabled = !searching && !preparing) {
                  Icon(Icons.Settings, stringResource(R.string.luma_settings))
                }
              }
          }
        }
      }
    }
  }
}
