#!/usr/bin/env bash
set -euo pipefail
mpv_script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
mpv_work_dir="${MPV_NATIVE_WORK:-/tmp/mpv-pause-rebuild}"
mpv_ndk_dir="${MPV_NATIVE_NDK:?Set MPV_NATIVE_NDK to Android NDK 27.0.12077973}"
mpv_ca_bundle="${MPV_NATIVE_CA:-/etc/ssl/certs/ca-certificates.crt}"
test -f "$mpv_ndk_dir/source.properties"
grep -q '27\.0\.12077973' "$mpv_ndk_dir/source.properties"
python3 "$mpv_script_dir/prepare.py" --work "$mpv_work_dir"
# Keep registry credentials and client proxy configuration unchanged; only buildx state is local.
BUILDX_CONFIG="${BUILDX_CONFIG:-$mpv_work_dir/buildx}" docker build \
  --secret "id=proxy_ca,src=$mpv_ca_bundle" -t mpv-native-pause-toolchain "$mpv_script_dir"
for mpv_abi in arm64-v8a armeabi-v7a; do
  docker run --rm --user "$(id -u):$(id -g)" \
    --mount "type=bind,src=$mpv_work_dir,dst=/build" \
    --mount "type=bind,src=$mpv_ndk_dir,dst=/ndk,readonly" \
    --mount "type=bind,src=$mpv_ca_bundle,dst=/run/proxy-ca.pem,readonly" \
    -e SSL_CERT_FILE=/run/proxy-ca.pem -e GIT_SSL_CAINFO=/run/proxy-ca.pem \
    -e XDG_CACHE_HOME=/build/cache \
    mpv-native-pause-toolchain python3 /build/build_native.py "$mpv_abi"
done
