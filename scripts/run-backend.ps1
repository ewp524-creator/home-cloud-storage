param(
    [int]$Port = 8080
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root "backend\backend"
$secrets = Join-Path $root ".secrets\filehome.env.ps1"
$repository = Join-Path $root ".m2repo"

if (-not (Test-Path -LiteralPath $secrets)) {
    throw "Missing local secrets file: $secrets"
}
. $secrets

$maven = Get-ChildItem `
    -Path (Join-Path $env:USERPROFILE ".m2\wrapper\dists\apache-maven-3.10.0\*\bin\mvn.cmd") `
    -ErrorAction Stop |
    Select-Object -First 1 -ExpandProperty FullName

Push-Location $backend
try {
    & $maven `
        "-Dmaven.repo.local=$repository" `
        "spring-boot:run" `
        "-Dspring-boot.run.arguments=--server.port=$Port"
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
