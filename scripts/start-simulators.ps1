param(
    [int] $DeviceCount = 4,
    [string] $Scenario = "MIXED",
    [int] $DurationSeconds = 120,
    [string] $DemoConfig = "config/demo.properties"
)

. (Join-Path $PSScriptRoot "_common.ps1")
Push-Location $ProjectRoot
try {
    & $JavaExe -jar $JarPath simulate `
        --config $DemoConfig `
        --devices $DeviceCount `
        --scenario $Scenario `
        --duration-seconds $DurationSeconds
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}

