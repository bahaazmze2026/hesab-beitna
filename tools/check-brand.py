"""Numeric WCAG contrast check for foreground/background pairs used by the interface."""
from pathlib import Path

def luminance(value):
    rgb=[int(value[i:i+2],16)/255 for i in (1,3,5)]
    linear=[x/12.92 if x<=.04045 else ((x+.055)/1.055)**2.4 for x in rgb]
    return sum(x*w for x,w in zip(linear,[.2126,.7152,.0722]))
pairs=[
    ('light text','#482623','#FFF8EF'),('light card text','#482623','#FFFDFA'),
    ('light secondary text','#745951','#FFFDFA'),('light secondary on hero','#745951','#E1EFE4'),
    ('primary button','#FFFFFF','#26734D'),('orange control','#482623','#EE9451'),
    ('green number','#26734D','#FFFDFA'),('selected navigation','#26734D','#E1EFE4'),
    ('hero number','#184A32','#E1EFE4'),('light error','#AD3038','#FFFDFA'),
    ('light alert','#7B1823','#FFE8E8'),('warning','#815000','#FFF8EF'),
    ('dark text','#F5F5F5','#111111'),('dark secondary','#B8B8B8','#111111'),
    ('dark primary button','#103820','#97D5AB'),('dark number','#97D5AB','#111111'),
    ('dark selected navigation','#97D5AB','#254733'),('dark hero','#D8F3E0','#254733'),
    ('dark error','#FFB3B8','#111111'),('dark alert','#FFDADC','#502129'),('black background text','#F5F5F5','#000000'),('dark field text','#B8B8B8','#191919'),('dark glass label','#B8B8B8','#292929')]
lines=[]
for name,fg,bg in pairs:
    a,b=sorted([luminance(fg),luminance(bg)])
    ratio=(b+.05)/(a+.05)
    assert ratio>=4.5,(name,ratio)
    lines.append(f'{name}: {fg} on {bg} = {ratio:.2f}:1 PASS')
# Alpha .96 navigation composited over ivory, before evaluating secondary label.
background='#'+''.join(f'{round(int(a,16)*.96+int(b,16)*.04):02x}' for a,b in zip(['FF','FD','FA'],['FF','F8','EF']))
a,b=sorted([luminance('#745951'),luminance(background)]);ratio=(b+.05)/(a+.05)
assert ratio>=4.5
lines.append(f'glass label after compositing: {ratio:.2f}:1 PASS')
print('\n'.join(lines))
Path('docs/contrast-results.txt').write_text('\n'.join(lines)+'\n')
