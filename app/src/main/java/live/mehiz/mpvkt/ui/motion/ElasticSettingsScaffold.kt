package live.mehiz.mpvkt.ui.motion

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.preferences.PlayerPreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
private val LocalSettingsScroll = staticCompositionLocalOf<TopAppBarScrollBehavior?> { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElasticSettingsScaffold(
  modifier: Modifier = Modifier,
  topBar: @Composable () -> Unit,
  floatingActionButton: @Composable () -> Unit = {},
  content: @Composable (PaddingValues) -> Unit,
) {
  val reduceMotion by koinInject<PlayerPreferences>().reduceMotion.collectAsState()
  val behavior =
    TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
      snapAnimationSpec =
        if (reduceMotion) tween(0) else spring(dampingRatio = 1f, stiffness = 400f)
    )
  CompositionLocalProvider(
    LocalSettingsScroll provides behavior,
    LocalOverscrollFactory provides if (reduceMotion) null else LibraryOverscrollFactory,
  ) {
    Scaffold(
      modifier = modifier.nestedScroll(behavior.nestedScrollConnection),
      topBar = topBar,
      floatingActionButton = floatingActionButton,
      content = content,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElasticSettingsTopAppBar(
  title: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  navigationIcon: @Composable () -> Unit = {},
  actions: @Composable RowScope.() -> Unit = {},
) {
  LargeTopAppBar(
    title = title,
    modifier = modifier,
    navigationIcon = navigationIcon,
    actions = actions,
    expandedHeight = 128.dp,
    scrollBehavior = LocalSettingsScroll.current,
  )
}
