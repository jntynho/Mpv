# Luma visual direction

![Luma concept](luma-concept.png)

This AI-generated concept is **not a runtime screenshot**. It communicates the navy/mint palette,
rounded cards and calmer hierarchy. The implementation uses real file/URL actions instead of sample
movies. The player controls retain mpvKt's layout and functionality with the shared Luma theme.
New strings include Arabic and English and follow the device locale. Android handles RTL.

To capture real screenshots after installing a build:

```sh
adb shell am start -n io.github.jntynho.luma.preview/live.mehiz.mpvkt.MainActivity
adb shell screencap -p /sdcard/luma-home.png
adb pull /sdcard/luma-home.png
```

Open Settings on the device and repeat for that screen. Test a local video, a network stream,
subtitle tracks, PiP, rotation and gesture controls on both ARM architectures before public release.
