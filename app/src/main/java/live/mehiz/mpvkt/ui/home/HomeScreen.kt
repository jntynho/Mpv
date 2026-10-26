// Modified for Luma Player: interface, appearance, or build configuration.
package live.mehiz.mpvkt.ui.home

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.github.k1rakishou.fsaf.FileManager
import `is`.xyz.mpv.Utils.PROTOCOLS
import kotlinx.serialization.Serializable
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.AppearancePreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.ui.player.PlayerActivity
import live.mehiz.mpvkt.ui.preferences.PreferencesScreen
import live.mehiz.mpvkt.ui.utils.LocalBackStack
import org.koin.compose.koinInject

@Serializable
object HomeScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val preferences = koinInject<AppearancePreferences>()
    val compact by preferences.compactHome.collectAsState()
    val tips by preferences.showTips.collectAsState()
    var url by rememberSaveable { mutableStateOf("") }
    val valid = MediaUrl.isValid(url.trim(), PROTOCOLS.toSet())
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      uri?.let { playFile(it.toString(), context) }
    }
    val fileManager = remember(context) { FileManager(context) }
    val directoryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
      if (uri != null) {
        val directory = runCatching { fileManager.fromUri(uri) }.getOrNull()
        if (directory != null) {
          backstack.add(FilePickerScreen(directory.getFullPath()))
        } else {
          Toast.makeText(context, R.string.luma_folder_error, Toast.LENGTH_LONG).show()
        }
      }
    }
    val colors = MaterialTheme.colorScheme
    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Column {
              Text("LUMA", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
              Text(stringResource(R.string.luma_tagline), style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant)
            }
          },
          actions = {
            IconButton(onClick = { backstack.add(PreferencesScreen) }) {
              Icon(Icons.Default.Settings, stringResource(R.string.luma_settings))
            }
          },
        )
      },
    ) { padding ->
      Column(
        modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 24.dp),
      ) {
        Text(stringResource(R.string.luma_headline), style = MaterialTheme.typography.headlineLarge,
          fontWeight = FontWeight.SemiBold)
        Card(
          onClick = { documentPicker.launch(arrayOf("video/*", "audio/*")) },
          shape = RoundedCornerShape(32.dp),
          colors = CardDefaults.cardColors(containerColor = colors.primaryContainer),
        ) {
          Column(
            Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(colors.primaryContainer,
              colors.secondaryContainer))).padding(if (compact) 24.dp else 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            Icon(Icons.Default.PlayCircle, null, Modifier.size(if (compact) 48.dp else 72.dp), tint = colors.primary)
            Text(stringResource(R.string.luma_open), style = MaterialTheme.typography.headlineMedium,
              color = colors.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
            if (!compact) Text(stringResource(R.string.luma_open_hint), color = colors.onPrimaryContainer)
          }
        }
        ActionCard(Icons.Default.FolderOpen, stringResource(R.string.luma_files),
          stringResource(R.string.luma_files_hint)) { directoryPicker.launch(null) }
        Surface(shape = RoundedCornerShape(28.dp), color = colors.surfaceContainer) {
          Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
              Icon(Icons.Default.Link, null, tint = colors.primary)
              Text(stringResource(R.string.luma_stream), style = MaterialTheme.typography.titleMedium)
            }
            OutlinedTextField(
              value = url, onValueChange = { url = it }, modifier = Modifier.fillMaxWidth(),
              label = { Text(stringResource(R.string.home_url_input_label)) },
              placeholder = { Text(stringResource(R.string.luma_url_hint)) },
              singleLine = true, shape = RoundedCornerShape(16.dp),
              isError = url.isNotBlank() && !valid,
              supportingText = {
                if (url.isNotBlank() && !valid) Text(stringResource(R.string.home_invalid_protocol))
              },
            )
            Button(onClick = { playFile(url.trim(), context) }, enabled = valid, modifier = Modifier.fillMaxWidth()) {
              Text(stringResource(R.string.home_open_url))
            }
          }
        }
        if (tips && !compact) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.luma_ready), style = MaterialTheme.typography.titleMedium, color = colors.primary)
            Text(stringResource(R.string.luma_tip_body), style = MaterialTheme.typography.bodyMedium,
              color = colors.onSurfaceVariant)
          }
        }
      }
    }
  }

  @Composable
  private fun ActionCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
      Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(title, style = MaterialTheme.typography.titleMedium)
          Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
  }

  fun playFile(filepath: String, context: Context) {
    context.startActivity(Intent(Intent.ACTION_VIEW, filepath.toUri()).setClass(context, PlayerActivity::class.java))
  }
}
