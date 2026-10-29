#!/usr/bin/env python3
"""Prepare checksum-pinned sources for the ARM pause fix; no binaries are downloaded from a fork."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tarfile
import urllib.request
import zipfile

HERE = Path(__file__).resolve().parent
parser = argparse.ArgumentParser()
parser.add_argument('--work', type=Path, required=True, help='Dedicated generated build directory')
args = parser.parse_args()
work = args.work.resolve()
work.mkdir(parents=True, exist_ok=True)
lock = json.loads((HERE/'sources.lock.json').read_text())


def download(name, url, digest):
    path = work/'downloads'/name
    path.parent.mkdir(exist_ok=True)
    if not path.exists():
        with urllib.request.urlopen(url, timeout=60) as response:
            data = response.read()
        if hashlib.sha256(data).hexdigest() != digest:
            raise ValueError('Source checksum mismatch: '+name)
        path.write_bytes(data)
    if hashlib.sha256(path.read_bytes()).hexdigest() != digest:
        raise ValueError('Cached source checksum mismatch: '+name)
    return path


def prepare(item):
    name, entry = item
    archive = download(name, entry['url'], entry['sha256'])
    destination = work/entry.get('target', 'src/'+name)
    destination.mkdir(parents=True, exist_ok=True)
    # Extract only into this package's generated source directories.
    with tarfile.open(archive) as source:
        for member in source.getmembers():
            parts = Path(member.name).parts
            if len(parts) < 2:
                continue
            member.name = str(Path(*parts[1:]))
            source.extract(member, destination, filter='data')
    print('Verified source:', name, flush=True)


# Re-extract base archives before nested submodules, so no extraction races occur.
base = [(name, entry) for name, entry in lock.items() if 'target' not in entry]
nested = [(name, entry) for name, entry in lock.items() if 'target' in entry]
list(ThreadPoolExecutor(max_workers=4).map(prepare, base))
list(ThreadPoolExecutor(max_workers=2).map(prepare, nested))

provenance = json.loads((HERE.parent/'provenance.json').read_text())
coordinate = 'io/github/abdallahmehiz/mpv-android-lib/0.1.9/mpv-android-lib-0.1.9.aar'
aar = download('mpv-android-lib-0.1.9.aar', 'https://repo.maven.apache.org/maven2/'+coordinate, provenance['originalAarSha256'])
with zipfile.ZipFile(aar) as source:
    for name in source.namelist():
        if name.startswith(('jni/arm64-v8a/', 'jni/armeabi-v7a/')) and name.endswith('.so'):
            destination = work/'original'/name.removeprefix('jni/')
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(source.read(name))

# Applying the patch must be exact; an upstream source change cannot silently select new context.
with (HERE.parent/'opensles-pause.patch').open('rb') as patch:
    subprocess.run(['patch', '--batch', '--fuzz=0', '-p1', '-d', str(work/'src/mpv')], stdin=patch, check=True)
(work/'src/mpv/MPV_VERSION').write_text(provenance['nativeVersion']+'\n')
shutil.copy2(HERE/'build_native.py', work/'build_native.py')
print('Prepared exact upstream runtime and patched mpv sources in', work)
