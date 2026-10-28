# MPV 0.16 — mpvlib Android replacement

The runtime dependency is now `app.gyrolet.mpvlib:mpvlib:1.1.4`, replacing `io.github.abdallahmehiz:mpv-android-lib:0.1.9`. The application keeps its current UI, Iconsax styles, folder artwork, player preferences, coordinated pause behavior and thumbnail implementation.

## Artifact and build

- Official AAR: `https://github.com/Riteshp2001/mpvlibAndroid/releases/download/v1.1.4/mpvlib-1.1.4.aar`.
- SHA-256: `4abc71bfc1a0e0c8d5008e8fb3a8ccf83f3ced6e430a04b51156f75ae53bb285` (85,272,110 bytes).
- Kotlin/Compose compiler 2.4.21 and KSP 2.3.12 read the artifact's Kotlin 2.4 metadata. R8 9.5.28 is pinned separately because AGP 8.13’s bundled R8 cannot parse that Kotlin metadata; its parsing warnings are treated as a compatibility defect, not suppressed. Java 17 and compile/target SDK 36 remain.
- The app minimum is API 24 / Android 7, matching both the AAR manifest and native build. Android 5/6 support is not retained by this release.
- Only ARM64 and ARMv7 enter the APKs. The artifact's x86/x86_64 libraries are excluded by the existing ABI filters. Native libraries remain compressed to avoid changing download packaging merely because minSdk increased.
- `verifyMpvRuntime` checks the runtime dependency graph, rejects the old module and hashes the selected AAR. CI runs it before validation/build. No checksum file is attached to the application Release.

The producer publishes the AAR without a Maven POM. A supplemental POM, containing wrapper identity/license information only, is versioned in `gradle/mpvlib-metadata` and fetched from a pinned project commit for the AboutLibraries collector. The native runtime is downloaded unmodified from the producer; it is not replaced with a local fork. Bundled native components retain their upstream licenses.

The packaged version metadata reports mpv `0.41.0-dev-g3aecc359b-dirty` and FFmpeg `9.0.2`. A release number on the wrapper is not a guarantee of device-specific decoder stability.

## Runtime integration

Observer implementations accept the new `(eventId, MPVNode)` signature. Format/event/log constants use the new names. The existing playback coordinator still owns manual pause/resume, seeks and queued player actions.

`StablePropertySession` and `MpvProperties` keep application-owned flow identities. The new library clears its own property maps on destruction; retained view models and Compose collectors therefore must not depend on those map entries surviving. On attach, existing subscriptions are observed and seeded again. On detach, values reset and generation checks reject callbacks copied by an older native observer snapshot. Duplicate native formats are observed once. Missing node/property values become null rather than stale UI data.

Activity teardown unregisters the surface callbacks through `BaseMPVView.destroy()`, closes coordinated work before native destruction, and disables the old activity observer before late queued UI callbacks can run. The existing single-task activity and rotation configuration remain.

Native node data is converted structurally through kotlinx.serialization JSON elements. Quotes, backslashes, newlines, Unicode and 64-bit integer values are preserved when reading track/chapter metadata; the integration does not depend on the library's hand-built JSON string escaping. JNI keep rules explicitly cover the new private `destroyNative` entrypoint.

## Verification

Regression tests cover flow rebinding after native-session destruction, unavailable values, stale generation rejection, duplicate registrations and native-node decoding. Existing UI/settings/library, pause acknowledgement, seeking, focus and subtitle tests remain part of the suite.

APK validation checks the three expected architectures/packages, signatures, ZIP alignment, JNI entrypoints and ARM64 ELF alignment. Robolectric and simulated playback-engine tests do not execute the ARM decoder or measure a phone's GPU/audio behavior. Actual H.264/HEVC/AV1, HDR, rotation, PiP, streaming and audio-focus checks on ARM32/ARM64 devices remain necessary before claiming device-wide playback stability.

### Completed local validation

`verifyMpvRuntime testDebugUnitTest lintDebug assemblePreview` succeeded with R8 9.5.28. All 137 app tests and the Seeker test passed (138 total, no failures/skips). Lint reported zero errors; 534 app warnings and five warnings in each bundled supporting module remain. R8 no longer reports Kotlin-metadata parsing errors. It reports two build-time fallback warnings because AGP 8.13’s resource provider does not implement asynchronous parsing; Gradle/plugin deprecation warnings also remain.

The three compressed, signed preview APKs passed signature and ZIP alignment verification. Native entrypoints contain `destroyNative` and exclude the former `destroy` symbol; DEX retains the new method name. The packaged `libmpv.so` identifies the requested newer runtime. Every packaged ARM64 ELF PT_LOAD segment has alignment of at least 16 KB. ARM32 is checked for ARM ELF identity, not held to the ARM64 alignment requirement.

| APK | Version code | Bytes (local build) |
| --- | ---: | ---: |
| arm64-v8a | 202 | 31,305,889 |
| armeabi-v7a | 201 | 29,848,979 |
| universal (both ARM ABIs only) | 200 | 48,894,520 |

These are larger than 0.15 because the new runtime ships a different native codec/rendering stack. Native codecs or fonts were not removed to conceal the increase. Final CI packaging may vary slightly due to commit metadata and signing. The icon checker still reports 81 keys, 142 Outline/Broken vectors and six RTL variants, with the original home folder untouched.
