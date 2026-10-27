package live.mehiz.mpvkt.ui.motion

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import live.mehiz.mpvkt.preferences.PlayerPreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
private val LocalSettingsScroll = staticCompositionLocalOf<TopAppBarScrollBehavior?> { null }
private val LocalSettingsPull = staticCompositionLocalOf { 0f }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElasticSettingsScaffold(
  modifier: Modifier = Modifier,
  topBar: @Composable () -> Unit,
  floatingActionButton: @Composable () -> Unit = {},
  content: @Composable (PaddingValues) -> Unit,
) {
  val reduceMotion by koinInject<PlayerPreferences>().reduceMotion.collectAsState()
  val scope = rememberCoroutineScope()
  val density = LocalDensity.current
  val pull = remember(density, reduceMotion) { ElasticPullState(scope, with(density) { 84.dp.toPx() }, !reduceMotion) }
  DisposableEffect(pull) { onDispose(pull::cancel) }
  val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
    snapAnimationSpec = if (reduceMotion) tween(0) else spring(dampingRatio = .9f, stiffness = 400f),
  )
  CompositionLocalProvider(LocalSettingsScroll provides behavior, LocalSettingsPull provides pull.offset,
    LocalOverscrollFactory provides null) {
    Scaffold(modifier = modifier.nestedScroll(pull.connection).nestedScroll(behavior.nestedScrollConnection),
      topBar = topBar, floatingActionButton = floatingActionButton) { padding ->
      Box(Modifier.fillMaxSize().graphicsLayer { translationY = pull.offset * .7f }) { content(padding) }
    }
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
  val extra = with(LocalDensity.current) { (LocalSettingsPull.current.coerceAtLeast(0f) * .3f).toDp() }
  LargeTopAppBar(title = title, modifier = modifier, navigationIcon = navigationIcon, actions = actions,
    expandedHeight = 128.dp + extra, scrollBehavior = LocalSettingsScroll.current)
}
