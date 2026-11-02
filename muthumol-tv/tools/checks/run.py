#!/usr/bin/env python3
"""Run production parser checks after :app:compileDebugJavaWithJavac.
ANDROID_HOME and JAVA_HOME select the SDK/JDK. No provider or device tests.
"""
import os,subprocess,sys,tempfile
from pathlib import Path
project=Path(__file__).resolve().parents[2]
sdk=Path(os.environ['ANDROID_HOME']); jdk=Path(os.environ['JAVA_HOME'])
if len(sys.argv)!=2:raise SystemExit('Usage: run.py /path/to/org.json.jar')
classes=project/'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes'
cp=os.pathsep.join(map(str,[classes,Path(sys.argv[1]).resolve(),sdk/'platforms/android-35/android.jar']))
with tempfile.TemporaryDirectory(prefix='tgstream-checks-') as target:
 subprocess.run([str(jdk/'bin/javac'),'-cp',cp,'-d',target,str(Path(__file__).with_name('PureFeatureChecks.java'))],check=True)
 subprocess.run([str(jdk/'bin/java'),'-cp',target+os.pathsep+cp,'com.muthumol.player.dummy.PureFeatureChecks'],check=True)
