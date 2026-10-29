package live.mehiz.mpvkt.ui.preferences

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.AdvancedPreferences
import live.mehiz.mpvkt.preferences.SubtitlesPreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.presentation.components.AppDialog as AlertDialog
import live.mehiz.mpvkt.presentation.components.DialogButton as TextButton
import live.mehiz.mpvkt.presentation.components.RoundedTextField as OutlinedTextField
import live.mehiz.mpvkt.subtitles.*
import live.mehiz.mpvkt.ui.icons.*
import live.mehiz.mpvkt.ui.motion.ElasticSettingsScaffold
import live.mehiz.mpvkt.ui.motion.ElasticSettingsTopAppBar
import live.mehiz.mpvkt.ui.player.PanelProperties
import live.mehiz.mpvkt.ui.utils.LocalNavigateBack
import org.koin.compose.koinInject

@Serializable
object SubtitlesPreferencesScreen : Screen {
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val preferences = koinInject<SubtitlesPreferences>()
    var folderError by remember { mutableStateOf(false) }
    val picker =
      rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null)
          try {
            context.contentResolver.takePersistableUriPermission(
              uri,
              Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            preferences.fontsFolder.set(uri.toString())
          } catch (_: SecurityException) {
            folderError = true
          }
      }
    SubtitleSettingsPage(
      preferences,
      koinInject(),
      koinInject(),
      koinInject(),
      koinInject(),
      LocalNavigateBack.current,
      { picker.launch(null) },
    )
    if (folderError)
      AlertDialog(
        title = { Text(stringResource(R.string.subtitle_fonts)) },
        icon = { Icon(Icons.Folder, null) },
        onDismissRequest = { folderError = false },
        text = { Text(stringResource(R.string.subtitle_font_failure)) },
        confirmButton = {
          TextButton(onClick = { folderError = false }) {
            Text(stringResource(R.string.subtitle_done))
          }
        },
      )
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTextApi::class)
@Composable
internal fun SubtitleSettingsPage(
  preferences: SubtitlesPreferences,
  fontsRepository: SubtitleFonts,
  keys: SubtitleKeys,
  service: SubtitleService,
  advancedPreferences: AdvancedPreferences,
  onBack: () -> Unit,
  chooseDirectory: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val snackbar = remember { SnackbarHostState() }
  val configText by advancedPreferences.mpvConf.collectAsState()
  val configFolder by advancedPreferences.mpvConfStorageUri.collectAsState()
  var owned by remember { mutableStateOf<Set<String>>(emptySet()) }
  var configReady by remember { mutableStateOf(false) }
  LaunchedEffect(configText, configFolder) {
    configReady = false
    try {
      owned = SubtitleConfig.owned(context, advancedPreferences)
      configReady = true
    } catch (c: CancellationException) {
      throw c
    } catch (_: Exception) {
      owned =
        setOf(
          "slang",
          "sub-auto",
          "sub-ass-override",
          "sub-scale-by-window",
          "sub-font",
          "sub-fonts-dir",
          "secondary-sub-scale",
        )
      configReady = true
    }
  }
  val fontsFolder by preferences.fontsFolder.collectAsState()
  val currentFont by preferences.font.collectAsState()
  val preferred by preferences.preferredLanguages.collectAsState()
  val auto by preferences.autoLoadExternal.collectAsState()
  val override by preferences.overrideAssSubs.collectAsState()
  val windowScale by preferences.scaleByWindow.collectAsState()
  val secondary by preferences.secondaryPinchZoom.collectAsState()
  val secondarySupported by preferences.secondaryScaleSupported.collectAsState()
  val mode by preferences.searchMode.collectAsState()
  val hub by preferences.hubSources.collectAsState()
  val wyzie by preferences.wyzieSources.collectAsState()
  val languages by preferences.searchLanguages.collectAsState()
  val formats by preferences.searchFormats.collectAsState()
  val encodings by preferences.searchEncodings.collectAsState()
  val hearing by preferences.hearingImpaired.collectAsState()
  val ai by preferences.aiResults.collectAsState()
  var refresh by remember { mutableIntStateOf(0) }
  var fonts by remember { mutableStateOf<List<SubtitleFont>>(emptyList()) }
  var fontLoading by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var choice by remember { mutableStateOf<SubtitleChoice?>(null) }
  var editLanguages by remember { mutableStateOf(false) }
  var confirmClear by remember { mutableStateOf(false) }
  var confirmFonts by remember { mutableStateOf(false) }
  var advanced by rememberSaveable { mutableStateOf(false) }
  var wyzieValues by remember {
    mutableStateOf<Map<String, String>>(
      mapOf("all" to context.getString(R.string.subtitle_all_sources))
    )
  }
  var sourcesLoading by remember { mutableStateOf(false) }
  LaunchedEffect(fontsFolder, refresh) {
    fontLoading = true
    try {
      fonts = fontsRepository.scan(fontsFolder)
    } catch (c: CancellationException) {
      throw c
    } catch (_: Exception) {
      fonts = emptyList()
      error = context.getString(R.string.subtitle_font_failure)
    } finally {
      fontLoading = false
    }
  }
  LaunchedEffect(error) {
    error?.let {
      snackbar.showSnackbar(it)
      error = null
    }
  }
  var previewFamily by remember { mutableStateOf<FontFamily>(FontFamily.Default) }
  LaunchedEffect(fonts, currentFont) {
    val selected = fonts.firstOrNull { it.family == currentFont }?.file
    previewFamily = FontFamily.Default
    if (selected != null)
      try {
        val family = FontFamily(Font(selected))
        withContext(Dispatchers.IO) { createFontFamilyResolver(context).preload(family) }
        previewFamily = family
      } catch (c: CancellationException) {
        throw c
      } catch (_: Exception) {}
  }
  fun choose(
    title: String,
    values: Map<String, String>,
    selected: Set<String>,
    multi: Boolean = false,
    additive: Set<String> = emptySet(),
    apply: (Set<String>) -> Unit,
  ) {
    choice = SubtitleChoice(title, values, selected, multi, additive, apply)
  }
  fun summary(values: Map<String, String>, selected: Set<String>) =
    selected
      .joinToString(", ") { values[it] ?: it }
      .ifBlank { context.getString(R.string.subtitle_none) }
  ElasticSettingsScaffold(
    topBar = {
      ElasticSettingsTopAppBar(
        title = { Text(stringResource(R.string.pref_subtitles)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.ArrowBack, stringResource(R.string.mpv_back))
          }
        },
      )
    }
  ) { padding ->
    Box(Modifier.fillMaxSize().padding(padding)) {
      LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 48.dp),
      ) {
        if (owned.isNotEmpty())
          item("configuration-note") {
            Text(
              stringResource(R.string.subtitle_owned),
              Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        item("general-title") { Section(stringResource(R.string.subtitle_general)) }
        item("general") {
          SubtitleSettingsCard {
            SubtitleSettingRow(
              stringResource(R.string.subtitle_preferred),
              preferred.ifBlank { stringResource(R.string.subtitle_not_set) },
              optionIcon(R.string.subtitle_preferred),
              enabled = configReady && "slang" !in owned,
            ) {
              editLanguages = true
            }
            SubtitleSettingsDivider()
            SubtitleSettingRow(
              stringResource(R.string.subtitle_autoload),
              stringResource(R.string.subtitle_autoload_summary),
              optionIcon(R.string.subtitle_autoload),
              enabled = configReady && "sub-auto" !in owned,
              toggle = auto,
            ) {
              preferences.autoLoadExternal.set(!auto)
              PanelProperties.setString("sub-auto", if (!auto) "exact" else "no")
            }
            SubtitleSettingsDivider()
            SubtitleSettingRow(
              stringResource(R.string.subtitle_ass),
              stringResource(R.string.subtitle_ass_summary),
              optionIcon(R.string.subtitle_ass),
              enabled =
                configReady &&
                  setOf("sub-ass-override", "secondary-sub-ass-override").none { it in owned },
              toggle = override,
            ) {
              preferences.overrideAssSubs.set(!override)
              PanelProperties.setString("sub-ass-override", if (!override) "force" else "scale")
              PanelProperties.setString(
                "secondary-sub-ass-override",
                if (!override) "force" else "scale",
              )
            }
            SubtitleSettingsDivider()
            SubtitleSettingRow(
              stringResource(R.string.subtitle_scale),
              stringResource(R.string.subtitle_scale_summary),
              optionIcon(R.string.subtitle_scale),
              enabled =
                configReady && setOf("sub-scale-by-window", "sub-use-margins").none { it in owned },
              toggle = windowScale,
            ) {
              preferences.scaleByWindow.set(!windowScale)
              PanelProperties.setBoolean("sub-scale-by-window", !windowScale)
              PanelProperties.setBoolean("sub-use-margins", !windowScale)
            }
            SubtitleSettingsDivider()
            SubtitleSettingRow(
              stringResource(R.string.subtitle_secondary),
              stringResource(
                if (secondarySupported) R.string.subtitle_secondary_summary
                else R.string.subtitle_unsupported
              ),
              optionIcon(R.string.subtitle_secondary),
              enabled =
                configReady &&
                  secondarySupported &&
                  setOf("sub-scale", "secondary-sub-scale").none { it in owned },
              toggle = secondary,
            ) {
              preferences.secondaryPinchZoom.set(!secondary)
            }
          }
        }
        item("fonts-title") { Section(stringResource(R.string.subtitle_fonts)) }
        item("fonts") {
          SubtitleSettingsCard {
            SubtitleSettingRow(
              stringResource(R.string.subtitle_fonts_dir),
              if (fontsFolder.isBlank()) stringResource(R.string.subtitle_fonts_hint)
              else runCatching { getSimplifiedPathFromUri(fontsFolder) }.getOrDefault(fontsFolder),
              optionIcon(R.string.subtitle_fonts_dir),
              enabled = configReady && "sub-fonts-dir" !in owned,
            ) {
              chooseDirectory()
            }
            SubtitleSettingsDivider()
            SubtitleSettingRow(
              stringResource(R.string.subtitle_font),
              if (fontLoading) stringResource(R.string.subtitle_loading)
              else currentFont + "\n" + stringResource(R.string.subtitle_font_count, fonts.size),
              optionIcon(R.string.subtitle_font),
              enabled = configReady && "sub-font" !in owned,
            ) {
              val choices =
                linkedMapOf("Sans Serif" to context.getString(R.string.subtitle_default_font))
              fonts.forEach { choices[it.family] = it.family + " · " + it.label }
              if (currentFont !in choices) choices[currentFont] = currentFont
              choose(context.getString(R.string.subtitle_font), choices, setOf(currentFont)) {
                chosen ->
                val value = chosen.first()
                preferences.font.set(value)
                PanelProperties.setString("sub-font", value)
              }
            }
            SubtitleSettingsDivider()
            SubtitleSettingRow(
              stringResource(R.string.subtitle_reload_fonts),
              stringResource(R.string.subtitle_reload_summary),
              optionIcon(R.string.subtitle_reload_fonts),
              enabled = fontsFolder.isNotBlank() && !fontLoading,
            ) {
              refresh++
            }
            SubtitleSettingsDivider()
            SubtitleSettingRow(
              stringResource(R.string.subtitle_clear_fonts),
              stringResource(R.string.subtitle_clear_fonts_summary),
              optionIcon(R.string.subtitle_clear_fonts),
              enabled = fontsFolder.isNotBlank(),
              danger = true,
            ) {
              confirmFonts = true
            }
          }
        }
        item("preview") {
          Section(stringResource(R.string.subtitle_preview))
          SubtitleSettingsCard {
            Column(
              Modifier.fillMaxWidth().padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(
                stringResource(R.string.subtitle_sample),
                fontFamily = previewFamily,
                style = MaterialTheme.typography.titleLarge,
              )
              Spacer(Modifier.height(14.dp))
              Text(
                currentFont,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Text(
            stringResource(R.string.subtitle_reopen_fonts),
            Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        item("search-title") { Section(stringResource(R.string.subtitle_search)) }
        item("search") {
          SubtitleSettingsCard {
            SubtitleSettingRow(
              stringResource(R.string.subtitle_mode),
              SubtitleCatalog.modes[mode].orEmpty(),
              optionIcon(R.string.subtitle_mode),
            ) {
              choose(
                context.getString(R.string.subtitle_mode),
                SubtitleCatalog.modes,
                setOf(mode),
              ) {
                preferences.searchMode.set(it.first())
              }
            }
            SubtitleSettingsDivider()
            if (mode != "wyzie") {
              SubtitleSettingRow(
                stringResource(R.string.subtitle_hub),
                summary(SubtitleCatalog.hub, hub),
                optionIcon(R.string.subtitle_hub),
              ) {
                choose(
                  context.getString(R.string.subtitle_hub),
                  SubtitleCatalog.hub,
                  hub,
                  true,
                  SubtitleCatalog.keys.keys - "wyzie",
                  preferences.hubSources::set,
                )
              }
              SubtitleSettingsDivider()
            }
            if (mode != "subhub") {
              SubtitleSettingRow(
                stringResource(R.string.subtitle_wyzie),
                if (sourcesLoading) stringResource(R.string.subtitle_loading)
                else summary(wyzieValues, wyzie),
                optionIcon(R.string.subtitle_wyzie),
                enabled = !sourcesLoading,
              ) {
                scope.launch {
                  sourcesLoading = true
                  try {
                    wyzieValues = withContext(Dispatchers.IO) { service.sources() }
                  } catch (c: CancellationException) {
                    throw c
                  } catch (_: Exception) {
                    error = context.getString(R.string.subtitle_source_problem)
                  } finally {
                    sourcesLoading = false
                  }
                  // Retain configured IDs when discovery is temporarily
                  // unavailable.
                  choose(
                    context.getString(R.string.subtitle_wyzie),
                    wyzieValues + wyzie.associateWith { wyzieValues[it] ?: it },
                    wyzie,
                    true,
                    apply = preferences.wyzieSources::set,
                  )
                }
              }
              SubtitleSettingsDivider()
            }
            SubtitleSettingRow(
              stringResource(R.string.subtitle_languages),
              summary(SubtitleCatalog.languages, languages),
              optionIcon(R.string.subtitle_languages),
            ) {
              choose(
                context.getString(R.string.subtitle_languages),
                SubtitleCatalog.languages,
                languages,
                true,
                apply = preferences.searchLanguages::set,
              )
            }
            SubtitleSettingsDivider()
            SubtitleSettingRow(
              stringResource(R.string.subtitle_filters),
              "",
              optionIcon(R.string.subtitle_filters),
              expanded = advanced,
            ) {
              advanced = !advanced
            }
            ExpandableSettingsContent(advanced) {
              SubtitleSettingsDivider()
              SubtitleSettingRow(
                stringResource(R.string.subtitle_hearing),
                stringResource(R.string.subtitle_hearing_summary),
                optionIcon(R.string.subtitle_hearing),
                toggle = hearing,
              ) {
                preferences.hearingImpaired.set(!hearing)
              }
              SubtitleSettingsDivider()
              val aiValues =
                linkedMapOf(
                  "all" to stringResource(R.string.subtitle_ai_all),
                  "human" to stringResource(R.string.subtitle_ai_human),
                  "ai" to stringResource(R.string.subtitle_ai_only),
                )
              SubtitleSettingRow(
                stringResource(R.string.subtitle_ai),
                aiValues[ai].orEmpty(),
                optionIcon(R.string.subtitle_ai),
              ) {
                choose(context.getString(R.string.subtitle_ai), aiValues, setOf(ai)) {
                  preferences.aiResults.set(it.first())
                }
              }
              SubtitleSettingsDivider()
              SubtitleSettingRow(
                stringResource(R.string.subtitle_formats),
                summary(SubtitleCatalog.formats, formats),
                optionIcon(R.string.subtitle_formats),
              ) {
                choose(
                  context.getString(R.string.subtitle_formats),
                  SubtitleCatalog.formats,
                  formats,
                  true,
                  apply = preferences.searchFormats::set,
                )
              }
              SubtitleSettingsDivider()
              SubtitleSettingRow(
                stringResource(R.string.subtitle_encodings),
                summary(SubtitleCatalog.encodings, encodings),
                optionIcon(R.string.subtitle_encodings),
              ) {
                choose(
                  context.getString(R.string.subtitle_encodings),
                  SubtitleCatalog.encodings,
                  encodings,
                  true,
                  apply = preferences.searchEncodings::set,
                )
              }
            }
          }
        }
        item("keys-title") { Section(stringResource(R.string.subtitle_keys)) }
        item("keys") {
          SubtitleSettingsCard {
            val visible =
              SubtitleCatalog.keys.filterKeys {
                (it == "wyzie" && mode != "subhub") || (it in hub && mode != "wyzie")
              }
            visible.entries.forEachIndexed { index, entry ->
              if (index > 0) SubtitleSettingsDivider()
              ProviderKeyRow(entry.key, entry.value, keys)
            }
            if (visible.isEmpty())
              Text(
                stringResource(R.string.subtitle_none),
                Modifier.padding(20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
          }
        }
        item("clear") {
          Spacer(Modifier.height(16.dp))
          SubtitleSettingsCard {
            SubtitleSettingRow(
              stringResource(R.string.subtitle_clear_downloads),
              stringResource(R.string.subtitle_clear_downloads_summary),
              optionIcon(R.string.subtitle_clear_downloads),
              danger = true,
            ) {
              confirmClear = true
            }
          }
        }
      }
      Box(
        Modifier.align(Alignment.BottomCenter)
          .fillMaxWidth()
          .height(28.dp)
          .background(
            Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface))
          )
      )
      SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
  }
  choice?.let { SubtitleChoiceSheet(it) { choice = null } }
  if (editLanguages) {
    var text by remember { mutableStateOf(preferred) }
    AlertDialog(
      onDismissRequest = { editLanguages = false },
      title = { Text(stringResource(R.string.subtitle_preferred)) },
      text = {
        Column {
          Text(stringResource(R.string.subtitle_preferred_hint))
          OutlinedTextField(text, { text = it.take(500) }, singleLine = true)
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            val value =
              text.split(',').map(String::trim).filter(String::isNotBlank).joinToString(",")
            preferences.preferredLanguages.set(value)
            PanelProperties.setString("slang", subtitleLanguageCodes(value))
            editLanguages = false
          }
        ) {
          Text(stringResource(R.string.subtitle_save))
        }
      },
      dismissButton = {
        TextButton(onClick = { editLanguages = false }) {
          Text(stringResource(R.string.subtitle_cancel))
        }
      },
    )
  }
  if (confirmClear || confirmFonts)
    AlertDialog(
      onDismissRequest = {
        confirmClear = false
        confirmFonts = false
      },
      title = {
        Text(
          stringResource(
            if (confirmFonts) R.string.subtitle_clear_fonts else R.string.subtitle_clear_downloads
          )
        )
      },
      text = {
        Text(
          stringResource(
            if (confirmFonts) R.string.subtitle_clear_fonts_summary
            else R.string.subtitle_confirm_downloads
          )
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            val clearFonts = confirmFonts
            confirmClear = false
            confirmFonts = false
            scope.launch {
              try {
                if (clearFonts) {
                  fontsRepository.clear()
                  preferences.fontsFolder.delete()
                  preferences.font.delete()
                  refresh++
                  PanelProperties.setString("sub-font", preferences.font.defaultValue())
                } else withContext(Dispatchers.IO) { service.clearDownloads() }
                snackbar.showSnackbar(
                  context.getString(
                    if (clearFonts) R.string.subtitle_clear_fonts
                    else R.string.subtitle_downloads_cleared
                  )
                )
              } catch (c: CancellationException) {
                throw c
              } catch (_: Exception) {
                error =
                  context.getString(
                    if (clearFonts) R.string.subtitle_font_failure
                    else R.string.subtitle_downloads_failure
                  )
              }
            }
          }
        ) {
          Text(stringResource(R.string.subtitle_done))
        }
      },
      dismissButton = {
        TextButton(
          onClick = {
            confirmClear = false
            confirmFonts = false
          }
        ) {
          Text(stringResource(R.string.subtitle_cancel))
        }
      },
    )
}

@Composable
private fun Section(title: String) {
  Text(
    title,
    Modifier.padding(start = 12.dp, top = 22.dp, bottom = 12.dp),
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.primary,
  )
}

@Composable
internal fun ProviderKeyRow(
  provider: String,
  name: String,
  keys: SubtitleKeys,
  inline: Boolean = false,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var value by remember(provider) { mutableStateOf("") }
  var loaded by remember(provider) { mutableStateOf(false) }
  var editing by remember { mutableStateOf(false) }
  var failure by remember { mutableStateOf(false) }
  LaunchedEffect(provider) {
    try {
      value = withContext(Dispatchers.IO) { keys.get(provider) }
    } catch (c: CancellationException) {
      throw c
    } catch (_: Exception) {
      failure = true
    } finally {
      loaded = true
    }
  }
  SubtitleSettingRow(
    "$name API key",
    stringResource(
      if (!loaded) R.string.subtitle_loading
      else if (value.isBlank()) R.string.subtitle_key_empty else R.string.subtitle_key_saved
    ),
    Icons.Lock,
    enabled = loaded,
  ) {
    editing = !editing
  }
  if (editing) {
    var draft by remember { mutableStateOf(value) }
    var show by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val editor: @Composable () -> Unit = {
      Column {
        Text(stringResource(R.string.subtitle_key_hint), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
          draft,
          { draft = it.take(4096) },
          Modifier.fillMaxWidth(),
          singleLine = true,
          label = { Text("API key") },
          enabled = !saving,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          visualTransformation =
            if (show) VisualTransformation.None else PasswordVisualTransformation(),
          trailingIcon = {
            IconButton(onClick = { show = !show }) {
              Icon(
                if (show) Icons.LockOpen else Icons.Lock,
                stringResource(if (show) R.string.subtitle_hide_key else R.string.subtitle_show_key),
              )
            }
          },
        )
        TextButton(
          onClick = {
            val clipboard =
              context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
            draft =
              clipboard.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString()
                ?.trim()
                ?.take(4096)
                .orEmpty()
          },
          enabled = !saving,
        ) {
          Text(stringResource(R.string.subtitle_paste))
        }
      }
    }
    val save: @Composable () -> Unit = {
      TextButton(
        onClick = {
          saving = true
          scope.launch {
            try {
              withContext(Dispatchers.IO) { keys.put(provider, draft) }
              value = draft.trim()
              editing = false
            } catch (c: CancellationException) {
              throw c
            } catch (_: Exception) {
              failure = true
            } finally {
              saving = false
            }
          }
        },
        enabled = !saving,
      ) {
        Text(stringResource(R.string.subtitle_save))
      }
    }
    val cancel: @Composable () -> Unit = {
      TextButton(onClick = { editing = false }, enabled = !saving) {
        Text(stringResource(R.string.subtitle_cancel))
      }
    }
    if (inline)
      Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        editor()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          cancel()
          save()
        }
      }
    else
      AlertDialog(
        icon = { Icon(Icons.Lock, null) },
        onDismissRequest = { if (!saving) editing = false },
        title = { Text("$name API key") },
        text = editor,
        confirmButton = save,
        dismissButton = cancel,
      )
  }
  if (failure) {
    if (inline)
      Text(
        stringResource(R.string.subtitle_key_failure),
        Modifier.padding(16.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
      )
    else
      AlertDialog(
        title = { Text("$name API key") },
        icon = { Icon(Icons.Lock, null) },
        onDismissRequest = { failure = false },
        text = { Text(stringResource(R.string.subtitle_key_failure)) },
        confirmButton = {
          TextButton(onClick = { failure = false }) { Text(stringResource(R.string.subtitle_done)) }
        },
      )
  }
}
