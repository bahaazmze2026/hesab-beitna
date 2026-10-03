"""Notification silhouettes from original Mew alpha; full image, uniform scale."""
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parents[1]
source = Image.open(root / 'tools/icon-source/Mew-Icon-Isolated.png').convert('RGBA')
for density, size in [('mdpi', 24), ('hdpi', 36), ('xhdpi', 48), ('xxhdpi', 72), ('xxxhdpi', 96)]:
    art_size = round(size * 22 / 24)
    alpha = source.getchannel('A').resize((art_size, art_size), Image.Resampling.LANCZOS)
    art = Image.new('RGBA', alpha.size, (255, 255, 255, 0))
    art.putalpha(alpha)
    output = Image.new('RGBA', (size, size))
    output.alpha_composite(art, ((size-art_size)//2, (size-art_size)//2))
    folder = root / f'app/src/main/res/drawable-{density}'
    folder.mkdir(exist_ok=True)
    output.save(folder / 'notification_logo.png')
