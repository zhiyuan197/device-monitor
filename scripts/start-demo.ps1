param(
    [int] $DeviceCount = 5,
    [int] $DurationSeconds = 300,
    [string] $Scenario = "MIXED",
    [int] $RealDevices = 0,
    [int] $LatencyMillis = 0,
    [switch] $ShowLogin,
    [switch] $LoginMode,
    [switch] $SecondMonitor
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "_common.ps1")
$runDir = Join-Path $ProjectRoot ".run"
New-Item -ItemType Directory -Force -Path $runDir | Out-Null

$demoConfig = Join-Path $ProjectRoot "config\demo.properties"
$serverConfig = Join-Path $ProjectRoot "config\server.properties"
if (-not (Test-Path -LiteralPath $demoConfig)) {
    throw "config/demo.properties is missing. Run scripts\seed-demo.ps1 before the demo."
}
if (-not (Test-Path -LiteralPath $serverConfig)) {
    Copy-Item -LiteralPath (Join-Path $ProjectRoot "config\server.properties.example") `
        -Destination $serverConfig
}
$tlsEnabledLine = Get-Content -LiteralPath $serverConfig |
    Where-Object { $_ -match '^server\.tls\.enabled=' } |
    Select-Object -First 1
if ($tlsEnabledLine -and $tlsEnabledLine -match '=true$') {
    $keystoreFile = Join-Path $ProjectRoot "config\tls\server-keystore.p12"
    if (-not (Test-Path -LiteralPath $keystoreFile)) {
        Write-Output "Generating TLS demo certificates..."
        & (Join-Path $PSScriptRoot "init-keystore.ps1")
        if ($LASTEXITCODE -ne 0) {
            throw "TLS keystore generation failed."
        }
    }
}
if (-not (Test-Path -LiteralPath $JarPath)) {
    & (Join-Path $PSScriptRoot "verify.ps1")
    if ($LASTEXITCODE -ne 0) {
        throw "Project build failed."
    }
}

$jarMarker = "device-monitor-0.1.0-SNAPSHOT-all.jar"

function Test-ProjectProcess {
    param([string] $Name)
    $pidFile = Join-Path $runDir "$Name.pid"
    if (-not (Test-Path -LiteralPath $pidFile)) {
        return $false
    }
    $processId = [int](Get-Content -LiteralPath $pidFile -Raw).Trim()
    $info = Get-CimInstance Win32_Process -Filter "ProcessId = $processId" `
        -ErrorAction SilentlyContinue
    if ($info -and $info.CommandLine -match [regex]::Escape($jarMarker)) {
        return $true
    }
    Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
    return $false
}

function Get-ServerPort {
    param([string] $ConfigPath)
    $portLine = Get-Content -LiteralPath $ConfigPath |
        Where-Object { $_ -match '^server\.port=' } |
        Select-Object -First 1
    if ($portLine) {
        return [int]($portLine -replace '^server\.port=', '')
    }
    return 5050
}

function Get-TlsPort {
    param([string] $ConfigPath)
    $portLine = Get-Content -LiteralPath $ConfigPath |
        Where-Object { $_ -match '^server\.tls\.port=' } |
        Select-Object -First 1
    if ($portLine) {
        return [int]($portLine -replace '^server\.tls\.port=', '')
    }
    return 5051
}

function Test-TcpPort {
    param([string] $HostName, [int] $Port)
    try {
        $probe = New-Object System.Net.Sockets.TcpClient
        $probe.Connect($HostName, $Port)
        $probe.Dispose()
        return $true
    } catch {
        return $false
    }
}

function Stop-RunningServer {
    $pidFile = Join-Path $runDir "server.pid"
    if (Test-Path -LiteralPath $pidFile) {
        $processId = [int](Get-Content -LiteralPath $pidFile -Raw).Trim()
        Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
    }
}

function Start-HiddenProcess {
    param(
        [string] $Name,
        [string[]] $Arguments,
        [switch] $Visible
    )
    $stdout = Join-Path $runDir "$Name.stdout.log"
    $stderr = Join-Path $runDir "$Name.stderr.log"
    $javaDir = Split-Path -Parent $JavaExe
    $javaw = Join-Path $javaDir "javaw.exe"
    if (Test-Path -LiteralPath $javaw) {
        $executable = $javaw
    } else {
        $executable = $JavaExe
    }
    $process = Start-Process -FilePath $executable `
        -ArgumentList $Arguments `
        -WorkingDirectory $ProjectRoot `
        -WindowStyle Hidden `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr `
        -PassThru
    $process.Id | Set-Content -LiteralPath (Join-Path $runDir "$Name.pid") -Encoding ascii
    return $process
}

# ---- server ----
$server = $null
$port = Get-ServerPort $serverConfig
if (Test-ProjectProcess "server") {
    $tlsLine = Get-Content -LiteralPath $serverConfig |
        Where-Object { $_ -match '^server\.tls\.enabled=' } |
        Select-Object -First 1
    $tlsExpected = $tlsLine -and $tlsLine -match '=true$'
    $tlsPort = Get-TlsPort $serverConfig
    if ($tlsExpected -and -not (Test-TcpPort "127.0.0.1" $tlsPort)) {
        Write-Output "Server is running without TLS; restarting it with the current configuration..."
        Stop-RunningServer
        Start-Sleep -Seconds 1
    } else {
        Write-Output "Server already running."
    }
}
if (-not (Test-ProjectProcess "server")) {
    try {
        $probe = New-Object System.Net.Sockets.TcpClient
        $probe.Connect("127.0.0.1", $port)
        $probe.Dispose()
        throw "Port $port is already in use. Stop the existing server before starting the demo."
    } catch {
        if ($_.Exception.Message -like "Port $port is already in use*") {
            throw
        }
    }

    $server = Start-HiddenProcess -Name "server" `
        -Arguments @("-jar", $JarPath, "server", "--config", $serverConfig)
} else {
    Write-Output "Server ready."
}

