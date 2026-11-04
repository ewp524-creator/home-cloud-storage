param(
    [ValidateSet("start", "stop", "restart", "status", "logs")]
    [string]$Action = "start"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$startScript = Join-Path $PSScriptRoot "start-filehome.ps1"
$stopScript = Join-Path $PSScriptRoot "stop-filehome.ps1"
. (Join-Path $PSScriptRoot "process-utils.ps1")

function Show-Status {
    $ports = @(
        @{ Name = "Web"; Port = 80 },
        @{ Name = "Backend"; Port = 8080 },
        @{ Name = "MinIO"; Port = 9000 },
        @{ Name = "MinIO console"; Port = 9001 },
        @{ Name = "Redis"; Port = 6379 }
        @{ Name = "Kafka"; Port = 9092 }
    )
    foreach ($item in $ports) {
        $state = if (Test-TcpPort -Port $item.Port) { "UP" } else { "DOWN" }
        Write-Host ("{0,-15} {1}" -f $item.Name, $state)
    }
    try {
        $health = Invoke-RestMethod `
            -Uri "http://127.0.0.1:8080/api/v1/health" `
            -TimeoutSec 3
        Write-Host "Health         $($health.status)"
    } catch {
        Write-Host "Health         DOWN"
    }
}

function Show-Logs {
    $backendOut = Join-Path $root "logs\backend.out.log"
    $backendErr = Join-Path $root "logs\backend.err.log"
    if (Test-Path -LiteralPath $backendOut) {
        Get-Content -LiteralPath $backendOut -Tail 200 -Wait
    } elseif (Test-Path -LiteralPath $backendErr) {
        Get-Content -LiteralPath $backendErr -Tail 200 -Wait
    } else {
        Write-Host "No backend log files found."
    }
}

switch ($Action) {
    "start" {
        & $startScript
    }
    "stop" {
        & $stopScript
    }
    "restart" {
        & $stopScript
        Start-Sleep -Seconds 2
        & $startScript
    }
    "status" {
        Show-Status
    }
    "logs" {
        Show-Logs
    }
}
