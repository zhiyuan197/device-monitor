$ErrorActionPreference = "Stop"
$script:ProjectRoot = Split-Path -Parent $PSScriptRoot
$script:WorkspaceRoot = Split-Path -Parent $ProjectRoot
$script:JarPath = Join-Path $ProjectRoot "target\device-monitor-0.1.0-SNAPSHOT-all.jar"
$script:JavaExe = $null

$localJdkRoot = Join-Path $WorkspaceRoot ".tools\jdk-17"
if (Test-Path -LiteralPath $localJdkRoot) {
    $jdk = Get-ChildItem -LiteralPath $localJdkRoot -Directory |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName "bin\java.exe") } |
        Select-Object -First 1
    if ($jdk) {
        $env:JAVA_HOME = $jdk.FullName
        $env:Path = "$($jdk.FullName)\bin;$env:Path"
        $script:JavaExe = Join-Path $jdk.FullName "bin\java.exe"
    }
}

if (-not $JavaExe) {
    $java = Get-Command java -ErrorAction SilentlyContinue
    if ($java) {
        $script:JavaExe = $java.Source
    }
}

if (-not $JavaExe) {
    throw "Java was not found. Configure JAVA_HOME or run scripts\verify.ps1 with the project tools."
}

if (-not (Test-Path -LiteralPath $JarPath)) {
    throw "Executable JAR not found. Run scripts\verify.ps1 first."
}

