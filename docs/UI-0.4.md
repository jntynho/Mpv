# MPV 0.4

Folder-only home, larger solid folder artwork, paths, video thumbnails, duration badges on videos only, search/sort/settings header, pull-to-refresh. Overflow actions (external picker/stream opener/manual refresh), tabs, global counts, tagline and ineffective home appearance switches removed.

Transitions copied from abdallahmehiz/mpvKt at `01a93062dd73f642cbb0511f6a05f1fd344b36a9`: forward and pop slide+fade at 220ms; predictive pop scale+fade at 220ms with the original transform origin and delayed scale-in. The latest upstream request replaces the previous 420ms motion preference. Folder navigation uses the same forward/pop specifications.

Thumbnail loading is off the main thread, bounded to two concurrent operations and a 6MiB LRU cache. Android 10+ uses loadThumbnail with scaled MediaMetadataRetriever fallback; older devices use scaled extraction or the system MINI_KIND thumbnail. Decode errors show a placeholder, so corrupt/unreadable files cannot be guaranteed to have an image.

Cleanup: removed unreachable legacy picker, its file comparator, unused URL helper/test, animation-graphics dependency and obsolete imports. Retained FSAF, Seeker, font parser, Room and media session dependencies because player/settings use them. Enabled resource shrinking and limited native packaging to ARM32/ARM64, including the universal APK. Native mpv binaries and JNI keep rules retained.

Baseline 0.3 APK sizes: ARM64 24423427 bytes; ARM32 23658376 bytes. Final sizes and validation recorded after build.

Paths use the actual indexed DATA value when available, with RELATIVE_PATH fallback on modern Android; no absolute storage prefix is invented. Opening and thumbnails always use the content URI. Thumbnail cache keys include the MediaStore modification timestamp to refresh changed videos.

No physical device or Android emulator is connected; media permission behavior, pull gesture, real decoding, accessibility, storage changes and animation feel still require device validation. No claim of zero defects.

Additional size optimization: pinned NDK 27.0.12077973 in Gradle, CI and the cloud setup helper so AGP can strip native debug symbols. The old libc++ binary carried large .debug_* sections. No codec library is removed; post-build validation compares original and packaged native .text bytes and dynamic symbol names.

## Verified outcome

Gradle testDebugUnitTest, lintDebug, assembleDebug and assemblePreview passed. Seven tests passed, zero failures; lint reports zero errors, with warnings remaining. APK signatures and 16KB alignment passed. All 20 mpv native libraries retained byte-identical .text and .dynsym sections after AGP stripping.

| APK | Before (0.3) | After | Reduction |
| --- | --- | --- | --- |
| ARM64 | 24.42 MB | 21.83 MB | 10.6% |
| ARM32 | 23.66 MB | 21.16 MB | 10.6% |

Universal APK now includes only ARM32 and ARM64. This removes x86/x86_64 support from the universal package; the requested ARM packages retain their codec libraries.

Universal package: 34.06 MB, ARM32/ARM64 only. R8 removed-code and resource-reachability reports are retained as CI validation artifacts (usage.txt and resources.txt). Manual cleanup intentionally excludes classes referenced from XML and active settings/player dependencies.
