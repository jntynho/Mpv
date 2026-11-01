# libplayer.so looks up nativeHandle and private callbacks by exact name/signature.
# Upstream 0.1.12 changed these callbacks from public/static to private/instance.
-keep class is.xyz.mpv.MPV { *; }
