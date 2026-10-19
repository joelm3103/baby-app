# Sets the Android toolchain environment for this machine (user scope).
#
# Re-runnable and idempotent. Deliberately does NOT use `setx`, which truncates
# PATH at 1024 characters and can silently destroy it.
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android-env.ps1

$ErrorActionPreference = 'Stop'

$Jbr      = 'C:\Program Files\Android\Android Studio\jbr'
# Derived, not hard-coded: an explicit ANDROID_SDK_ROOT / ANDROID_HOME wins, otherwise
# the per-user default Studio uses. Keeps a username out of the repo and works on any account.
$Sdk      = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT }
            elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME }
            else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$PathAdds = @("$Sdk\platform-tools", "$Sdk\cmdline-tools\latest\bin")

if (-not (Test-Path "$Jbr\bin\java.exe")) {
    throw "Android Studio's bundled JDK not found at $Jbr - is Android Studio installed?"
}
if (-not (Test-Path "$Sdk\platform-tools\adb.exe")) {
    Write-Warning "adb not found under $Sdk - run: scripts\sdkmanager.ps1 'platform-tools'"
}

[Environment]::SetEnvironmentVariable('JAVA_HOME',    $Jbr, 'User')
[Environment]::SetEnvironmentVariable('ANDROID_HOME', $Sdk, 'User')

$cur = [Environment]::GetEnvironmentVariable('Path', 'User')
if ([string]::IsNullOrEmpty($cur)) { $cur = '' }
$parts = @($cur -split ';' | Where-Object { $_ -ne '' })
foreach ($p in $PathAdds) {
    if ($parts -notcontains $p) { $parts = @($p) + $parts }
}
[Environment]::SetEnvironmentVariable('Path', ($parts -join ';'), 'User')

Write-Output "JAVA_HOME    = $([Environment]::GetEnvironmentVariable('JAVA_HOME','User'))"
Write-Output "ANDROID_HOME = $([Environment]::GetEnvironmentVariable('ANDROID_HOME','User'))"
Write-Output ''
Write-Output 'PATH (user)  ='
$([Environment]::GetEnvironmentVariable('Path','User') -split ';') | ForEach-Object { Write-Output "  $_" }
Write-Output ''
Write-Output "NOTE: a shell's 'java -version' still reports 1.8.0_461. That comes from the Oracle"
Write-Output '      java8path shim in the MACHINE path, which always precedes user-path entries.'
Write-Output '      Gradle and Android Studio use JAVA_HOME, so builds are unaffected. Leave it alone.'
