package live.mehiz.mpvkt.ui.player.controls.components.panels

import android.annotation.SuppressLint
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.github.k1rakishou.fsaf.FileManager
import com.yubyf.truetypeparser.TTFFile
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.SubtitleJustification
import live.mehiz.mpvkt.preferences.SubtitlesPreferences
import live.mehiz.mpvkt.preferences.preference.deleteAndGet
import live.mehiz.mpvkt.presentation.components.ExpandableCard
import live.mehiz.mpvkt.presentation.components.ExposedTextDropDownMenu
import live.mehiz.mpvkt.presentation.components.SliderItem
import live.mehiz.mpvkt.ui.icons.BorderColor
import live.mehiz.mpvkt.ui.icons.BorderStyle
import live.mehiz.mpvkt.ui.icons.FormatBold
import live.mehiz.mpvkt.ui.icons.FormatClear
import live.mehiz.mpvkt.ui.icons.FormatColorText
import live.mehiz.mpvkt.ui.icons.FormatItalic
import live.mehiz.mpvkt.ui.icons.FormatSize
import live.mehiz.mpvkt.ui.icons.Icons
import live.mehiz.mpvkt.ui.icons.Shadow
import live.mehiz.mpvkt.ui.player.MpvProperties
import live.mehiz.mpvkt.ui.player.PanelProperties
import live.mehiz.mpvkt.ui.player.controls.CARDS_MAX_WIDTH
import live.mehiz.mpvkt.ui.player.controls.panelCardsColors
import live.mehiz.mpvkt.ui.theme.spacing
import org.koin.compose.koinInject

