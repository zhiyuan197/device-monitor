param(
    [int] $DeviceCount = 4,
    [string] $Database = "data/device-monitor.db",
    [string] $Output = "config/demo.properties"
)

. (Join-Path $PSScriptRoot "_common.ps1")
Push-Location $ProjectRoot
try {
    & $JavaExe -jar $JarPath seed-demo --db $Database --devices $DeviceCount --output $Output
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}

