package live.mehiz.mpvkt.ui.secure

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import live.mehiz.mpvkt.R

/** An old/cancelled Android callback cannot authorize a newer request. */
internal class DeviceAuthRequests {
  private var sequence = 0L
  private var active: Long? = null

  fun begin(): Long? {
    if (active != null) return null
    return (++sequence).also { active = it }
  }

  fun complete(id: Long): Boolean {
    if (active != id) return false
    active = null
    return true
  }

  fun cancel() {
    active = null
    sequence++
  }
}

/** Android proves the device owner; the vault key additionally requires its hardware auth token. */
@Composable
internal fun rememberDeviceOwnerPrompt(success: () -> Unit, cancelled: () -> Unit): () -> Unit {
  val context = LocalContext.current
  val onSuccess by rememberUpdatedState(success)
  val onCancel by rememberUpdatedState(cancelled)
  val requests = remember { DeviceAuthRequests() }
  var signal by remember { mutableStateOf<CancellationSignal?>(null) }
  var legacyId by remember { mutableStateOf<Long?>(null) }
  val credential =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      legacyId?.let { id ->
        if (requests.complete(id)) {
          if (result.resultCode == Activity.RESULT_OK) onSuccess() else onCancel()
        }
      }
      legacyId = null
    }
  DisposableEffect(Unit) {
    onDispose {
      requests.cancel()
      signal?.cancel()
    }
  }
  return {
    val id = requests.begin()
    if (id != null) {
      try {
        val manager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!manager.isKeyguardSecure) {
          requests.complete(id)
          onCancel()
        } else if (Build.VERSION.SDK_INT >= 29) {
          val next = CancellationSignal()
          signal = next
          val builder =
            BiometricPrompt.Builder(context).setTitle(context.getString(R.string.update_secure))
          if (Build.VERSION.SDK_INT >= 30)
            builder.setAllowedAuthenticators(
              android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
          else builder.setDeviceCredentialAllowed(true)
          builder
            .build()
            .authenticate(
              next,
              ContextCompat.getMainExecutor(context),
              object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                  result: BiometricPrompt.AuthenticationResult
                ) {
                  if (requests.complete(id)) {
                    signal = null
                    onSuccess()
                  }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                  if (requests.complete(id)) {
                    signal = null
                    onCancel()
                  }
                }
              },
            )
        } else {
          val intent =
            manager.createConfirmDeviceCredentialIntent(
              context.getString(R.string.update_secure),
              null,
            )
          if (intent == null) {
            requests.complete(id)
            onCancel()
          } else {
            legacyId = id
            credential.launch(intent)
          }
        }
      } catch (_: Exception) {
        if (requests.complete(id)) {
          signal = null
          onCancel()
        }
      }
    }
  }
}
