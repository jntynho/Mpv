package live.mehiz.mpvkt.ui.secure

import android.content.Intent
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.draw.clip
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

internal val LocalSecureVaultFactory =
  staticCompositionLocalOf<(android.content.Context) -> SecureVault> { { SecureVault(it) } }

private enum class VaultPage {
  Gate,
  Recovery,
  Files,
  Settings,
}

private enum class AccountAction {
  Pin,
  Recovery,
  Delete,
}

@Serializable
object SecureFolderScreen : Screen {
  @OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
  )
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val navigateBack = LocalNavigateBack.current
    val scope = rememberCoroutineScope()
    val vaultFactory = LocalSecureVaultFactory.current
    val vault = remember(context, vaultFactory) { vaultFactory(context.applicationContext) }
    var configured by remember { mutableStateOf(vault.configured) }
    var page by remember { mutableStateOf(VaultPage.Gate) }
    var entries by remember { mutableStateOf(emptyList<VaultEntry>()) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var pinLength by remember { mutableIntStateOf(vault.pinLength) }
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var recoveryQuestion by remember { mutableStateOf("") }
    var hasRecovery by remember { mutableStateOf(vault.hasRecovery) }
    var deviceAccess by remember { mutableStateOf(vault.hasDeviceAccess) }
    var reveal by remember { mutableStateOf(false) }
    var attached by remember { mutableStateOf(true) }
    var sessionEpoch by remember { mutableLongStateOf(0L) }
    var busy by remember { mutableStateOf(false) }
    var external by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var pendingMove by remember { mutableStateOf(emptyList<android.net.Uri>()) }
    var pendingRestore by remember { mutableStateOf(emptySet<String>()) }
    var accountAction by remember { mutableStateOf<AccountAction?>(null) }
    var newPin by remember { mutableStateOf("") }
    var newConfirmation by remember { mutableStateOf("") }
    var newQuestion by remember { mutableStateOf("") }
    var newAnswer by remember { mutableStateOf("") }
    var enablingDevice by remember { mutableStateOf(false) }
    val hiddenFlow = remember(context) { SecureFolderOptions.observeHidden(context) }
    val hidden by hiddenFlow.collectAsState(initial = SecureFolderOptions.hidden(context))
    fun lock() {
      sessionEpoch++
      vault.close()
      page = VaultPage.Gate
      reveal = false
      entries = emptyList()
      selected = emptySet()
      pin = ""
      confirmation = ""
      answer = ""
      newPin = ""
      newConfirmation = ""
      newAnswer = ""
      pendingMove = emptyList()
      accountAction = null
    }
    fun back() {
      when {
        selected.isNotEmpty() -> selected = emptySet()
        page == VaultPage.Settings -> page = VaultPage.Files
        page == VaultPage.Recovery -> {
          page = VaultPage.Gate
          pin = ""
          answer = ""
          confirmation = ""
          pinLength = vault.pinLength
        }
        else -> {
          lock()
          navigateBack()
        }
      }
    }
    BackHandler(page != VaultPage.Gate || selected.isNotEmpty()) { back() }
    DisposableEffect(activity, lifecycle) {
      val wasSecure =
        (activity?.window?.attributes?.flags ?: 0) and WindowManager.LayoutParams.FLAG_SECURE != 0
      activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
      val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_STOP && !external) lock()
      }
      lifecycle.addObserver(observer)
      onDispose {
        attached = false
        sessionEpoch++
        lifecycle.removeObserver(observer)
        vault.close()
        if (!wasSecure) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
      }
    }
    LaunchedEffect(configured, hasRecovery) {
      if (hasRecovery)
        recoveryQuestion =
          withContext(Dispatchers.IO) { runCatching { vault.recoveryQuestion() }.getOrDefault("") }
    }
    fun perform(action: suspend () -> Unit) {
      if (busy) return
      busy = true
      failed = false
      val epoch = sessionEpoch
      scope.launch {
        try {
          withContext(Dispatchers.IO) { action() }
          if (!attached || epoch != sessionEpoch) {
            vault.close()
            return@launch
          }
          if (vault.unlocked) {
            if (vault.hasRecovery)
              recoveryQuestion = withContext(Dispatchers.IO) { vault.recoveryQuestion() }
            entries = withContext(Dispatchers.IO) { vault.entries() }
            if (page == VaultPage.Gate || page == VaultPage.Recovery) page = VaultPage.Files
          }
        } catch (c: CancellationException) {
          throw c
        } catch (_: Exception) {
          failed = true
          Toast.makeText(context, R.string.secure_operation_failed, Toast.LENGTH_LONG).show()
          if (vault.unlocked) {
            val loaded = withContext(Dispatchers.IO) { runCatching { vault.entries() }.getOrNull() }
            if (loaded == null) lock() else entries = loaded
          }
        } finally {
          configured = vault.configured
          hasRecovery = vault.hasRecovery
          deviceAccess = vault.hasDeviceAccess
          if (
            !attached ||
              epoch != sessionEpoch ||
              !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && !external
          )
            lock()
          busy = false
        }
      }
    }
    val authenticate =
      rememberDeviceOwnerPrompt(
        success = {
          external = false
          perform { if (enablingDevice) vault.enableDeviceAccess() else vault.unlockWithDevice() }
        },
        cancelled = {
          external = false
          Toast.makeText(context, R.string.secure_device_unavailable, Toast.LENGTH_SHORT).show()
        },
      )
    val playback =
      rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val movePicker =
      rememberLauncherForActivityResult(SecureMediaPicker()) { uris ->
        external = false
        if (vault.unlocked) pendingMove = uris
      }
    val restorePicker =
      rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        external = false
        val ids = pendingRestore
        pendingRestore = emptySet()
        if (tree != null && vault.unlocked) {
          val targets = entries.filter { it.id in ids }
          perform {
            targets.forEach { entry -> vault.restore(entry, tree) }
            selected = emptySet()
            withContext(Dispatchers.Main) {
              Toast.makeText(context, R.string.secure_restored, Toast.LENGTH_SHORT).show()
            }
          }
        }
      }
    ElasticSettingsScaffold(
      topBar = {
        ElasticSettingsTopAppBar(
          {
            Text(
              if (selected.isNotEmpty()) "${selected.size}"
              else
                stringResource(
                  if (page == VaultPage.Settings) R.string.secure_manage else R.string.update_secure
                )
            )
          },
          navigationIcon = {
            IconButton({ back() }) {
              Icon(
                if (selected.isNotEmpty()) Icons.Close else Icons.AutoMirrored.ArrowBack,
                stringResource(R.string.mpv_back),
              )
            }
          },
          actions = {
            if (page == VaultPage.Files) {
              if (selected.isNotEmpty())
                IconButton({ selected = entries.map { it.id }.toSet() }) {
                  Icon(Icons.List, stringResource(R.string.secure_select_all))
                }
              else {
                IconButton({ page = VaultPage.Settings }) {
                  Icon(Icons.Settings, stringResource(R.string.secure_manage))
                }
                IconButton({ lock() }) { Icon(Icons.Lock, stringResource(R.string.update_lock)) }
              }
            }
          },
        )
      },
      floatingActionButton = {
        if (page == VaultPage.Files && selected.isEmpty())
          FloatingActionButton(
            {
              external = true
              movePicker.launch(arrayOf("video/*", "audio/*"))
            },
            shape = androidx.compose.foundation.shape.CircleShape,
          ) {
            Icon(Icons.Add, stringResource(R.string.secure_move_files))
          }
      },
    ) { padding ->
      when (page) {
        VaultPage.Gate,
        VaultPage.Recovery ->
          Column(
            Modifier.fillMaxSize()
              .padding(padding)
              .imePadding()
              .verticalScroll(rememberScrollState())
              .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            Icon(
              Icons.Lock,
              null,
              Modifier.padding(top = 12.dp).size(44.dp),
              tint = MaterialTheme.colorScheme.primary,
            )
            Text(
              stringResource(
                when {
                  page == VaultPage.Recovery -> R.string.secure_recover
                  configured -> R.string.update_unlock
                  else -> R.string.update_secure_setup
                }
              ),
              style = MaterialTheme.typography.headlineSmall,
            )
            Text(
              stringResource(
                if (page == VaultPage.Recovery) R.string.secure_recovery_hint
                else R.string.secure_gate_hint
              ),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (Build.VERSION.SDK_INT < 23) Text(stringResource(R.string.update_secure_requires))
            else {
              if (!configured || page == VaultPage.Recovery)
                PinLengthSelector(pinLength) {
                  pinLength = it
                  pin = ""
                  confirmation = ""
                }
              PinField(
                pin,
                {
                  pin = it
                  failed = false
                },
                pinLength,
                reveal,
                { reveal = !reveal },
                stringResource(
                  if (page == VaultPage.Recovery) R.string.secure_new_pin else R.string.secure_pin
                ),
                busy,
                failed,
              )
              if (!configured || page == VaultPage.Recovery) {
                PinField(
                  confirmation,
                  { confirmation = it },
                  pinLength,
                  reveal,
                  { reveal = !reveal },
                  stringResource(R.string.update_pin_confirm),
                  busy,
                  false,
                )
                if (page == VaultPage.Recovery)
                  Text(
                    recoveryQuestion,
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    style = MaterialTheme.typography.titleMedium,
                  )
                else
                  RoundedTextField(
                    question,
                    { question = it.take(200) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.secure_question)) },
                    enabled = !busy,
                  )
                RoundedTextField(
                  answer,
                  { answer = it.take(256) },
                  singleLine = true,
                  label = { Text(stringResource(R.string.secure_answer)) },
                  enabled = !busy,
                  visualTransformation = PasswordVisualTransformation(),
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
              }
              Button(
                {
                  val attemptedPage = page
                  val wasConfigured = configured
                  val pass = pin.toCharArray()
                  val reply = answer.toCharArray()
                  val prompt = question
                  pin = ""
                  confirmation = ""
                  answer = ""
                  perform {
                    when {
                      attemptedPage == VaultPage.Recovery -> vault.recoverPin(reply, pass)
                      wasConfigured -> vault.unlock(pass)
                      else -> vault.setupAccount(pass, prompt, reply)
                    }
                  }
                },
                enabled =
                  !busy &&
                    pin.length == pinLength &&
                    (configured && page == VaultPage.Gate ||
                      (pin == confirmation &&
                        answer.trim().length >= 3 &&
                        (page == VaultPage.Recovery || question.trim().length >= 3))),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  stringResource(
                    if (page == VaultPage.Recovery) R.string.secure_reset_pin
                    else if (configured) R.string.update_unlock else R.string.update_secure_setup
                  )
                )
              }
              if (configured && page == VaultPage.Gate) {
                if (deviceAccess)
                  OutlinedButton(
                    {
                      enablingDevice = false
                      external = true
                      authenticate()
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Icon(Icons.Touch, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.secure_device_unlock))
                  }
                TextButton(
                  {
                    if (hasRecovery) {
                      page = VaultPage.Recovery
                      pinLength = vault.pinLength
                      pin = ""
                      confirmation = ""
                    } else
                      Toast.makeText(context, R.string.secure_legacy_recovery, Toast.LENGTH_LONG)
                        .show()
                  },
                  enabled = !busy,
                ) {
                  Text(stringResource(R.string.secure_forgot))
                }
              }
            }
            if (busy) CircularProgressIndicator(Modifier.size(26.dp))
          }
        VaultPage.Files ->
          Column(Modifier.fillMaxSize().padding(padding)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (entries.isEmpty())
              Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  Icon(
                    Icons.Folder,
                    null,
                    Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary,
                  )
                  Text(
                    stringResource(R.string.secure_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            else
              LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                items(entries, key = { it.id }) { entry ->
                  Row(
                    Modifier.fillMaxWidth()
                      .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                      .background(
                        if (entry.id in selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainer
                      )
                      .combinedClickable(
                        enabled = !busy,
                        onLongClick = { selected = selected + entry.id },
                        onClick = {
                          if (selected.isNotEmpty())
                            selected =
                              if (entry.id in selected) selected - entry.id else selected + entry.id
                          else
                            perform {
                              val file = vault.materialize(entry)
                              var launched = false
                              try {
                                withContext(Dispatchers.Main) {
                                  val token = SecurePlaybackRegistry.register(file)
                                  try {
                                    val uri =
                                      FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        file,
                                      )
                                    playback.launch(
                                      Intent(context, PlayerActivity::class.java)
                                        .setAction(Intent.ACTION_VIEW)
                                        .setDataAndType(uri, entry.mime)
                                        .putExtra("secure_session", token)
                                        .putExtra("secure_title", entry.name)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    )
                                    launched = true
                                  } catch (e: Exception) {
                                    SecurePlaybackRegistry.remove(token)
                                    throw e
                                  }
                                }
                              } finally {
                                if (!launched) file.delete()
                              }
                            }
                        },
                      )
                      .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                  ) {
                    if (selected.isNotEmpty())
                      Checkbox(entry.id in selected, onCheckedChange = null)
                    else
                      Icon(
                        Icons.Movie,
                        null,
                        Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary,
                      )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                      Text(
                        entry.name,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                      )
                      Text(
                        live.mehiz.mpvkt.ui.home.formatBytes(entry.bytes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                      if (!entry.sourceRemoved)
                        Text(
                          stringResource(R.string.secure_original_retained),
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                  }
                }
              }
            if (selected.isNotEmpty())
              Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                OutlinedButton(
                  {
                    pendingRestore = selected
                    external = true
                    restorePicker.launch(null)
                  },
                  enabled = !busy,
                  modifier = Modifier.weight(1f),
                ) {
                  Icon(Icons.Upload, null)
                  Spacer(Modifier.width(8.dp))
                  Text(stringResource(R.string.secure_restore))
                }
                OutlinedButton(
                  { accountAction = AccountAction.Delete },
                  enabled = !busy,
                  modifier = Modifier.weight(1f),
                ) {
                  Icon(Icons.Delete, null)
                  Spacer(Modifier.width(8.dp))
                  Text(stringResource(R.string.secure_delete))
                }
              }
          }
        VaultPage.Settings ->
          Column(
            Modifier.fillMaxSize()
              .padding(padding)
              .verticalScroll(rememberScrollState())
              .padding(vertical = 12.dp)
          ) {
            SettingsGroup {
              SettingsRow(
                { Text(stringResource(R.string.secure_change_pin)) },
                icon = { Icon(Icons.Lock, null) },
                enabled = !busy,
              ) {
                newPin = ""
                newConfirmation = ""
                accountAction = AccountAction.Pin
              }
              SettingsRow(
                { Text(stringResource(R.string.secure_question)) },
                {
                  Text(
                    if (hasRecovery) recoveryQuestion
                    else context.getString(R.string.secure_legacy_recovery)
                  )
                },
                { Icon(Icons.Info, null) },
                enabled = !busy,
              ) {
                newQuestion = recoveryQuestion
                newAnswer = ""
                accountAction = AccountAction.Recovery
              }
              SwitchPreference(
                deviceAccess,
                { enable ->
                  if (enable) {
                    enablingDevice = true
                    external = true
                    authenticate()
                  } else perform { vault.disableDeviceAccess() }
                },
                { Text(stringResource(R.string.secure_device_unlock)) },
                icon = { Icon(Icons.Touch, null) },
                enabled = !busy,
              )
              SwitchPreference(
                hidden,
                { SecureFolderOptions.hide(context, it) },
                { Text(stringResource(R.string.secure_hide)) },
                { Text(stringResource(R.string.secure_hide_hint)) },
                icon = { Icon(Icons.Eye, null) },
                enabled = !busy,
              )
            }
            Text(
              stringResource(R.string.secure_storage_hint),
              Modifier.padding(24.dp),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
      }
    }
    if (pendingMove.isNotEmpty())
      AppDialog(
        { pendingMove = emptyList() },
        title = { Text(stringResource(R.string.secure_move_files)) },
        icon = { Icon(Icons.FolderOpen, null) },
        text = { Text(stringResource(R.string.secure_move_confirm)) },
        confirmButton = {
          DialogButton({
            val uris = pendingMove
            pendingMove = emptyList()
            perform {
              var kept = 0
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
                val entry = vault.add(uri, name)
                if (!vault.moveOriginal(entry)) kept++
              }
              withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    if (kept > 0) R.string.secure_partial_move else R.string.secure_moved,
                    Toast.LENGTH_LONG,
                  )
                  .show()
              }
            }
          }) {
            Text(stringResource(R.string.secure_move_files))
          }
        },
        dismissButton = {
          DialogButton({ pendingMove = emptyList() }) {
            Text(stringResource(R.string.generic_cancel))
          }
        },
      )
    accountAction?.let { action ->
      AppDialog(
        { accountAction = null },
        title = {
          Text(
            stringResource(
              when (action) {
                AccountAction.Pin -> R.string.secure_change_pin
                AccountAction.Recovery -> R.string.secure_question
                AccountAction.Delete -> R.string.secure_delete
              }
            )
          )
        },
        icon = { Icon(if (action == AccountAction.Delete) Icons.Delete else Icons.Lock, null) },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (action) {
              AccountAction.Delete ->
                Text(stringResource(R.string.secure_delete_confirm, selected.size))
              AccountAction.Pin -> {
                PinLengthSelector(pinLength) {
                  pinLength = it
                  newPin = ""
                  newConfirmation = ""
                }
                PinField(
                  newPin,
                  { newPin = it },
                  pinLength,
                  reveal,
                  { reveal = !reveal },
                  stringResource(R.string.secure_new_pin),
                  false,
                  false,
                )
                PinField(
                  newConfirmation,
                  { newConfirmation = it },
                  pinLength,
                  reveal,
                  { reveal = !reveal },
                  stringResource(R.string.update_pin_confirm),
                  false,
                  false,
                )
              }
              AccountAction.Recovery -> {
                RoundedTextField(
                  newQuestion,
                  { newQuestion = it.take(200) },
                  label = { Text(stringResource(R.string.secure_question)) },
                  singleLine = true,
                )
                RoundedTextField(
                  newAnswer,
                  { newAnswer = it.take(256) },
                  label = { Text(stringResource(R.string.secure_answer)) },
                  singleLine = true,
                  visualTransformation = PasswordVisualTransformation(),
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
              }
            }
          }
        },
        confirmButton = {
          DialogButton(
            {
              accountAction = null
              perform {
                when (action) {
                  AccountAction.Delete -> {
                    val targets = entries.filter { it.id in selected }
                    targets.forEach { vault.delete(it) }
                    selected = emptySet()
                  }
                  AccountAction.Pin -> vault.changePin(newPin.toCharArray())
                  AccountAction.Recovery -> vault.setRecovery(newQuestion, newAnswer.toCharArray())
                }
                newPin = ""
                newConfirmation = ""
                newAnswer = ""
              }
            },
            enabled =
              when (action) {
                AccountAction.Delete -> selected.isNotEmpty()
                AccountAction.Pin -> newPin.length == pinLength && newPin == newConfirmation
                AccountAction.Recovery ->
                  newQuestion.trim().length >= 3 && newAnswer.trim().length >= 3
              },
          ) {
            Text(stringResource(R.string.subtitle_done))
          }
        },
        dismissButton = {
          DialogButton({
            accountAction = null
            pinLength = vault.pinLength
            newPin = ""
            newConfirmation = ""
            newAnswer = ""
          }) {
            Text(stringResource(R.string.generic_cancel))
          }
        },
      )
    }
  }
}

