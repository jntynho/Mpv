package live.mehiz.mpvkt.ui.preferences

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.presentation.components.*
import live.mehiz.mpvkt.ui.icons.*
import live.mehiz.mpvkt.ui.motion.LocalReduceMotion

internal val LocalSettingsIcon =
  compositionLocalOf<@Composable () -> Unit> { { Icon(Icons.Tune, null) } }

/** One height animation owner, reversible from the current frame, and no width/corner animation. */
@Composable
internal fun ExpandableSettingsContent(
  expanded: Boolean,
  content: @Composable ColumnScope.() -> Unit,
) {
  val duration = if (LocalReduceMotion.current) 0 else 280
  AnimatedVisibility(
    expanded,
    enter = expandVertically(tween(duration), expandFrom = Alignment.Top) + fadeIn(tween(duration)),
    exit =
      shrinkVertically(tween(duration), shrinkTowards = Alignment.Top) + fadeOut(tween(duration)),
  ) {
    Column(content = content)
  }
}

@Composable
internal fun SettingsCard(
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(
    modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(24.dp))
      .background(MaterialTheme.colorScheme.surfaceContainer),
    content = content,
  )
}

private val LocalInsideSettingsCard = compositionLocalOf { false }

@Composable
internal fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
  SettingsCard(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
    CompositionLocalProvider(LocalInsideSettingsCard provides true) { content() }
  }
}

@Composable
private fun PreferenceCard(content: @Composable ColumnScope.() -> Unit) {
  if (LocalInsideSettingsCard.current) Column(content = content) else SettingsGroup(content)
}

@Composable
internal fun SettingsRow(
  title: @Composable () -> Unit,
  summary: (@Composable () -> Unit)? = null,
  icon: (@Composable () -> Unit)? = null,
  enabled: Boolean = true,
  toggle: Boolean? = null,
  expanded: Boolean? = null,
  trailing: (@Composable () -> Unit)? = null,
  onClick: () -> Unit,
) {
  val action =
    if (toggle != null) Modifier.toggleable(toggle, enabled, Role.Switch) { onClick() }
    else Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
  val rotation by
    animateFloatAsState(
      if (expanded == true) 180f else 0f,
      tween(if (LocalReduceMotion.current) 0 else 280),
      label = "settings_chevron",
    )
  Row(
    Modifier.fillMaxWidth().then(action).padding(16.dp).heightIn(min = 48.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    CompositionLocalProvider(
      LocalContentColor provides
        MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else .38f)
    ) {
      Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        if (icon != null) icon() else LocalSettingsIcon.current()
      }
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      CompositionLocalProvider(
        LocalContentColor provides
          MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .38f)
      ) {
        ProvideTextStyle(MaterialTheme.typography.bodyLarge, title)
      }
      if (summary != null)
        CompositionLocalProvider(
          LocalContentColor provides
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else .38f)
        ) {
          ProvideTextStyle(MaterialTheme.typography.bodySmall, summary)
        }
    }
    if (trailing != null) trailing()
    else if (toggle != null) Switch(toggle, onCheckedChange = null, enabled = enabled)
    else if (expanded != null)
      Icon(Icons.ArrowDropDown, null, Modifier.size(18.dp).graphicsLayer { rotationZ = rotation })
    else {
      val rtl =
        androidx.compose.ui.platform.LocalLayoutDirection.current ==
          androidx.compose.ui.unit.LayoutDirection.Rtl
      Icon(
        Icons.ArrowDropDown,
        null,
        Modifier.size(18.dp).graphicsLayer { rotationZ = if (rtl) 90f else -90f },
      )
    }
  }
}

@Composable
internal fun Preference(
  title: @Composable () -> Unit,
  summary: (@Composable () -> Unit)? = null,
  icon: (@Composable () -> Unit)? = null,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  PreferenceCard { SettingsRow(title, summary, icon, enabled, onClick = onClick) }
}

@Composable
internal fun SwitchPreference(
  value: Boolean,
  onValueChange: (Boolean) -> Unit,
  title: @Composable () -> Unit,
  summary: (@Composable () -> Unit)? = null,
  enabled: Boolean = true,
  icon: (@Composable () -> Unit)? = null,
) {
  PreferenceCard {
    SettingsRow(title, summary, icon ?: LocalSettingsIcon.current, enabled, toggle = value) {
      onValueChange(!value)
    }
  }
}

