param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $MavenArguments
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$workspaceRoot = Split-Path -Parent $projectRoot
$localJdkRoot = Join-Path $workspaceRoot ".tools\jdk-17"
$localMavenRoot = Join-Path $workspaceRoot ".tools\maven-3.9.9"

if (Test-Path -LiteralPath $localJdkRoot) {
    $jdk = Get-ChildItem -LiteralPath $localJdkRoot -Directory |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName "bin\java.exe") } |
        Select-Object -First 1
    if ($jdk) {
        $env:JAVA_HOME = $jdk.FullName
    }
}

$mavenCommand = $null
if (Test-Path -LiteralPath $localMavenRoot) {
    $maven = Get-ChildItem -LiteralPath $localMavenRoot -Directory |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName "bin\mvn.cmd") } |
        Select-Object -First 1
    if ($maven) {
        $mavenCommand = Join-Path $maven.FullName "bin\mvn.cmd"
    }
}

if (-not $mavenCommand) {
    $systemMaven = Get-Command mvn -ErrorAction SilentlyContinue
    if ($systemMaven) {
        $mavenCommand = $systemMaven.Source
    }
}

if (-not $mavenCommand) {
    throw "Maven was not found. Configure JAVA_HOME and install Maven, or place portable tools under .tools."
}

$effectiveArguments = @()
$localSettings = Join-Path $workspaceRoot ".tools\maven-settings.xml"
if (Test-Path -LiteralPath $localSettings) {
    $effectiveArguments += @("--settings", $localSettings)
}
$effectiveArguments += $MavenArguments

Push-Location $projectRoot
try {
    $previousErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    & $mavenCommand @effectiveArguments
    $mavenExitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousErrorAction
}
finally {
    Pop-Location
}
exit $mavenExitCode

