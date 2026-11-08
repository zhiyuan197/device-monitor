$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$runDir = Join-Path $projectRoot ".run"
$jarMarker = "device-monitor-0.1.0-SNAPSHOT-all.jar"

foreach ($name in "monitor2", "monitor", "simulator2", "simulator", "server") {
    $pidFile = Join-Path $runDir "$name.pid"
    if (-not (Test-Path -LiteralPath $pidFile)) {
        Write-Output "$name is not running."
        continue
    }
    $processId = [int](Get-Content -LiteralPath $pidFile -Raw).Trim()
    $info = Get-CimInstance Win32_Process -Filter "ProcessId = $processId" -ErrorAction SilentlyContinue
    if (-not $info) {
        Write-Output "$name is already stopped."
        remove-item -LiteralPath $pidFile -Force
        continue
    }
    if ($info.CommandLine -notmatch [regex]::Escape($jarMarker)) {
        Write-Output "Skipped $name because PID $processId is not the project JAR."
        continue
    }
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    if ($process -and $process.MainWindowHandle -ne 0) {
        $process.CloseMainWindow() | Out-Null
        Start-Sleep -Milliseconds 700
    }
    if (Get-Process -Id $processId -ErrorAction SilentlyContinue) {
        Stop-Process -Id $processId -Force
    }
    Remove-Item -LiteralPath $pidFile -Force
    Write-Output "Stopped $name PID $processId."
}

