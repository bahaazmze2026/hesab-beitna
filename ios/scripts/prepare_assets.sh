#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
swift scripts/make_icon.swift "$PWD"
python3 - <<'PY'
from pathlib import Path
import json,subprocess
p=Path('Mew/Assets.xcassets/AppIcon.appiconset');images=[]
for size in [20,29,40,60]:
 for scale in [2,3]:
  pixels=size*scale;name=f'icon-{pixels}.png';subprocess.run(['sips','-z',str(pixels),str(pixels),str(p/'icon-1024.png'),'--out',str(p/name)],check=True,stdout=subprocess.DEVNULL);images.append({'idiom':'iphone','size':f'{size}x{size}','scale':f'{scale}x','filename':name})
images.append({'idiom':'ios-marketing','size':'1024x1024','scale':'1x','filename':'icon-1024.png'})
(p/'Contents.json').write_text(json.dumps({'images':images,'info':{'author':'xcode','version':1}},indent=2))
PY
