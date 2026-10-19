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
  [switch]$SkipInstall,
  [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$apk = Join-Path $repo "app\build\outputs\apk\debug\app-debug.apk"
$pkg = "app.babylog"

if (-not (Test-Path $adb)) { throw "adb not found at $adb - set the Android SDK path." }

# Serial: default to the one attached device, and fail loudly if ambiguous.
#
# NOTE the @(): without it a pipeline that yields ONE item assigns a scalar STRING, and $devices[0] on
# a string returns its first CHARACTER - so a phone serialised as <handset> became "R", and
# `adb -s R` then blocked forever waiting for a device that does not exist.
$devices = @(
  foreach ($line in (& $adb devices)) {
    if ($line -match '^(\S+)\s+device\s*$') { $Matches[1] }
  }
)
if (-not $Serial) {
  if ($devices.Count -eq 0) { throw "No device in 'adb devices'. Connect the phone and enable USB debugging." }
  if ($devices.Count -gt 1) { throw "Found $($devices.Count) devices ($($devices -join ', ')). Pass -Serial." }
  $Serial = $devices[0]
}
# Re-validate anything the caller passed, so a typo fails here instead of hanging in adb.
if ($devices -notcontains $Serial) { throw "Serial '$Serial' is not attached. Attached: $($devices -join ', ')" }
Write-Host "device  : $Serial"

if ($DryRun) {
  Write-Host "dry run : resolving device only, sending nothing to the phone"
  Write-Host "apk     : $apk (exists: $(Test-Path $apk))"
  exit 0
}

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
  # Write on the device, then `adb pull`. Do NOT capture `exec-out screencap -p` into a PowerShell
  # variable: PowerShell decodes a native command's stdout as TEXT, which corrupts the PNG, and `>`
  # redirection adds a BOM and CRLF translation. `pull` copies bytes.
  $remote = "/sdcard/babylog-shot.png"
  $path = Join-Path $outDir "$name.png"
  & $adb -s $Serial shell screencap -p $remote | Out-Null
  & $adb -s $Serial pull $remote $path | Out-Null
  $size = (Get-Item $path).Length
  if ($size -lt 1000) { throw "Screenshot $name is only $size bytes - the capture is broken." }
  Write-Host ("shot    : {0} ({1:N0} bytes)" -f $name, $size)
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
Write-Host "Add an entry to docs/timeline/README.md with the commit, what changed, and WHY."
