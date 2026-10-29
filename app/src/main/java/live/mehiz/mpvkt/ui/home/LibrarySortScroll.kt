package live.mehiz.mpvkt.ui.home

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable

/** Request before the next measure so stable item keys cannot anchor the old first row. */
@Composable
internal fun ResetScrollOnLibrarySort(state: LazyListState, sort: LibrarySort) {
  val identity = "${sort.key.storedName}:${sort.ascending}"
  var applied by rememberSaveable { mutableStateOf(identity) }
  SideEffect {
    if (applied != identity) {
      state.requestScrollToItem(0)
      applied = identity
    }
  }
}

@Composable
internal fun ResetScrollOnLibrarySort(
  state: androidx.compose.foundation.lazy.grid.LazyGridState,
  sort: LibrarySort,
) {
  val identity = "${sort.key.storedName}:${sort.ascending}"
  var applied by rememberSaveable { mutableStateOf(identity) }
  SideEffect {
    if (applied != identity) {
      state.requestScrollToItem(0)
      applied = identity
    }
  }
}
