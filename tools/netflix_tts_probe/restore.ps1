param(
    [Parameter(Mandatory = $true)][string]$CaptureDirectory,
    [string]$Serial = '192.168.29.33:5555',
    [string]$SdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
)
$ErrorActionPreference = 'Stop'
$adbPath = Join-Path $SdkRoot 'platform-tools\adb.exe'
$original = Get-Content (Join-Path $CaptureDirectory 'original-settings.json') | ConvertFrom-Json -AsHashtable
function Quote-AndroidShell([string]$Value) {
    # POSIX single-quoted argument, including embedded single quotes.
    $replacement = "'" + '"' + "'" + '"' + "'"
    return "'" + $Value.Replace("'", $replacement) + "'"
}
function Restore-Setting([string]$Key, $Value) {
    if ($Key -notmatch '^(tts_[a-z_]+|enabled_accessibility_services|accessibility_enabled|touch_exploration_enabled|touch_exploration_granted_accessibility_services)$') {
        throw "Unexpected setting in backup: $Key"
    }
    $command = if ($null -eq $Value) {
        'settings delete secure ' + (Quote-AndroidShell $Key)
    } else {
        'settings put secure ' + (Quote-AndroidShell $Key) + ' ' + (Quote-AndroidShell ([string]$Value))
    }
    & $adbPath -s $Serial shell $command
    if ($LASTEXITCODE -ne 0) { throw "Failed to restore $Key" }
}
foreach ($key in @('enabled_accessibility_services', 'accessibility_enabled')) {
    Restore-Setting $key $original[$key]
}
foreach ($key in @($original.Keys | Where-Object { $_ -notin @('enabled_accessibility_services','accessibility_enabled') })) {
    Restore-Setting $key $original[$key]
}
$currentLines = @(& $adbPath -s $Serial shell settings list secure)
foreach ($line in @($currentLines | Where-Object { $_.StartsWith('tts_') })) {
    $key = ($line -split '=', 2)[0]
    if (-not $original.ContainsKey($key)) { Restore-Setting $key $null }
}
& $adbPath -s $Serial uninstall com.altersub.ttsprobe
if ($LASTEXITCODE -ne 0) { throw 'Probe uninstall failed; inspect before retrying' }
& $adbPath -s $Serial shell settings list secure |
    Where-Object { $_ -match '^(tts_|accessibility_|enabled_accessibility_services|touch_exploration)' } |
    Set-Content (Join-Path $CaptureDirectory 'restored-speech-settings.txt')
$difference = Compare-Object (Get-Content (Join-Path $CaptureDirectory 'original-speech-settings.txt')) (Get-Content (Join-Path $CaptureDirectory 'restored-speech-settings.txt'))
if ($difference) { $difference; throw 'Speech/accessibility settings do not match the saved original' }
& $adbPath -s $Serial shell dumpsys accessibility | Set-Content (Join-Path $CaptureDirectory 'restored-accessibility-state.txt')
'Original speech/accessibility settings match; temporary probe uninstalled.'
