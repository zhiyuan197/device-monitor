$ErrorActionPreference = "SilentlyContinue"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir
$runDir = Join-Path $projectRoot ".run"

function Get-RunningMonitorProcess {
    foreach ($name in "monitor", "monitor2") {
        $pidFile = Join-Path $runDir "$name.pid"
        if (-not (Test-Path -LiteralPath $pidFile)) {
            continue
        }
        $processId = [int](Get-Content -LiteralPath $pidFile -Raw).Trim()
        $proc = Get-Process -Id $processId -ErrorAction SilentlyContinue
        if ($proc -and $proc.MainWindowHandle -ne 0) {
            return $proc
        }
    }
    return $null
}

$monitor = Get-RunningMonitorProcess
if ($monitor) {
    Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public static class Foreground {
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
}
"@
    [Foreground]::ShowWindow($monitor.MainWindowHandle, 9) | Out-Null
    [Foreground]::SetForegroundWindow($monitor.MainWindowHandle) | Out-Null
    exit 0
}

# LatencyMillis 35 is a clearly labeled demo value so the link-latency
# observability feature shows meaningful numbers on a single machine.
& (Join-Path $scriptDir "start-demo.ps1") -LoginMode -RealDevices 1 -LatencyMillis 35 | Out-Null
exit 0
