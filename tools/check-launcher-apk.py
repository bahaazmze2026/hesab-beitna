"""Verify final compiled launcher PNGs retain exported geometry and RGBA pixels."""
from pathlib import Path
from io import BytesIO
import hashlib
import json
import re
import sys
import zipfile
from PIL import Image

root = Path(__file__).resolve().parents[1]
families = {'ic_launcher', 'ic_launcher_round', 'ic_launcher_foreground', 'ic_launcher_monochrome'}
verified = []
with zipfile.ZipFile(sys.argv[1]) as apk:
    for name in apk.namelist():
        match = re.fullmatch(r'res/mipmap-(mdpi|hdpi|xhdpi|xxhdpi|xxxhdpi)(?:-v\d+)?/(ic_launcher(?:_round|_foreground|_monochrome)?).png', name)
        if not match:
            continue
        density, family = match.groups()
        actual = Image.open(BytesIO(apk.read(name))).convert('RGBA')
        expected = Image.open(root / f'app/src/main/res/mipmap-{density}/{family}.png').convert('RGBA')
        assert actual.size == expected.size and actual.tobytes() == expected.tobytes(), name
        verified.append((density, family))
    assert set(verified) == {(d, f) for d in ['mdpi', 'hdpi', 'xhdpi', 'xxhdpi', 'xxxhdpi'] for f in families}, verified
    brand = next(n for n in apk.namelist() if re.fullmatch(r'res/drawable-nodpi(?:-v\d+)?/brand_cat.png', n))
    actual_brand = Image.open(BytesIO(apk.read(brand))).convert('RGBA')
    original = Image.open(root / 'tools/icon-source/Mew-Icon-Isolated.png').convert('RGBA')
    assert actual_brand.size == original.size and actual_brand.tobytes() == original.tobytes(), brand
    for density in ['mdpi', 'hdpi', 'xhdpi', 'xxhdpi', 'xxxhdpi']:
        name = next(n for n in apk.namelist() if re.fullmatch(rf'res/drawable-{density}(?:-v\d+)?/notification_logo.png', n))
        actual = Image.open(BytesIO(apk.read(name))).convert('RGBA')
        expected = Image.open(root / f'app/src/main/res/drawable-{density}/notification_logo.png').convert('RGBA')
        assert actual.size == expected.size and actual.tobytes() == expected.tobytes(), name
    assert not any('drawable-nodpi' in n and 'ic_launcher_foreground' in n for n in apk.namelist())
print(json.dumps({'compiled_launcher_images_verified': len(verified),
                  'source_png_sha256': hashlib.sha256((root / 'tools/icon-source/Mew-Icon-Isolated.png').read_bytes()).hexdigest(),
                  'result': 'PASS: launcher, in-app original and notification PNGs verified; old foreground absent'}, indent=2))
