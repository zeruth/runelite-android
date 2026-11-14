param(
    [string]$Python = 'python'
)
$ErrorActionPreference = 'Stop'
& $Python "$PSScriptRoot/fetch-jdk-reference.py"
if ($LASTEXITCODE -ne 0) { throw "fetch-jdk-reference.py failed with $LASTEXITCODE" }
