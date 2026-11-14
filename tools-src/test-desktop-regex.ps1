param(
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$AndroidSdk = "$env:LOCALAPPDATA/Android/Sdk",
    [string]$Serial = 'emulator-5580',
    [string]$OutputDirectory = "$PSScriptRoot/../../analysis/runelingual-dex/desktop-tests"
)
$ErrorActionPreference = 'Stop'
if (!$JdkHome) { throw 'Set JAVA_HOME to Temurin 21.0.6+7' }
$taskRoot = Split-Path $PSScriptRoot
$taskClasses = "$OutputDirectory/classes"
$taskAsm = (Get-ChildItem "$env:USERPROFILE/.gradle/caches/modules-2/files-2.1/org.ow2.asm/asm/9.8" -Recurse -Filter '*.jar' | Select-Object -First 1).FullName
$taskCommons = (Get-ChildItem "$env:USERPROFILE/.gradle/caches/modules-2/files-2.1/org.ow2.asm/asm-commons/9.8" -Recurse -Filter '*.jar' | Select-Object -First 1).FullName
$taskR8 = (Get-ChildItem "$env:USERPROFILE/.gradle/caches/modules-2/files-2.1/com.android.tools/r8/9.5.28" -Recurse -Filter '*.jar' | Select-Object -First 1).FullName
$taskAndroid = "$AndroidSdk/platforms/android-37.0/android.jar"
function Invoke-Checked($exe, [string[]]$arguments) {
    & $exe @arguments
    if ($LASTEXITCODE -ne 0) { throw "$exe failed with $LASTEXITCODE" }
}
New-Item -ItemType Directory -Force $taskClasses | Out-Null
$taskSources = (Get-ChildItem "$taskRoot/android/src/androidMain/java/net/runelite/mp/compat/regex/*.java").FullName
$taskSources += (Get-ChildItem "$taskRoot/android/src/androidMain/java/net/runelite/mp/compat/jdk/*.java").FullName
$taskSources += @("$taskRoot/android/src/androidMain/java/net/runelite/mp/plugins/DesktopRegexStrings.java", "$taskRoot/android/src/androidMain/java/net/runelite/mp/compat/JdkRetargeter.java","$PSScriptRoot/TestDesktopRegexTransform.java", "$PSScriptRoot/DesktopRegexFixture.java", "$PSScriptRoot/TestDesktopRegexRuntime.java")
Invoke-Checked "$JdkHome/bin/javac.exe" (@('-source','17','-target','17','-encoding','UTF-8','-cp',"$taskAndroid;$taskAsm;$taskCommons",'-d',$taskClasses) + $taskSources)
Invoke-Checked "$JdkHome/bin/jar.exe" @('--create','--file',"$OutputDirectory/fixture.jar",'-C',$taskClasses,'DesktopRegexFixture.class')
Invoke-Checked "$JdkHome/bin/java.exe" @('-cp',$taskClasses,'TestDesktopRegexRuntime','write',"$OutputDirectory/oracle.bin")
Invoke-Checked "$JdkHome/bin/java.exe" @('-Xverify:all','-cp',"$taskClasses;$taskAsm;$taskCommons",'TestDesktopRegexTransform',"$OutputDirectory/fixture.jar","$OutputDirectory/fixture-transformed.jar")
Invoke-Checked "$JdkHome/bin/jar.exe" @('--create','--file',"$OutputDirectory/runtime.jar",'-C',$taskClasses,'net/runelite/mp/compat/regex','-C',$taskClasses,'net/runelite/mp/compat/jdk','-C',$taskClasses,'net/runelite/mp/plugins/DesktopRegexStrings.class','-C',$taskClasses,'TestDesktopRegexRuntime.class')
Invoke-Checked "$JdkHome/bin/java.exe" @('-cp',$taskR8,'com.android.tools.r8.D8','--release','--min-api','26','--lib',$taskAndroid,'--output',"$OutputDirectory/runtime-dex.jar","$OutputDirectory/runtime.jar","$OutputDirectory/fixture-transformed.jar")
Invoke-Checked "$AndroidSdk/platform-tools/adb.exe" @('-s',$Serial,'push',"$OutputDirectory/runtime-dex.jar",'/data/local/tmp/desktop-regex-tests.jar')
Invoke-Checked "$AndroidSdk/platform-tools/adb.exe" @('-s',$Serial,'push',"$OutputDirectory/oracle.bin",'/data/local/tmp/desktop-regex-oracle.bin')
Invoke-Checked "$AndroidSdk/platform-tools/adb.exe" @('-s',$Serial,'shell','dalvikvm','-cp','/data/local/tmp/desktop-regex-tests.jar','TestDesktopRegexRuntime','check','/data/local/tmp/desktop-regex-oracle.bin')
