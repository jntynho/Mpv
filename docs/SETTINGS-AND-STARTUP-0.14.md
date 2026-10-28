# MPV 0.14 — settings, dialogs, library modes and first-use preparation

Implements the ten points approved after the settings/dialog review. Existing preference keys, the
Iconsax Outline/Broken catalogue and the home folder artwork/88 × 76 dp geometry are preserved.

## Approved points and implementation

| Point | Result |
| --- | --- |
| 1. Elastic settings scrolling | All settings scaffolds and nested pages use the same `LibraryOverscrollFactory`/Miuix spring as the library. Removed the settings-only pull translation and changing toolbar height, which could apply a second movement. Settings do not refresh media on pull. The shared theme also supplies the factory to choice dialogs and sheets. |
| 2. Settings detail pages like Subtitles | Audio, Appearance, Player, Gestures, Decoder and Advanced use shared `SettingsGroup`, `SettingsCard` and `SettingsRow`: 24 dp corners, category icons, title/summary, whole-row switches or direction-aware chevrons. Subtitles delegates its card surface to the same component. Root settings uses the same row. |
| 3. Three library modes | Sort's existing bottom sheet adds only **Folder**, **Tree**, **Videos**. Folder retains the existing grouped library; Tree navigates ancestors and immediate children; Videos shows a flat sorted list. The mode is saved separately from sort criterion/direction. Switching modes fades the content, preserves keyed items, and maintains independent page state. |
| 4. Quiet haptics | One system tick policy, with 65 ms deduplication, observes completed consumed taps without consuming input or waiting on gestures. Existing long-press feedback uses the same policy. Sliders tick at coarse 5% intervals, not on every frame. System haptic preferences remain authoritative; no vibrator permission or forced vibration flags are added. Hardware strength still depends on the device. |
| 5. Inline expansion | `ExpandableSettingsContent` is the default for hierarchical sections, including Advanced search filters and settings sliders: one reversible 280 ms height animation, light fade and animated chevron. Child switches do not collapse their parent. Expansion state survives navigation/restoration where appropriate; reduce motion skips the animation. Leaf editors and choices retain their purpose-specific windows. |
| 6. Quiet Floating Soft dialogs | `AppDialog` replaces app-owned editor/confirmation windows, including automatically generated preference editors and the OSS license dialog. A matte surface, 36 dp outer corners, 20 dp fields and a fine quiet rim replace bright edge glow. Cancel/Save use equal capsule actions (at least 52 dp high), including at large font sizes. The window and actions share a restrained border treatment. Focus/error/disabled/masked states remain available; long bodies scroll above a pinned footer. Framework window animations are disabled so the app's entrance is the only animation owner. |
| 7. Larger subtitle posters | 76 × 114 dp instead of 56 × 84, with 6 dp corners instead of 12. New and cached rows have a 220 ms reveal. Requests use TMDB w342 for high-density screens; decoding is capped at 384 × 576, the cache stays at 4 MiB and concurrent loads stay at two. Canonical URL lookup now also applies to cache peeks. Missing/corrupt images retain the movie-icon fallback. |
| 8. Side-pane entrance | A single analytical horizontal spring (damping .72, stiffness 320) adds a controlled overshoot. Width/inset measurement updates no longer cancel the first entrance and snap the surface open. Position remains normalized across measurement changes. Closing cancels the entrance, blocks late input and retains one surface; header dragging and the 12 dp safe margins remain. |
| 9. Original Play/Pause icons | Replaced the hand-drawn morph/dashed approximation with the actual Iconsax Play and Pause vectors in both styles. Two draw layers crossfade with small scale/rotation over 260 ms, while one permanent circular target and one accessible action remain. Fast reversals animate from the current value. |
| 10. Real startup work | The preparation page has a thin loading animation and localized text. It parses both icon styles off the UI thread, opens Room off the UI thread, builds the initial library listing and prefetches a bounded three thumbnails. Three separate frames precompose and measure real header, settings, player-control, slider and field components. These warmup components are never placed, never receive input or appear in accessibility. No player/decoder, remote search or provider key is initialized by this UI warmup. No artificial minimum waiting time is added. |

**Tree scope:** the hierarchy contains folders with accessible MediaStore-indexed videos. It is not
an unrestricted file manager and does not enumerate empty directories or inaccessible storage.
No extra storage permission is requested. Runtime permissions and system document pickers retain
Android's own UI; they cannot use an arbitrary app dialog skin.

