"""Offline source/resource checks. This is deliberately not an Android/Kotlin compiler."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
android = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(root/'app/src/main/AndroidManifest.xml').getroot()
permissions = [p.attrib[android+'name'] for p in manifest.findall('uses-permission')]
assert 'android.permission.INTERNET' not in permissions
application = manifest.find('application')
assert application.attrib[android+'allowBackup'] == 'false'
assert application.attrib[android+'supportsRtl'] == 'true'
assert application.attrib[android+'label'] == 'حساب بيتنا'
for path in root.rglob('*.xml'):
    ET.parse(path)
for resource in ['logo', 'notification_logo']:
    assert (root/f'app/src/main/res/drawable/{resource}.xml').is_file()
required = ['Models.kt','Finance.java','BackupCrypto.java','Storage.kt','AppModel.kt',
            'MainActivity.kt','HouseApp.kt','Ui.kt','Forms.kt','Screens.kt','Reports.kt','DuePlanForm.kt']
for name in required:
    assert (root/'app/src/main/java/com/hesabbeitna/app'/name).is_file()

# Catch truncated files/unbalanced delimiters, including nested string interpolation.
# This scanner is not a Kotlin compiler and performs no type or dependency resolution.
for path in (root/'app/src').rglob('*.kt'):
    source = path.read_text()
    stack=[]
    def quoted(i, quote):
        triple=source.startswith('"""',i)
        delimiter='"""' if triple else quote
        i+=len(delimiter)
        while i<len(source):
            if not triple and source[i]=='\\': i+=2; continue
            if source.startswith(delimiter,i): return i+len(delimiter)
            if quote=='"' and source.startswith('${',i):
                stack.append('{');i=code(i+2,True);continue
            i+=1
        raise AssertionError(f'unclosed string {path}')
    def code(i, template=False):
        level=len(stack)
        while i<len(source):
            if source.startswith('//',i):
                end=source.find('\n',i);i=len(source) if end<0 else end+1;continue
            if source.startswith('/*',i):
                depth=1;i+=2
                while depth and i<len(source):
                    if source.startswith('/*',i):depth+=1;i+=2
                    elif source.startswith('*/',i):depth-=1;i+=2
                    else:i+=1
                assert depth==0,f'unclosed comment {path}'
                continue
            c=source[i]
            if c in ['"',"'"]:i=quoted(i,c);continue
            if c in '([{':stack.append(c)
            elif c in ')]}':
                assert stack and stack.pop()=={')':'(',']':'[','}':'{'}[c],f'unbalanced {path}:{source[:i].count(chr(10))+1}'
                if template and len(stack)==level-1:return i+1
            i+=1
        assert not template,f'unclosed interpolation {path}'
        return i
    code(0)
    assert not stack, f'unclosed {path}'
assert not list(root.rglob('*.jks'))
assert not (root/'signing.properties').exists()
print('PASS: offline project structure, XML, RTL/privacy declarations, delimiters and no signing secrets')
