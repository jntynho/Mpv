package live.mehiz.mpvkt.ui.home

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.presentation.components.*
import live.mehiz.mpvkt.ui.icons.*
import live.mehiz.mpvkt.ui.player.controls.components.PlayerLazyScrollArea

private enum class QuickAction {
  File,
  Recent,
  Link,
}

@Composable
internal fun HomeQuickActions(reduceMotion: Boolean) {
  if (live.mehiz.mpvkt.ui.startup.LocalInterfacePreparation.current) return
  val context = LocalContext.current
  val density = androidx.compose.ui.platform.LocalDensity.current
  val gestureNavigation = WindowInsets.mandatorySystemGestures.getBottom(density) > 0
  val clearance = if (gestureNavigation) 12.dp else 4.dp
  val popupOffset = with(density) { (-72.dp - clearance).roundToPx() }
  var expanded by rememberSaveable { mutableStateOf(false) }
  val visibility = remember { MutableTransitionState(false) }
  visibility.targetState = expanded
  var pending by remember { mutableStateOf<QuickAction?>(null) }
  var recent by rememberSaveable { mutableStateOf(false) }
  var link by rememberSaveable { mutableStateOf(false) }
  var url by rememberSaveable { mutableStateOf("") }
  var invalid by remember { mutableStateOf(false) }
  val picker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        runCatching {
          context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
          )
        }
        HomeScreen.playFile(uri.toString(), context)
      }
    }
  LaunchedEffect(visibility.currentState, visibility.targetState, pending) {
    if (!visibility.currentState && !visibility.targetState) {
      when (pending) {
        QuickAction.File -> picker.launch(arrayOf("*/*"))
        QuickAction.Recent -> recent = true
        QuickAction.Link -> {
          invalid = false
          link = true
        }
        null -> Unit
      }
      pending = null
    }
  }
  BackHandler(expanded) { expanded = false }
  Box(Modifier.padding(bottom = clearance)) {
    FloatingActionButton(
      onClick = { expanded = !expanded },
      shape = CircleShape,
      containerColor = MaterialTheme.colorScheme.primaryContainer,
    ) {
      Crossfade(
        expanded,
        animationSpec = tween(if (reduceMotion) 0 else 180),
        label = "quick_action_icon",
      ) {
        Icon(if (it) Icons.Close else Icons.Play, stringResource(R.string.mpv_quick_actions))
      }
    }
    if (visibility.currentState || visibility.targetState)
      Popup(
        alignment = Alignment.BottomEnd,
        offset = IntOffset(0, popupOffset),
        onDismissRequest = { expanded = false },
        properties = PopupProperties(focusable = true),
      ) {
        AnimatedVisibility(
          visibleState = visibility,
          enter = fadeIn(tween(if (reduceMotion) 0 else 220)),
          exit = fadeOut(tween(if (reduceMotion) 0 else 180)),
        ) {
          Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            listOf(QuickAction.File, QuickAction.Recent, QuickAction.Link).forEachIndexed {
              index,
              action ->
              Surface(
                Modifier.animateEnterExit(
                  enter =
                    fadeIn(
                      tween(
                        if (reduceMotion) 0 else 180,
                        delayMillis = if (reduceMotion) 0 else (2 - index) * 30,
                      )
                    ) +
                      slideInVertically(
                        tween(
                          if (reduceMotion) 0 else 220,
                          delayMillis = if (reduceMotion) 0 else (2 - index) * 30,
                        )
                      ) {
                        it / 3
                      },
                  exit = fadeOut(tween(if (reduceMotion) 0 else 140)),
                ),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 3.dp,
              ) {
                Row(
                  Modifier.widthIn(max = 280.dp)
                    .heightIn(min = 56.dp)
                    .clickable {
                      if (expanded) {
                        pending = action
                        expanded = false
                      }
                    }
                    .padding(16.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                  Text(
                    stringResource(
                      when (action) {
                        QuickAction.File -> R.string.mpv_open_file
                        QuickAction.Recent -> R.string.mpv_recently_played
                        QuickAction.Link -> R.string.mpv_open_link
                      }
                    ),
                    Modifier,
                  )
                  Icon(
                    when (action) {
                      QuickAction.File -> Icons.FolderOpen
                      QuickAction.Recent -> Icons.History
                      QuickAction.Link -> Icons.Link
                    },
                    null,
                    Modifier.size(22.dp),
                  )
                }
              }
            }
          }
        }
      }
  }
  if (link)
    AppDialog(
      onDismissRequest = { link = false },
      title = { Text(stringResource(R.string.mpv_open_link)) },
      icon = { Icon(Icons.Link, null) },
      confirmButton = {
        DialogButton({
          if (validMediaLink(url.trim())) {
            link = false
            HomeScreen.playFile(url.trim(), context)
          } else invalid = true
        }) {
          Text(stringResource(R.string.mpv_open_link))
        }
      },
      dismissButton = {
        DialogButton({ link = false }) { Text(stringResource(R.string.generic_cancel)) }
      },
      text = {
        RoundedTextField(
          url,
          {
            url = it
            invalid = false
          },
          singleLine = true,
          keyboardOptions =
            androidx.compose.foundation.text.KeyboardOptions(
              keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri
            ),
          label = { Text(stringResource(R.string.mpv_link_address)) },
          isError = invalid,
          supportingText =
            if (invalid) ({ Text(stringResource(R.string.mpv_invalid_link)) }) else null,
        )
      },
    )
  if (recent) {
    val flow = remember(context) { RecentMedia.observe(context) }
    val entries by flow.collectAsState(initial = emptyList())
    StableBottomSheet(
      onDismissRequest = { recent = false },
      reduceMotion = reduceMotion,
      title = stringResource(R.string.mpv_recently_played),
    ) {
      Text(
        stringResource(R.string.mpv_recently_played),
        Modifier.padding(20.dp),
        style = MaterialTheme.typography.titleLarge,
      )
      PlayerLazyScrollArea(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
        if (entries.isEmpty())
          item { Text(stringResource(R.string.mpv_recent_empty), Modifier.padding(24.dp)) }
        items(entries, key = { it.uri }) { entry ->
          Row(
            Modifier.fillMaxWidth()
              .clickable {
                recent = false
                HomeScreen.playFile(entry.uri, context)
              }
              .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(Icons.History, null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                entry.title,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
              )
              Text(
                entry.uri,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Icon(Icons.Play, null, Modifier.size(20.dp))
          }
        }
      }
    }
  }
}
