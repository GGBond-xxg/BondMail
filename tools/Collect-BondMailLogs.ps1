param(
    [string]$Serial,
    [string]$AdbPath,
    [string]$OutputDirectory = [Environment]::GetFolderPath('Desktop')
)

$ErrorActionPreference = 'Stop'
if (-not $AdbPath) {
    $adbCommand = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($adbCommand) { $AdbPath = $adbCommand.Source }
    else {
        $candidates = @(
            (Join-Path $PSScriptRoot 'adb.exe'),
            (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe')
        )
        $AdbPath = $candidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    }
}
if (-not $AdbPath -or -not (Test-Path -LiteralPath $AdbPath)) {
    throw 'ADB not found. Put these scripts beside adb.exe, or pass -AdbPath with its full path.'
}
$devices = @(& $AdbPath devices)
if ($LASTEXITCODE -ne 0) { throw 'Cannot start ADB.' }
$online = @($devices | ForEach-Object { if ($_ -match '^(.+?)\tdevice(?:\s|$)') { $Matches[1] } })
if (-not $Serial) {
    if ($online.Count -eq 1) { $Serial = $online[0] }
    elseif ($online.Count -gt 1) {
        for ($i = 0; $i -lt $online.Count; $i++) { Write-Host "[$($i + 1)] $($online[$i])" }
        $choice = 0
        if (-not [int]::TryParse((Read-Host 'Select the MAIN phone number'), [ref]$choice) -or
            $choice -lt 1 -or $choice -gt $online.Count) { throw 'Invalid device selection.' }
        $Serial = $online[$choice - 1]
    } else { throw 'No authorized phone. Enable USB debugging and accept the authorization prompt, then retry.' }
}
if ($Serial -notin $online) { throw 'Selected phone is not online.' }
$folder = Join-Path $OutputDirectory ('BondMail-Logs-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $folder | Out-Null
$utf8 = New-Object System.Text.UTF8Encoding($false)
function Save-AdbOutput([string]$Name, [string[]]$Command) {
    $result = @(& $AdbPath -s $Serial @Command 2>&1 | ForEach-Object { "$_" })
    [IO.File]::WriteAllLines((Join-Path $folder $Name), [string[]]$result, $utf8)
}
$info = @("Capture started: $(Get-Date -Format o)")
foreach ($property in @('ro.product.manufacturer', 'ro.product.model', 'ro.build.version.release', 'ro.build.version.sdk')) {
    $info += "$property = $(& $AdbPath -s $Serial shell getprop $property)"
}
$info += @(& $AdbPath -s $Serial shell dumpsys package com.bond.mail | Select-String 'versionName=|versionCode=') | ForEach-Object { "$_" }
[IO.File]::WriteAllLines((Join-Path $folder 'device-app.txt'), [string[]]$info, $utf8)

# Do not clear existing logs, change phone settings, restart the app, or read mail storage.
# Keep framework crash/native crash records across app process death; do not pin to a PID.
$arguments = @('-s', ('"' + $Serial + '"'), 'logcat', '-b', 'main', '-b', 'system', '-b', 'crash',
    '-v', 'threadtime', '-T', '1', 'AndroidRuntime:E', 'libc:F', 'DEBUG:F',
    'ActivityManager:I', 'ActivityTaskManager:W', 'WindowManager:W', 'SQLiteLog:E', '*:S')
$capture = $null
try {
    $capture = Start-Process -FilePath $AdbPath -ArgumentList $arguments -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $folder 'crash-live.txt') `
        -RedirectStandardError (Join-Path $folder 'adb-errors.txt')
    Start-Sleep -Seconds 1
    if ($capture.HasExited) { throw "Log capture stopped. See $folder\adb-errors.txt" }
    Write-Host ''
    Write-Host 'Recording. On the MAIN phone, open BondMail and reproduce the Sent-folder crash.'
    Write-Host 'After it crashes, return here and press Enter. Do not close this window.'
    [void](Read-Host)
} finally {
    if ($capture -and -not $capture.HasExited) { $capture.Kill(); $capture.WaitForExit() }
}
Save-AdbOutput 'app-exit-info.txt' @('shell', 'dumpsys', 'activity', 'exit-info', 'com.bond.mail')
[IO.File]::WriteAllText((Join-Path $folder 'reproduction.txt'),
    "Capture finished: $(Get-Date -Format o)`r`nPlease add: folder, account provider, scroll position (OSL messages), delay before crash, and any taps or gestures.`r`n", $utf8)
$zip = "$folder.zip"
Compress-Archive -LiteralPath @(Get-ChildItem -LiteralPath $folder -File | Select-Object -ExpandProperty FullName) -DestinationPath $zip
Write-Host "Saved: $zip"
Write-Host 'Send this ZIP to Codex. Logs may include account names/addresses; review before sharing.'
