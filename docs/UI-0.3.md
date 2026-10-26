# MPV 0.3 UI

- Replaced Material Icons and the additional SimpleIcons dependency with selected Lucide artwork converted to cached Compose vectors. Source commit and license: third_party/lucide. License copies are bundled in app assets. Native PiP/notification action vectors also use Lucide. The application launcher icon remains the MPV artwork chosen in 0.2.
- Replaced all navigation animations with symmetric 420ms horizontal motion using FastOutSlowInEasing, including predictive back. Forward/back use the same duration, with RTL direction.
- Added matching horizontal AnimatedContent between folders and videos in the library, with independent outgoing/incoming page data.
- Header search opens on demand, requests keyboard focus, and closes/clears on back. Toolbar overflow groups file/stream/refresh actions to reclaim space.
- Sort menu persists the chosen order: newest, name ascending/descending, duration descending. Folder duration order compares the total duration of its videos; newest folders follow their newest MediaStore entry.
- Compact rows remain configurable. Permissions, partial video access and background queries remain intact.

icons-preview.png is a rendered sheet of the actual bundled icon artwork, not a runtime Android screenshot. No Android device/emulator is connected, so animation feel, interactive back gestures, keyboard/IME and large-library performance need device validation.

Lint identified a leading-decimal SVG path compatibility issue in the play icon. All converted paths normalize fractional literals to include a leading zero. LucideIconsTest constructs all 75 selected vectors and checks RTL mirroring of the back arrow.

Final validation: Gradle testDebugUnitTest, lintDebug, assembleDebug and assemblePreview passed. Eight tests passed, including construction of all 75 Lucide vectors. Lint has zero errors (translation/deprecation warnings remain). Both ARM APK signatures, 16KB ZIP alignment, native libmpv presence and bundled Lucide license verified.
