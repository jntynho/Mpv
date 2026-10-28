package live.mehiz.mpvkt.preferences

import live.mehiz.mpvkt.preferences.preference.PreferenceStore

class FoldersPreferences(store: PreferenceStore) {
  val excluded = store.getStringSet("library_excluded_folders", emptySet())
  val included = store.getStringSet("library_included_folders", emptySet())
  val includeOnly = store.getBoolean("library_include_only", false)
  val minimumDuration = store.getInt("library_minimum_duration", 0)
  val minimumSizeMb = store.getInt("library_minimum_size_mb", 0)
  val includeHidden = store.getBoolean("library_include_hidden", false)
  val hiddenTrees = store.getStringSet("library_hidden_trees", emptySet())
}
