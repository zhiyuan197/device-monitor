param(
    [string] $DemoConfig = "config/demo.properties",
    [switch] $AutoLogin
)

. (Join-Path $PSScriptRoot "_common.ps1")
Push-Location $ProjectRoot
try {
    if ($AutoLogin) {
        & $JavaExe -jar $JarPath monitor --demo --demo-config $DemoConfig
    } else {
        & $JavaExe -jar $JarPath monitor --config "config/server.properties"
    }
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}

