# Runs sdkmanager with the correct environment, so no quoting gymnastics are needed.
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\sdkmanager.ps1 --list
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\sdkmanager.ps1 "platforms;android-36" "build-tools;36.0.0"
#
# Note: sdkmanager reports itself deprecated. Studio 2026 also ships a newer `android` CLI
# (android sdk ...) in the same bin directory - prefer it for new work once documented.

param([Parameter(ValueFromRemainingArguments = $true)][string[]]$SdkArgs)

$env:JAVA_HOME    = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = 'C:\Users\<user>\AppData\Local\Android\Sdk'

& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" @SdkArgs
exit $LASTEXITCODE
