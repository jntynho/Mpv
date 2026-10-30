package live.mehiz.mpvkt.ui.player.controls.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.subtitles.SubtitleConfig

internal val LocalPanelOpacity = staticCompositionLocalOf { 1f }
internal val LocalPanelMediaKey = staticCompositionLocalOf { 0L }

@Composable
internal fun propertyEditable(vararg names: String): Boolean {
  val owned by SubtitleConfig.ownedProperties.collectAsState()
  return names.all { SubtitleConfig.canEdit(it, owned) }
}

@Composable
internal fun PanelConfigNotice(properties: Set<String>? = null) {
  val owned by SubtitleConfig.ownedProperties.collectAsState()
  if (
    owned.any {
      if (properties != null)
        properties.any { property ->
          SubtitleConfig.canonical(property) == SubtitleConfig.canonical(it)
        }
      else it.startsWith("sub-") || it.startsWith("secondary-sub-")
    }
  )
    Text(
      stringResource(R.string.panel_config_owned),
      Modifier.padding(16.dp),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
