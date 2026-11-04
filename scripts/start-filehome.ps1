$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$minio = Join-Path $root "nginx\io\minio.exe"
$storageConfig = Join-Path $root ".secrets\storage.path"
$storage = if ($env:FILEHOME_WINDOWS_STORAGE) {
    $env:FILEHOME_WINDOWS_STORAGE
} elseif (Test-Path -LiteralPath $storageConfig) {
    (Get-Content -LiteralPath $storageConfig -Raw).Trim()
} else {
    Join-Path $root "data\storage"
}
if (-not $storage) {
    throw "Storage path is empty. Configure FILEHOME_WINDOWS_STORAGE or .secrets\storage.path."
}
$env:FILEHOME_STORAGE_DATA_ROOT = $storage
$backendRunner = Join-Path $root "scripts\run-backend.ps1"
$redisRunner = Join-Path $root "scripts\run-redis.ps1"
$kafkaRunner = Join-Path $root "scripts\run-kafka.ps1"
$nginx = Join-Path $root "nginx\nginx-1.30.6\nginx.exe"
$nginxTemplate = Join-Path $root "nginx\filehome.conf"
$nginxConfig = Join-Path $root "nginx\nginx-1.30.6\conf\filehome.runtime.conf"
$secrets = Join-Path $root ".secrets\filehome.env.ps1"
$logs = Join-Path $root "logs"
$nginxLogs = Join-Path $root "nginx\nginx-1.30.6\logs"
. (Join-Path $PSScriptRoot "process-utils.ps1")

if (-not (Test-Path -LiteralPath $secrets)) {
    throw "Missing local secrets file: $secrets"
}
. $secrets
New-Item -ItemType Directory -Path $logs -Force | Out-Null
New-Item -ItemType Directory -Path $nginxLogs -Force | Out-Null
New-Item -ItemType Directory -Path $storage -Force | Out-Null

$nginxRoot = $root.Replace("\", "/")
$nginxConfiguration = Get-Content -LiteralPath $nginxTemplate -Raw
$nginxConfiguration = $nginxConfiguration.Replace("@FILEHOME_ROOT@", $nginxRoot)
[System.IO.File]::WriteAllText(
    $nginxConfig,
    $nginxConfiguration,
    [System.Text.Encoding]::ASCII
)

function Wait-TcpPort {
    param(
        [int]$Port,
        [string]$Name,
        [int]$Attempts = 60
    )
    for ($attempt = 0; $attempt -lt $Attempts; $attempt++) {
        if (Test-TcpPort -Port $Port) {
            return
        }
        Start-Sleep -Milliseconds 500
    }
    throw "$Name did not start on port $Port."
}

function Wait-BackendHealth {
    for ($attempt = 0; $attempt -lt 120; $attempt++) {
        try {
            $health = Invoke-RestMethod `
                -Uri "http://127.0.0.1:8080/api/v1/health" `
                -TimeoutSec 2
            if ($health.status -eq "UP") {
                return
            }
        } catch {
            # The backend is still starting.
        }
        Start-Sleep -Milliseconds 500
    }
    throw "Backend did not become healthy. Check logs\backend.err.log."
}

& $redisRunner
& $kafkaRunner

if (-not (Test-TcpPort -Port 9000)) {
    $env:MINIO_ROOT_USER = $env:FILEHOME_MINIO_ACCESS_KEY
    $env:MINIO_ROOT_PASSWORD = $env:FILEHOME_MINIO_SECRET_KEY
    Start-Process -FilePath $minio `
        -ArgumentList @("server", $storage, "--address", "127.0.0.1:9000", "--console-address", "127.0.0.1:9001") `
        -WorkingDirectory $root -WindowStyle Hidden
}
Wait-TcpPort -Port 9000 -Name "MinIO"

if (-not (Test-TcpPort -Port 8080)) {
    Start-Process -FilePath "powershell.exe" `
        -ArgumentList @(
            "-NoProfile",
            "-ExecutionPolicy", "Bypass",
            "-File", $backendRunner,
            "-Port", "8080"
        ) `
        -WorkingDirectory $root `
        -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $logs "backend.out.log") `
        -RedirectStandardError (Join-Path $logs "backend.err.log")
}
Wait-BackendHealth

if (-not (Test-TcpPort -Port 80)) {
    Start-Process -FilePath $nginx `
        -ArgumentList @("-p", (Join-Path $root "nginx\nginx-1.30.6"), "-c", $nginxConfig) `
        -WorkingDirectory (Join-Path $root "nginx\nginx-1.30.6") -WindowStyle Hidden
} else {
    & $nginx `
        -p (Join-Path $root "nginx\nginx-1.30.6") `
        -c $nginxConfig `
        -s reload
}
Wait-TcpPort -Port 80 -Name "Nginx"

Write-Host "File Home services started:"
Write-Host "  Frontend: http://localhost/"
Write-Host "  Backend:  http://localhost:8080/api/v1/health"
Write-Host "  MinIO:    http://localhost:9001/"
Write-Host "  Redis:    127.0.0.1:6379"
