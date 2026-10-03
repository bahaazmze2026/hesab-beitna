#!/usr/bin/env python3
"""Generate a deterministic, dependency-free Xcode project and shared scheme."""
from pathlib import Path
import hashlib
root=Path(__file__).resolve().parents[1]
objects={}
def oid(name): return hashlib.sha1(name.encode()).hexdigest()[:24].upper()
def put(name,body): k=oid(name);objects[k]=body;return k
def refs(xs):return '('+','.join(xs)+',)'
def quote(s):return '"'+s.replace('\\','\\\\').replace('"','\\"')+'"'
product=put('product-app','isa = PBXFileReference; explicitFileType = wrapper.application; path = Mew.app; sourceTree = BUILT_PRODUCTS_DIR;')
uiProduct=put('product-ui','isa = PBXFileReference; explicitFileType = wrapper.cfbundle; path = MewUITests.xctest; sourceTree = BUILT_PRODUCTS_DIR;')
unitProduct=put('product-unit','isa = PBXFileReference; explicitFileType = wrapper.cfbundle; path = MewAppTests.xctest; sourceTree = BUILT_PRODUCTS_DIR;')
files=[]
def file(path,kind): k=put('file:'+path,f'isa = PBXFileReference; lastKnownFileType = {kind}; path = {quote(path)}; sourceTree = "<group>";');files.append(k);return k
def phase(name,kind,paths):
 bs=[]
 for path,ft in paths:
  fr=file(path,ft);bs.append(put('build:'+name+path,f'isa = PBXBuildFile; fileRef = {fr};'))
 return put(name,f'isa = {kind}; buildActionMask = 2147483647; files = {refs(bs)}; runOnlyForDeploymentPostprocessing = 0;')
appSources=phase('app-sources','PBXSourcesBuildPhase',[(str(p.relative_to(root)),'sourcecode.swift') for p in sorted((root/'Mew').rglob('*.swift'))])
resources=phase('app-resources','PBXResourcesBuildPhase',[('Mew/Assets.xcassets','folder.assetcatalog'),('Mew/PrivacyInfo.xcprivacy','text.xml')])
uiSources=phase('ui-sources','PBXSourcesBuildPhase',[(str(p.relative_to(root)),'sourcecode.swift') for p in sorted((root/'UITests').glob('*.swift'))])
unitSources=phase('unit-sources','PBXSourcesBuildPhase',[(str(p.relative_to(root)),'sourcecode.swift') for p in sorted((root/'AppTests').glob('*.swift'))])
frameworks=put('frameworks','isa = PBXFrameworksBuildPhase; buildActionMask = 2147483647; files = (); runOnlyForDeploymentPostprocessing = 0;')
mainGroup=put('main-group',f'isa = PBXGroup; children = {refs(files+[product,uiProduct,unitProduct])}; sourceTree = "<group>";')
productsGroup=put('products-group',f'isa = PBXGroup; children = {refs([product,uiProduct,unitProduct])}; name = Products; sourceTree = "<group>";')
appTarget=oid('app-target');uiTarget=oid('ui-target');unitTarget=oid('unit-target');project=oid('project')
def configs(name,settings):
 keys=[]
 for config in ['Debug','Release']:
  s=dict(settings);s['SWIFT_OPTIMIZATION_LEVEL']='-Onone' if config=='Debug' else '-O';s['SWIFT_ACTIVE_COMPILATION_CONDITIONS']='DEBUG' if config=='Debug' else '';s['DEBUG_INFORMATION_FORMAT']='dwarf' if config=='Debug' else 'dwarf-with-dsym'
  body=' '.join(k+' = '+quote(v)+';' for k,v in s.items());keys.append(put(name+config,f'isa = XCBuildConfiguration; buildSettings = {{{body}}}; name = {config};'))
 return put(name+'list',f'isa = XCConfigurationList; buildConfigurations = {refs(keys)}; defaultConfigurationIsVisible = 0; defaultConfigurationName = Release;')
