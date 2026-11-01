# MPV runtime integration 0.1.12

The app declares `io.github.jntynho:mpv-android-lib:0.1.12`. Gradle substitutes this with the local `:mpv-android-lib` project. No public Maven publication is claimed or required. The original upstream author and native dependencies remain attributed in About → Libraries.

The upstream input is `io.github.abdallahmehiz:mpv-android-lib:0.1.12`, SHA-256 `bb1a007c545cc7ac3304293ae79866b5361a48449ee7648c4030d5355869effc`. Its Kotlin/Java classes, instance-based JNI wrapper, FFmpeg and supporting shared libraries are retained. Only the two ARM libmpv binaries are replaced by builds from matching mpv commit `76a5eba99`, preserving the queue/clock native pause correction. The native version marker is `0.41.0-174-g76a5eba99-jntynho-pause1`.

The old `MPVLib` singleton no longer exists in upstream 0.1.12. Existing app controls now use the local `MpvRuntime` boundary, bound to the same `MPV` instance owned by `BaseMPVView`. It translates observer events, rejects callbacks from retired instances, and waits for in-flight native accesses before allowing native destruction. Existing activity-owned property sessions and playback coordination remain in place. Upstream `MPVNode` representation is unchanged.

OpenSL ES with the native pause correction remains the default, maintaining the reviewed behavior and Android API 21 support. The new AAudio/AudioTrack implementations are present but are not made defaults without device validation. ARM64 shared-library load segments are 16KB aligned. Both rebuilt libraries export the same 54 public `mpv_*` symbols as upstream 0.1.12.

## Verification

- Native C regression: reproduces upstream pause queue loss and verifies preservation, long/short pauses, repeated toggles, underrun ordering, paused/playing seeks and state errors using simulated device/clock callbacks.
- Source preparation smoke: all source archive hashes verified, patch applied with zero fuzz, and resulting patched source/version inputs match the builds.
- 198 unit/UI tests passed: 194 app, 3 session-lifetime tests, 1 Seeker. The empty-history test explicitly drains Robolectric's paused Android Main handler after the IO operation.
- Gradle `verifyMpvRuntime` checks the local project coordinate, upstream input hash, absence of the removed runtime and byte preservation of all retained upstream AAR entries other than the two libmpv binaries.
- Final APK verification checks signatures/alignment, native checksums, instance JNI entrypoint, both ARM architecture variants and the universal APK.

These are host/Robolectric and simulated native checks. They do not execute ARM decoding or measure a physical Android device. Real-device comparisons of sound/video pause precision, Bluetooth/headphone routing, decoder modes, rotation, background playback and PiP remain necessary before claiming hardware-wide stability.

Source and rebuild instructions: [native package](../third_party/mpv-pause/README.md). Integration boundary: [local module](../third_party/mpv-android-lib/README.md).