@Composable
private fun PinLengthSelector(value: Int, onChange: (Int) -> Unit) {
  Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    listOf(4, 6).forEach { length ->
      FilterChip(
        value == length,
        { onChange(length) },
        label = { Text(stringResource(R.string.secure_digits, length)) },
      )
    }
  }
}

@Composable
private fun PinField(
  value: String,
  onChange: (String) -> Unit,
  length: Int,
  reveal: Boolean,
  onReveal: () -> Unit,
  label: String,
  busy: Boolean,
  error: Boolean,
) {
  RoundedTextField(
    value,
    {
      onChange(
        it.mapNotNull { c -> c.digitToIntOrNull()?.digitToChar() }.joinToString("").take(length)
      )
    },
    singleLine = true,
    label = { Text(label) },
    enabled = !busy,
    isError = error,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    visualTransformation =
      if (reveal) androidx.compose.ui.text.input.VisualTransformation.None
      else PasswordVisualTransformation(),
    trailingIcon = {
      IconButton(onReveal) { Icon(Icons.Eye, stringResource(R.string.secure_reveal_pin)) }
    },
  )
}

private class SecureMediaPicker : ActivityResultContracts.OpenMultipleDocuments() {
  override fun createIntent(context: android.content.Context, input: Array<String>): Intent =
    super.createIntent(context, input)
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
}
