# Icon, library fields and player input refinement

Source update following v0.22.0. The user requested pushing the changes **without building the application**. No Gradle task, APK build, tag or Release is started for this update. Its commit contains `[skip ci]`.

## Changes

- The approved soft blue Play concept is implemented as small vector resources with a blue gradient and curved accent. Android 8+ uses independent full-bleed graphite background and transparent foreground layers; Android 13+ supplies a monochrome silhouette for themed icons. Legacy launchers receive a separate rounded tile. Foreground padding accounts for the adaptive icon viewport, mask and motion safe area. Android 5–6 retain a solid-color vector fallback because framework vector gradients require API 24. Launcher-specific theme packs may still substitute their own artwork.
- Reviewable geometry: [SVG](branding/mpv-play.svg). [Common launcher mask previews](branding/launcher-masks.png) are vector renders, not Android screenshots.
- Video `Duration` is removed from the Fields controls and metadata row. Duration sorting and folder total duration remain available. The duration badge is always on the video cover when duration is known, including when thumbnail loading is disabled.
- Thumbnails off preserves the cover viewport in list and Card modes, displays an Image icon, and does not invoke thumbnail decoding for that row.
- Card defaults to two columns when selected. Auto and 2 are removed from the column-choice chips; larger column counts remain available on sufficiently wide windows. Existing Auto preferences normalize to two. Original home folder artwork is unchanged.
- All app-owned editable fields now use `AppTextField`, with a 56dp default minimum matching the original Movie/series field. Panel and dialog helpers delegate to it. Labels reserve their slot to prevent typing jumps. Disabled/error/password/IME handling is shared. Supporting text sits outside the field shell. Explicit multiline editors and large font scales can grow above that minimum to avoid clipping.
- Play/Pause no longer renders a circular loading/pending indicator. One persistent clickable target and Iconsax artwork remain; the requested state drives a restrained scale/crossfade with reduced-motion support. Loading remains available to accessibility.
- Recently Played launches the most recent recorded URI after the quick menu finishes exiting. Reading is off the UI thread; empty/corrupt history gives a message. Its former bottom sheet and unused observation API are removed.
- Progress uses a finite clamped seconds/milliseconds calculation, a stable-height slot, rounded thin track and no Material stop marker/gap. New saves identify videos by URI/path. Legacy title-only progress is used only for unique names, avoiding misleading progress on duplicate filenames. Secure playback remains excluded from persistence.
- Subtitle Delay and Speed use the same shared field shell. Sliders keep local drag preview, reset obsolete preview after range/enabled changes, use consistent disabled colors and a subtle circular thumb edge; value labels have bounded width.
- The subtitle + action pushes a local-file browser into the same persistent pane. It lists folders plus supported subtitle extensions, supports search, parent folder, refresh and storage roots, performs enumeration on IO, cancels outdated listings, clears stale rows and resets scrolling on navigation. Route identity retains Primary/Secondary and media generation. Audio's separate file-picker flow is unchanged.

## Android file access

The local subtitle browser does not launch a system document picker. Browsing shared non-media subtitle files on Android 11+ requires the special **All files access** grant; the manifest declares `MANAGE_EXTERNAL_STORAGE`. It is requested only by an explicit button inside this browser, opening the OS permission settings, not at startup. Older Android versions request Read external storage. Denial and unreadable locations display an explanation; Android-protected folders can remain unavailable even after granting access. This broad special permission needs a separate distribution-policy review if the app is later submitted to Google Play.

## Verification boundaries

Kotlin syntax/parser checks, XML/resource reference checks, icon catalogue/folder-artwork checks, and static source invariants are run without Gradle. Source regressions for direct recent playback, progress bounds/units and subtitle extension filtering are included for the next authorized Android test run.

The Android application and these updated tests have **not been compiled or executed** for this source update. Physical launcher masks/themed icons, large fonts/RTL/IME, special file permission flows on multiple Android releases, SD cards, external subtitle import and real playback still require validation when building is authorized. This source review cannot guarantee stability on every device.
