param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $ServerArguments
)

. (Join-Path $PSScriptRoot "_common.ps1")
Push-Location $ProjectRoot
try {
    $configPath = "config/server.properties"
    if (-not (Test-Path -LiteralPath $configPath)) {
        $configPath = "config/server.properties.example"
    }
    & $JavaExe -jar $JarPath server --config $configPath @ServerArguments
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}

