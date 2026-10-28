# MPV 0.15 — library, appearance and landscape controls

Reference behavior was reviewed in `Riteshp2001/mpvRx`, commit `4389b7b01487b91f07ae5ecfb31e4035211cafd2`. MPV retains its own Compose components and Iconsax Outline/Broken catalogue. No icon font, reorder library or new runtime dependency is added.

## Refresh and dialogs

Pull-to-refresh keeps the existing elastic list behavior and refresh threshold. One thin rounded arc replaces the icon-to-spinner swap; its sweep follows the pull and rotates only during refresh. Reduced-motion behavior is respected.

Shared app dialogs use a 28 dp outer radius, smaller field/icon corners and 48 dp minimum footer targets. Native window entrance/exit animations provide a slight vertical movement with opacity, including when the Compose caller removes a dialog immediately after a selection. There is no scale-shrink exit or additional glow. Text fields, quiet borders and the existing icon family remain shared.

## Folder preferences

Folders settings provide included/excluded paths, an optional include-only mode, minimum duration/size, selected-folder access, rescan and in-memory cache clearing. Filters apply to all three library views. Matching respects directory boundaries, exclusion wins and an empty active include list intentionally produces an empty library. Settings never delete or move videos.

Additional videos are read only from explicitly granted Storage Access Framework trees. This can include hidden folders that MediaStore does not index; it is not a claim to access all hidden storage automatically. Scanning is cancellable between provider operations, deduplicated, cycle-checked and bounded by depth/node limits. Failed or incomplete grants are reported. Metadata unavailable from a document provider remains unknown rather than being invented.

## Colour Accent and thumbnails

Colour Accent offers MPV's original palette and the 31 static App Theme palettes from the reference. Palette names and colour values are attributed in `ColourAccent.kt`; palette application is implemented for MPV. Wallpaper colours retain the existing Android 12+ option. Choosing a named palette disables wallpaper colours. Foregrounds are selected for readable contrast in light/dark modes; OLED black remains available.

Thumbnail options include an actual enable/disable switch, automatic/provider/frame sources, two bounded resolutions, frame position for explicit frame extraction, and cache clearing. Position is saved on release rather than starting native frame extraction on every drag sample. Cache identity includes generation settings. Cache clearing invalidates in-flight writes, and disabled covers do not query providers. The existing 6 MiB memory bound, two-worker bound, cancellation and failure fallbacks remain.

## Landscape Controls

The player preferences open a live preview and editor for top-right, bottom-right and bottom-left regions. Existing supported controls can be hidden, moved and reordered with native buttons. Saved layouts are normalized, duplicate-free, bounded to six controls per region and recover safely from invalid saved data. Narrow regions scroll instead of overlapping controls. Decoder labels keep a reserved width. The preview keeps the player's physical positions in RTL settings. Portrait behavior remains separate, and original chapter visibility preferences are migrated.

## Verification limits

All 133 app tests passed, including folder boundaries and filters, selected-tree traversal, disabled thumbnail I/O, palette contrast, layout persistence/normalization, real Compose selection/editing, RTL preview and decoder label geometry. Native-rendering previews help inspect layout. Warmup captures now occur after exercising each animated pose, allowing an entering pane to be drawn inside the capture bounds. Decoder behavior, device-specific document providers, native window animation timing and real frame rates still require physical-device checks; no zero-lag guarantee is made.

Android Lint: zero errors, 523 warnings. The new UI text is localized in English and Arabic; other existing locales fall back to English, contributing translation warnings.

The minified preview build, all signatures, 16 KB ZIP alignment and ABI contents passed. Local APK sizes: arm64 20,869,322 bytes; arm32 20,196,346 bytes; universal 33,094,131 bytes (about 73 KB over 0.14.1). The Seeker module test also passed. Release builds can differ slightly due to their commit-count suffix.

Rendered previews from production components (Robolectric native graphics, not phone screenshots): [Appearance](design/library-0.15/appearance.png), [Folders](design/library-0.15/folders.png), [Thumbnails](design/library-0.15/thumbnails.png), [landscape editor under RTL direction](design/library-0.15/landscape-controls-rtl.png), [shared dialog](design/library-0.15/dialog.png).
