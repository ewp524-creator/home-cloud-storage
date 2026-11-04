param(
    [switch]$Foreground
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$redis = Get-ChildItem `
    -Path (Join-Path $root "tools\redis") `
    -Recurse `
    -Filter "redis-server.exe" `
    -ErrorAction Stop |
    Select-Object -First 1 -ExpandProperty FullName
$redisCli = Join-Path (Split-Path -Parent $redis) "redis-cli.exe"
$data = Join-Path $root ".redis-data"
$logs = Join-Path $root "logs"
$template = Join-Path $root "config\redis.conf"
$runtimeConfig = Join-Path $data "redis.runtime.conf"
$runtimeConfigRelative = ".redis-data/redis.runtime.conf"

New-Item -ItemType Directory -Path $data -Force | Out-Null
New-Item -ItemType Directory -Path $logs -Force | Out-Null

$configuration = Get-Content -LiteralPath $template -Raw
$configuration = $configuration.Replace(
    "@FILEHOME_ROOT@",
    $root.Replace("\", "/")
)
[System.IO.File]::WriteAllText(
    $runtimeConfig,
    $configuration,
    [System.Text.Encoding]::ASCII
)

function Test-RedisReady {
    try {
        $response = & $redisCli -h 127.0.0.1 -p 6379 ping 2>$null
        return $LASTEXITCODE -eq 0 -and $response -eq "PONG"
    } catch {
        return $false
    }
}

if (Test-RedisReady) {
    Write-Host "Redis is already listening on 127.0.0.1:6379"
    exit 0
}

if ($Foreground) {
    Push-Location $root
    try {
        & $redis $runtimeConfigRelative
    } finally {
        Pop-Location
    }
    exit $LASTEXITCODE
}

Start-Process `
    -FilePath $redis `
    -ArgumentList @($runtimeConfigRelative) `
    -WorkingDirectory $root `
    -WindowStyle Hidden

for ($attempt = 0; $attempt -lt 20; $attempt++) {
    if (Test-RedisReady) {
        Write-Host "Redis started on 127.0.0.1:6379"
        exit 0
    }
    Start-Sleep -Milliseconds 250
}

throw "Redis failed to start. Check logs\redis.log"