## Bugs found while unifying the settings

- Custom-button validation now accepts command edits without renaming, rejects whitespace-only
  inputs, and checks conflicting names consistently.
- Configuration edits save off the main thread and show an error instead of crashing on a failed
  write. Imported configuration streams are closed, and the editor observes updated preferences.
- Clear playback history previously obtained the DAO without deleting anything. It now awaits
  `clearAllPlaybackStates()` before reporting success, while preserving custom buttons.
- Clear cached configurations previously deleted the contents of the entire private files directory.
  It now deletes only `mpv.conf` and `input.conf` copies rebuilt by the player, preserving subtitle
  downloads, scripts and other private files. Repeated clearing is safe.
- Font-cache clearing uses the existing serialized subtitle-font cache operation off the main thread.
- Font scaling could give dialog action buttons different heights. The footer now shares its measured
  height between actions; a large-font RTL test covers the case.
- The icon-asset check previously depended on one exact source formatting line. It still verifies the
  artwork hash and exact 88 × 76 geometry, while accepting multiline Kotlin formatting.

## Dialog coverage

The standard covers custom-button Add/Edit/Delete, preferred subtitle and audio languages, seven
provider-key editors and their errors, font-folder errors, font/download/history confirmations,
Appearance's icon-style choices, Audio channels, Player orientation/control timeout, Gesture actions
and duration, Decoder debanding, mpv.conf/input.conf editors, and OSS license details. Inline API-key
fields in the player reuse the same rounded field without stacking a centered dialog on the pane.
The existing sort/choice bottom sheets and single player pane retain their distinct navigation roles.
The player typography border-style control remains an inline dropdown.

## Validation

- `python3 scripts/check-icon-assets.py`: passes; unchanged folder art/geometry, 81 keys and both styles.
- `testDebugUnitTest`: 112 app tests passed, zero skipped/failed; the Seeker module's existing test also passes.
- Native Robolectric UI tests cover reversible expansion, child toggles, equal dialog action geometry,
  RTL with 1.8× text, hidden warmup completion, real preference choices, command editing, actual history
  deletion, pane entrance during inset changes, bounded overshoot/cancel-on-close, keyed sorting,
  and subtitle-result scrolling after increasing the poster size.
- A filesystem regression verifies configuration-cache clearing preserves downloads and scripts.
- `lintDebug`: no errors; non-blocking warnings remain (see generated report).
- `assemblePreview`: builds minified/shrunk ARM32, ARM64 and universal APKs; no runtime dependency added.

Screenshots are native renders of the shared UI with deterministic example data, not image-generation
mockups. The generic dialog sample uses a test value; the real provider editor masks saved keys.
The poster artwork and movie/series names are fictional fixtures used only in tests, never bundled
into APKs: [dialog](design/settings-0.14/dialog.png), [results](design/settings-0.14/poster-results.png),
[selected series](design/settings-0.14/poster-selected-series.png).

This environment has no physical phone/emulator. The tests verify state, layout and animation
interruption, not device FPS, real playback/rotation or cold-start GPU performance. Precomposition
reduces first-use CPU work but does not precompile every ART/GPU/decoder path or guarantee zero jank.
Live poster/provider hosts may be blocked by this environment's proxy; deterministic transport tests
cover parsing, size limits, cache, cancellation and retry behavior instead.

Release publication remains the existing tag workflow, with exactly `arm64-v8a.apk`,
`armeabi-v7a.apk`, `universal.apk` as custom assets, and no SHA256SUMS or custom ZIP. Signing policy
and native playback engine are unchanged.

Final local verification: `testDebugUnitTest lintDebug assemblePreview` passed. App Lint reports
0 errors / 451 warnings. All three APKs passed `apksigner verify` and 16 KiB `zipalign` checks, with
exactly the expected ABI sets and Android version codes 172 / 171 / 170.

| Local APK | Bytes |
| --- | ---: |
| arm64-v8a | 20,773,003 |
| armeabi-v7a | 20,100,028 |
| universal | 32,997,801 |

These are approximately 2.7 KiB larger than the local 0.13.0 artifacts. CI artifacts can differ
slightly because commit metadata and automatic signing are generated during their own build.
