package live.mehiz.mpvkt.ui.secure

import android.content.Intent
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.presentation.components.*
import live.mehiz.mpvkt.ui.icons.*
import live.mehiz.mpvkt.ui.motion.*
import live.mehiz.mpvkt.ui.player.PlayerActivity
import live.mehiz.mpvkt.ui.preferences.*
import live.mehiz.mpvkt.ui.utils.LocalNavigateBack

/** Tokens and decrypted paths exist only in this process, never in settings or playback history. */
internal object SecurePlaybackRegistry {
  private val files = java.util.concurrent.ConcurrentHashMap<String, File>()

  fun register(file: File): String =
    java.util.UUID.randomUUID().toString().also { files[it] = file }

  fun file(token: String?) = token?.let(files::get)

  fun remove(token: String?) {
    if (token != null) files.remove(token)?.delete()
  }
}

@Serializable
object SecureFolderScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val vault = remember { SecureVault(context.applicationContext) }
    var configured by remember { mutableStateOf(vault.configured) }
    var unlocked by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf(emptyList<VaultEntry>()) }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var external by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<VaultEntry?>(null) }
    var exporting by remember { mutableStateOf<VaultEntry?>(null) }
    fun lock() {
      vault.close()
      unlocked = false
      entries = emptyList()
      pin = ""
      confirm = ""
    }
    DisposableEffect(activity, lifecycle) {
      val oldSecure =
        activity?.window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE) != 0
      activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
      val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_STOP && !external) lock()
      }
      lifecycle.addObserver(observer)
      onDispose {
        lifecycle.removeObserver(observer)
        vault.close()
        if (!oldSecure) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
      }
    }
    fun perform(action: suspend () -> Unit) {
      if (busy) return
      busy = true
      error = false
      scope.launch {
        try {
          withContext(Dispatchers.IO) { action() }
          if (vault.unlocked) {
            entries = withContext(Dispatchers.IO) { vault.entries() }
            unlocked = true
          }
        } catch (c: CancellationException) {
          throw c
        } catch (_: Exception) {
          error = true
          // A batch may have safely imported earlier files before one provider failed.
          if (vault.unlocked) {
            val refreshed =
              withContext(Dispatchers.IO) { runCatching { vault.entries() }.getOrNull() }
            if (refreshed == null) lock()
            else {
              entries = refreshed
              unlocked = true
            }
          }
          Toast.makeText(context, R.string.update_secure_failed, Toast.LENGTH_LONG).show()
        } finally {
          if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && !external) lock()
          busy = false
        }
      }
    }
    val playback =
      rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val export =
      rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
      ) { uri ->
        external = false
        val entry = exporting
        exporting = null
        if (uri != null && entry != null)
          perform {
            val file = vault.materialize(entry)
            try {
              context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                file.inputStream().use { it.copyTo(out) }
              } ?: error("Cannot write")
            } finally {
              file.delete()
            }
          }
      }
    val picker =
      rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        external = false
        if (uris.isNotEmpty())
          perform {
            uris.forEach { uri ->
              val name =
                runCatching {
                    context.contentResolver
                      .query(
                        uri,
                        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null,
                      )
                      ?.use { if (it.moveToFirst()) it.getString(0) else null }
                  }
                  .getOrNull() ?: "Video"
              vault.add(uri, name)
            }
          }
      }
    ElasticSettingsScaffold(
      topBar = {
        ElasticSettingsTopAppBar(
          { Text(stringResource(R.string.update_secure)) },
          navigationIcon = {
            IconButton(LocalNavigateBack.current) { Icon(Icons.AutoMirrored.ArrowBack, null) }
          },
          actions = {
            if (unlocked)
              IconButton({ lock() }) { Icon(Icons.Lock, stringResource(R.string.update_lock)) }
          },
        )
      }
    ) { padding ->
      if (!unlocked)
        Column(
          Modifier.fillMaxSize()
            .padding(padding)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
          Spacer(Modifier.height(24.dp))
          Icon(Icons.Lock, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
          Text(
            stringResource(
              if (configured) R.string.update_unlock else R.string.update_secure_setup
            ),
            style = MaterialTheme.typography.headlineSmall,
          )
          Text(
            stringResource(
              if (Build.VERSION.SDK_INT < 23) R.string.update_secure_requires
              else R.string.update_secure_detail
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (Build.VERSION.SDK_INT >= 23) {
            RoundedTextField(
              pin,
              {
                pin = it.filter(Char::isDigit).take(6)
                error = false
              },
              singleLine = true,
              label = { Text(stringResource(R.string.update_pin)) },
              visualTransformation = PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
              isError = error,
              enabled = !busy,
            )
            if (!configured)
              RoundedTextField(
                confirm,
                { confirm = it.filter(Char::isDigit).take(6) },
                singleLine = true,
                label = { Text(stringResource(R.string.update_pin_confirm)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                enabled = !busy,
              )
            Button(
              {
                val pass = pin.toCharArray()
                pin = ""
                confirm = ""
                perform {
                  if (configured) vault.unlock(pass)
                  else {
                    vault.setup(pass)
                    configured = true
                  }
                }
              },
              enabled = !busy && pin.length == 6 && (configured || pin == confirm),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                stringResource(
                  if (configured) R.string.update_unlock else R.string.update_secure_setup
                )
              )
            }
          }
          if (busy) CircularProgressIndicator(Modifier.size(28.dp))
        }
      else
        LazyColumn(
          Modifier.fillMaxSize().padding(padding),
          contentPadding = PaddingValues(bottom = 32.dp),
        ) {
          item {
            SettingsGroup {
              SettingsRow(
                { Text(stringResource(R.string.update_secure_add)) },
                { Text(stringResource(R.string.update_secure_copy)) },
                { Icon(Icons.Add, null) },
                enabled = !busy,
              ) {
                external = true
                picker.launch(arrayOf("video/*", "audio/*"))
              }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (entries.isEmpty())
              Text(
                stringResource(R.string.update_secure_empty),
                Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
          }
          items(entries, key = { it.id }) { entry ->
            SettingsGroup {
              SettingsRow(
                { Text(entry.name, maxLines = 2) },
                { Text(live.mehiz.mpvkt.ui.home.formatBytes(entry.bytes)) },
                { Icon(Icons.PlayCircle, null) },
                enabled = !busy,
                trailing = {
                  Row {
                    IconButton(
                      {
                        exporting = entry
                        external = true
                        export.launch(entry.name)
                      },
                      enabled = !busy,
                    ) {
                      Icon(Icons.Download, stringResource(R.string.update_export))
                    }
                    IconButton({ deleting = entry }, enabled = !busy) {
                      Icon(Icons.Delete, stringResource(R.string.update_secure_delete))
                    }
                  }
                },
              ) {
                perform {
                  val file = vault.materialize(entry)
                  withContext(Dispatchers.Main) {
                    val token = SecurePlaybackRegistry.register(file)
                    try {
                      val uri =
                        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                      playback.launch(
                        Intent(context, PlayerActivity::class.java)
                          .setAction(Intent.ACTION_VIEW)
                          .setDataAndType(uri, "video/*")
                          .putExtra("secure_session", token)
                          .putExtra("secure_title", entry.name)
                          .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                      )
                    } catch (e: Exception) {
                      SecurePlaybackRegistry.remove(token)
                      throw e
                    }
                  }
                }
              }
            }
          }
        }
    }
    deleting?.let { entry ->
      ConfirmDialog(
        stringResource(R.string.update_secure_delete),
        entry.name,
        onConfirm = {
          deleting = null
          perform { vault.delete(entry) }
        },
        onCancel = { deleting = null },
      )
    }
  }
}
