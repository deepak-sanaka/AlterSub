<#
.SYNOPSIS
Read-only Netflix identification probes for a connected Android TV.
.EXAMPLE
.\tools\probe_netflix_metadata.ps1 -Serial 192.168.29.33:5555
.EXAMPLE
.\tools\probe_netflix_metadata.ps1 -Serial USB_SERIAL -TvAddress 192.168.1.20
.NOTES
Does not launch, pause, pair with, or modify Netflix. No screen or audio capture.
Reports go into git-ignored device-logs because they can contain viewing metadata.
Requires ADB and PowerShell 5.1 or later. LAN probes use HTTP without a proxy.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [string]$TvAddress,
    [string]$AdbPath,
    [ValidateRange(1, 65535)][int]$DialPort = 8008
)

$ErrorActionPreference = 'Stop'
if (-not $AdbPath) {
    $taskAdbCommand = Get-Command adb -ErrorAction SilentlyContinue
    if ($taskAdbCommand) { $AdbPath = $taskAdbCommand.Source }
    else { $AdbPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe' }
}
if (-not (Test-Path -LiteralPath $AdbPath -PathType Leaf)) { throw "ADB not found: $AdbPath" }
if (-not $TvAddress -and $Serial -match '^([0-9.]+):[0-9]+$') { $TvAddress = $Matches[1] }
if ($TvAddress) {
    $taskIp = $null
    if (-not [Net.IPAddress]::TryParse($TvAddress, [ref]$taskIp)) {
        throw 'TvAddress must be a numeric IP address.'
    }
    $TvAddress = $taskIp.ToString()
    if ($taskIp.AddressFamily -eq [Net.Sockets.AddressFamily]::InterNetworkV6) {
        $TvAddress = "[$TvAddress]"
    }
}

$taskState = & $AdbPath -s $Serial get-state 2>&1
if ($LASTEXITCODE -ne 0 -or $taskState -notcontains 'device') { throw "TV unavailable: $taskState" }
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskReportDirectory = Join-Path $taskRoot ('device-logs\netflix-probe-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $taskReportDirectory -Force | Out-Null
$taskReportPath = Join-Path $taskReportDirectory 'report.txt'
$taskReport = [Collections.Generic.List[string]]::new()

function Add-Result([string]$Label, [string]$Value) {
    Write-Host "$Label`: $Value"
    $taskReport.Add("$Label`: $Value")
}

function Read-Adb([string[]]$Arguments, [string]$FileName) {
    $taskOutput = & $AdbPath -s $Serial shell @Arguments 2>&1
    $taskExit = $LASTEXITCODE
    $taskText = ($taskOutput | ForEach-Object { "$_" }) -join "`n"
    $taskText | Set-Content -LiteralPath (Join-Path $taskReportDirectory $FileName) -Encoding UTF8
    if ($taskExit -ne 0) { Add-Result $FileName "ADB failed ($taskExit): $taskText" }
    return $taskText
}

Add-Result 'Time' (Get-Date -Format 'o')
Add-Result 'Serial' $Serial
$taskPackage = Read-Adb @('dumpsys', 'package', 'com.netflix.ninja') 'package.txt'
Add-Result 'Netflix version' ([regex]::Match($taskPackage, 'versionName=([^\r\n]+)').Groups[1].Value)
$taskSessions = Read-Adb @('dumpsys', 'media_session') 'media-session.txt'
# Keep the full dump on disk: the queue and metadata lines must belong to Netflix,
# rather than a different player's session elsewhere in the dump.
Add-Result 'Media session' 'Saved metadata, queue, active item ID and playback state in media-session.txt'
$taskActivities = Read-Adb @('dumpsys', 'activity', 'activities') 'activities.txt'
Add-Result 'Netflix intents' (($taskActivities -split "`n" | Where-Object {
    $_ -match '(?:intent=|Intent \{).*com\.netflix\.ninja|mResumedActivity'
}) -join "`n")
$taskProvider = Read-Adb @('content', 'query', '--uri', 'content://com.netflix.mediaclient.preapp') 'preapp-provider.txt'
Add-Result 'Exported preapp provider query' $taskProvider
$taskService = Read-Adb @('dumpsys', 'activity', 'service', 'com.netflix.ninja/.NetflixService') 'netflix-service.txt'
Add-Result 'Netflix service dump' $taskService

if ($TvAddress) {
    Add-Type -AssemblyName System.Net.Http
    $taskHandler = [Net.Http.HttpClientHandler]::new()
    $taskHandler.UseProxy = $false
    $taskHandler.AllowAutoRedirect = $false
    $taskClient = [Net.Http.HttpClient]::new($taskHandler)
    $taskClient.Timeout = [TimeSpan]::FromSeconds(4)

    function Read-Http([string]$Url, [string]$FileName) {
        $taskResponse = $null
        try {
            $taskResponse = $taskClient.GetAsync($Url).GetAwaiter().GetResult()
            $taskBody = $taskResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            $taskBody | Set-Content -LiteralPath (Join-Path $taskReportDirectory $FileName) -Encoding UTF8
            Add-Result $Url ("HTTP " + [int]$taskResponse.StatusCode + ' ' + $taskBody)
            if ($taskResponse.IsSuccessStatusCode) { return $taskBody }
        } catch { Add-Result $Url $_.Exception.GetBaseException().Message }
        finally { if ($taskResponse) { $taskResponse.Dispose() } }
        return $null
    }

    try {
        $taskDialUrl = "http://$TvAddress`:$DialPort"
        $null = Read-Http "$taskDialUrl/ssdp/device-desc.xml" 'dial-device.xml'
        $taskDial = Read-Http "$taskDialUrl/apps/Netflix" 'dial-netflix.xml'
        # Extract only the advertised port, never follow a server-supplied host or URL.
        # No XML entity processing; DIAL output is untrusted.
        $taskPortMatch = [regex]::Match([string]$taskDial, '<port>\s*([0-9]{1,5})\s*</port>')
        if ($taskPortMatch.Success) {
            $taskMdxPort = [int]$taskPortMatch.Groups[1].Value
            if ($taskMdxPort -ge 1 -and $taskMdxPort -le 65535) {
                $null = Read-Http "http://$TvAddress`:$taskMdxPort/" 'mdx-health.txt'
                $taskSocket = [Net.WebSockets.ClientWebSocket]::new()
                $taskSocket.Options.Proxy = $null
                $taskCancel = [Threading.CancellationTokenSource]::new(4000)
                try {
                    # Handshake only: no player commands, pairing request or authentication data.
                    $taskSocket.ConnectAsync([Uri]"ws://$TvAddress`:$taskMdxPort/", $taskCancel.Token).GetAwaiter().GetResult()
                    Add-Result 'MDX WebSocket' "Handshake accepted ($($taskSocket.State)); this alone does not identify content"
                } catch { Add-Result 'MDX WebSocket' $_.Exception.GetBaseException().Message }
                finally { $taskSocket.Dispose(); $taskCancel.Dispose() }
            }
        }
    } finally { $taskClient.Dispose(); $taskHandler.Dispose() }
} else {
    Add-Result 'LAN probes' 'Skipped; supply -TvAddress to inspect DIAL and the advertised MDX endpoint'
}

Add-Result 'Interpretation' 'A running Netflix app or a successful health response is not a title. Check results during actual playback; blank fields are inconclusive on other versions.'
$taskReport | Set-Content -LiteralPath $taskReportPath -Encoding UTF8
Write-Host "Report: $taskReportPath"
