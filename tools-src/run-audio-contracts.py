"""Run audio implementations as a DEX overlay on an emulator; never installs an APK."""
import argparse
import os
from pathlib import Path
import subprocess
import zipfile


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--serial', required=True)
    p.add_argument('--apk', type=Path, required=True)
    p.add_argument('--awt-jar', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--source-root', type=Path)
    p.add_argument('--test', default='TestAudioLineContracts')
    p.add_argument('--standalone', action='store_true', help='Include the sampled-audio API instead of loading the large host APK')
    p.add_argument('--package', action='store_true', help='Install and run an isolated test package with no visible activity')
    p.add_argument('--mode', default='')
    p.add_argument('--trace', action='store_true', help='Trace native calls in test bytecode only')
    p.add_argument('--release-host', action='store_true', help='Test only the implementation shipped in --apk, without an overlay')
    args = p.parse_args()
    if args.release_host and (args.package or args.standalone or args.trace):
        p.error('--release-host runs against the actual APK and cannot use an overlay or instrumentation')
    repo = Path(__file__).resolve().parents[1]
    sdk = Path(os.environ['ANDROID_HOME'])
    java = Path(os.environ['JAVA_HOME']) / 'bin'
    adb = sdk / 'platform-tools/adb.exe'
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=True)
    classes = out / 'classes'
    classes.mkdir(exist_ok=True)
    boot = sdk / 'platforms/android-37.0/android.jar'
    buildtools = sdk / 'build-tools/36.0.0'
    root = args.source_root or repo
    source = root / 'android-awt/src/main/java/net/runelite/awt/impl'
    runner = out / 'AudioContractMain.java'
    runner.write_text('''public class AudioContractMain {
 public static void main(String[] args) {
  try { System.out.println("BEGIN " + args[0]); Class.forName(args[0]).getMethod("main", String[].class).invoke(null, (Object) new String[0]); System.exit(0); }
  catch (Throwable error) { while(error.getCause()!=null) error=error.getCause(); error.printStackTrace(System.out); System.exit(1); }
 }
}''', encoding='utf-8')

    def run(command, timeout=120):
        result = subprocess.run(list(map(str, command)), text=True, encoding='utf-8', errors='replace',
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout)
        print(result.stdout, end='', flush=True)
        if result.returncode:
            raise RuntimeError(f'Command failed ({result.returncode}): {command[0]}')
        return result.stdout

    sources = [source/'PooledSourceDataLine.java', source/'AndroidAudioBridge.java', repo/'tools-src'/f'{args.test}.java', runner]
    if (source/'LegacySourceDataLine.java').is_file(): sources.append(source/'LegacySourceDataLine.java')
    if args.release_host: sources = [repo/'tools-src'/f'{args.test}.java', runner]
    if args.package:
        sources.extend([repo/'tools-src/audio-probe/AudioInstrumentation.java', repo/'tools-src/audio-probe/AudioProbeActivity.java'])
    if args.trace:
        sources.append(repo/'tools-src/audio-probe/AudioTrackTrace.java')
    run([java/'javac.exe', '-source', '8', '-target', '8', '-encoding', 'UTF-8',
         '-bootclasspath', str(boot)+os.pathsep+str(buildtools/'core-lambda-stubs.jar'),
         '-classpath', args.awt_jar.resolve(), '-d', classes,
         *sources])
    if args.trace:
        asm = next((Path.home()/'.gradle/caches/modules-2/files-2.1/org.ow2.asm/asm/9.8').glob('*/*.jar'))
        tool = out/'trace-tool'
        tool.mkdir(exist_ok=True)
        run([java/'javac.exe', '--release', '11', '-cp', asm, '-d', tool, repo/'tools-src/audio-probe/TraceAudioBytecode.java'])
        run([java/'java.exe', '-cp', str(tool)+os.pathsep+str(asm), 'TraceAudioBytecode', classes])
    jar = out/'audio-classes.jar'
    with zipfile.ZipFile(jar, 'w') as archive:
        for path in classes.rglob('*.class'):
            archive.write(path, path.relative_to(classes).as_posix())
        if args.standalone or args.package:
            with zipfile.ZipFile(args.awt_jar) as awt:
                for name in awt.namelist():
                    if name.endswith('.class') and name.startswith(('javax/sound/sampled/', 'net/runelite/awt/impl/AndroidClip', 'net/runelite/awt/impl/WavDecoder')):
                        archive.writestr(name, awt.read(name))
    dex = out/'audio-probe.jar'
    run([java/'java.exe', '-cp', buildtools/'lib/d8.jar', 'com.android.tools.r8.D8',
         '--min-api', '26', '--lib', boot, '--classpath', args.awt_jar.resolve(), '--output', dex, jar])
    if args.package:
        manifest = out/'AndroidManifest.xml'
        manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="net.runelite.mp.audioprobe">
 <application android:label="Audio Compatibility Probe" android:debuggable="true">
  <activity android:name="net.runelite.mp.audioprobe.AudioProbeActivity" android:exported="true" />
 </application>
 <instrumentation android:name="net.runelite.mp.audioprobe.AudioInstrumentation" android:targetPackage="net.runelite.mp.audioprobe" />
