package live.mehiz.mpvkt.ui.preferences

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.SettingsBackup
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.presentation.components.*
import live.mehiz.mpvkt.ui.icons.*
import live.mehiz.mpvkt.ui.motion.*
import live.mehiz.mpvkt.ui.utils.LocalNavigateBack

@Serializable
object BackupRestoreScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Uri?>(null) }
    fun perform(action: suspend () -> Unit) {
      if (busy) return
      busy = true
      scope.launch {
        try {
          withContext(Dispatchers.IO) { action() }
          Toast.makeText(context, R.string.update_operation_done, Toast.LENGTH_SHORT).show()
        } catch (c: CancellationException) {
          throw c
        } catch (_: Exception) {
          Toast.makeText(context, R.string.update_operation_failed, Toast.LENGTH_LONG).show()
        } finally {
          busy = false
        }
      }
    }
    val export =
      rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/xml")) { uri ->
        if (uri != null) perform { SettingsBackup.export(context, uri) }
      }
    val restore =
      rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pending = uri
      }
    ElasticSettingsScaffold(
      topBar = {
        ElasticSettingsTopAppBar(
          title = { Text(stringResource(R.string.update_backup)) },
          navigationIcon = {
            IconButton(LocalNavigateBack.current) { Icon(Icons.AutoMirrored.ArrowBack, null) }
          },
        )
      }
    ) { padding ->
      Column(Modifier.fillMaxSize().padding(padding)) {
        SettingsGroup {
          SettingsRow(
            { Text(stringResource(R.string.update_export)) },
            { Text(stringResource(R.string.update_export_summary)) },
            { Icon(Icons.Upload, null) },
            enabled = !busy,
          ) {
            export.launch("MPV-settings.xml")
          }
          SettingsRow(
            { Text(stringResource(R.string.update_import)) },
            { Text(stringResource(R.string.update_import_summary)) },
            { Icon(Icons.Download, null) },
            enabled = !busy,
          ) {
            restore.launch(arrayOf("text/xml", "application/xml", "application/octet-stream"))
          }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(
          stringResource(R.string.update_backup_detail),
          Modifier.padding(24.dp),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    pending?.let { uri ->
      AppDialog(
        onDismissRequest = { pending = null },
        title = { Text(stringResource(R.string.update_import)) },
        icon = { Icon(Icons.Download, null) },
        text = { Text(stringResource(R.string.update_import_confirm)) },
        confirmButton = {
          DialogButton({
            pending = null
            perform { SettingsBackup.restore(context, uri) }
          }) {
            Text(stringResource(R.string.update_import))
          }
        },
        dismissButton = {
          DialogButton({ pending = null }) { Text(stringResource(R.string.generic_cancel)) }
        },
      )
    }
  }
}
