# MPV 0.14.1 — pause acknowledgement and rendered startup preparation

## What was wrong

The previous playback queue mixed pause/resume with settings, seeks and status reads. It presented the requested pause immediately, before the native player acknowledged it. Rapid pause/play could supersede the pause without ever stopping the engine. Integer playback time also concealed sub-second movement. These are confirmed code-level defects; no physical-device timing measurement was available.

The previous startup preparation composed and measured a few components in a zero-size layout without placing or drawing them. That did not prepare rendering paths. Its completion also replaced the loading composition with a newly constructed home. A short loading screen therefore did not establish that the UI had been rendered or settled.

## Playback changes

- Keep one native-command owner. Pause/resume commands have priority over queued settings; status reads are coalesced and queued keyed settings physically replace stale work.
- Preserve an acknowledged pause barrier before a subsequent rapid resume. Keep seek completion behind the final seek and invalidate old seek releases when media changes.
- Read the native pause state before completing a request. The central icon follows the known native state while a manual command is pending, with an accessible pending description and delayed progress indicator.
- Bound confirmation retries and report failure instead of silently presenting success or spinning indefinitely. Preserve user intent across unrelated status reads.
- Sample double-precision position for pause diagnostics; do not seek backwards to conceal movement. An already-running native operation cannot be preempted, and audio/video output buffering still needs physical-device measurement.

## Startup changes

Preparation is version-aware. First launch after this update has a minimum 3.8-second cover; later cold launches have a minimum 1.6-second cover. Actual preparation may take longer. The minimum is a reveal guard, not the preparation itself.

Six scenes run in separate frames with three poses each: navigation/library stretch and item movement; header/search and expanding settings; the real player pane, controls and seek bars; sort selection/order; text fields, buttons and a small thumbnail prefetch; and the official icon vectors in both styles, including RTL variants. Components are placed and drawn, and eighteen graphics-layer captures force rasterization even when the opaque cover would otherwise permit occlusion. Textures are bounded in size and owned capture bitmaps are released after each capture.

The real home is mounted once behind the cover, captured after its library layout, and retained when revealed. Vector parsing, Room opening and media loading run off the UI thread. Thumbnail prefetch uses the existing bounded loader and does not hold startup hostage. Hidden preparation cannot request search focus, trigger a keyboard, capture Back through the player pane, or expose interactive accessibility targets.

Preparation pauses when permissions or backgrounding remove the foreground window. A nine-second scene deadline, a bounded home capture and rendering-error fallback prevent an unsupported graphics path from trapping startup. `MPV-startup` records completed scenes/captures, elapsed time and failure class; it does not log paths or credentials. No hidden decoder, network subtitle request or API key is used.

This prepares shared UI/render paths; it is not a substitute for ART compilation, shader/cache behavior on each GPU, or physical-device frame-time profiling. It cannot guarantee every first-use animation is free of jank.

## Haptics

Removed the added global soft-tap provider, pointer feedback and slider ticks. Original system behavior and the pre-existing player long-press feedback remain. The home folder artwork and dimensions are unchanged.

## Validation

Playback tests cover queue priority, pause acknowledgement, rapid pause/play, delayed and rejected native responses, bounded failures, native errors, seek ordering and media changes. Native-rendering Robolectric tests exercise all eighteen captures, retained-home startup gating and unsupported-capture fallback. These are automated rendering and simulated-engine tests, not measurements of a phone's decoder, audio latency or frame rate.

Release validation runs the complete unit suite, Android Lint, the minified preview build, original-folder/icon checks, APK signature verification, 16 KB alignment checks and native-ABI inspection. GitHub publication attaches only `arm64-v8a.apk`, `armeabi-v7a.apk` and `universal.apk`.

Local results: 122 app tests passed, no failures or skipped tests; the Seeker module test passed. Android Lint reported zero errors and 455 warnings. Existing warnings are not claimed to have been eliminated by this change.
