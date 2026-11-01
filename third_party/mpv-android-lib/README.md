# jntynho MPV Android integration 0.1.12

Local Gradle coordinate: `io.github.jntynho:mpv-android-lib:0.1.12`.

This repository substitutes that coordinate with the `:mpv-android-lib` project. It is **not published to Maven Central**. The module contains jntynho's integration boundary and session lifetime protection. It adapts the upstream `io.github.abdallahmehiz:mpv-android-lib:0.1.12` MPV instance API to the application's existing controls and property ownership model; upstream authorship is retained.

The `MPV` object belongs to `BaseMPVView`. The adapter creates no competing native engine. Binding/releasing is serialized, in-flight native calls finish before destruction, listeners are removed on release, and late callbacks from retired instances are ignored. A new view uses a fresh MPV instance: an instance whose upstream coroutine session was destroyed is never recycled.

The app excludes the module's transitive upstream AAR and packages the checksum-verified derivative prepared by `prepareMpvRuntime`. That derivative retains the upstream 0.1.12 Java/Kotlin classes, JNI wrapper, FFmpeg and other shared libraries, while replacing only ARM libmpv with matching 0.41 source builds carrying the queue-preserving pause correction. See [native sources and build instructions](../mpv-pause/README.md).

The reviewed OpenSL ES output remains the default, preserving behavior on Android API 21+. AAudio and AudioTrack are present in the engine; changing audio backend defaults requires device comparison. Advanced user configuration can override the default.

The adapter is MIT licensed; the upstream wrapper and each native dependency retain their original licenses. The modified libmpv distribution remains subject to its GPL terms. The integration coordinate does not reattribute mpv, FFmpeg, libass or the upstream Android wrapper to jntynho.