@OptIn(ExperimentalLayoutApi::class)
@SuppressLint("MutableCollectionMutableState")
@Composable
fun SubtitleSettingsTypographyCard(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val preferences = koinInject<SubtitlesPreferences>()
  val fileManager = koinInject<FileManager>()
  var isExpanded by rememberSaveable { mutableStateOf(true) }
  var fonts by remember {
    mutableStateOf(listOf(preferences.font.defaultValue(), preferences.font.get()).distinct())
  }
  var fontsLoadingIndicator: (@Composable () -> Unit)? by remember {
    val indicator: (@Composable () -> Unit) = { CircularProgressIndicator(Modifier.size(32.dp)) }
    mutableStateOf(indicator)
  }
  LaunchedEffect(Unit) {
    if (!preferences.fontsFolder.isSet()) {
      fontsLoadingIndicator = null
      return@LaunchedEffect
    }
    try {
      val loaded =
        withContext(Dispatchers.IO) {
          val folder =
            fileManager.fromUri(preferences.fontsFolder.get().toUri())
              ?: return@withContext emptyList<String>()
          fileManager
            .listFiles(folder)
            .filter {
              fileManager.isFile(it) &&
                fileManager.getName(it).lowercase().matches(".*\\.[ot]tf$".toRegex())
            }
            .mapNotNull { file ->
              kotlinx.coroutines.currentCoroutineContext().ensureActive()
              runCatching {
                  fileManager.getInputStream(file)?.use {
                    TTFFile.open(it).families.values.firstOrNull()
                  }
                }
                .getOrNull()
            }
            .distinct()
        }
      fonts = (listOf(preferences.font.defaultValue(), preferences.font.get()) + loaded).distinct()
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      // A revoked document grant must not crash or close the player's panel.
    } finally {
      fontsLoadingIndicator = null
    }
  }

  ExpandableCard(
    isExpanded = isExpanded,
    onExpand = { isExpanded = !isExpanded },
    title = {
      Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
        Icon(Icons.FormatColorText, null)
        Text(stringResource(R.string.player_sheets_sub_typography_card_title))
      }
    },
    modifier = modifier.widthIn(max = CARDS_MAX_WIDTH),
    colors = panelCardsColors(),
  ) {
    Column {
      val isBold by MpvProperties.propBoolean["sub-bold"].collectAsState()
      val isItalic by MpvProperties.propBoolean["sub-italic"].collectAsState()
      val mpvJustify by MpvProperties.propString["sub-justify"].collectAsState()
      val justify by remember {
        derivedStateOf {
          SubtitleJustification.entries.firstOrNull { it.value == mpvJustify }
            ?: preferences.justification.get()
        }
      }
      val font by MpvProperties.propString["sub-font"].collectAsState()
      val fontSize by MpvProperties.propInt["sub-font-size"].collectAsState()
      val mpvBorderStyle by MpvProperties.propString["sub-border-style"].collectAsState()
      val borderStyle by remember {
        derivedStateOf {
          SubtitlesBorderStyle.entries.firstOrNull { it.value == mpvBorderStyle }
            ?: preferences.borderStyle.get()
        }
      }
      val borderSize by MpvProperties.propInt["sub-outline-size"].collectAsState()
      val shadowOffset by MpvProperties.propInt["sub-shadow-offset"].collectAsState()
      FlowRow(
        Modifier.fillMaxWidth()
          .padding(start = MaterialTheme.spacing.extraSmall, end = MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.Center,
      ) {
        IconToggleButton(
          checked = isBold ?: preferences.bold.get(),
          enabled = live.mehiz.mpvkt.ui.player.controls.components.propertyEditable("sub-bold"),
          onCheckedChange = {
            preferences.bold.set(it)
            PanelProperties.setBoolean("sub-bold", it)
          },
        ) {
          Icon(
            Icons.FormatBold,
            stringResource(R.string.panel_bold),
            modifier = Modifier.size(24.dp),
          )
        }
        IconToggleButton(
          checked = isItalic ?: preferences.italic.get(),
          enabled = live.mehiz.mpvkt.ui.player.controls.components.propertyEditable("sub-italic"),
          onCheckedChange = {
            preferences.italic.set(it)
            PanelProperties.setBoolean("sub-italic", it)
          },
        ) {
          Icon(
            Icons.FormatItalic,
            stringResource(R.string.panel_italic),
            modifier = Modifier.size(24.dp),
          )
        }
        SubtitleJustification.entries.minus(SubtitleJustification.Auto).forEach { justification ->
          IconToggleButton(
            checked = justify == justification,
            enabled =
              live.mehiz.mpvkt.ui.player.controls.components.propertyEditable(
                "sub-ass-justify",
                "sub-justify",
              ),
            onCheckedChange = {
              PanelProperties.setBoolean("sub-ass-justify", it)
              if (it) {
                preferences.justification.set(justification)
                PanelProperties.setString("sub-justify", justification.value)
              } else {
                preferences.justification.set(SubtitleJustification.Auto)
                PanelProperties.setString("sub-justify", SubtitleJustification.Auto.value)
              }
            },
          ) {
            Icon(
              justification.icon,
              stringResource(
                R.string.panel_alignment,
                stringResource(
                  when (justification) {
                    SubtitleJustification.Left -> R.string.panel_align_left
                    SubtitleJustification.Center -> R.string.panel_align_center
                    SubtitleJustification.Right -> R.string.panel_align_right
                    SubtitleJustification.Auto -> R.string.panel_align_auto
                  }
                ),
              ),
            )
          }
        }
        TextButton(onClick = { resetTypography(preferences) }) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.FormatClear, null)
            Text(stringResource(R.string.generic_reset))
          }
        }
      }
      Row(
        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(Icons.FormatColorText, null, modifier = Modifier.size(24.dp))
        ExposedTextDropDownMenu(
          selectedValue = font ?: preferences.font.get(),
          enabled = live.mehiz.mpvkt.ui.player.controls.components.propertyEditable("sub-font"),
          options = fonts.toImmutableList(),
          label = stringResource(R.string.player_sheets_sub_typography_font),
          onValueChangedEvent = {
            preferences.font.set(it)
            PanelProperties.setString("sub-font", it)
          },
          leadingIcon = fontsLoadingIndicator,
        )
      }
      SliderItem(
        label = stringResource(R.string.player_sheets_sub_typography_font_size),
        max = 100,
        min = 1,
        property = "sub-font-size",
        value = fontSize ?: preferences.fontSize.get(),
        valueText = (fontSize ?: preferences.fontSize.get()).toString(),
        onChange = {
          preferences.fontSize.set(it)
          PanelProperties.setInt("sub-font-size", it)
        },
      ) {
        Icon(Icons.FormatSize, null)
      }
      Row(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Icon(Icons.BorderStyle, null, Modifier.size(24.dp))
        ExposedTextDropDownMenu(
          selectedValue = context.getString(borderStyle.titleRes),
          enabled =
            live.mehiz.mpvkt.ui.player.controls.components.propertyEditable("sub-border-style"),
          options =
            SubtitlesBorderStyle.entries.map { context.getString(it.titleRes) }.toImmutableList(),
          label = stringResource(R.string.player_sheets_subtitles_border_style),
          onValueChangedEvent = { label ->
            SubtitlesBorderStyle.entries
              .firstOrNull { context.getString(it.titleRes) == label }
              ?.let { selected ->
                preferences.borderStyle.set(selected)
                PanelProperties.setString("sub-border-style", selected.value)
              }
          },
          modifier = Modifier.weight(1f),
        )
      }
      SliderItem(
        stringResource(R.string.player_sheets_sub_typography_border_size),
        property = "sub-outline-size",
        value = borderSize ?: preferences.borderSize.get(),
        valueText = (borderSize ?: preferences.borderSize.get()).toString(),
        onChange = {
          preferences.borderSize.set(it)
          PanelProperties.setInt("sub-outline-size", it)
        },
        max = 100,
        icon = { Icon(Icons.BorderColor, null) },
      )
      SliderItem(
        stringResource(R.string.player_sheets_subtitles_shadow_offset),
        property = "sub-shadow-offset",
        value = shadowOffset ?: preferences.shadowOffset.get(),
        valueText = (shadowOffset ?: preferences.shadowOffset.get()).toString(),
        onChange = {
          preferences.shadowOffset.set(it)
          PanelProperties.setInt("sub-shadow-offset", it)
        },
        max = 100,
        icon = { Icon(Icons.Shadow, null) },
      )
    }
  }
}

