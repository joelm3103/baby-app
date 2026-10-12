<#
.SYNOPSIS
  Capture a dated set of screenshots of BabyLog from a connected phone into the visual timeline.

.DESCRIPTION
  Installs the current debug APK, launches the app, screenshots the top of the list and then each
  scroll position, and writes them to docs/timeline/<date>-<label>/.

  The screenshot history exists because a green build says nothing about how the app looks, and
  because the options that got rejected are as informative as the ones that shipped. Full resolution
  on purpose - a 4dp accent bar's corner artifact is invisible at 50%.

  SAFETY: refuses to send input unless BabyLog is the foreground activity. A scripted swipe once
  landed on an active phone call.

.EXAMPLE
  powershell -NoProfile -File scripts/capture-timeline.ps1 -Label tinted-sections
  powershell -NoProfile -File scripts/capture-timeline.ps1 -Label phase6-sheets -Scrolls 8
#>
param(
  [Parameter(Mandatory = $true)][string]$Label,
  [int]$Scrolls = 5,
  [string]$Serial = "",
  [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$apk = Join-Path $repo "app\build\outputs\apk\debug\app-debug.apk"
$pkg = "app.babylog"

if (-not (Test-Path $adb)) { throw "adb not found at $adb - set the Android SDK path." }

# Serial: default to the one attached device, and fail loudly if ambiguous.
$devices = (& $adb devices) -split "`r?`n" | Where-Object { $_ -match "\sdevice$" } |
           ForEach-Object { ($_ -split "\s+")[0] }
if (-not $Serial) {
  if ($devices.Count -ne 1) { throw "Expected exactly one device, found $($devices.Count): $($devices -join ', '). Pass -Serial." }
  $Serial = $devices[0]
}
Write-Host "device  : $Serial"

$outDir = Join-Path $repo ("docs\timeline\" + (Get-Date -Format "yyyy-MM-dd") + "-" + $Label)
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
Write-Host "output  : $outDir"

if (-not $SkipInstall) {
  & $adb -s $Serial shell am force-stop $pkg | Out-Null
  $install = & $adb -s $Serial install -r $apk
  Write-Host "install : $($install[-1])"
  if ($install -match "Failure") { throw "APK install failed." }
}

& $adb -s $Serial logcat -c | Out-Null
& $adb -s $Serial shell am start -n "$pkg/.MainActivity" | Out-Null
Start-Sleep -Seconds 8

function Get-Foreground { (& $adb -s $Serial shell dumpsys activity activities) | Select-String -Pattern "topResumedActivity" | Select-Object -First 1 }

$fg = Get-Foreground
if ($fg -notmatch $pkg) {
  throw "Refusing to send input: BabyLog is not the foreground app. Foreground was: $fg"
}

function Save-Shot([string]$name) {
  $path = Join-Path $outDir "$name.png"
  # exec-out, not shell: shell mangles binary output
  $bytes = & $adb -s $Serial exec-out screencap -p
  [System.IO.File]::WriteAllBytes($path, $bytes)
  Write-Host ("shot    : {0} ({1:N0} bytes)" -f $name, (Get-Item $path).Length)
}

Save-Shot "01-top"
for ($i = 1; $i -le $Scrolls; $i++) {
  & $adb -s $Serial shell input swipe 540 1900 540 500 180 | Out-Null
  Start-Sleep -Milliseconds 900
  Save-Shot ("{0:D2}-scrolled" -f ($i + 1))
}

$crash = (& $adb -s $Serial logcat -d) | Select-String -Pattern "FATAL EXCEPTION" | Select-Object -First 1
if ($crash) { Write-Warning "A FATAL EXCEPTION is in logcat - inspect before trusting these shots." } else { Write-Host "crash   : none" }

Write-Host ""
Write-Host "Add an entry to docs/timeline/README.md with the commit hash, what changed, and WHY."
