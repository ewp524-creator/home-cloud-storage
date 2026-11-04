function Test-TcpPort {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Port,
        [string]$HostName = "127.0.0.1",
        [int]$TimeoutMilliseconds = 400
    )

    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $async = $client.BeginConnect($HostName, $Port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne($TimeoutMilliseconds)) {
            return $false
        }
        $client.EndConnect($async)
        return $true
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Get-ListenerProcessId {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Port
    )

    $listener = Get-NetTCPConnection `
        -State Listen `
        -LocalPort $Port `
        -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($listener) {
        return $listener.OwningProcess
    }

    $netstat = (netstat -ano) -join "`n"
    $match = [regex]::Match(
        $netstat,
        "(?m)^\s*TCP\s+\S+:" + $Port + "\s+\S+\s+LISTENING\s+(\d+)\s*$"
    )
    if ($match.Success) {
        return [int]$match.Groups[1].Value
    }
    return $null
}

function Get-ListenerProcessIds {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Port
    )

    $processIds = @()
    $processIds += Get-NetTCPConnection `
        -State Listen `
        -LocalPort $Port `
        -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess

    $netstat = (netstat -ano) -join "`n"
    $matches = [regex]::Matches(
        $netstat,
        "(?m)^\s*TCP\s+\S+:" + $Port + "\s+\S+\s+LISTENING\s+(\d+)\s*$"
    )
    foreach ($match in $matches) {
        $processIds += [int]$match.Groups[1].Value
    }
    return $processIds |
        Where-Object { $_ -and $_ -gt 0 } |
        Sort-Object -Unique
}