common={'IPHONEOS_DEPLOYMENT_TARGET':'17.0','SWIFT_VERSION':'5.0','SDKROOT':'iphoneos','TARGETED_DEVICE_FAMILY':'1','CODE_SIGN_STYLE':'Automatic','CURRENT_PROJECT_VERSION':'1','MARKETING_VERSION':'1.0.0','CLANG_ENABLE_MODULES':'YES','SWIFT_STRICT_CONCURRENCY':'targeted','ENABLE_USER_SCRIPT_SANDBOXING':'YES'}
appConfig=configs('app-config',dict(common,PRODUCT_BUNDLE_IDENTIFIER='com.hesabbeitna.mew.ios',PRODUCT_NAME='Mew',INFOPLIST_FILE='Mew/Info.plist',ASSETCATALOG_COMPILER_APPICON_NAME='AppIcon',GENERATE_INFOPLIST_FILE='NO',LD_RUNPATH_SEARCH_PATHS='$(inherited) @executable_path/Frameworks'))
uiConfig=configs('ui-config',dict(common,PRODUCT_BUNDLE_IDENTIFIER='com.hesabbeitna.mew.ios.uitests',PRODUCT_NAME='MewUITests',GENERATE_INFOPLIST_FILE='YES',TEST_TARGET_NAME='Mew'))
unitConfig=configs('unit-config',dict(common,PRODUCT_BUNDLE_IDENTIFIER='com.hesabbeitna.mew.ios.tests',PRODUCT_NAME='MewAppTests',GENERATE_INFOPLIST_FILE='YES',TEST_HOST='$(BUILT_PRODUCTS_DIR)/Mew.app/$(BUNDLE_EXECUTABLE_FOLDER_PATH)/Mew',BUNDLE_LOADER='$(TEST_HOST)'))
projConfig=configs('proj-config',{'CLANG_ENABLE_MODULES':'YES','SWIFT_VERSION':'5.0','IPHONEOS_DEPLOYMENT_TARGET':'17.0'})
proxy=put('proxy',f'isa = PBXContainerItemProxy; containerPortal = {project}; proxyType = 1; remoteGlobalIDString = {appTarget}; remoteInfo = Mew;')
dep=put('dependency',f'isa = PBXTargetDependency; target = {appTarget}; targetProxy = {proxy};')
objects[appTarget]=f'isa = PBXNativeTarget; buildConfigurationList = {appConfig}; buildPhases = {refs([appSources,frameworks,resources])}; buildRules = (); dependencies = (); name = Mew; productName = Mew; productReference = {product}; productType = "com.apple.product-type.application";'
for target,name,config,source,prod,kind in [(uiTarget,'MewUITests',uiConfig,uiSources,uiProduct,'ui-testing'),(unitTarget,'MewAppTests',unitConfig,unitSources,unitProduct,'unit-test')]:objects[target]=f'isa = PBXNativeTarget; buildConfigurationList = {config}; buildPhases = {refs([source,frameworks])}; buildRules = (); dependencies = {refs([dep])}; name = {name}; productName = {name}; productReference = {prod}; productType = "com.apple.product-type.bundle.{kind}";'
objects[project]=f'isa = PBXProject; attributes = {{LastUpgradeCheck = 1600; TargetAttributes = {{{appTarget} = {{CreatedOnToolsVersion = 16.0;}};{uiTarget} = {{CreatedOnToolsVersion = 16.0;TestTargetID = {appTarget};}};{unitTarget} = {{CreatedOnToolsVersion = 16.0;TestTargetID = {appTarget};}};}};}}; buildConfigurationList = {projConfig}; compatibilityVersion = "Xcode 14.0"; developmentRegion = ar; hasScannedForEncodings = 0; knownRegions = (ar,en,Base); mainGroup = {mainGroup}; productRefGroup = {productsGroup}; projectDirPath = ""; projectRoot = ""; targets = {refs([appTarget,unitTarget,uiTarget])};'
p=root/'Mew.xcodeproj';p.mkdir(exist_ok=True)
(p/'project.pbxproj').write_text('// !$*UTF8*$!\n{archiveVersion = 1; classes = {}; objectVersion = 56; objects = {\n'+''.join(f'{k} = {{{v}}};\n' for k,v in sorted(objects.items()))+'}; rootObject = '+project+';}\n')
def buildRef(target,name,product):return f'<BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{target}" BuildableName="{product}" BlueprintName="{name}" ReferencedContainer="container:Mew.xcodeproj"/>'
r=buildRef(appTarget,'Mew','Mew.app');scheme=f'''<?xml version="1.0" encoding="UTF-8"?><Scheme LastUpgradeVersion="1600" version="1.3"><BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries><BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES">{r}</BuildActionEntry></BuildActionEntries></BuildAction><TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="YES"><Testables><TestableReference skipped="NO">{buildRef(unitTarget,'MewAppTests','MewAppTests.xctest')}</TestableReference><TestableReference skipped="NO">{buildRef(uiTarget,'MewUITests','MewUITests.xctest')}</TestableReference></Testables></TestAction><LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugDocumentVersioning="YES" debugServiceExtension="internal" allowLocationSimulation="YES"><BuildableProductRunnable runnableDebuggingMode="0">{r}</BuildableProductRunnable></LaunchAction><ProfileAction buildConfiguration="Release" shouldUseLaunchSchemeArgsEnv="YES" savedToolIdentifier="" useCustomWorkingDirectory="NO" debugDocumentVersioning="YES"><BuildableProductRunnable runnableDebuggingMode="0">{r}</BuildableProductRunnable></ProfileAction><AnalyzeAction buildConfiguration="Debug"/><ArchiveAction buildConfiguration="Release" revealArchiveInOrganizer="YES"/></Scheme>'''
s=p/'xcshareddata/xcschemes';s.mkdir(parents=True,exist_ok=True);(s/'Mew.xcscheme').write_text(scheme)
print('Generated Mew.xcodeproj with app, unit and UI acceptance targets')