@Composable
internal fun <T> ListPreference(
  value: T,
  onValueChange: (T) -> Unit,
  values: List<T>,
  title: @Composable () -> Unit,
  summary: (@Composable () -> Unit)? = null,
  icon: (@Composable () -> Unit)? = null,
  valueToText: (T) -> AnnotatedString = { AnnotatedString(it.toString()) },
) {
  var showing by rememberSaveable { mutableStateOf(false) }
  Preference(title, summary, icon) { showing = true }
  if (showing)
    AppDialog(
      onDismissRequest = { showing = false },
      title = title,
      icon = icon,
      confirmButton = {
        DialogButton({ showing = false }) { Text(stringResource(R.string.subtitle_done)) }
      },
      text = {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          values.forEach { choice ->
            val selected = choice == value
            Row(
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                  if (selected) MaterialTheme.colorScheme.primaryContainer
                  else
                    androidx.compose.ui.graphics.lerp(
                      MaterialTheme.colorScheme.surfaceContainer,
                      MaterialTheme.colorScheme.onSurface,
                      .025f,
                    )
                )
                .selectable(selected, role = Role.RadioButton) {
                  onValueChange(choice)
                  showing = false
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                valueToText(choice),
                style = MaterialTheme.typography.bodyLarge,
                color =
                  if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                  else MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      },
    )
}

@Composable
internal fun <T> TextFieldPreference(
  value: T,
  onValueChange: (T) -> Unit,
  title: @Composable () -> Unit,
  summary: (@Composable () -> Unit)? = null,
  textToValue: (String) -> T,
  icon: (@Composable () -> Unit)? = null,
  textField: @Composable (String, (String) -> Unit, () -> Unit) -> Unit,
) {
  var showing by rememberSaveable { mutableStateOf(false) }
  var draft by rememberSaveable { mutableStateOf("") }
  var failure by remember { mutableStateOf(false) }
  Preference(title, summary, icon ?: { Icon(Icons.Edit, null) }) {
    draft = value.toString()
    failure = false
    showing = true
  }
  var saving by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val save: () -> Unit = {
    if (!saving) {
      saving = true
      scope.launch {
        try {
          val result = withContext(Dispatchers.IO) { textToValue(draft) }
          onValueChange(result)
          showing = false
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (_: Exception) {
          failure = true
        } finally {
          saving = false
        }
      }
    }
  }
  if (showing)
    AppDialog(
      onDismissRequest = { if (!saving) showing = false },
      title = title,
      text = {
        Column {
          textField(
            draft,
            {
              draft = it
              failure = false
            },
            save,
          )
          if (failure)
            Text(
              stringResource(R.string.mpv_setting_save_failed),
              color = MaterialTheme.colorScheme.error,
            )
        }
      },
      confirmButton = {
        DialogButton(save, enabled = !saving) { Text(stringResource(R.string.subtitle_save)) }
      },
      dismissButton = {
        DialogButton({ showing = false }, enabled = !saving) {
          Text(stringResource(R.string.generic_cancel))
        }
      },
    )
}

@Composable
internal fun SliderPreference(
  onValueChange: (Float) -> Unit,
  title: @Composable () -> Unit,
  valueRange: ClosedFloatingPointRange<Float>,
  summary: (@Composable () -> Unit)? = null,
  onSliderValueChange: (Float) -> Unit,
  sliderValue: Float,
  icon: (@Composable () -> Unit)? = null,
) {
  var expanded by rememberSaveable { mutableStateOf(false) }
  var draft by remember { mutableFloatStateOf(sliderValue) }
  LaunchedEffect(sliderValue) { draft = sliderValue }
  PreferenceCard {
    SettingsRow(title, summary, icon = icon, expanded = expanded) { expanded = !expanded }
    ExpandableSettingsContent(expanded) {
      SlimPlayerSlider(
        draft.coerceIn(valueRange.start, valueRange.endInclusive),
        {
          draft = it
          onSliderValueChange(it)
        },
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        valueRange = valueRange,
        onValueChangeFinished = { onValueChange(draft) },
      )
    }
  }
}

@Composable
internal fun PreferenceCategory(title: @Composable () -> Unit) {
  Box(Modifier.padding(start = 32.dp, end = 20.dp, top = 22.dp, bottom = 12.dp)) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
      ProvideTextStyle(MaterialTheme.typography.labelLarge, title)
    }
  }
}

@Composable
internal fun TwoTargetIconButtonPreference(
  title: @Composable () -> Unit,
  summary: (@Composable () -> Unit)? = null,
  onClick: () -> Unit,
  iconButtonIcon: @Composable () -> Unit,
  onIconButtonClick: () -> Unit,
  iconButtonEnabled: Boolean = true,
) {
  PreferenceCard {
    SettingsRow(
      title,
      summary,
      { Icon(Icons.Folder, null) },
      trailing = {
        IconButton(onIconButtonClick, enabled = iconButtonEnabled) { iconButtonIcon() }
      },
      onClick = onClick,
    )
  }
}

internal fun androidx.compose.foundation.lazy.LazyListScope.preference(
  key: String,
  title: @Composable () -> Unit,
  summary: (@Composable () -> Unit)? = null,
  icon: (@Composable () -> Unit)? = null,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  item(key) { SettingsCard(modifier) { SettingsRow(title, summary, icon, onClick = onClick) } }
}
