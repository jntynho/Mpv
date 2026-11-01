<div align="center">
  <img src="docs/assets/app-icon.svg" alt="MPV app icon" width="112" height="112" />

  <h1>MPV</h1>
  <p>A clean, customisable Android video player powered by libmpv.</p>

  <a href="https://github.com/jntynho/Mpv/releases/latest"><img src="https://img.shields.io/github/v/release/jntynho/Mpv?style=flat-square" alt="Latest release" /></a>
  <a href="https://github.com/jntynho/Mpv/actions/workflows/build-apk.yml"><img src="https://github.com/jntynho/Mpv/actions/workflows/build-apk.yml/badge.svg" alt="APK build" /></a>
  <img src="https://img.shields.io/badge/Android-5.0%2B-3DDC84?style=flat-square" alt="Android 5.0 or newer" />

  <p>
    <a href="#download">Download</a> ·
    <a href="#features">Features</a> ·
    <a href="#build-from-source">Build from source</a> ·
    <a href="#credits--licensing">Credits</a>
  </p>
</div>

## Overview

MPV combines the playback capabilities of **libmpv** with an interface built in **Kotlin and Jetpack Compose**. Browse your video library, adjust playback through side panels, and customise the app's appearance and controls.

The project is derived from [mpvKt](https://github.com/abdallahmehiz/mpvKt). Its Android integration is maintained here under the local coordinate `io.github.jntynho:mpv-android-lib:0.1.12`, retaining attribution to the upstream library and native components.

## Download

Get installable APKs from the [latest GitHub Release](https://github.com/jntynho/Mpv/releases/latest). Choose the variant that matches your device:

| APK | Recommended for | Download |
| --- | --- | --- |
| `arm64-v8a.apk` | Most modern Android phones and tablets with a 64-bit ARM system | [ARM64 APK](https://github.com/jntynho/Mpv/releases/latest/download/arm64-v8a.apk) |
| `armeabi-v7a.apk` | Devices running a 32-bit ARM system | [ARM32 APK](https://github.com/jntynho/Mpv/releases/latest/download/armeabi-v7a.apk) |
| `universal.apk` | Both supported ARM architectures, when you are unsure which to choose | [Universal APK](https://github.com/jntynho/Mpv/releases/latest/download/universal.apk) |

**Requirements:** Android 5.0 / API 21 or newer. The universal APK contains ARM64 and ARM32; x86 and x86_64 are not included in these app builds. APK downloads require no ZIP extraction.

> **Signing and updates:** Current CI builds use automatic test signing, not a permanent production key. Certificates may differ between builds, which can prevent installing an update over an existing copy. Before uninstalling, export private media from Secure Folder and verify your copies. Uninstalling deletes private files and device-bound encryption keys; a settings backup does not contain that media.

## Features

- **Video library:** Folder, Tree and Videos views, search, sorting, list/card layouts, configurable metadata fields and pull-to-refresh.
- **Playback controls:** Hardware/software decoding choices, playback speed, chapters, seekbar styles, gesture controls and configurable landscape controls.
- **Player panels:** Audio tracks, subtitle tracks, delays, video filters and a sleep timer inside a persistent side panel.
- **Subtitles:** Primary and secondary tracks, styling controls, online search with movie/series results, and an in-panel external subtitle browser.
- **Appearance:** Dark, Light and System modes, colour accents, OLED black, and Iconsax Outline/Broken styles.
- **Thumbnails:** Configurable source, quality and capture position, with cover placeholders when images are disabled or unavailable.
- **Quick actions:** Open a file, resume the most recently played video, or open a supported stream URL.
- **Background and multitasking:** Background playback, media controls and Picture-in-Picture.
- **Settings and private media:** XML settings backup/restore and Secure Folder functionality.

Local playback does not require an API key. Online subtitle providers may require configuration and depend on their service availability. Android file permissions determine which folders and documents the app can access; protected system and app-private locations remain restricted.

## Playback engine

| Component | Current integration |
| --- | --- |
| App-facing module | `io.github.jntynho:mpv-android-lib:0.1.12` — resolved to a local Gradle project |
| Upstream Android wrapper | `io.github.abdallahmehiz:mpv-android-lib:0.1.12` |
| Native core | mpv `0.41.0-174-g76a5eba99`, rebuilt for ARM64 and ARM32 with the queue-preserving pause correction |
| Native support libraries | FFmpeg and supporting shared libraries retained from the upstream 0.1.12 AAR |
| Default audio output | OpenSL ES with the native pause correction |

The local coordinate is not published to Maven Central. It identifies this repository's integration module, not a transfer of authorship of mpv or its dependencies.

The integration binds controls to the view-owned MPV instance, protects in-flight native calls during teardown, and retains the private JNI callbacks required after code shrinking. The modified native runtime has pinned source inputs, checksums and rebuild instructions.

- [Integration and migration details](docs/MPVLIB-0.1.12.md)
- [Local Android module](third_party/mpv-android-lib/README.md)
- [Native patch, sources and rebuild instructions](third_party/mpv-pause/README.md)

## Build from source

### Prerequisites

- JDK **17**
- Android SDK Platform **36**
- Android Build Tools **35.0.0**
- Android NDK **27.0.12077973**
- Python **3** and a host C compiler for the native regression check
- Access to Google Maven, Maven Central, Gradle distribution servers and GitHub

Set `ANDROID_HOME` to your Android SDK installation, then run:

```bash
git clone https://github.com/jntynho/Mpv.git
cd Mpv

python3 scripts/check-icon-assets.py
python3 scripts/check-native-pause.py

bash ./gradlew --no-daemon --max-workers=1 \
  verifyMpvRuntime testDebugUnitTest lintDebug assemblePreview

python3 scripts/check-mpv-apks.py
```

The APKs are generated in:

```text
app/build/outputs/apk/preview/
├── app-arm64-v8a-preview.apk
├── app-armeabi-v7a-preview.apk
└── app-universal-preview.apk
```

Preview builds use the application ID `io.github.jntynho.luma.preview`. The reviewed ARM native binaries are included in the repository, so an ordinary app build does not need to rebuild mpv from source. Follow the separate native rebuild guide when changing the engine or its patch.

### GitHub Actions

[Build MPV APKs](.github/workflows/build-apk.yml) runs validation and builds the three APK variants. It can also be started from **Actions → Build MPV APKs → Run workflow**.

[Publish verified APK release](.github/workflows/publish-release.yml) publishes successful eligible `main` builds as actual GitHub Releases, with exactly these attached APK assets:

```text
arm64-v8a.apk
armeabi-v7a.apk
universal.apk
```

No custom ZIP or `SHA256SUMS.txt` asset is attached. Publication uses GitHub's automatic workflow token; user-configured Secrets are not required. Pull-request validation does not publish a release.

## Validation

The build checks the upstream runtime checksum, native provenance, icon catalogue, unit/UI tests, Android Lint, APK architecture contents, signatures and alignment. Final DEX inspection verifies that code shrinking has preserved the native context field and JNI callback signatures.

Native regression tests use a simulated audio device and clock. UI tests use Robolectric. These checks do not replace physical-device testing of audio/video synchronisation, Bluetooth routing, decoding, rotation, background playback and PiP.

## Contributing

Bug reports and focused improvements are welcome through [Issues](https://github.com/jntynho/Mpv/issues) and pull requests.

For playback problems, include your device, Android version, app build, decoder mode, media format and clear reproduction steps. Share a short sample only if you have permission to distribute it, and remove personal data from logs.

## Credits & licensing

- [mpvKt](https://github.com/abdallahmehiz/mpvKt) — the original application foundation.
- [mpv-android](https://github.com/abdallahmehiz/mpv-android) and its contributors — upstream Android integration.
- [mpv](https://github.com/mpv-player/mpv), [FFmpeg](https://github.com/FFmpeg/FFmpeg), [libass](https://github.com/libass/libass) and [libplacebo](https://github.com/haasn/libplacebo) — playback, decoding, subtitles and rendering.
- [Iconsax](https://github.com/lusaxweb/iconsax) — Outline and Broken icon artwork; see the notices in [assets/licenses](app/src/main/assets/licenses).
- Seeker and FSAF — supporting components maintained in [third_party](third_party) with their license notices.

See the repository's [Apache License 2.0](LICENSE) for the application source. Dependencies and separately licensed components retain their own terms. The modified libmpv runtime is distributed under **GPL-2.0-or-later**; its source inputs, patch, notices and build instructions are available in [third_party/mpv-pause](third_party/mpv-pause). The local jntynho integration module has its own [MIT license](third_party/mpv-android-lib/LICENSE).
