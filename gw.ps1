# Runs gradlew.bat under JDK 21. The Android build (AGP + built-in Kotlin) fails under the
# machine default JDK 25 before any source compiles, so every local gradle call goes through
# here: .\gw.ps1 :android-awt:assembleDebug
param([Parameter(ValueFromRemainingArguments = $true)][string[]]$GradleArgs)

$jdk = $env:JDK21_HOME
if (-not $jdk) {
    $jdk = Get-ChildItem 'C:\Program Files\Java' -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $jdk) { Write-Error 'No JDK 21 found; set JDK21_HOME'; exit 1 }
$env:JAVA_HOME = $jdk

& "$PSScriptRoot\gradlew.bat" @GradleArgs --console=plain
exit $LASTEXITCODE
