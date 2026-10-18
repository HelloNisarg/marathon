#!/usr/bin/env python3
"""Build a signed, offline personal-use APK with official Android 34 tools.

No Gradle, paid build service or third-party runtime dependency is required.
See README.md for the required SDK paths and private signing environment.
"""
import os, pathlib, shutil, subprocess, zipfile

root=pathlib.Path(__file__).resolve().parent
repo=root.parent
sdk=pathlib.Path(os.environ['STRIDE_ANDROID_PLATFORM'])
tools=pathlib.Path(os.environ['STRIDE_ANDROID_BUILD_TOOLS'])
keystore=pathlib.Path(os.environ['STRIDE_ANDROID_KEYSTORE'])
password=pathlib.Path(os.environ['STRIDE_ANDROID_PASSWORD_FILE'])
build=root/'build'
if build.exists(): shutil.rmtree(build)
for name in ['res/drawable','assets','classes','dex']:(build/name).mkdir(parents=True,exist_ok=True)
shutil.copy(repo/'dist/icon-192.png',build/'res/drawable/icon.png')
assets=['index.html','app.js','core.mjs','health.mjs','activity.mjs','strength.mjs','style.css','manifest.webmanifest','icon.svg','icon-192.png','icon-512.png']
for filename in assets:shutil.copy(repo/'dist'/filename,build/'assets'/filename)
def run(*command): subprocess.run([str(x) for x in command],check=True,cwd=repo)
run(tools/'aapt2','compile','--dir',build/'res','-o',build/'resources.zip')
run(tools/'aapt2','link','--manifest',root/'AndroidManifest.xml','-I',sdk/'android.jar','-A',build/'assets','-o',build/'unsigned.apk',build/'resources.zip')
run('java','-m','jdk.compiler/com.sun.tools.javac.Main','-source','17','-target','17','-classpath',sdk/'android.jar','-d',build/'classes',*sorted((root/'src').rglob('*.java')))
run('java','-cp',tools/'lib/d8.jar','com.android.tools.r8.D8','--lib',sdk/'android.jar','--min-api','34','--output',build/'dex',*sorted((build/'classes').rglob('*.class')))
with zipfile.ZipFile(build/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:
    for dex in (build/'dex').glob('*.dex'):z.write(dex,dex.name)
run(tools/'zipalign','-f','4',build/'unsigned.apk',build/'aligned.apk')
output=build/'Stride-Android.apk'
run('java','-jar',tools/'lib/apksigner.jar','sign','--ks',keystore,'--ks-key-alias','stride','--ks-pass','file:'+str(password),'--out',output,build/'aligned.apk')
run('java','-jar',tools/'lib/apksigner.jar','verify','--verbose',output)
print('Built:',output)