fun resetTypography(preferences: SubtitlesPreferences) {
  if (PanelProperties.editable("sub-bold"))
    PanelProperties.setBoolean("sub-bold", preferences.bold.deleteAndGet())
  if (PanelProperties.editable("sub-italic"))
    PanelProperties.setBoolean("sub-italic", preferences.italic.deleteAndGet())
  if (PanelProperties.editable("sub-ass-justify"))
    PanelProperties.setBoolean("sub-ass-justify", false)
  if (PanelProperties.editable("sub-justify"))
    PanelProperties.setString("sub-justify", preferences.justification.deleteAndGet().value)
  if (PanelProperties.editable("sub-font"))
    PanelProperties.setString("sub-font", preferences.font.deleteAndGet())
  if (PanelProperties.editable("sub-font-size"))
    PanelProperties.setInt("sub-font-size", preferences.fontSize.deleteAndGet())
  if (PanelProperties.editable("sub-border-style"))
    PanelProperties.setString("sub-border-style", preferences.borderStyle.deleteAndGet().value)
  if (PanelProperties.editable("sub-outline-size"))
    PanelProperties.setInt("sub-outline-size", preferences.borderSize.deleteAndGet())
  if (PanelProperties.editable("sub-shadow-offset"))
    PanelProperties.setInt("sub-shadow-offset", preferences.shadowOffset.deleteAndGet())
}

enum class SubtitlesBorderStyle(val value: String, @StringRes val titleRes: Int) {
  OutlineAndShadow(
    "outline-and-shadow",
    R.string.player_sheets_subtitles_border_style_outline_and_shadow,
  ),
  OpaqueBox("opaque-box", R.string.player_sheets_subtitles_border_style_opaque_box),
  BackgroundBox("background-box", R.string.player_sheets_subtitles_border_style_background_box),
}
