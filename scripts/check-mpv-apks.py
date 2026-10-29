#!/usr/bin/env python3
"""Verify that each installable APK actually packages the reviewed native pause fix."""
from pathlib import Path
import hashlib
import json
import os
import re
import struct
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT/'app/build/outputs/apk/preview'
PROVENANCE = json.loads((ROOT/'third_party/mpv-pause/provenance.json').read_text())
AAPT = Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0/aapt'


def allocated_sections(data):
    """LLVM may strip libc++ debug sections; its complete loadable content must remain identical."""
    is64 = data[4] == 2
    offset = struct.unpack_from('<Q' if is64 else '<I', data, 40 if is64 else 32)[0]
    size, count, strings = struct.unpack_from('<HHH', data, 58 if is64 else 46)
    sections = [struct.unpack_from('<IIQQQQIIQQ' if is64 else '<IIIIIIIIII', data, offset+i*size)
                for i in range(count)]
    string_header = sections[strings]
    names = data[string_header[4]:string_header[4]+string_header[5]]
    result = {}
    for section in sections:
        name = names[section[0]:].split(b'\0', 1)[0].decode()
        if section[2] & 2:  # SHF_ALLOC
            payload = b'' if section[1] == 8 else data[section[4]:section[4]+section[5]]
            result[name] = (section[1], section[2], section[3], section[5], payload)
    return result


metadata = json.loads((OUTPUT/'output-metadata.json').read_text())
expected_architectures = {'arm64-v8a': {'arm64-v8a'}, 'armeabi-v7a': {'armeabi-v7a'},
                          'universal': {'arm64-v8a', 'armeabi-v7a'}}
assert len(metadata['elements']) == 3
with zipfile.ZipFile(ROOT/'app/build/generated/mpv-runtime/mpv-android-lib-0.1.9-pause1.aar') as runtime:
    for element in metadata['elements']:
        path = OUTPUT/element['outputFile']
        kind = next(kind for kind in expected_architectures if path.name == f'app-{kind}-preview.apk')
        badging = subprocess.check_output([str(AAPT), 'dump', 'badging', str(path)], text=True)
        assert "name='io.github.jntynho.luma.preview'" in badging
        assert "sdkVersion:'21'" in badging
        assert f"versionCode='{element['versionCode']}'" in badging
        assert f"versionName='{element['versionName']}'" in badging
        with zipfile.ZipFile(path) as apk:
            native = [name for name in apk.namelist() if name.startswith('lib/') and name.endswith('.so')]
            abis = {name.split('/')[1] for name in native}
            assert abis == expected_architectures[kind]
            for name in native:
                abi = name.split('/')[1]
                data = apk.read(name)
                assert data[:4] == b'\x7fELF' and data[5] == 1
                assert struct.unpack_from('<H', data, 18)[0] == (183 if abi == 'arm64-v8a' else 40)
                assert apk.getinfo(name).compress_type == 8
                if abi == 'arm64-v8a':
                    offset = struct.unpack_from('<Q', data, 32)[0]
                    size, count = struct.unpack_from('<HH', data, 54)
                    alignments = [struct.unpack_from('<Q', data, offset+i*size+48)[0]
                                  for i in range(count) if struct.unpack_from('<I', data, offset+i*size)[0] == 1]
                    assert alignments and min(alignments) >= 16384
                library = Path(name).name
                if library == 'libmpv.so':
                    assert hashlib.sha256(data).hexdigest() == PROVENANCE['binaries'][abi]['sha256']
                    assert b'mpvkt-pause1' in data and b'Failed to change native pause state' in data
                if library == 'libplayer.so':
                    assert b'Java_is_xyz_mpv_MPVLib_destroy\0' in data
                    assert b'Java_is_xyz_mpv_MPVLib_destroyNative\0' not in data
                if library != 'libandroidx.graphics.path.so':
                    upstream = runtime.read('jni/'+name.removeprefix('lib/'))
                    if library == 'libc++_shared.so':
                        assert allocated_sections(data) == allocated_sections(upstream), name
                    else:
                        assert data == upstream, name
            dex = [apk.read(name) for name in apk.namelist() if re.fullmatch(r'classes\d*\.dex', name)]
            assert dex and not any(b'destroyNative' in data for data in dex)
        print(json.dumps({'apk': path.name, 'bytes': path.stat().st_size, 'abis': sorted(abis),
                          'versionCode': element['versionCode'], 'nativePauseFix': 'verified',
                          'otherRuntimeContent': 'unchanged (libc++ debug stripping allowed)',
                          'minSdk': 21}), flush=True)
print('PASS: all three APKs contain the exact reviewed ARM native fix and original runtime support libraries.')
