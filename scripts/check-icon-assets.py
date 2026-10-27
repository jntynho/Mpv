#!/usr/bin/env python3
"""Validate the shared icon catalogue and the user's unchanged home-folder asset."""
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
A = '{http://schemas.android.com/apk/res/android}'
res = ROOT / 'app/src/main/res/drawable'
manifest = json.loads((ROOT / 'third_party/iconsax/selection.json').read_text())['icons']
styles = {}
regular = set()
for entry in manifest:
    key, style, name = entry['key'], entry['style'], entry['resource']
    styles.setdefault(key, set()).add(style)
    regular.add(name)
    path = res / (name + '.xml')
    assert hashlib.sha256(path.read_bytes()).hexdigest() == entry['sha256'], path
    root = ET.parse(path).getroot()
    assert root.get(A+'viewportWidth') == root.get(A+'viewportHeight') == '24', path
    assert list(root), path
    for p in root:
        assert p.tag == 'path' and p.get(A+'pathData'), path
        assert not re.search(r'(?<![0-9])\.(?=\d)', p.get(A+'pathData')), path
        assert p.get(A+'fillColor') in {'#FF000000', '#00000000'}, path
    assert entry['source'].startswith('Svg/All/'+style+'/'), entry
assert all(s == {'outline','broken'} for s in styles.values())
rtl = set()
for entry in manifest:
    if entry['key'] not in {'ArrowBack','FormatAlignLeft','FormatAlignRight'}: continue
    name = entry['resource'] + '_rtl'
    rtl.add(name)
    tree = ET.parse(res/(name+'.xml')).getroot()
    assert tree.get(A+'autoMirrored') == 'true', name
    normal = ET.parse(res/(entry['resource']+'.xml')).getroot()
    assert [x.attrib for x in tree] == [x.attrib for x in normal], name
assert {p.stem for p in res.glob('iconsax_*.xml')} == regular | rtl
folder = res/'library_folder.xml'
assert hashlib.sha256(folder.read_bytes()).hexdigest() == '3a38cf0350069410870c3fbe51314d4e4f6c6cb2de1beaecf3f2e0c530f786b4', 'The home folder must remain unchanged'
home = (ROOT/'app/src/main/java/live/mehiz/mpvkt/ui/home/HomeScreen.kt').read_text()
assert 'Image(painterResource(R.drawable.library_folder), null, Modifier.size(width = 88.dp, height = 76.dp))' in home
assert not list(res.glob('fa_*.xml'))
assert not list((ROOT/'app/src/main/java').rglob('FontAwesomeIcons.kt'))
assert not (ROOT/'app/src/main/assets/licenses/fontawesome.txt').exists()
assert (ROOT/'app/src/main/assets/licenses/folder-artwork.txt').is_file()
print(f'PASS: {len(styles)} keys, {len(regular)} Outline/Broken vectors, {len(rtl)} RTL variants; original home folder unchanged')
