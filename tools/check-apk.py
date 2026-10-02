"""Inspect compiled APK manifest without Android SDK; signature verification uses apksigner."""
import sys, struct, zipfile, json, hashlib
from pathlib import Path

def manifest(apk):
    with zipfile.ZipFile(apk) as archive:
        data=archive.read('AndroidManifest.xml')
        assert archive.testzip() is None
    u16=lambda p:struct.unpack_from('<H',data,p)[0]
    u32=lambda p:struct.unpack_from('<I',data,p)[0]
    assert u16(0)==3 and u32(4)==len(data)
    strings=[]; nodes=[]; offset=u16(2)
    while offset<len(data):
        kind,header,size=u16(offset),u16(offset+2),u32(offset+4)
        assert size>=header and offset+size<=len(data)
        if kind==1:
            count,flags,start=u32(offset+8),u32(offset+16),u32(offset+20)
            for i in range(count):
                pos=offset+start+u32(offset+header+4*i)
                if flags&256:
                    def length8(p):
                        x=data[p]
                        return (((x&127)<<8)|data[p+1],p+2) if x&128 else (x,p+1)
                    _,pos=length8(pos);length,pos=length8(pos)
                    strings.append(data[pos:pos+length].decode('utf-8'))
                else:
                    length=u16(pos);pos+=2
                    if length&32768:length=((length&32767)<<16)|u16(pos);pos+=2
                    strings.append(data[pos:pos+length*2].decode('utf-16-le'))
        elif kind==0x102:
            extension=offset+16
            name=strings[u32(extension+4)]
            attr_start,attr_size,attr_count=u16(extension+8),u16(extension+10),u16(extension+12)
            attrs={}
            for i in range(attr_count):
                p=extension+attr_start+i*attr_size
                key=strings[u32(p+4)];raw=u32(p+8);value_type=data[p+15];value=u32(p+16)
                attrs[key]=strings[raw] if raw!=0xffffffff else strings[value] if value_type==3 else bool(value) if value_type==0x12 else value
            nodes.append((name,attrs))
        offset+=size
    return nodes

if __name__=='__main__':
    apk=Path(sys.argv[1]);nodes=manifest(apk)
    permissions=[a['name'] for name,a in nodes if name=='uses-permission']
    app=next(a for name,a in nodes if name=='application')
    package=next(a for name,a in nodes if name=='manifest')
    sdk=next(a for name,a in nodes if name=='uses-sdk')
    assert 'android.permission.INTERNET' not in permissions
    assert app['allowBackup'] is False and app['supportsRtl'] is True
    assert app['label']=='Meow Budget'
    assert package['package']==(sys.argv[2] if len(sys.argv)>2 else 'com.hesabbeitna.app')
    assert sdk['minSdkVersion']==26
    result={'apk':apk.name,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),
        'label':app['label'],'package':package['package'],'version':package.get('versionName'),
        'minSdk':sdk['minSdkVersion'],'targetSdk':sdk['targetSdkVersion'],
        'internetPermission':False,'allowBackup':False,'RTL':True,'permissions':permissions}
    print(json.dumps(result,ensure_ascii=False,indent=2))
