# MPV 0.17.1 — restore mpv-android-lib and preserve paused audio

The runtime source dependency is again `io.github.abdallahmehiz:mpv-android-lib:0.1.9`. `app.gyrolet.mpvlib:mpvlib`, its exclusive repository and supplemental POM are removed. Kotlin/Compose compiler 2.2.20, KSP 2.2.20-2.0.3 and AGP's bundled R8 are restored. Minimum Android is API 21 / Android 5.0 again.

The application keeps its UI, Iconsax Outline/Broken catalogue, original home folder, preferences, acknowledged playback coordinator and activity-owned property subscriptions. Observer signatures and constants match the restored library.

## The native failure being addressed

The old AAR bundles mpv `0.40.0-276-g7653cc8f0` with OpenSLES as its Android audio output. OpenSLES exposes reset/start, but no native pause callback. The generic mpv audio buffer falls back to reset on pause, clearing the device queue. Samples consumed from mpv's queue but not yet heard can be lost; a later Play can advance the audio reference and video catches up. A `pause=true` property or a frozen video `time-pos` does not establish that the audio samples were preserved.

The previous simulated PlaybackEngine test assumed that confirmed Pause froze playback correctly. It did not test the Android output queue. The new C regression test exercises the actual upstream buffer functions and OpenSLES functions with a simulated device/clock, so it explicitly checks that missing behavior.

## Native correction

- OpenSLES implements `set_pause` with `SL_PLAYSTATE_PAUSED` / `SL_PLAYSTATE_PLAYING`, preserving queued audio.
- Ordinary Pause does not clear/reset the buffer. Seeks and actual stops still clear stale audio.
- A fresh start sets the OpenSL device back to PLAYING. This matters after seeking while paused: generic AO reset clears `hw_paused`, but the physical player can still be paused. A targeted regression test exposed this extra case during review, and the final implementation fixes it.
- Callback output reports a frozen delay while hardware is paused, and accesses pause/resume timestamps under the existing mutex.
- Android state changes run outside the generic buffer mutex to avoid blocking a callback that needs that mutex. Failed state changes request AO reload.
- An output underrun can leave the hardware paused even after logical unpause. A subsequent AO start now restores the preserved clock and resumes hardware instead of re-priming an existing buffer. Both orderings of data readiness versus Play are regression-tested.
- No fixed backwards seek, fabricated time, added delay, global mute or arbitrary buffer reduction is used.

Both ARM libmpv binaries were rebuilt from the old engine revision with matching libass, libplacebo and Lua versions. FFmpeg shared libraries, the Kotlin classes, JNI wrapper, resources and manifest are copied byte-for-byte from the original AAR. All 54 public mpv API entrypoints and the original shared-library dependency names remain available.

This is a patched derivative of 0.1.9, not a claim that the published upstream AAR already contains the fix. Build task `prepareMpvRuntime` resolves and hashes the original Maven artifact, then replaces only the two ARM `libmpv.so` entries in a generated AAR. `verifyMpvRuntime` rejects gyrolet, verifies the original coordinate/version/checksum, and checks that all other retained AAR entries are unchanged. Native binaries and source patch checksums are checked separately in CI.

## Native regression and source availability

`python3 scripts/check-native-pause.py` verifies the binary/source provenance, ARM ELF identities, native version marker and ARM64 16 KB alignment, then compiles the original/patched C functions. It tests 1/10/100/1000/20000ms pauses, native queue preservation, frozen delay, 100 repeated toggles per duration, seek/reset while paused and playing, and native state failures. In the simulated device with 125ms queued audio, the old path loses up to 125ms; the patched path preserves it. This number is an experiment input, not a phone measurement.

The [native source package](../third_party/mpv-pause/README.md) includes the patch, upstream fixtures, licenses, source archive URLs/hashes, binary provenance and tested rebuild scripts. A fresh source-preparation smoke check produced the same patched C sources used for both ARM builds. About → Libraries retains the original wrapper attribution and adds the modified native runtime's GPL notice/source link.

## Completed local validation

`verifyMpvRuntime testDebugUnitTest lintDebug assemblePreview` passed. All 137 app tests and the Seeker test passed (138 total, zero failures/errors/skips). Lint reported zero errors, with 524 existing/application warnings and two/four warnings in FSAF/Seeker respectively. Gradle/plugin deprecation warnings remain; Kotlin metadata parsing warnings from the newer toolchain are gone.

Native regression/provenance checks and the source-preparation smoke check passed. The icon catalogue check still reports 81 keys, 142 Outline/Broken vectors and six RTL variants, with the original home folder unchanged.

All three APKs passed signature verification and ZIP alignment checks. APK inspection confirms the exact checked-in native fix hashes, old JNI destroy entrypoint, API 21 minimum and expected ABI sets. FFmpeg and the JNI wrapper are byte-identical to the prepared upstream runtime. Normal stripping changes libc++ debug metadata, while every allocated ELF section remains identical.

| APK | Version code | Local bytes |
| --- | ---: | ---: |
| arm64-v8a | 222 | 20,784,823 |
| armeabi-v7a | 221 | 20,145,333 |
| universal (both ARM ABIs only) | 220 | 32,949,929 |

CI reruns the native regression, dependency/provenance check, application tests, Lint, build, APK signatures/alignment and native packaging checks before publication. Commit metadata and automatic signing may cause slight final size differences.

## Device limits

The full modified native library is cross-compiled and linked for ARM64/ARMv7, and the application is built and checked separately. The native regression harness uses simulated hardware; Robolectric application tests do not execute the ARM decoder or measure a phone's sound/video presentation. There is no connected Android device or emulator. Playback on a real device, especially speaker/headphone/Bluetooth routes, decoder modes, long pauses, seek while paused, rotation, PiP and focus changes still requires confirmation. The change fixes the identified reset/queue-loss path; it is not a claim of zero latency on every device.

## Release

0.17.1 supersedes the first 0.17.0 build with the underrun-resume guard. It preserves the hardware queue and clock whether decoded data becomes ready before or after Play, rather than re-priming a preserved buffer.

Release assets remain exactly `arm64-v8a.apk`, `armeabi-v7a.apk` and `universal.apk`. No custom ZIP or SHA256SUMS asset is published. Automatic test signing is unchanged.
