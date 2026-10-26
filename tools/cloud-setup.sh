#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export GRADLE_USER_HOME=/workspace/.cache/gradle
export ANDROID_HOME=/workspace/android-sdk
export ANDROID_USER_HOME=/workspace/android-user
export JAVA_HOME=/workspace/toolchains/jdk-17.0.17+10
mkdir -p "$GRADLE_USER_HOME" "$ANDROID_HOME" "$ANDROID_USER_HOME" /workspace/toolchains
if [ ! -x "$JAVA_HOME/bin/javac" ]; then
  base='https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.17%2B10'
  curl -fLsS "$base/OpenJDK17U-jdk_x64_linux_hotspot_17.0.17_10.tar.gz" -o /tmp/luma-jdk.tar.gz
  curl -fLsS "$base/OpenJDK17U-jdk_x64_linux_hotspot_17.0.17_10.tar.gz.sha256.txt" -o /tmp/luma-jdk.sha256
  python3 - <<'PY'
import hashlib,pathlib
assert hashlib.sha256(pathlib.Path('/tmp/luma-jdk.tar.gz').read_bytes()).hexdigest() == pathlib.Path('/tmp/luma-jdk.sha256').read_text().split()[0]
PY
  tar -xzf /tmp/luma-jdk.tar.gz -C /workspace/toolchains
fi
# Keep machine-specific proxy/trust settings outside the checkout; never persist credentials.
python3 - <<'PY'
import os,urllib.parse,pathlib
p=urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY',''))
f=pathlib.Path(os.environ['GRADLE_USER_HOME'])/'gradle.properties'
existing=f.read_text() if f.exists() else ''
lines=[]
if p.hostname:
 for proto in ('http','https'):
  lines += [f'systemProp.{proto}.proxyHost={p.hostname}', f'systemProp.{proto}.proxyPort={p.port or 80}']
if pathlib.Path('/etc/ssl/certs/java/cacerts').exists():
 lines += ['systemProp.javax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts']
for line in lines:
 key=line.split('=',1)[0]
 if not any(x.startswith(key+'=') for x in existing.splitlines()): existing += '\n'+line
f.write_text(existing+'\n')
PY
if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  curl -fLsS https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip -o /tmp/luma-android-tools.zip
  unpack=$(mktemp -d /tmp/luma-sdk.XXXXXX)
  unzip -q /tmp/luma-android-tools.zip -d "$unpack"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  mv "$unpack/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rmdir "$unpack"
fi
python3 - <<'PY'
import os,urllib.parse,subprocess
p=urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY','')); e=os.environ.copy()
opts=f'-Duser.home={os.environ["ANDROID_USER_HOME"]}'
if p.hostname:
 opts += f' -Dhttps.proxyHost={p.hostname} -Dhttps.proxyPort={p.port or 80} -Dhttp.proxyHost={p.hostname} -Dhttp.proxyPort={p.port or 80}'
if os.path.exists('/etc/ssl/certs/java/cacerts'):
 opts += ' -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts'
e['JAVA_TOOL_OPTIONS']=opts
sdk=[os.environ['ANDROID_HOME']+'/cmdline-tools/latest/bin/sdkmanager','--sdk_root='+os.environ['ANDROID_HOME']]
subprocess.run(sdk+['--licenses'],input='y\n'*100,text=True,env=e,check=True)
subprocess.run(sdk+['platforms;android-36','build-tools;35.0.0','platform-tools'],env=e,check=True)
PY
bash ./gradlew --no-daemon --max-workers=2 testDebugUnitTest lintDebug assembleDebug assemblePreview
