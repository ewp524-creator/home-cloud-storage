$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$kafkaHome = Join-Path $root "tools\kafka\kafka_2.13-4.4.0"
$template = Join-Path $root "config\kafka.properties"
$gitCommand = Get-Command git -ErrorAction Stop
$gitRoot = Split-Path -Parent (Split-Path -Parent $gitCommand.Source)
$gitBash = Join-Path $gitRoot "bin\bash.exe"
$data = Join-Path $root ".kafka-data"
$runtimeConfig = Join-Path $data "kafka.runtime.properties"
$logs = Join-Path $root "logs"
. (Join-Path $PSScriptRoot "process-utils.ps1")

if (-not (Test-Path -LiteralPath (Join-Path $kafkaHome "bin\kafka-server-start.sh"))) {
    throw "Kafka runtime is missing under $kafkaHome"
}
if (-not (Test-Path -LiteralPath $gitBash)) {
    throw "Git Bash is required to run Kafka on Windows: $gitBash"
}

if (Test-TcpPort -Port 9092) {
    Write-Host "Kafka already running on 127.0.0.1:9092"
    exit 0
}

New-Item -ItemType Directory -Path $data -Force | Out-Null
New-Item -ItemType Directory -Path $logs -Force | Out-Null

$configuration = Get-Content -LiteralPath $template -Raw
$configuration = $configuration.Replace(
    "@FILEHOME_KAFKA_DATA@",
    $data.Replace("\", "/")
)
[System.IO.File]::WriteAllText(
    $runtimeConfig,
    $configuration,
    [System.Text.Encoding]::ASCII
)

$drive = $root.Substring(0, 1).ToLowerInvariant()
$bashRoot = "/$drive/" + $root.Substring(3).Replace("\", "/")
$bashHome = "$bashRoot/tools/kafka/kafka_2.13-4.4.0"
$bashConfig = "$bashRoot/.kafka-data/kafka.runtime.properties"
$fileRoot = "file:/" + $root.Replace("\", "/")
$toolsLog4j = "$fileRoot/tools/kafka/kafka_2.13-4.4.0/config/tools-log4j2.yaml"
$serverLog4j = "$fileRoot/tools/kafka/kafka_2.13-4.4.0/config/log4j2.yaml"

if (-not (Test-Path -LiteralPath (Join-Path $data "meta.properties"))) {
    $clusterId = (& $gitBash --noprofile --norc -c "cd '$bashRoot' && KAFKA_LOG4J_OPTS='-Dlog4j.configurationFile=$toolsLog4j' '$bashHome/bin/kafka-storage.sh' random-uuid" |
        Where-Object { $_ -match '^[A-Za-z0-9_-]{16,}$' } |
        Select-Object -Last 1)
    if ($LASTEXITCODE -ne 0 -or -not $clusterId) {
        throw "Could not generate Kafka cluster id."
    }
    & $gitBash --noprofile --norc -c "cd '$bashRoot' && KAFKA_LOG4J_OPTS='-Dlog4j.configurationFile=$toolsLog4j' '$bashHome/bin/kafka-storage.sh' format --standalone -t '$clusterId' -c '$bashConfig'"
    if ($LASTEXITCODE -ne 0) {
        throw "Could not format Kafka storage."
    }
}

$startCommand = "cd '$bashRoot' && KAFKA_LOG4J_OPTS='-Dlog4j.configurationFile=$serverLog4j' exec '$bashHome/bin/kafka-server-start.sh' '$bashConfig'"
Start-Process -FilePath $gitBash `
    -ArgumentList "--noprofile --norc -c `"$startCommand`"" `
    -WorkingDirectory $root `
    -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $logs "kafka.out.log") `
    -RedirectStandardError (Join-Path $logs "kafka.err.log")

for ($attempt = 0; $attempt -lt 120; $attempt++) {
    if (Test-TcpPort -Port 9092) {
        Write-Host "Kafka started on 127.0.0.1:9092"
        exit 0
    }
    Start-Sleep -Milliseconds 500
}

throw "Kafka did not start. Check logs\kafka.err.log."