</manifest>''', encoding='utf-8')
        apk = out/'audio-probe.apk'
        run([buildtools/'aapt2.exe', 'link', '-I', boot, '--manifest', manifest,
             '--min-sdk-version', '26', '--target-sdk-version', '37', '-o', apk])
        with zipfile.ZipFile(apk, 'a') as archive, zipfile.ZipFile(dex) as dexes:
            for name in dexes.namelist(): archive.writestr(name, dexes.read(name))
        run([buildtools/'apksigner.bat', 'sign', '--ks', Path.home()/'.android/debug.keystore',
             '--ks-key-alias', 'androiddebugkey', '--ks-pass', 'pass:android', '--key-pass', 'pass:android', apk])
        run([adb, '-s', args.serial, 'install', '--user', '0', '-r', '--no-incremental', apk])
        result = run([adb, '-s', args.serial, 'shell', 'am', 'instrument', '--user', '0', '-w', '-r', '-e', 'test', args.test,
                      '-e', 'mode', args.mode or 'contracts',
                      'net.runelite.mp.audioprobe/net.runelite.mp.audioprobe.AudioInstrumentation'], timeout=180)
        (out/'result.txt').write_text(result, encoding='utf-8')
        if 'INSTRUMENTATION_CODE: -1' not in result: raise SystemExit(1)
        return
    remote = '/data/local/tmp/jay-audio-contracts'
    run([adb, '-s', args.serial, 'shell', 'mkdir', '-p', remote])
    run([adb, '-s', args.serial, 'push', dex, remote+'/audio-probe.jar'])
    # Unique content-addressed APK names avoid re-copying the large host for every fixture.
    import hashlib
    name = 'host-'+hashlib.sha256(args.apk.read_bytes()).hexdigest()[:16]+'.apk'
    exists = subprocess.run([str(adb), '-s', args.serial, 'shell', 'test', '-f', remote+'/'+name])
    if exists.returncode and not args.standalone:
        run([adb, '-s', args.serial, 'push', args.apk.resolve(), remote+'/'+name])
    classpath = remote+'/audio-probe.jar' if args.standalone else f'{remote}/audio-probe.jar:{remote}/{name}'
    command = f'CLASSPATH={classpath} app_process /system/bin AudioContractMain {args.test}'
    result = subprocess.run([str(adb), '-s', args.serial, 'shell', command], text=True,
                            encoding='utf-8', errors='replace', stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, timeout=180)
    (out/'result.txt').write_text(result.stdout, encoding='utf-8')
    print(result.stdout, end='', flush=True)
    raise SystemExit(result.returncode)


if __name__ == '__main__':
    main()