# ---- monitor (visible window first, no waiting for the server) ----
$monitorArguments = if ($LoginMode) {
    @("-jar", $JarPath, "monitor", "--config", $serverConfig, "--demo-stop-on-exit")
} elseif ($ShowLogin) {
    @("-jar", $JarPath, "monitor", "--config", $serverConfig)
} else {
    @("-jar", $JarPath, "monitor", "--demo", "--demo-config", $demoConfig,
        "--demo-stop-on-exit")
}

$monitor = $null
if (Test-ProjectProcess "monitor") {
    Write-Output "Monitor already running."
} else {
    $monitor = Start-HiddenProcess -Name "monitor" `
        -Arguments $monitorArguments -Visible
}

$monitor2 = $null
if ($SecondMonitor) {
    if (Test-ProjectProcess "monitor2") {
        Write-Output "Second monitor already running."
    } else {
        $monitor2 = Start-HiddenProcess -Name "monitor2" `
            -Arguments $monitorArguments -Visible
    }
}

# ---- wait for the server port before starting simulators ----
if (-not (Test-ProjectProcess "server")) {
    if ($null -eq $server) {
        throw "Server did not start. Check $runDir\server.stderr.log"
    }
    $deadline = (Get-Date).AddSeconds(15)
    $ready = $false
    while ((Get-Date) -lt $deadline) {
        if (-not (Get-Process -Id $server.Id -ErrorAction SilentlyContinue)) {
            throw "Server exited while starting. Check $runDir\server.stderr.log"
        }
        try {
            $client = New-Object System.Net.Sockets.TcpClient
            $client.Connect("127.0.0.1", $port)
            $client.Dispose()
            $ready = $true
            break
        } catch {
            Start-Sleep -Milliseconds 250
        }
    }
    if (-not $ready) {
        Stop-Process -Id $server.Id -Force -ErrorAction SilentlyContinue
        throw "Server did not open port $port. Check $runDir\server.stderr.log"
    }
}

# ---- simulator ----
$simulator = $null
$simulator2 = $null
$realCount = [Math]::Max(0, [Math]::Min($RealDevices, $DeviceCount))
if (Test-ProjectProcess "simulator") {
    Write-Output "Simulator already running."
} else {
    $realArguments = @(
        "-jar", $JarPath, "simulate",
        "--config", $demoConfig,
        "--devices", "$realCount",
        "--offset", "0",
        "--scenario", "REAL",
        "--latency-millis", "$LatencyMillis",
        "--duration-seconds", "$DurationSeconds"
    )
    $demoArguments = @(
        "-jar", $JarPath, "simulate",
        "--config", $demoConfig,
        "--devices", "$($DeviceCount - $realCount)",
        "--offset", "$realCount",
        "--scenario", $Scenario,
        "--latency-millis", "$LatencyMillis",
        "--duration-seconds", "$DurationSeconds"
    )
    if ($realCount -gt 0 -and $realCount -lt $DeviceCount) {
        $simulator = Start-HiddenProcess -Name "simulator" -Arguments $realArguments
        $simulator2 = Start-HiddenProcess -Name "simulator2" -Arguments $demoArguments
    } elseif ($realCount -gt 0) {
        $simulator = Start-HiddenProcess -Name "simulator" -Arguments $realArguments
    } else {
        $simulator = Start-HiddenProcess -Name "simulator" -Arguments $demoArguments
    }
}

Write-Output "Demo started."
if ($server) { Write-Output "Server PID: $($server.Id)" }
if ($simulator) { Write-Output "Simulator PID: $($simulator.Id)" }
if ($simulator2) { Write-Output "Second Simulator PID: $($simulator2.Id)" }
if ($monitor) { Write-Output "Monitor PID: $($monitor.Id)" }
if ($monitor2) { Write-Output "Second Monitor PID: $($monitor2.Id)" }
Write-Output "Use scripts\stop-demo.ps1 to stop project processes."
