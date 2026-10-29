# mpv-android-lib 0.1.9 — native pause correction

The app resolves `io.github.abdallahmehiz:mpv-android-lib:0.1.9` from Maven Central. `prepareMpvRuntime` retains its original classes, JNI wrapper, Android resources/manifest and all FFmpeg/native support libraries. Only `libmpv.so` for ARM64 and ARMv7 is replaced. The generated AAR is a locally patched derivative, not an unmodified upstream publication. The removed `app.gyrolet.mpvlib` runtime is not used.

## Why the native change is needed

The upstream OpenSLES output has `reset` and `start` but no `set_pause`. The generic audio buffer therefore clears the hardware queue on pause. Samples already removed from mpv's queue can be discarded before being heard. A `pause=true` acknowledgement and frozen video `time-pos` do not establish that those samples survived.

`opensles-pause.patch` adds native `SL_PLAYSTATE_PAUSED` / `SL_PLAYSTATE_PLAYING` transitions, preserving queued samples. Explicit seeks/stops still call the original `reset` and clear stale audio. A fresh start explicitly restores `PLAYING`, including after a seek made while paused; clearing `hw_paused` during reset must not leave the OpenSL device paused. Failed state transitions request audio-output reload. Callback output's reported delay also freezes while hardware is paused; its pause/resume timestamps are accessed under the existing mutex. No fixed backwards seek, global mute, arbitrary delay or reduced audio buffer is used.

The callback's internal mutex is released before calling the Android API, so a synchronous state change can finish a callback without waiting on a lock held by that caller.

## Upstream provenance

`provenance.json` records the original AAR checksum, source revisions, patched binary checksums, API level and NDK version. `buildscripts/sources.lock.json` records archive checksums. The source revisions for mpv, FFmpeg headers, libass and libplacebo match the versions embedded in the original AAR; FFmpeg binaries themselves are reused unmodified. Lua keeps the producer's Android portability flags, including the old Android 32-bit file-seek fallback.

Both binaries retain all 54 public `mpv_*` entrypoints from the original library and the same required shared-library names. Codec binaries and formats are not removed. The native marker is `mpvkt-pause1`. API 21 is the minimum, and ARM64 load segments are aligned to 16 KB.

## Tests and their limits

Run `python3 scripts/check-native-pause.py` from the repository root. It extracts the actual upstream and patched C functions, compiles them with a simulated OpenSL device and clock, reproduces the old queue loss, and verifies preserved samples/delay after short and long pauses, repeated toggles, seeking/resetting while paused and playing, and native state errors. Original source fixtures retain their upstream copyright and GPL terms.

The harness is not Android playback or a physical audio/GPU measurement. The complete modified mpv source is separately cross-compiled for both ARM ABIs. Device validation of sound, frame presentation, routes/headphones, rotation, seeking and background playback remains required before claiming hardware-wide stability.

## Rebuild from source

Requirements: Linux, Docker, Python 3.12+, and Android NDK `27.0.12077973`. The helper `buildscripts/rebuild.sh` accepts `MPV_NATIVE_NDK`, `MPV_NATIVE_WORK` and `MPV_NATIVE_CA` and runs preparation plus both builds. Use a dedicated generated work directory. Source preparation verifies all downloaded archives and extracts the original Maven AAR support libraries:

```sh
python3 third_party/mpv-pause/buildscripts/prepare.py --work /tmp/mpv-pause-rebuild
```

Build the toolchain image from `buildscripts/Dockerfile`, supplying the system CA bundle as the BuildKit secret `proxy_ca` when needed. Preserve your configured proxy and registry credentials:

```sh
docker build --secret id=proxy_ca,src=/etc/ssl/certs/ca-certificates.crt \
  -t mpv-native-pause-toolchain third_party/mpv-pause/buildscripts
```

Run the image with `/tmp/mpv-pause-rebuild` mounted read/write at `/build`, the NDK directory read-only at `/ndk`, and the CA bundle read-only at `/run/proxy-ca.pem`. Use `SSL_CERT_FILE` and `GIT_SSL_CAINFO` pointing at that mounted bundle for any tool that needs network access. Run each architecture explicitly:

```sh
python3 /build/build_native.py arm64-v8a
python3 /build/build_native.py armeabi-v7a
```

The output is `/build/output/<abi>/libmpv.so`. Rebuilding may change build-date metadata; the checked-in hashes identify the binaries used in this release. Updating binaries requires reviewing and updating the recorded hashes/provenance, then rerunning the native regression, application tests, Lint, APK validation and device checks.

## License and complete corresponding source

mpv and these modifications are distributed under the applicable GPL-2.0-or-later terms; see `LICENSE.GPL` and `Copyright`. `LICENSE.LGPL` is retained for relevant upstream components. The patch, original fixtures, source URLs/checksums and build scripts in this directory provide the inputs to rebuild the modified library. Third-party dependency archives retain their own upstream licenses. The application wrapper and unmodified libraries retain the licenses of `mpv-android-lib:0.1.9`.
