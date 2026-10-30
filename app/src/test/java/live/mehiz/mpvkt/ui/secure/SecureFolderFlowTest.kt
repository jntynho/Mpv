package live.mehiz.mpvkt.ui.secure

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import javax.crypto.spec.SecretKeySpec
import live.mehiz.mpvkt.preferences.*
import live.mehiz.mpvkt.preferences.preference.AndroidPreferenceStore
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.testutils.captureWindow
import live.mehiz.mpvkt.ui.theme.MpvKtTheme
import live.mehiz.mpvkt.ui.utils.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.koin.core.context.*
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS-w392dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SecureFolderFlowTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setup() {
    val store = AndroidPreferenceStore(compose.activity)
    startKoin {
      modules(
        module {
          single { PlayerPreferences(store) }
          single { AppearancePreferences(store) }
        }
      )
    }
  }

  @After
  fun stop() {
    stopKoin()
  }

  @Test
  fun duplicateAndLateAuthenticationCallbacksCannotUnlockANewRequest() {
    val requests = DeviceAuthRequests()
    val first = requests.begin()!!
    assertNull(requests.begin())
    requests.cancel()
    val second = requests.begin()!!
    assertFalse(requests.complete(first))
    assertTrue(requests.complete(second))
    assertFalse(requests.complete(second))
  }

  @Test
  fun setupRecoveryAndManagementUseTheSameExistingVault() {
    val wrapping = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    val factory: (android.content.Context) -> SecureVault = { ctx -> SecureVault(ctx) { wrapping } }
    compose.setContent {
      MpvKtTheme {
        val stack = androidx.navigation3.runtime.rememberNavBackStack<Screen>(SecureFolderScreen)
        CompositionLocalProvider(
          LocalBackStack provides stack,
          LocalNavigateBack provides {},
          LocalSecureVaultFactory provides factory,
        ) {
          Surface(Modifier.fillMaxSize()) { SecureFolderScreen.Content() }
        }
      }
    }
    compose.onNodeWithText("4 digits").assertExists()
    compose.onNodeWithText("PIN").performTextInput("1234")
    compose.onNodeWithText("Confirm PIN").performTextInput("1234")
    compose.onNodeWithText("Security question").performTextInput("First pet?")
    compose.onNodeWithText("Your answer").performTextInput("Mimi")
    compose
      .onNode(hasText("Create Secure Folder") and hasClickAction())
      .performScrollTo()
      .performClick()
    compose.waitUntil(10000) {
      compose
        .onAllNodesWithText("Move videos here to keep them private.")
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    compose.onNodeWithContentDescription("Lock").performClick()
    compose.onNodeWithText("Forgot PIN?").performClick()
    compose.onNodeWithText("First pet?").assertIsDisplayed()
    compose.onNodeWithText("6 digits").performClick()
    compose.onNodeWithText("New PIN").performTextInput("654321")
    compose.onNodeWithText("Confirm PIN").performTextInput("654321")
    compose.onNodeWithText("Your answer").performTextInput("mimi")
    compose.onNodeWithText("Reset PIN").performScrollTo().performClick()
    compose.waitUntil(10000) {
      compose
        .onAllNodesWithText("Move videos here to keep them private.")
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    compose.onNodeWithContentDescription("Secure Folder settings").performClick()
    compose.onNodeWithText("Change PIN").assertIsDisplayed()
    compose.onNodeWithText("Biometrics or device lock").assertExists()
    compose.onNodeWithText("Hide settings entry").performScrollTo().performClick()
    compose.runOnIdle { assertTrue(SecureFolderOptions.hidden(compose.activity)) }
    compose.runOnUiThread { captureWindow(compose.activity, "secure-management") }
  }
}
