# MPV 0.5

- Shared LocalNavigateBack action for settings toolbar buttons and committed system back. An Activity BackHandler registered after NavDisplay handles internal completed gestures. Cancelling a gesture does not pop; root exit remains delegated to Android. This intentionally replaces the interactive predictive scale preview with the same 280ms committed slide/fade used by the toolbar. The NavDisplay predictive fallback also uses that geometry. Dialog and popup windows keep their own dismissal handling.
- Folder/search back uses one local callback and the shared motion. Header back-button width expands/collapses while fading, and title content fades with a 280ms size transform. No reverse fast-scale animation.
- A draw-only 64dp transparent-to-background gradient at the bottom of main application screens. No pointer/semantics handlers. Library bottom padding leaves the final row accessible above it. It does not darken the video playback surface.
- Replaced Lucide artwork with Tabler outline icons at 1.65 stroke width; source commit/license under third_party/tabler, license bundled in APK. Native notification and PiP action vectors also migrated. Launcher branding and the approved solid folder artwork are retained.
- docs/tabler-preview.png is rendered from the actual bundled paths, not a screenshot of Android. No device/emulator is connected: gesture priority, IME, dialogs, rapid back, RTL, animation feel and touch-through require device validation.

Validation: testDebugUnitTest, lintDebug, assembleDebug and assemblePreview succeeded. Seven tests passed, including all 75 Tabler vectors and RTL back-arrow mirroring. Lint has zero errors (warnings remain). Both ARM APK signatures, 16KB alignment, libmpv presence and bundled Tabler license verified. No device-level gesture validation is claimed.

0.5.1 audit: the player toolbar previously bypassed its dispatcher with finish(). It now invokes the same onBackPressedDispatcher path as the system, including the existing automatic-PiP policy. This avoids a second back behavior inside playback.

0.5.1 final: seven unit tests, lintDebug and assemblePreview succeeded; both ARM APK signatures and 16KB alignment verified again after the player dispatcher fix.
