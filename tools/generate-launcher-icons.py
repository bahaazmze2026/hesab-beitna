"""Deterministic launcher-only exports; never redraw, recolor or crop the source."""
from pathlib import Path
import hashlib
import json
import math
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
source = ROOT / 'tools/icon-source/Mew-Icon-Isolated.png'
image = Image.open(source)
assert image.format == 'PNG' and image.mode == 'RGBA'
assert image.getchannel('A').getextrema() == (0, 255)
ivory = (255, 248, 239, 255)
alpha = image.getchannel('A')
cx, cy = image.width / 2, image.height / 2
radius = max(math.hypot(x + .5 - cx, y + .5 - cy)
             for y in range(image.height) for x in range(image.width)
             if alpha.getpixel((x, y)))
# 30 dp radius leaves a 3 dp margin inside Android's guaranteed 33 dp circle.
art_dp = min(image.size) * 30 / radius
results = []
for density, multiplier, legacy in [('mdpi', 1, 48), ('hdpi', 1.5, 72),
                                    ('xhdpi', 2, 96), ('xxhdpi', 3, 144), ('xxxhdpi', 4, 192)]:
    folder = ROOT / f'app/src/main/res/mipmap-{density}'
    folder.mkdir(parents=True, exist_ok=True)
    size = round(108 * multiplier)
    art = image.resize((round(image.width * art_dp / image.width * multiplier),
                        round(image.height * art_dp / image.width * multiplier)), Image.Resampling.LANCZOS)
    foreground = Image.new('RGBA', (size, size))
    foreground.alpha_composite(art, ((size - art.width) // 2, (size - art.height) // 2))
    # Include interpolation fringe in the safe-zone check. No alpha pixels get discarded.
    a = foreground.getchannel('A')
    maximum = max(math.hypot(x + .5 - size / 2, y + .5 - size / 2)
                  for y in range(size) for x in range(size) if a.getpixel((x, y)))
    assert maximum <= 33 * multiplier, (density, maximum)
    foreground.save(folder / 'ic_launcher_foreground.png')
    monochrome = Image.new('RGBA', foreground.size, (255, 255, 255, 0))
    monochrome.putalpha(a)
    monochrome.save(folder / 'ic_launcher_monochrome.png')
    plate = Image.new('RGBA', foreground.size, ivory)
    plate.alpha_composite(foreground)
    inset = round(18 * multiplier)
    plate = plate.crop((inset, inset, size - inset, size - inset)).resize((legacy, legacy), Image.Resampling.LANCZOS)
    plate.save(folder / 'ic_launcher.png')
    mask = Image.new('L', (legacy, legacy)); ImageDraw.Draw(mask).ellipse((0, 0, legacy - 1, legacy - 1), fill=255)
    rounded = plate.copy(); rounded.putalpha(mask); rounded.save(folder / 'ic_launcher_round.png')
    results.append({'density': density, 'foreground_px': size, 'legacy_px': legacy,
                    'max_alpha_radius_dp': round(maximum / multiplier, 3)})
print(json.dumps({'source_sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
                  'source_size': image.size, 'art_size_dp': art_dp, 'exports': results}, indent=2))
