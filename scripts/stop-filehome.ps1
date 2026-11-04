$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$nginx = Join-Path $root "nginx\nginx-1.30.6\nginx.exe"
$redisCli = Get-ChildItem `
    -Path (Join-Path $root "tools\redis") `
    -Recurse `
    -Filter "redis-cli.exe" `
    -ErrorAction SilentlyContinue |
    Select-Object -First 1 -ExpandProperty FullName
. (Join-Path $PSScriptRoot "process-utils.ps1")

foreach ($process in Get-Process nginx -ErrorAction SilentlyContinue) {
    try {
        if ($process.Path -and $process.Path -eq $nginx) {
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        }
    } catch {
        Write-Verbose "Cannot inspect Nginx process $($process.Id)."
    }
}

$stoppedProcessIds = @()
foreach ($port in @(80, 8080, 9000, 9092, 9093)) {
    foreach ($processId in Get-ListenerProcessIds -Port $port) {
        $stoppedProcessIds += $processId
        Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
    }
}

foreach ($processId in ($stoppedProcessIds | Sort-Object -Unique)) {
    Wait-Process -Id $processId -Timeout 8 -ErrorAction SilentlyContinue
}

Start-Sleep -Seconds 2
$kafkaProcessIds = @(
    Get-ListenerProcessIds -Port 9092
    Get-ListenerProcessIds -Port 9093
) | Sort-Object -Unique
foreach ($processId in $kafkaProcessIds) {
    & taskkill.exe /F /T /PID $processId 2>$null | Out-Null
}
foreach ($processId in $kafkaProcessIds) {
    Wait-Process -Id $processId -Timeout 8 -ErrorAction SilentlyContinue
}

if ($redisCli) {
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & $redisCli -h 127.0.0.1 -p 6379 shutdown save 2>$null | Out-Null
    } catch {
        Write-Verbose "Redis was already stopped."
    } finally {
        $ErrorActionPreference = $previousPreference
    }
}

Write-Host "File Home local services stopped."
