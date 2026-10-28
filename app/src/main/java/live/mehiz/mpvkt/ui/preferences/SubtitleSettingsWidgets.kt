package live.mehiz.mpvkt.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.PlayerPreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.presentation.components.RoundedTextField as OutlinedTextField
import live.mehiz.mpvkt.presentation.components.StableBottomSheet
import live.mehiz.mpvkt.ui.icons.*
import org.koin.compose.koinInject

@Composable
internal fun SubtitleSettingRow(
  title: String,
  summary: String,
  icon: ImageVector,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  danger: Boolean = false,
  toggle: Boolean? = null,
  expanded: Boolean? = null,
  onClick: () -> Unit,
) {
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val rotation by
    androidx.compose.animation.core.animateFloatAsState(
      if (expanded == true) 180f else 0f,
      androidx.compose.animation.core.tween(
        if (live.mehiz.mpvkt.ui.motion.LocalReduceMotion.current) 0 else 280
      ),
      label = "subtitle_expand",
    )
  val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
  val action =
    if (toggle != null)
      Modifier.toggleable(
        toggle,
        enabled = enabled,
        role = Role.Switch,
        onValueChange = { onClick() },
      )
    else Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
  Row(
    modifier
      .fillMaxWidth()
      .then(action)
      .padding(horizontal = 16.dp, vertical = 16.dp)
      .heightIn(min = 48.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Icon(
      icon,
      null,
      Modifier.size(24.dp),
      tint =
        (if (danger) color else MaterialTheme.colorScheme.primary).copy(
          alpha = if (enabled) 1f else .38f
        ),
    )
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
        title,
        style = MaterialTheme.typography.bodyLarge,
        color = color.copy(alpha = if (enabled) 1f else .38f),
      )
      if (summary.isNotBlank())
        Text(
          summary,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else .6f),
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
    }
    if (toggle != null) Switch(toggle, onCheckedChange = null, enabled = enabled)
    else if (expanded != null)
      Icon(Icons.ArrowDropDown, null, Modifier.size(18.dp).graphicsLayer { rotationZ = rotation })
    else if (!danger)
      Icon(
        Icons.ArrowDropDown,
        null,
        Modifier.size(18.dp).graphicsLayer { rotationZ = if (rtl) 90f else -90f },
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
  }
}

@Composable
internal fun SubtitleSettingsCard(content: @Composable ColumnScope.() -> Unit) {
  SettingsCard(content = content)
}

@Composable
internal fun SubtitleSettingsDivider() {
  HorizontalDivider(
    Modifier.padding(start = 54.dp, end = 16.dp),
    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f),
  )
}

internal data class SubtitleChoice(
  val title: String,
  val values: Map<String, String>,
  val selected: Set<String>,
  val multiple: Boolean,
  val additiveAll: Set<String> = emptySet(),
  val apply: (Set<String>) -> Unit,
)

internal fun toggleSubtitleChoice(
  selected: Set<String>,
  key: String,
  additive: Set<String>,
): Set<String> =
  when {
    key in selected && selected.size == 1 -> selected
    key == "all" -> setOf("all") + selected.intersect(additive)
    key in selected -> selected - key
    key in additive -> selected + key
    else -> (selected - "all") + key
  }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubtitleChoiceSheet(choice: SubtitleChoice, dismiss: () -> Unit) {
  var closing by remember(choice) { mutableStateOf(false) }
  val close = { closing = true }
  val reduceMotion by koinInject<PlayerPreferences>().reduceMotion.collectAsState()
  var selection by remember(choice) { mutableStateOf(choice.selected) }
  var query by remember { mutableStateOf("") }
  val filtered =
    remember(query, choice.values) {
      choice.values.entries.filter {
        it.value.contains(query, true) || it.key.contains(query, true)
      }
    }
  StableBottomSheet(
    onDismissRequest = dismiss,
    dismissRequested = closing,
    reduceMotion = reduceMotion,
    title = choice.title,
  ) {
    Text(
      choice.title,
      Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
      style = MaterialTheme.typography.titleLarge,
    )
    if (choice.values.size > 8)
      OutlinedTextField(
        query,
        { query = it },
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        label = { Text(stringResource(R.string.subtitle_choice_search)) },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        leadingIcon = { Icon(Icons.Search, null) },
      )
    LazyColumn(
      Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 420.dp),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      items(filtered, key = { it.key }) { entry ->
        val checked = entry.key in selection
        Row(
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
              if (checked) MaterialTheme.colorScheme.primaryContainer
              else MaterialTheme.colorScheme.surfaceContainer
            )
            .semantics { selected = checked }
            .clickable(enabled = !closing) {
              if (choice.multiple)
                selection = toggleSubtitleChoice(selection, entry.key, choice.additiveAll)
              else {
                choice.apply(setOf(entry.key))
                close()
              }
            }
            .padding(18.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            entry.value,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
          )
          if (choice.multiple) RadioButton(checked, onClick = null)
        }
      }
    }
    if (choice.multiple)
      Button(
        onClick = {
          choice.apply(selection)
          close()
        },
        Modifier.fillMaxWidth().padding(20.dp),
        enabled = selection.isNotEmpty() && !closing,
        shape = RoundedCornerShape(18.dp),
      ) {
        Text(stringResource(R.string.subtitle_done))
      }
  }
}
