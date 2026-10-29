package live.mehiz.mpvkt.ui.preferences

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import kotlinx.serialization.Serializable
import live.mehiz.mpvkt.BuildConfig
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.presentation.components.AppDialog
import live.mehiz.mpvkt.presentation.components.DialogButton
import live.mehiz.mpvkt.presentation.crash.CrashActivity.Companion.collectDeviceInfo
import live.mehiz.mpvkt.ui.icons.*
import live.mehiz.mpvkt.ui.icons.ArrowBack
import live.mehiz.mpvkt.ui.icons.Code
import live.mehiz.mpvkt.ui.icons.Icons
import live.mehiz.mpvkt.ui.motion.ElasticSettingsScaffold as Scaffold
import live.mehiz.mpvkt.ui.motion.ElasticSettingsTopAppBar as TopAppBar
import live.mehiz.mpvkt.ui.utils.LocalBackStack
import live.mehiz.mpvkt.ui.utils.LocalNavigateBack

@Serializable
object AboutScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val backstack = LocalBackStack.current
    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(text = stringResource(id = R.string.pref_about_title)) },
          navigationIcon = {
            IconButton(onClick = LocalNavigateBack.current) {
              Icon(imageVector = Icons.AutoMirrored.ArrowBack, contentDescription = null)
            }
          },
        )
      }
    ) { paddingValues ->
      Column(
        Modifier.padding(paddingValues)
          .verticalScroll(rememberScrollState())
          .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        SettingsCard(Modifier.padding(horizontal = 20.dp)) {
          Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Image(painterResource(R.drawable.mpv_launcher_foreground), null, Modifier.size(88.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
            Text(
              stringResource(R.string.update_about_tagline),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.material3.Surface(
              shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
              color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
              Text(
                "v${BuildConfig.VERSION_NAME}",
                Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
              )
            }
          }
        }
        SettingsGroup {
          SettingsRow(
            { Text(stringResource(R.string.pref_about_app_version)) },
            { Text("${BuildConfig.VERSION_NAME} · ${BuildConfig.BUILD_TYPE}") },
            { Icon(Icons.Info, null) },
          ) {
            clipboard.setText(AnnotatedString(collectDeviceInfo()))
          }
          SettingsRow(
            { Text(stringResource(R.string.pref_about_oss_libraries)) },
            icon = { Icon(Icons.Code, null) },
          ) {
            backstack.add(LibrariesScreen)
          }
          SettingsRow(
            { Text(stringResource(R.string.pref_about_privacy_policy)) },
            icon = { Icon(Icons.Lock, null) },
          ) {
            context.startActivity(
              Intent(Intent.ACTION_VIEW, context.getString(R.string.privacy_policy_url).toUri())
            )
          }
          SettingsRow({ Text("GitHub") }, icon = { Icon(Icons.Link, null) }) {
            context.startActivity(
              Intent(Intent.ACTION_VIEW, context.getString(R.string.github_repo_url).toUri())
            )
          }
        }
        SettingsCard(Modifier.padding(horizontal = 20.dp)) {
          Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              stringResource(R.string.update_about_credits),
              style = MaterialTheme.typography.titleSmall,
            )
            Text(
              stringResource(R.string.mpv_icons_credit),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              stringResource(R.string.mpv_folder_credit),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@Serializable
object LibrariesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    var selectedLibrary by remember {
      mutableStateOf<com.mikepenz.aboutlibraries.entity.Library?>(null)
    }
    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(text = stringResource(R.string.pref_about_oss_libraries)) },
          navigationIcon = {
            IconButton(onClick = LocalNavigateBack.current) {
              Icon(imageVector = Icons.AutoMirrored.ArrowBack, contentDescription = null)
            }
          },
        )
      }
    ) { paddingValues ->
      LibrariesContainer(
        modifier = Modifier.padding(paddingValues),
        onLibraryClick = { selectedLibrary = it },
      )
    }
    selectedLibrary?.let { library -> LibraryLicenseDialog(library) { selectedLibrary = null } }
  }
}

@Composable
internal fun LibraryLicenseDialog(
  library: com.mikepenz.aboutlibraries.entity.Library,
  dismiss: () -> Unit,
) {
  val uriHandler = LocalUriHandler.current
  AppDialog(
    onDismissRequest = dismiss,
    title = { Text(library.name) },
    icon = { Icon(Icons.Code, null) },
    confirmButton = { DialogButton(dismiss) { Text(stringResource(R.string.subtitle_done)) } },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        library.description?.takeIf(String::isNotBlank)?.let { Text(it) }
        library.licenses.forEach { license ->
          Text(license.name, style = MaterialTheme.typography.titleSmall)
          license.licenseContent?.takeIf(String::isNotBlank)?.let { content ->
            SelectionContainer { Text(content) }
          }
          license.url
            ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            ?.let { url -> TextButton({ uriHandler.openUri(url) }) { Text(url) } }
        }
      }
    },
  )
}
