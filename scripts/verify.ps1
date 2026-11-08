$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$workspaceRoot = Split-Path -Parent $projectRoot
$mavenScript = Join-Path $PSScriptRoot "mvn.ps1"
$testResults = Join-Path $projectRoot "test-results"
New-Item -ItemType Directory -Force -Path $testResults | Out-Null

$mavenOutput = & $mavenScript clean verify 2>&1
$mavenExitCode = $LASTEXITCODE
$mavenOutput | Tee-Object -FilePath (Join-Path $testResults "maven-build-output.txt")
if ($mavenExitCode -ne 0) {
    exit $mavenExitCode
}

$shadedJar = Join-Path $projectRoot "target\device-monitor-0.1.0-SNAPSHOT-all.jar"
if (-not (Test-Path -LiteralPath $shadedJar)) {
    throw "Expected shaded JAR was not created: $shadedJar"
}

$javaCommand = $null
$localJdkRoot = Join-Path $workspaceRoot ".tools\jdk-17"
if (Test-Path -LiteralPath $localJdkRoot) {
    $jdk = Get-ChildItem -LiteralPath $localJdkRoot -Directory |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName "bin\java.exe") } |
        Select-Object -First 1
    if ($jdk) {
        $javaCommand = Join-Path $jdk.FullName "bin\java.exe"
    }
}

if (-not $javaCommand) {
    $systemJava = Get-Command java -ErrorAction SilentlyContinue
    if ($systemJava) {
        $javaCommand = $systemJava.Source
    }
}

if (-not $javaCommand) {
    throw "Java was not found after the Maven build."
}

$runOutput = & $javaCommand -jar $shadedJar 2>&1
$runOutput | Set-Content -LiteralPath (Join-Path $testResults "bootstrap-run.txt") -Encoding utf8
if (($runOutput -join "`n") -notmatch "Device Monitor application is ready") {
    throw "The shaded JAR did not produce the expected bootstrap message."
}

Write-Output "Build verification passed: $shadedJar"

