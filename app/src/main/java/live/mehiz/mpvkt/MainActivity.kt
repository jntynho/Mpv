package live.mehiz.mpvkt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import live.mehiz.mpvkt.preferences.AppearancePreferences
import live.mehiz.mpvkt.preferences.PlayerPreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.ui.home.HomeScreen
import live.mehiz.mpvkt.ui.startup.StartupGate
import live.mehiz.mpvkt.ui.theme.DarkMode
import live.mehiz.mpvkt.ui.theme.MpvKtTheme
import live.mehiz.mpvkt.ui.utils.LocalBackStack
import live.mehiz.mpvkt.ui.utils.LocalNavigateBack
import live.mehiz.mpvkt.ui.utils.navigationMotion
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {
  private val appearancePreferences by inject<AppearancePreferences>()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      val dark by appearancePreferences.darkMode.collectAsState()
      val isSystemInDarkTheme = isSystemInDarkTheme()
      enableEdgeToEdge(
        SystemBarStyle.auto(lightScrim = Color.White.toArgb(), darkScrim = Color.White.toArgb()) {
          dark == DarkMode.Dark || (dark == DarkMode.System && isSystemInDarkTheme)
        }
      )
      MpvKtTheme { Surface { StartupGate { Navigator() } } }
    }
  }

  @Composable
  fun Navigator() {
    val preparing = live.mehiz.mpvkt.ui.startup.LocalInterfacePreparation.current
    val backstack = rememberNavBackStack<Screen>(HomeScreen)
    val reduceMotion by koinInject<PlayerPreferences>().reduceMotion.collectAsState()
    val goBack: () -> Unit = { if (backstack.size > 1) backstack.removeLastOrNull() else finish() }
    CompositionLocalProvider(LocalBackStack provides backstack, LocalNavigateBack provides goBack) {
      Box(Modifier.fillMaxSize()) {
        NavDisplay(
          backStack = backstack,
          onBack = { goBack() },
          entryProvider = { route -> NavEntry(route) { (it as Screen).Content() } },
          popTransitionSpec = { navigationMotion(back = true, reduceMotion = reduceMotion) },
          transitionSpec = { navigationMotion(back = false, reduceMotion = reduceMotion) },
          predictivePopTransitionSpec = {
            navigationMotion(back = true, reduceMotion = reduceMotion)
          },
        )
        // Draw-only overlay: no click handler or semantics, touches reach the content.
        Box(
          Modifier.align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(64.dp)
            .background(
              Brush.verticalGradient(
                listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = .82f))
              )
            )
        )
      }
      // Registered after NavDisplay to handle internal back on gesture completion.
      // A cancelled gesture never invokes goBack; dialogs/popups retain their window-level
      // handling.
      BackHandler(enabled = preparing || backstack.size > 1) {
        if (preparing) finish() else goBack()
      }
    }
  }
}
