param(
    [int] $DeviceCount = 20,
    [int] $DurationSeconds = 600,
    [int] $IntervalMillis = 1000
)

. (Join-Path $PSScriptRoot "_common.ps1")
Push-Location $ProjectRoot
try {
    & $JavaExe -jar $JarPath load-test `
        --devices $DeviceCount `
        --duration-seconds $DurationSeconds `
        --interval-millis $IntervalMillis `
        --output-dir "test-results"
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}

